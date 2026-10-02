import { useEffect, useMemo, useState } from "react";
import { ChevronDown, ChevronRight, History } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { formatDateTime, toLocaleTag } from "@/lib/i18nFormat";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";
import { Td, Th } from "@/components/ui/TableContainer";
import {
  AttendanceMarkHistoryResponse,
  AttendanceSessionResponse,
  ClassResponse,
  ClassSessionResponse,
  getAttendanceHistory,
  getAttendanceSession,
  listClassSessions
} from "../api";
import FloatingError from "@/components/ui/FloatingError";

/**
 * 1 lần bấm "Xác nhận & Lưu điểm danh" ghi N dòng attendance_marks_history (1/học sinh, cùng actor +
 * cùng thời điểm, xem StudentAttendanceService#writeAttendanceMarkHistory) — không có batch id dùng
 * chung nên gộp thành "1 đợt thao tác" bằng khoảng cách thời gian, mirror BURST_GAP_MS của
 * SessionVersionHistoryModal.tsx (Nhận xét học viên), nhưng đơn giản hơn — chỉ liệt kê timeline (ai/lúc
 * nào/bao nhiêu học sinh), KHÔNG tái dựng lại toàn bộ bảng tại từng mốc (đã chốt phạm vi với người dùng
 * 2026-10-01: "Timeline đơn giản", không cần bản đầy đủ kiểu Google Sheets như bên Nhận xét).
 */
const BURST_GAP_MS = 12_000;

interface TimelineBucket {
  key: string;
  timestamp: string;
  actor: string;
  action: "CREATED" | "UPDATED";
  studentCount: number;
}

function buildTimelineBuckets(history: AttendanceMarkHistoryResponse[]): TimelineBucket[] {
  // Backend trả cũ→mới — đảo lại mới→cũ để hiện thao tác gần nhất trước (cùng quy ước SessionVersionHistoryModal).
  const desc = [...history].reverse();
  const buckets: { timestamp: string; actor: string; action: "CREATED" | "UPDATED"; studentIds: Set<number> }[] = [];
  for (const entry of desc) {
    const last = buckets[buckets.length - 1];
    const gap = last ? new Date(last.timestamp).getTime() - new Date(entry.createdAt).getTime() : Infinity;
    if (last && gap <= BURST_GAP_MS && last.actor === entry.changedByName) {
      last.studentIds.add(entry.studentId);
      // Đợt chứa ít nhất 1 dòng CREATED (học sinh lần đầu được điểm danh) thì tính cả đợt là "đã điểm danh", không phải "sửa".
      if (entry.action === "CREATED") last.action = "CREATED";
    } else {
      buckets.push({ timestamp: entry.createdAt, actor: entry.changedByName, action: entry.action, studentIds: new Set([entry.studentId]) });
    }
  }
  return buckets.map((b, i) => ({ key: `${b.timestamp}-${i}`, timestamp: b.timestamp, actor: b.actor, action: b.action, studentCount: b.studentIds.size }));
}

interface SessionAttendanceEntry {
  session: ClassSessionResponse;
  attendance: AttendanceSessionResponse;
}

const sessionStatusVariant: Record<string, BadgeVariant> = {
  DRAFT: "warning",
  SUBMITTED: "success",
  LOCKED: "success"
};

const markStatusVariant: Record<string, BadgeVariant> = {
  PRESENT: "success",
  ABSENT: "danger",
  EXCUSED: "info",
  LATE: "warning",
  EARLY_LEAVE: "warning"
};

interface AttendanceHistoryPanelProps {
  classes: ClassResponse[];
  loadingClasses: boolean;
  onOpenSession: (classId: number, sessionId: number) => void;
}

