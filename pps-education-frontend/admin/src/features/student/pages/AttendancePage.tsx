import React, { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Save } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import {
  AttendanceMarkResponse,
  ClassSessionResponse,
  EnterAttendanceMarkRequest,
  getAttendanceSession,
  listClassEnrollments,
  listClassSessions,
  markAttendance,
  submitAttendance
} from "@/features/academic/api";
import { useEligibleClasses } from "@/features/academic/hooks/useEligibleClasses";
import StudentNameLink from "@/features/reports/components/StudentNameLink";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Select from "@/components/ui/Select";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
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
 * Sửa đổi nghiệp vụ 2026-08-18 (đã xác nhận với người dùng) — thay thế rule
 * V45 "sửa được tới hết ngày": GV thường chỉ được điểm danh/sửa/submit
 * TRONG ĐÚNG khung giờ buổi học [startTime, endTime], đồng bộ với
 * StudentAttendanceService.isWithinSessionWindow (backend chặn cả API,
 * không chỉ khóa nút ở UI).
 */
function isWithinAttendanceWindow(s: ClassSessionResponse): boolean {
  const now = new Date();
  const start = new Date(`${s.sessionDate}T${s.startTime}`);
  const end = new Date(`${s.sessionDate}T${s.endTime}`);
  return now >= start && now <= end;
}

