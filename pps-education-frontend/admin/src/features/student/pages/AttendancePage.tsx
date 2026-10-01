import React, { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Download, History, PenLine, Save } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import {
  AttendanceMarkResponse,
  ClassSessionResponse,
  EnterAttendanceMarkRequest,
  exportClassAttendanceSummary,
  getAttendanceSession,
  listClassEnrollments,
  listClassSessions,
  markAttendance,
  submitAttendance
} from "@/features/academic/api";
import { useEligibleClasses } from "@/features/academic/hooks/useEligibleClasses";
import { useAttendanceGracePeriodMinutes } from "@/features/academic/hooks/useAttendanceGracePeriodMinutes";
import AttendanceHistoryPanel from "@/features/academic/components/AttendanceHistoryPanel";
import StudentNameLink from "@/features/reports/components/StudentNameLink";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Select from "@/components/ui/Select";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import DatePicker from "@/components/ui/DatePicker";
import { downloadBlob } from "@/lib/xlsxTemplate";
import { toISODate } from "@/lib/calendarDates";
import AttendanceReminderBanner from "@/features/hrm/components/AttendanceReminderBanner";

type SimpleStatus = "PRESENT" | "ABSENT" | "EXCUSED" | "LATE";

interface Row {
  studentId: number;
  studentFullName: string;
  studentCode: string;
  status: SimpleStatus;
}

function toSimpleStatus(status: EnterAttendanceMarkRequest["status"]): SimpleStatus {
  if (status === "PRESENT") return "PRESENT";
  if (status === "EXCUSED") return "EXCUSED";
  if (status === "LATE" || status === "EARLY_LEAVE") return "LATE";
  return "ABSENT";
}

/** UC-15 "Sự kiện kích hoạt": chỉ điểm danh được từ khi buổi học bắt đầu — chặn chọn buổi tương lai (cùng logic ClassDetailPanel.tsx). */
function hasSessionStarted(s: ClassSessionResponse): boolean {
  return new Date(`${s.sessionDate}T${s.startTime}`) <= new Date();
}

/** Dùng để lọc dropdown chọn buổi học (danh sách chỉ hiển thị buổi trong ngày, kể cả đã hết giờ, để GV còn xem lại). */
function isToday(s: ClassSessionResponse): boolean {
  return s.sessionDate === new Date().toISOString().slice(0, 10);
}

/**
 * Sửa đổi nghiệp vụ 2026-08-18 (đã xác nhận với người dùng), MỞ RỘNG
 * 2026-08-22 + đồng bộ lại 2026-08-27: GV thường chỉ được điểm danh/sửa/
 * submit TRONG khung giờ buổi học [startTime, endTime + gracePeriodMinutes]
 * — đồng bộ với StudentAttendanceService.isWithinSessionWindow (backend
 * chặn cả API, không chỉ khóa nút ở UI). gracePeriodMinutes đọc từ
 * system_settings.student_attendance.grace_period_minutes qua
 * useAttendanceGracePeriodMinutes — trước đây hardcode [startTime, endTime]
 * không cộng grace, khoá nút sớm hơn backend cho phép.
 */
function isWithinAttendanceWindow(s: ClassSessionResponse, gracePeriodMinutes: number): boolean {
  const now = new Date();
  const start = new Date(`${s.sessionDate}T${s.startTime}`);
  const end = graceEndDate(s, gracePeriodMinutes);
  return now >= start && now <= end;
}

function graceEndDate(s: ClassSessionResponse, gracePeriodMinutes: number): Date {
  const end = new Date(`${s.sessionDate}T${s.endTime}`);
  return new Date(end.getTime() + gracePeriodMinutes * 60_000);
}

/** HH:mm:ss local — cùng định dạng với ClassSessionResponse.endTime, để hiển thị khớp thông báo backend (graceEnd). */
function formatTime(d: Date): string {
  return d.toTimeString().slice(0, 8);
}

const sessionStatusVariant: Record<string, BadgeVariant> = {
  DRAFT: "warning",
  SUBMITTED: "success",
  LOCKED: "success"
};