/**
 * Lịch sử các buổi đã điểm danh (DRAFT/SUBMITTED/LOCKED), gom theo lớp — mirror CommentHistoryPanel.tsx
 * (tab "Lịch sử" của Nhận xét học viên), bổ sung theo yêu cầu người dùng 2026-10-01: trước đây điểm
 * danh xong không có chỗ nào tra lại buổi nào đã điểm danh/chưa, dễ bấm nhầm điểm danh lại từ đầu.
 *
 * Không có endpoint "toàn bộ điểm danh của 1 lớp" ở backend (khác listCommentsForClass của Nhận xét)
 * nên gom bằng listClassSessions(classId) rồi getAttendanceSession(sessionId) từng buổi (N+M request,
 * cùng cách ClassDetailPanel.tsx đã làm cho badge trạng thái trong danh sách buổi của 1 lớp) — buổi nào
 * chưa điểm danh trả 404, bỏ qua (không tính vào "lịch sử").
 */
export default function AttendanceHistoryPanel({ classes, loadingClasses, onOpenSession }: AttendanceHistoryPanelProps) {
  const { t } = useTranslation("student");
  const [entries, setEntries] = useState<SessionAttendanceEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [classFilter, setClassFilter] = useState<number | "ALL">("ALL");
  const [statusFilter, setStatusFilter] = useState<"ALL" | AttendanceSessionResponse["status"]>("ALL");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  useEffect(() => {
    if (classes.length === 0) {
      setEntries([]);
      return;
    }
    setLoading(true);
    setError(null);
    Promise.all(
      classes.map((cls) =>
        listClassSessions(cls.id)
          .then((sessions) =>
            Promise.allSettled(
              sessions.map((session) => getAttendanceSession(session.id).then((attendance) => ({ session, attendance })))
            )
          )
          .then((results) =>
            results
              .filter((r): r is PromiseFulfilledResult<SessionAttendanceEntry> => r.status === "fulfilled")
              .map((r) => r.value)
          )
          .catch(() => [] as SessionAttendanceEntry[])
      )
    )
      .then((perClass) => setEntries(perClass.flat()))
      .catch((err) => setError(err instanceof ApiError ? err.message : t("attendancePage.history.loadFailed")))
      .finally(() => setLoading(false));
  }, [classes, t]);

  const filtered = entries.filter((e) => {
    if (classFilter !== "ALL" && e.session.classId !== classFilter) return false;
    if (statusFilter !== "ALL" && e.attendance.status !== statusFilter) return false;
    if (dateFrom && e.session.sessionDate < dateFrom) return false;
    if (dateTo && e.session.sessionDate > dateTo) return false;
    return true;
  });

  const classesById: Record<number, ClassResponse> = Object.fromEntries(classes.map((c) => [c.id, c]));
  const classIdsInOrder = Array.from(new Set(filtered.map((e) => e.session.classId))).sort((a, b) =>
    (classesById[a]?.name ?? "").localeCompare(classesById[b]?.name ?? "")
  );

  if (loadingClasses) return <p className="text-xs text-slate-500 p-4">{t("attendancePage.history.loadingClasses")}</p>;
  if (classes.length === 0) return <p className="text-xs text-slate-400 italic text-center py-10">{t("attendancePage.history.noClasses")}</p>;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Select
          value={classFilter}
          onChange={(e) => setClassFilter(e.target.value === "ALL" ? "ALL" : Number(e.target.value))}
          className="bg-white border border-slate-200 text-xs px-2.5 py-2 rounded-lg focus:outline-none"
        >
          <option value="ALL">{t("attendancePage.history.allClasses")}</option>
          {classes.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name} ({c.classCode})
            </option>
          ))}
        </Select>
        <Select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as typeof statusFilter)}
          className="bg-white border border-slate-200 text-xs px-2.5 py-2 rounded-lg focus:outline-none"
        >
          <option value="ALL">{t("attendancePage.history.allStatuses")}</option>
          <option value="DRAFT">{t("attendancePage.sessionStatus.DRAFT")}</option>
          <option value="SUBMITTED">{t("attendancePage.sessionStatus.SUBMITTED")}</option>
          <option value="LOCKED">{t("attendancePage.sessionStatus.LOCKED")}</option>
        </Select>
        <span className="text-[10px] uppercase font-bold text-slate-400">{t("attendancePage.history.fromDateLabel")}</span>
        <div className="w-36">
          <DatePicker value={dateFrom} onChange={setDateFrom} max={dateTo || undefined} />
        </div>
        <span className="text-xs text-slate-400">{t("attendancePage.history.toDateConnector")}</span>
        <div className="w-36">
          <DatePicker value={dateTo} onChange={setDateTo} min={dateFrom || undefined} />
        </div>
        {(dateFrom || dateTo || statusFilter !== "ALL" || classFilter !== "ALL") && (
          <button
            onClick={() => {
              setDateFrom("");
              setDateTo("");
              setStatusFilter("ALL");
              setClassFilter("ALL");
            }}
            className="text-[11px] font-semibold text-brand-red hover:underline"
          >
            {t("attendancePage.history.clearFilter")}
          </button>
        )}
        <span className="text-[11px] text-slate-400 ml-auto">
          {t("attendancePage.history.countSummary", { filtered: filtered.length, total: entries.length })}
        </span>
      </div>

      <FloatingError message={error} onClose={() => setError(null)} />

      {loading ? (
        <p className="text-xs text-slate-500 p-4">{t("attendancePage.history.loading")}</p>
      ) : filtered.length === 0 ? (
        <p className="text-xs text-slate-400 italic text-center py-10">
          {entries.length === 0 ? t("attendancePage.history.emptyNoHistory") : t("attendancePage.history.emptyNoMatch")}
        </p>
      ) : (
        <div className="space-y-4">
          {classIdsInOrder.map((classId) => {
            const cls = classesById[classId];
            const classEntries = filtered
              .filter((e) => e.session.classId === classId)
              .sort((a, b) => `${b.session.sessionDate}T${b.session.startTime}`.localeCompare(`${a.session.sessionDate}T${a.session.startTime}`));
            return (
              <Card key={classId} padded={false} className="overflow-hidden">
                <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex items-center justify-between flex-wrap gap-2">
                  <span className="text-xs font-bold text-slate-700 font-display">
                    {cls ? `${cls.name} (${cls.classCode})` : t("attendancePage.history.classFallback", { id: classId })}
                  </span>
                  <Badge variant="neutral">{t("attendancePage.history.sessionCountBadge", { count: classEntries.length })}</Badge>
                </div>
                {classEntries.map((entry) => (
                  <SessionRow key={entry.session.id} entry={entry} onOpenSession={onOpenSession} />
                ))}
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}

function SessionRow({ entry, onOpenSession }: { entry: SessionAttendanceEntry; onOpenSession: (classId: number, sessionId: number) => void }) {
  const { t, i18n } = useTranslation("student");
  const [expanded, setExpanded] = useState(false);
  const { session, attendance } = entry;
  const weekday = new Date(session.sessionDate).toLocaleDateString(toLocaleTag(i18n.language), { weekday: "long" }).replace(/^./, (c) => c.toUpperCase());

  // Nạp lười (chỉ khi bung buổi ra) — mỗi buổi là 1 request riêng, không tải trước cho mọi buổi trong danh sách.
  const [history, setHistory] = useState<AttendanceMarkHistoryResponse[] | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  useEffect(() => {
    if (!expanded || history !== null) return;
    setHistoryLoading(true);
    setHistoryError(null);
    getAttendanceHistory(session.id)
      .then(setHistory)
      .catch((err) => setHistoryError(err instanceof ApiError ? err.message : t("attendancePage.history.timeline.loadFailed")))
      .finally(() => setHistoryLoading(false));
  }, [expanded, history, session.id, t]);
  const timelineBuckets = useMemo(() => (history ? buildTimelineBuckets(history) : []), [history]);

  const counts = {
    present: attendance.marks.filter((m) => m.status === "PRESENT").length,
    absent: attendance.marks.filter((m) => m.status === "ABSENT").length,
    excused: attendance.marks.filter((m) => m.status === "EXCUSED").length,
    late: attendance.marks.filter((m) => m.status === "LATE" || m.status === "EARLY_LEAVE").length
  };

  return (
    <div className="border-b border-slate-100 last:border-b-0">
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="w-full px-5 py-2.5 bg-white hover:bg-slate-50/60 flex items-center gap-2 flex-wrap transition-colors text-left"
      >
        {expanded ? <ChevronDown className="w-3.5 h-3.5 text-slate-400 shrink-0" /> : <ChevronRight className="w-3.5 h-3.5 text-slate-400 shrink-0" />}
        <span className="text-[11px] font-bold text-slate-600">{t("attendancePage.history.sessionLabel", { date: session.sessionDate, weekday })}</span>
        <span className="text-[10px] font-mono text-slate-400">{session.startTime.slice(0, 5)}–{session.endTime.slice(0, 5)}</span>
        <Badge variant={sessionStatusVariant[attendance.status] ?? "neutral"}>{t(`attendancePage.sessionStatus.${attendance.status}`)}</Badge>
        <span className="text-[10px] text-slate-400">
          {t("attendancePage.history.presentCount", { count: counts.present })} · {t("attendancePage.history.absentCount", { count: counts.absent })} · {t("attendancePage.history.excusedCount", { count: counts.excused })} · {t("attendancePage.history.lateCount", { count: counts.late })}
        </span>
        <span className="ml-auto flex items-center gap-2">
          <span className="text-[10px] text-slate-400">
            {attendance.submittedAt ? t("attendancePage.history.submittedAtLabel", { time: formatDateTime(attendance.submittedAt, i18n.language) }) : t("attendancePage.history.notSubmittedYet")}
          </span>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={(e) => {
              e.stopPropagation();
              onOpenSession(session.classId, session.id);
            }}
          >
            {t("attendancePage.history.openButton")}
          </Button>
        </span>
      </button>
      {expanded && (
        <div className="overflow-x-auto max-h-[50vh] overflow-y-auto">
          <table className="w-full text-xs text-left border-separate border-spacing-0">
            <thead className="sticky top-0 z-10 bg-slate-50">
              <tr className="border-b border-slate-300">
                <Th className="border-r border-b border-slate-300">{t("attendancePage.history.columns.studentCode")}</Th>
                <Th className="border-r border-b border-slate-300">{t("attendancePage.history.columns.fullName")}</Th>
                <Th className="border-r border-b border-slate-300 text-center">{t("attendancePage.history.columns.status")}</Th>
                <Th className="border-b border-slate-300">{t("attendancePage.history.columns.note")}</Th>
              </tr>
            </thead>
            <tbody>
              {attendance.marks.map((m) => (
                <tr key={m.id} className="hover:bg-slate-50/40">
                  <Td className="font-mono font-bold text-slate-500 border-r border-b border-slate-200">{m.studentCode}</Td>
                  <Td className="font-bold text-slate-900 border-r border-b border-slate-200">{m.studentFullName}</Td>
                  <Td className="text-center border-r border-b border-slate-200">
                    <Badge variant={markStatusVariant[m.status] ?? "neutral"}>{m.status}</Badge>
                  </Td>
                  <Td className="border-b border-slate-200 text-slate-500">{m.absenceReason || "—"}</Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {expanded && (
        <div className="px-5 py-3 border-t border-slate-100 bg-slate-50/60">
          <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wide flex items-center gap-1.5 mb-1.5">
            <History className="w-3 h-3" />
            {t("attendancePage.history.timeline.title")}
          </p>
          <FloatingError message={historyError} onClose={() => setHistoryError(null)} />
          {historyLoading ? (
            <p className="text-[11px] text-slate-400">{t("attendancePage.history.timeline.loading")}</p>
          ) : timelineBuckets.length === 0 ? (
            !historyError && <p className="text-[11px] text-slate-400 italic">{t("attendancePage.history.timeline.empty")}</p>
          ) : (
            <ul className="space-y-1">
              {timelineBuckets.map((b) => (
                <li key={b.key} className="text-[11px] text-slate-600">
                  {t(b.action === "CREATED" ? "attendancePage.history.timeline.createdLabel" : "attendancePage.history.timeline.updatedLabel", {
                    actor: b.actor,
                    time: formatDateTime(b.timestamp, i18n.language),
                    count: b.studentCount
                  })}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