export default function AttendancePage() {
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

  const { classes } = useEligibleClasses();
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [rows, setRows] = useState<Row[]>([]);
  const [attendanceMode, setAttendanceMode] = useState<"SESSION_LEVEL" | "PERIOD_LEVEL">("SESSION_LEVEL");
  const [sessionStatus, setSessionStatus] = useState<string | null>(null);
  const [loadingRows, setLoadingRows] = useState(false);
  const [saving, setSaving] = useState(false);
  const [notification, setNotification] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;
  const selectedSession = sessions.find((s) => s.id === selectedSessionId) ?? null;
  // Tick vô hại, chỉ để ép re-render mỗi 30s — cho nút Submit tự làm mờ đúng lúc hết giờ buổi học
  // mà không cần GV thao tác/reload lại trang (isWithinAttendanceWindow phụ thuộc "now" tại thời điểm render).
  const [, forceTick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => forceTick((t) => t + 1), 30_000);
    return () => clearInterval(id);
  }, []);
  // Sửa đổi 2026-08-18: SUBMITTED không tự khoá sửa — GV vẫn sửa được, nhưng CHỈ trong đúng khung
  // giờ buổi học [startTime, endTime] (không còn "tới hết ngày" như rule V45 cũ). Tài khoản có quyền
  // quản trị điểm danh vượt rào này.
  const locked = !hasAttendanceOverride && !!selectedSession && !isWithinAttendanceWindow(selectedSession);
  const lockedReason = !selectedSession
    ? null
    : new Date() < new Date(`${selectedSession.sessionDate}T${selectedSession.startTime}`)
      ? `Chưa tới giờ bắt đầu buổi học (${selectedSession.startTime})`
      : `Buổi học đã kết thúc lúc ${selectedSession.endTime}`;

  useEffect(() => {
    if (!selectedClassId) {
      setSessions([]);
      return;
    }
    listClassSessions(selectedClassId)
      .then(setSessions)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách buổi học."));
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
        setError(err instanceof ApiError ? err.message : "Không tải được dữ liệu điểm danh.");
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
      let message = "🔔 BÁO CÁO CHUYÊN CẦN:\n- Hệ thống đã lưu điểm danh lớp học.\n";
      if (absentStudents.length > 0) {
        message += `- ĐÃ TỰ ĐỘNG GỬI thông báo khẩn qua SMS & Zalo tới phụ huynh học sinh vắng mặt: ${absentStudents.map((s) => s.studentFullName).join(", ")}.\n`;
      }
      if (excusedStudents.length > 0) {
        // Backend chỉ gửi thông báo cho ABSENT/LATE (StudentAttendanceService.notifyParents) — nghỉ có
        // phép không gửi, vì phụ huynh đã chủ động xin nghỉ nên không cần cảnh báo khẩn.
        message += `- Nghỉ có phép (không gửi thông báo khẩn): ${excusedStudents.map((s) => s.studentFullName).join(", ")}.\n`;
      }
      if (lateStudents.length > 0) {
        message += `- Đã gửi tin nhắn đi muộn tới phụ huynh các em: ${lateStudents.map((s) => s.studentFullName).join(", ")}.\n`;
      }
      if (absentStudents.length === 0 && excusedStudents.length === 0 && lateStudents.length === 0) {
        message = "✅ Điểm danh thành công! Toàn bộ học sinh trong lớp đã có mặt đầy đủ.";
      }
      setNotification(message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu điểm danh thất bại.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Điểm Danh Chuyên Cần (SMS)</h1>
        <p className="text-xs text-slate-500 mt-1">Giảng viên lưu chuyên cần, vắng học tự động cảnh báo phụ huynh.</p>
      </div>

      {notification && (
        <Modal open onClose={() => setNotification(null)} title="Báo cáo chuyên cần" size="md">
          <div className="space-y-4">
            <div className="text-xs text-slate-700 leading-relaxed whitespace-pre-line">{notification}</div>
            <div className="flex justify-end">
              <Button type="button" variant="primary" onClick={() => setNotification(null)}>
                Đã hiểu
              </Button>
            </div>
          </div>
        </Modal>
      )}
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      <AttendanceReminderBanner />

      <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50">
            <div>
              <span className="text-xs font-bold text-slate-700 font-display">Điểm danh Chuyên cần đầu giờ</span>
              <p className="text-[10px] text-slate-400 mt-0.5">
                {selectedClass ? `${selectedClass.name} (${selectedClass.classCode})` : "Chưa chọn lớp — chọn ở góc trên bên phải (Header) để bắt đầu điểm danh."}
              </p>
            </div>
            <div className="flex items-center gap-2">
              {selectedClass && (
                <Select
                  value={selectedSessionId ?? ""}
                  onChange={(e) => pickSession(e.target.value)}
                  className="bg-white border text-[10px] font-bold text-slate-700 px-2 py-1 rounded focus:outline-none"
                >
                  <option value="">-- Chọn buổi học --</option>
                  {sessions.filter((s) => hasAttendanceOverride || (hasSessionStarted(s) && isToday(s))).map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.sessionDate} ({s.startTime}–{s.endTime})
                    </option>
                  ))}
                </Select>
              )}
              <Select
                value={attendanceMode}
                onChange={(e) => setAttendanceMode(e.target.value as "SESSION_LEVEL" | "PERIOD_LEVEL")}
                disabled={locked}
                className="bg-white border text-[10px] font-bold text-slate-700 px-2 py-1 rounded focus:outline-none disabled:opacity-50"
              >
                <option value="SESSION_LEVEL">Mức cả Buổi (SESSION)</option>
                <option value="PERIOD_LEVEL">Mức từng Tiết (PERIOD)</option>
              </Select>
            </div>
          </div>

          {locked && (
            <div className="px-5 py-2.5 bg-amber-50 border-b border-amber-100 text-amber-700 text-[11px] font-semibold">
              {lockedReason} — chỉ điểm danh/sửa được trong khung giờ buổi học ({selectedSession?.startTime}–{selectedSession?.endTime}, ngày {selectedSession?.sessionDate}). Cần quyền quản trị điểm danh để thao tác ngoài khung giờ này.
            </div>
          )}

          {/* max-h + overflow-y-auto tạo vùng cuộn dọc riêng cho bảng, để "sticky top-0" trên
              thead thực sự dính lại khi cuộn danh sách học sinh dài, thay vì cuộn theo cả trang. */}
          <TableContainer className="rounded-none border-0 max-h-[65vh] overflow-y-auto">
            <thead className="sticky top-0 z-10">
              <tr>
                <Th className="sticky top-0 z-10">Mã ID</Th>
                <Th className="sticky top-0 z-10">Họ và tên</Th>
                <Th className="sticky top-0 z-10 text-center">Có mặt</Th>
                <Th className="sticky top-0 z-10 text-center">Vắng mặt (ABSENT)</Th>
                <Th className="sticky top-0 z-10 text-center">Nghỉ có phép (EXCUSED)</Th>
                <Th className="sticky top-0 z-10 text-center">Đi trễ (LATE)</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {!selectedSessionId ? (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                    {selectedClass ? "Chọn buổi học ở trên để tải danh sách học sinh." : "Chọn 1 lớp ở Header (góc trên bên phải)."}
                  </td>
                </tr>
              ) : loadingRows ? (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-xs text-slate-400">
                    Đang tải...
                  </td>
                </tr>
              ) : rows.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                    Không tìm thấy học sinh nào thuộc lớp học này.
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
                          disabled={locked}
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

          <div className="px-6 py-4 bg-slate-50 border-t flex justify-end">
            <button
              onClick={handleSaveAttendance}
              disabled={locked || !selectedSessionId || rows.length === 0 || saving}
              title={locked ? lockedReason ?? undefined : undefined}
              className="bg-brand-orange hover:bg-brand-orange/90 text-white font-semibold text-xs px-4 py-2 rounded-lg flex items-center gap-1.5 shadow-soft transition-all disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-brand-orange"
            >
              <Save className="w-4 h-4 text-white" />
              <span>{saving ? "Đang lưu..." : "Xác nhận & Lưu điểm danh"}</span>
            </button>
          </div>
      </div>
    </div>
  );
}