export default function AttendancePage() {
  const { t } = useTranslation("student");
  const { hasPermission, selectedClassId: globalClassId } = useApp();
  // Quyền quản trị điểm danh vượt rào "chỉ trong khung giờ buổi học" của Giáo viên thường (xem isWithinAttendanceWindow).
  const hasAttendanceOverride = hasPermission("academic.attendance.create") || hasPermission("academic.attendance.update");
  const [searchParams, setSearchParams] = useSearchParams();
  // classId trên URL (deep-link từ Quản lý lớp học/ClassDetailPanel — mở đúng buổi cụ thể) được ưu
  // tiên hơn lớp đang chọn ở Header; không có thì mới rơi về lớp global (UC-15 không còn dropdown/
  // card chọn lớp riêng trên trang này nữa).
  const classIdParam = searchParams.get("classId");
  const sessionIdParam = searchParams.get("sessionId");
  const selectedClassId = classIdParam ? Number(classIdParam) : globalClassId;
  const selectedSessionId = sessionIdParam ? Number(sessionIdParam) : null;

  // Bổ sung ngoài SDD gốc (xác nhận với người dùng 2026-10-01, bug): classId trên URL ưu tiên Header
  // (xem comment trên) nhưng trước đây KHÔNG BAO GIỜ bị xoá — hễ đã chọn 1 buổi (pickSession ghi classId
  // vào URL) thì đổi điểm trường/lớp ở Header sau đó không còn tác dụng gì trên trang này nữa, URL cũ
  // ghim cứng mãi. globalClassId chỉ đổi khi Header TỰ reset (đổi điểm trường, xem AppContext#
  // setSelectedCampusId) hoặc người dùng bấm chọn lớp khác ở Header (Header.tsx không có auto-select lớp
  // nào khác) — ref chặn lần chạy đầu lúc mount để không xoá mất deep-link classId ban đầu (vd từ
  // ClassDetailPanel/thông báo), chỉ xoá khi globalClassId THỰC SỰ đổi sau đó.
  const globalClassIdRef = useRef(globalClassId);
  useEffect(() => {
    if (globalClassIdRef.current === globalClassId) return;
    globalClassIdRef.current = globalClassId;
    if (classIdParam || sessionIdParam) setSearchParams({});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [globalClassId]);

  const { classes, loading: loadingClasses } = useEligibleClasses();
  const [tab, setTab] = useState<"today" | "history">("today");
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [rows, setRows] = useState<Row[]>([]);
  const [attendanceMode, setAttendanceMode] = useState<"SESSION_LEVEL" | "PERIOD_LEVEL">("SESSION_LEVEL");
  const [sessionStatus, setSessionStatus] = useState<string | null>(null);
  const [loadingRows, setLoadingRows] = useState(false);
  const [saving, setSaving] = useState(false);
  const [notification, setNotification] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // V203 — xuất Excel tổng hợp chuyên cần của lớp đang chọn (Trưởng phòng đào tạo cần xuất báo cáo quản lý).
  const [summaryOpen, setSummaryOpen] = useState(false);
  const [summaryFrom, setSummaryFrom] = useState("");
  const [summaryTo, setSummaryTo] = useState("");
  const [summaryExporting, setSummaryExporting] = useState(false);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;
  const selectedSession = sessions.find((s) => s.id === selectedSessionId) ?? null;
  // Tick vô hại, chỉ để ép re-render mỗi 30s — cho nút Submit tự làm mờ đúng lúc hết giờ buổi học
  // mà không cần GV thao tác/reload lại trang (isWithinAttendanceWindow phụ thuộc "now" tại thời điểm render).
  const [, forceTick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => forceTick((t) => t + 1), 30_000);
    return () => clearInterval(id);
  }, []);
  // Sửa đổi 2026-08-18: SUBMITTED không tự khoá sửa — GV vẫn sửa được, nhưng CHỈ trong khung giờ
  // buổi học [startTime, endTime + gracePeriodMinutes] (không còn "tới hết ngày" như rule V45 cũ).
  // Tài khoản có quyền quản trị điểm danh vượt rào này.
  const gracePeriodMinutes = useAttendanceGracePeriodMinutes();
  const locked = !hasAttendanceOverride && !!selectedSession && !isWithinAttendanceWindow(selectedSession, gracePeriodMinutes);
  // V202 — vai trò chỉ có quyền xem điểm danh (VD Quản lý điểm trường) vào trang để xem, không thấy ô chọn và nút lưu.
  const canMark = hasPermission("academic.attendance.mark") || hasAttendanceOverride;
  const lockedReason = !selectedSession
    ? null
    : new Date() < new Date(`${selectedSession.sessionDate}T${selectedSession.startTime}`)
      ? t("attendancePage.notYetStarted", { time: selectedSession.startTime })
      : t("attendancePage.alreadyEnded", { time: formatTime(graceEndDate(selectedSession, gracePeriodMinutes)) });

  useEffect(() => {
    if (!selectedClassId) {
      setSessions([]);
      return;
    }
    listClassSessions(selectedClassId)
      .then(setSessions)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("attendancePage.loadSessionsError")));
  }, [selectedClassId]);

  useEffect(() => {
    if (!selectedClassId || !selectedSessionId) {
      setRows([]);
      setSessionStatus(null);
      return;
    }
    setLoadingRows(true);
    setError(null);
    getAttendanceSession(selectedSessionId)
      .then((session) => {
        setSessionStatus(session.status);
        setAttendanceMode(session.mode);
        setRows(session.marks.map((m: AttendanceMarkResponse) => ({ studentId: m.studentId, studentFullName: m.studentFullName, studentCode: m.studentCode, status: toSimpleStatus(m.status) })));
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) {
          setSessionStatus(null);
          return listClassEnrollments(selectedClassId).then((enrollments) => {
            setRows(
              enrollments
                .filter((en) => en.status === "ACTIVE")
                .map((en) => ({ studentId: en.studentId, studentFullName: en.studentFullName, studentCode: en.studentCode, status: "PRESENT" as const }))
            );
          });
        }
        setError(err instanceof ApiError ? err.message : t("attendancePage.loadDataError"));
      })
      .finally(() => setLoadingRows(false));
  }, [selectedClassId, selectedSessionId]);

  const pickSession = (id: string) => {
    if (!id) {
      setSearchParams({ classId: String(selectedClassId) });
      return;
    }
    setSearchParams({ classId: String(selectedClassId), sessionId: id });
  };

  const openSessionFromHistory = (classId: number, sessionId: number) => {
    setTab("today");
    setSearchParams({ classId: String(classId), sessionId: String(sessionId) });
  };

  const handleSaveAttendance = async () => {
    if (!selectedSessionId || rows.length === 0) return;
    setSaving(true);
    setError(null);
    try {
      await markAttendance(selectedSessionId, {
        mode: attendanceMode,
        marks: rows.map((r) => ({ studentId: r.studentId, status: r.status }))
      });
      const result = await submitAttendance(selectedSessionId);
      setSessionStatus(result.status);

      const absentStudents = rows.filter((r) => r.status === "ABSENT");
      const excusedStudents = rows.filter((r) => r.status === "EXCUSED");
      const lateStudents = rows.filter((r) => r.status === "LATE");
      let message = t("attendancePage.reportHeader");
      if (absentStudents.length > 0) {
        // Kênh gửi thật phụ thuộc NotificationPreference của từng phụ huynh (NotificationService) —
        // Zalo mặc định TẮT nên không nêu đích danh kênh cụ thể ở đây kẻo sai với phần lớn trường hợp.
        message += t("attendancePage.absentNotified", { names: absentStudents.map((s) => s.studentFullName).join(", ") });
      }
      if (excusedStudents.length > 0) {
        // Backend chỉ gửi thông báo cho ABSENT/LATE (StudentAttendanceService.notifyParents) — nghỉ có
        // phép không gửi, vì phụ huynh đã chủ động xin nghỉ nên không cần cảnh báo khẩn.
        message += t("attendancePage.excusedNotNotified", { names: excusedStudents.map((s) => s.studentFullName).join(", ") });
      }
      if (lateStudents.length > 0) {
        // Backend chỉ gửi thông báo cho ABSENT (StudentAttendanceService.submitAttendance) — đi trễ
        // không gửi, đúng nghiệp vụ chốt 2026-08-04 (không còn coi trễ giờ là tình huống khẩn).
        message += t("attendancePage.lateNotNotified", { names: lateStudents.map((s) => s.studentFullName).join(", ") });
      }
      if (absentStudents.length === 0 && excusedStudents.length === 0 && lateStudents.length === 0) {
        message = t("attendancePage.allPresentMessage");
      }
      setNotification(message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("attendancePage.saveError"));
    } finally {
      setSaving(false);
    }
  };

  const openSummaryExport = () => {
    setSummaryFrom(selectedClass?.startDate ?? "");
    setSummaryTo(toISODate(new Date()));
    setSummaryError(null);
    setSummaryOpen(true);
  };

  const handleSummaryExport = async () => {
    if (!selectedClass) return;
    setSummaryExporting(true);
    setSummaryError(null);
    try {
      const blob = await exportClassAttendanceSummary(selectedClass.id, summaryFrom || undefined, summaryTo || undefined);
      downloadBlob(blob, `tong-hop-chuyen-can-${selectedClass.classCode}-${summaryFrom}-${summaryTo}.xlsx`);
      setSummaryOpen(false);
    } catch (err) {
      setSummaryError(err instanceof ApiError ? err.message : t("attendancePage.summaryExport.failed"));
    } finally {
      setSummaryExporting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("attendancePage.title")}</h1>
          <p className="text-xs text-slate-500 mt-1">{t("attendancePage.description")}</p>
        </div>
        {selectedClass && (
          <Button type="button" variant="secondary" onClick={openSummaryExport}>
            <Download className="w-3.5 h-3.5" /> {t("attendancePage.summaryExport.button")}
          </Button>
        )}
      </div>

      <div className="flex border-b border-slate-200 gap-5">
        {(
          [
            ["today", t("attendancePage.tabs.today"), PenLine],
            ["history", t("attendancePage.tabs.history"), History]
          ] as const
        ).map(([key, label, Icon]) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`pb-2.5 text-xs font-bold border-b-2 flex items-center gap-1.5 transition-all ${
              tab === key ? "border-brand-red text-brand-red" : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <Icon className="w-3.5 h-3.5" />
            {label}
          </button>
        ))}
      </div>

      <Modal
        open={summaryOpen}
        onClose={() => setSummaryOpen(false)}
        title={t("attendancePage.summaryExport.title")}
        description={selectedClass ? `${selectedClass.name} (${selectedClass.classCode})` : undefined}
        size="md"
      >
        <div className="space-y-4">
          <p className="text-xs text-slate-600 leading-relaxed">{t("attendancePage.summaryExport.description")}</p>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-slate-500 mb-1">{t("attendancePage.summaryExport.fromDate")}</label>
              <DatePicker value={summaryFrom} onChange={setSummaryFrom} max={summaryTo || undefined} />
            </div>
            <div>
              <label className="block text-xs text-slate-500 mb-1">{t("attendancePage.summaryExport.toDate")}</label>
              <DatePicker value={summaryTo} onChange={setSummaryTo} min={summaryFrom || undefined} />
            </div>
          </div>
          {summaryError && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{summaryError}</div>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setSummaryOpen(false)}>
              {t("attendancePage.summaryExport.cancel")}
            </Button>
            <Button type="button" variant="primary" onClick={handleSummaryExport} disabled={summaryExporting}>
              <Download className="w-3.5 h-3.5" />
              {summaryExporting ? t("attendancePage.summaryExport.exporting") : t("attendancePage.summaryExport.export")}
            </Button>
          </div>
        </div>
      </Modal>

      {notification && (
        <Modal open onClose={() => setNotification(null)} title={t("attendancePage.notificationModalTitle")} size="md">
          <div className="space-y-4">
            <div className="text-xs text-slate-700 leading-relaxed whitespace-pre-line">{notification}</div>
            <div className="flex justify-end">
              <Button type="button" variant="primary" onClick={() => setNotification(null)}>
                {t("attendancePage.understood")}
              </Button>
            </div>
          </div>
        </Modal>
      )}
      {tab === "history" ? (
        <AttendanceHistoryPanel classes={classes} loadingClasses={loadingClasses} onOpenSession={openSessionFromHistory} />
      ) : (
        <>
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      <AttendanceReminderBanner />

      <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50">
            <div>
              <span className="text-xs font-bold text-slate-700 font-display">{t("attendancePage.sectionTitle")}</span>
              <p className="text-[10px] text-slate-400 mt-0.5">
                {selectedClass ? `${selectedClass.name} (${selectedClass.classCode})` : t("attendancePage.noClassSelected")}
              </p>
            </div>
            <div className="flex items-center gap-2">
              {selectedClass && (
                <Select
                  value={selectedSessionId ?? ""}
                  onChange={(e) => pickSession(e.target.value)}
                  className="bg-white border text-[10px] font-bold text-slate-700 px-2 py-1 rounded focus:outline-none"
                >
                  <option value="">{t("attendancePage.selectSessionPlaceholder")}</option>
                  {sessions.filter((s) => hasAttendanceOverride || (hasSessionStarted(s) && isToday(s))).map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.sessionDate} ({s.startTime}–{s.endTime})
                    </option>
                  ))}
                </Select>
              )}
              {selectedSessionId && sessionStatus && (
                <Badge variant={sessionStatusVariant[sessionStatus] ?? "neutral"}>
                  {t(`attendancePage.sessionStatus.${sessionStatus}`)}
                </Badge>
              )}
              <Select
                value={attendanceMode}
                onChange={(e) => setAttendanceMode(e.target.value as "SESSION_LEVEL" | "PERIOD_LEVEL")}
                disabled={locked || !canMark}
                className="bg-white border text-[10px] font-bold text-slate-700 px-2 py-1 rounded focus:outline-none disabled:opacity-50"
              >
                <option value="SESSION_LEVEL">{t("attendancePage.modeSessionLevel")}</option>
                <option value="PERIOD_LEVEL">{t("attendancePage.modePeriodLevel")}</option>
              </Select>
            </div>
          </div>

          {locked && canMark && (
            <div className="px-5 py-2.5 bg-amber-50 border-b border-amber-100 text-amber-700 text-[11px] font-semibold">
              {t("attendancePage.lockedNotice", {
                reason: lockedReason,
                start: selectedSession?.startTime,
                end: selectedSession ? formatTime(graceEndDate(selectedSession, gracePeriodMinutes)) : selectedSession,
                date: selectedSession?.sessionDate
              })}
            </div>
          )}

          {/* max-h + overflow-y-auto tạo vùng cuộn dọc riêng cho bảng, để "sticky top-0" trên
              thead thực sự dính lại khi cuộn danh sách học sinh dài, thay vì cuộn theo cả trang. */}
          <TableContainer className="rounded-none border-0 max-h-[65vh] overflow-y-auto">
            <thead className="sticky top-0 z-10">
              <tr>
                <Th className="sticky top-0 z-10">{t("attendancePage.columns.id")}</Th>
                <Th className="sticky top-0 z-10">{t("attendancePage.columns.fullName")}</Th>
                <Th className="sticky top-0 z-10 text-center">{t("attendancePage.columns.present")}</Th>
                <Th className="sticky top-0 z-10 text-center">{t("attendancePage.columns.absent")}</Th>
                <Th className="sticky top-0 z-10 text-center">{t("attendancePage.columns.excused")}</Th>
                <Th className="sticky top-0 z-10 text-center">{t("attendancePage.columns.late")}</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {!selectedSessionId ? (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                    {selectedClass ? t("attendancePage.selectSessionPrompt") : t("attendancePage.selectClassPrompt")}
                  </td>
                </tr>
              ) : loadingRows ? (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-xs text-slate-400">
                    {t("attendancePage.loading")}
                  </td>
                </tr>
              ) : rows.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                    {t("attendancePage.noStudents")}
                  </td>
                </tr>
              ) : (
                rows.map((stud) => (
                  <tr key={stud.studentId} className="hover:bg-slate-50/40 transition-colors">
                    <Td className="font-mono font-bold text-slate-500">{stud.studentCode}</Td>
                    <Td className="font-bold text-slate-900">
                      <StudentNameLink studentId={stud.studentId} name={stud.studentFullName} />
                    </Td>
                    {(["PRESENT", "ABSENT", "EXCUSED", "LATE"] as const).map((statusOption) => (
                      <Td key={statusOption} className="text-center">
                        <input
                          type="radio"
                          name={`att-${stud.studentId}`}
                          checked={stud.status === statusOption}
                          disabled={locked || !canMark}
                          onChange={() => setRows((prev) => prev.map((r) => (r.studentId === stud.studentId ? { ...r, status: statusOption } : r)))}
                          className={`h-4 w-4 border-slate-300 disabled:opacity-50 ${
                            statusOption === "PRESENT"
                              ? "text-emerald-600 focus:ring-emerald-500"
                              : statusOption === "ABSENT"
                                ? "text-rose-600 focus:ring-rose-500"
                                : statusOption === "EXCUSED"
                                  ? "text-sky-600 focus:ring-sky-500"
                                  : "text-amber-500 focus:ring-amber-500"
                          }`}
                        />
                      </Td>
                    ))}
                  </tr>
                ))
              )}
            </tbody>
          </TableContainer>

          {canMark && (
            <div className="px-6 py-4 bg-slate-50 border-t flex justify-end">
              <button
                onClick={handleSaveAttendance}
                disabled={locked || !selectedSessionId || rows.length === 0 || saving}
                title={locked ? lockedReason ?? undefined : undefined}
                className="bg-brand-orange hover:bg-brand-orange/90 text-white font-semibold text-xs px-4 py-2 rounded-lg flex items-center gap-1.5 shadow-soft transition-all disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-brand-orange"
              >
                <Save className="w-4 h-4 text-white" />
                <span>{saving ? t("attendancePage.saving") : t("attendancePage.saveButton")}</span>
              </button>
            </div>
          )}
      </div>
        </>
      )}
    </div>
  );
}
