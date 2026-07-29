import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  Award,
  Calendar,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clock,
  Filter,
  MessageSquareText,
  ShieldCheck,
  Sparkles,
  TrendingUp,
  Video
} from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  AttendanceMarkResponse,
  ClassSessionResponse,
  StudentCommentResponse,
  listAttendance,
  listMyAttendance,
  listMyComments,
  listComments,
  listMySessions,
  listSchedule
} from "../api";

const WEEKDAY_LABELS = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"];
const pad2 = (n: number) => String(n).padStart(2, "0");

const sessionTypeLabels: Record<string, string> = {
  REGULAR: "Buổi học thường",
  REVIEW: "Ôn tập",
  EXAM: "Kiểm tra",
  MAKEUP: "Học bù"
};

const attitudeLabels: Record<NonNullable<StudentCommentResponse["attitude"]>, string> = {
  POOR: "Kém",
  WEAK: "Yếu",
  AVERAGE: "Trung bình",
  ABOVE_AVERAGE: "Trung bình khá",
  FAIR: "Khá",
  GOOD: "Tốt"
};

const attitudeStyles: Record<NonNullable<StudentCommentResponse["attitude"]>, string> = {
  GOOD: "bg-emerald-50 text-emerald-800 border-emerald-300",
  FAIR: "bg-teal-50 text-teal-800 border-teal-300",
  ABOVE_AVERAGE: "bg-sky-50 text-sky-800 border-sky-300",
  AVERAGE: "bg-amber-50 text-amber-800 border-amber-300",
  WEAK: "bg-orange-50 text-orange-800 border-orange-300",
  POOR: "bg-rose-50 text-rose-800 border-rose-300"
};

/** "NN%" -> NN; "Chưa làm bài" -> 0 (chưa làm, tính vào tỷ lệ); null/"Đang chờ chấm" -> null (chưa rõ, không tính vào trung bình). */
function parseProgressPercent(text: string | null): number | null {
  if (!text) return null;
  if (text === "Chưa làm bài") return 0;
  const match = text.match(/(\d+)%/);
  return match ? Number(match[1]) : null;
}

interface SessionFeedbackLog {
  id: string;
  commentDate: string;
  timeSlot: string | null;
  sessionTypeLabel: string | null;
  attitude: StudentCommentResponse["attitude"];
  homeworkPreviousScore: string | null;
  homeworkPreviousSpeakingScore: string | null;
  grammarPreviousProgress: string | null;
  videoPreviousProgress: string | null;
  content: string;
  homeworkNextGrammarLabel: string | null;
  homeworkNextVideoLabel: string | null;
  note: string | null;
}

interface DailyLearningProgressTabProps {
  studentName: string;
  studentCode: string;
  classId: number;
  /** UC-64 (2026-07-29) — chỉ set khi xem qua Cổng Phụ huynh (dùng API .../parent/children/{studentId}/...). Không set thì Học sinh tự xem (self-service /students/me/...). */
  parentStudentId?: number;
}

/**
 * UC-21 (phía học sinh/phụ huynh xem lại) — bản chuyển thể từ thiết kế Google AI Studio (form nhập
 * liệu của giáo viên) thành màn CHỈ XEM: bỏ hết select/input chỉnh sửa (thái độ, % BTVN, chọn link/bài
 * tập) vì không được tự sửa nhận xét/thái độ do giáo viên chấm — ô "Thái độ học tập" vẫn giữ kiểu dáng
 * dropdown (có mũi tên ChevronDown) để khớp UI gốc, nhưng là <div> tĩnh.
 *
 * Nối API thật từ UC-64 (2026-07-29, PR #112) — GET /students/me/classes/{classId}/comments (Học sinh
 * tự xem, suy studentId từ JWT) hoặc GET /portal/parent/children/{studentId}/classes/{classId}/comments
 * (Phụ huynh xem con), chỉ trả nhận xét ĐÃ DUYỆT (status=APPROVED). Ghép thêm buổi học (để lấy giờ bắt
 * đầu/kết thúc + loại buổi) và điểm danh (để tính KPI "Tình trạng chuyên cần") từ 2 API self-service/
 * phụ huynh tương ứng đã có sẵn.
 */
export default function DailyLearningProgressTab({ studentName, studentCode, classId, parentStudentId }: DailyLearningProgressTabProps) {
  const [comments, setComments] = useState<StudentCommentResponse[]>([]);
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [attendance, setAttendance] = useState<AttendanceMarkResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    const commentsPromise = parentStudentId != null ? listComments(parentStudentId, classId) : listMyComments(classId);
    const sessionsPromise = parentStudentId != null ? listSchedule(parentStudentId, classId) : listMySessions(undefined, undefined, classId);
    const attendancePromise = parentStudentId != null ? listAttendance(parentStudentId, classId) : listMyAttendance(classId);
    Promise.all([commentsPromise, sessionsPromise, attendancePromise])
      .then(([commentRes, sessionRes, attendanceRes]) => {
        setComments(commentRes.filter((c) => c.commentType === "DAILY"));
        setSessions(sessionRes);
        setAttendance(attendanceRes);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được quá trình học tập."))
      .finally(() => setLoading(false));
  }, [classId, parentStudentId]);

  const sessionById = useMemo(() => new Map(sessions.map((s) => [s.id, s])), [sessions]);

  const logs: SessionFeedbackLog[] = useMemo(
    () =>
      comments
        .slice()
        .sort((a, b) => (a.commentDate < b.commentDate ? 1 : -1))
        .map((c) => {
          const session = c.classSessionId != null ? sessionById.get(c.classSessionId) : undefined;
          return {
            id: String(c.id),
            commentDate: session?.sessionDate ?? c.commentDate,
            timeSlot: session ? `${session.startTime} - ${session.endTime}` : null,
            sessionTypeLabel: session ? sessionTypeLabels[session.sessionType] ?? session.sessionType : null,
            attitude: c.attitude,
            homeworkPreviousScore: c.homeworkPreviousScore,
            homeworkPreviousSpeakingScore: c.homeworkPreviousSpeakingScore,
            grammarPreviousProgress: c.grammarPreviousProgress,
            videoPreviousProgress: c.videoPreviousProgress,
            content: c.content,
            homeworkNextGrammarLabel: c.homeworkNext ?? c.homeworkNextExerciseTitle,
            homeworkNextVideoLabel: c.homeworkNextReviewVideoSetTitle,
            note: c.note
          };
        }),
    [comments, sessionById]
  );

  const [selectedSessionId, setSelectedSessionId] = useState<string>("ALL");
  // Filter theo Thái độ học tập (2026-07-29) — độc lập với filter theo buổi/ngày ở trên, áp dụng cùng lúc (AND).
  const [attitudeFilter, setAttitudeFilter] = useState<"ALL" | NonNullable<StudentCommentResponse["attitude"]>>("ALL");
  const [attitudeOpen, setAttitudeOpen] = useState(false);
  const attitudeRef = useRef<HTMLDivElement>(null);
  // Lọc theo khoảng thời gian (Từ ngày - Đến ngày, 2026-07-29) — độc lập với chọn 1 buổi cụ thể ở
  // trên, cả 2 cùng áp dụng (AND); dùng để xem nhiều buổi trong 1 khoảng ngày thay vì từng buổi lẻ.
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [rangeOpen, setRangeOpen] = useState(false);
  const [rangeViewMonth, setRangeViewMonth] = useState<Date>(() => new Date());
  const rangeRef = useRef<HTMLDivElement>(null);
  const filteredLogs = logs.filter((log) => {
    if (selectedSessionId !== "ALL" && log.id !== selectedSessionId) return false;
    if (attitudeFilter !== "ALL" && log.attitude !== attitudeFilter) return false;
    if (dateFrom && log.commentDate < dateFrom) return false;
    if (dateTo && log.commentDate > dateTo) return false;
    return true;
  });
  const displayCode = studentCode || "";
  const selectedLog = logs.find((log) => log.id === selectedSessionId) ?? null;

  useEffect(() => {
    if (!attitudeOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (attitudeRef.current && !attitudeRef.current.contains(e.target as Node)) setAttitudeOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [attitudeOpen]);

  useEffect(() => {
    if (!rangeOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (rangeRef.current && !rangeRef.current.contains(e.target as Node)) setRangeOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [rangeOpen]);

  const rangeLabel = dateFrom || dateTo ? `${dateFrom || "…"} → ${dateTo || "…"}` : "Khoảng thời gian";

  const toggleRange = () => {
    setRangeOpen((v) => {
      const next = !v;
      if (next) setRangeViewMonth(new Date(dateFrom || dateTo || Date.now()));
      return next;
    });
  };

  const rYear = rangeViewMonth.getFullYear();
  const rMonth = rangeViewMonth.getMonth();
  const rFirstWeekday = (new Date(rYear, rMonth, 1).getDay() + 6) % 7;
  const rDaysInMonth = new Date(rYear, rMonth + 1, 0).getDate();
  const rangeCalendarCells: (number | null)[] = [
    ...Array.from({ length: rFirstWeekday }, () => null),
    ...Array.from({ length: rDaysInMonth }, (_, i) => i + 1)
  ];
  const rDateStrFor = (day: number) => `${rYear}-${pad2(rMonth + 1)}-${pad2(day)}`;

  /** Bấm 1: đặt Từ ngày. Bấm 2 (sau ngày Từ): đặt Đến ngày. Bấm khi đã đủ cặp hoặc bấm ngày trước "Từ": bắt đầu chọn lại từ đầu. */
  const handleRangeDayClick = (day: number) => {
    const dateStr = rDateStrFor(day);
    if (!dateFrom || dateTo || dateStr < dateFrom) {
      setDateFrom(dateStr);
      setDateTo("");
    } else {
      setDateTo(dateStr);
    }
  };

  const rangeCellState = (dateStr: string): "start" | "end" | "single" | "in-range" | "none" => {
    if (dateFrom === dateStr && dateTo === dateStr) return "single";
    if (dateFrom === dateStr) return dateTo ? "start" : "single";
    if (dateTo === dateStr) return "end";
    if (dateFrom && dateTo && dateStr > dateFrom && dateStr < dateTo) return "in-range";
    return "none";
  };

  // Datepicker chọn buổi học theo ngày — thay cho <select> gốc vì danh sách dropdown mặc định của
  // trình duyệt hiện tràn/xấu. Vẫn lọc theo session.id như cũ, chỉ đổi cách chọn.
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [viewMonth, setViewMonth] = useState<Date>(() => new Date());
  const [multiDayLogs, setMultiDayLogs] = useState<SessionFeedbackLog[] | null>(null);
  const calendarRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (logs[0]) setViewMonth(new Date(logs[0].commentDate));
  }, [logs]);

  const logsByDate = useMemo(() => {
    const map = new Map<string, SessionFeedbackLog[]>();
    for (const log of logs) {
      const existing = map.get(log.commentDate) ?? [];
      existing.push(log);
      map.set(log.commentDate, existing);
    }
    return map;
  }, [logs]);

  useEffect(() => {
    if (!calendarOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (calendarRef.current && !calendarRef.current.contains(e.target as Node)) {
        setCalendarOpen(false);
        setMultiDayLogs(null);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [calendarOpen]);

  const toggleCalendar = () => {
    setCalendarOpen((v) => !v);
    setMultiDayLogs(null);
  };

  const closeCalendar = () => {
    setCalendarOpen(false);
    setMultiDayLogs(null);
  };

  const year = viewMonth.getFullYear();
  const month = viewMonth.getMonth();
  const firstWeekday = (new Date(year, month, 1).getDay() + 6) % 7;
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const calendarCells: (number | null)[] = [...Array.from({ length: firstWeekday }, () => null), ...Array.from({ length: daysInMonth }, (_, i) => i + 1)];
  const dateStrFor = (day: number) => `${year}-${pad2(month + 1)}-${pad2(day)}`;

  const handleDayClick = (day: number) => {
    const dayLogs = logsByDate.get(dateStrFor(day));
    if (!dayLogs || dayLogs.length === 0) return;
    if (dayLogs.length === 1) {
      setSelectedSessionId(dayLogs[0].id);
      closeCalendar();
    } else {
      setMultiDayLogs(dayLogs);
    }
  };

  const triggerLabel =
    selectedSessionId === "ALL" || !selectedLog
      ? `Tất cả các buổi học (${logs.length})`
      : `${selectedLog.commentDate}${selectedLog.timeSlot ? ` · ${selectedLog.timeSlot}` : ""}`;

  // ===== KPI: tính từ dữ liệu thật =====
  const ratedLogs = logs.filter((l) => l.attitude);
  const attitudeFrequency = new Map<NonNullable<StudentCommentResponse["attitude"]>, number>();
  ratedLogs.forEach((l) => attitudeFrequency.set(l.attitude!, (attitudeFrequency.get(l.attitude!) ?? 0) + 1));
  let mostCommonAttitude: NonNullable<StudentCommentResponse["attitude"]> | null = null;
  let mostCommonCount = 0;
  attitudeFrequency.forEach((count, key) => {
    if (count > mostCommonCount) {
      mostCommonCount = count;
      mostCommonAttitude = key;
    }
  });
  const positiveAttitudeShare = ratedLogs.length
    ? Math.round((ratedLogs.filter((l) => l.attitude === "GOOD" || l.attitude === "FAIR").length / ratedLogs.length) * 100)
    : null;

  const progressValues = logs
    .flatMap((l) => [parseProgressPercent(l.grammarPreviousProgress), parseProgressPercent(l.videoPreviousProgress)])
    .filter((v): v is number => v != null);
  const avgHomeworkCompletion = progressValues.length ? Math.round(progressValues.reduce((a, b) => a + b, 0) / progressValues.length) : null;

  const attendanceRate = attendance.length
    ? Math.round((attendance.filter((a) => a.status === "PRESENT" || a.status === "LATE").length / attendance.length) * 100)
    : null;

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  return (
    <div className="bg-white rounded-[24px] border border-line shadow-sm p-4 md:p-6 space-y-6">
      {/* Header Bar */}
      <div className="bg-slate-50/80 p-5 rounded-2xl border border-line/80 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-lg md:text-xl font-black text-ink font-display">Nhận xét hàng ngày theo buổi học</h2>
            <span className="px-2.5 py-0.5 rounded-full bg-teal/10 text-teal text-xs font-bold border border-teal/20">UC-21</span>
          </div>
          <p className="text-xs text-muted font-bold mt-1">
            Quá trình rèn luyện &amp; Đánh giá phản xạ ngôn ngữ của học viên{" "}
            <span className="text-teal font-extrabold">
              {studentName} ({displayCode})
            </span>
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="relative" ref={calendarRef}>
            <button
              type="button"
              onClick={toggleCalendar}
              aria-label="Lọc theo buổi học"
              aria-haspopup="dialog"
              aria-expanded={calendarOpen}
              className="flex items-center gap-2 min-h-[44px] bg-white border border-line rounded-xl pl-3.5 pr-3 py-2.5 text-xs font-bold text-ink focus:outline-none focus:ring-2 focus:ring-teal/50 shadow-sm cursor-pointer"
            >
              <Calendar size={14} className="text-teal shrink-0" aria-hidden="true" />
              <span className="max-w-[180px] truncate">{triggerLabel}</span>
              <ChevronDown size={14} className={`text-muted shrink-0 transition-transform ${calendarOpen ? "rotate-180" : ""}`} aria-hidden="true" />
            </button>

            {calendarOpen && (
              <div
                role="dialog"
                aria-label="Chọn buổi học theo ngày"
                className="absolute right-0 top-full mt-2 z-30 w-[300px] bg-white border border-line rounded-2xl shadow-lg p-3 space-y-3"
              >
                <button
                  type="button"
                  onClick={() => {
                    setSelectedSessionId("ALL");
                    closeCalendar();
                  }}
                  className={`w-full text-left px-3 py-2 rounded-xl text-xs font-bold border transition-colors ${selectedSessionId === "ALL" ? "bg-teal text-white border-teal" : "bg-slate-50 text-ink border-line/80 hover:bg-sky-2"
                    }`}
                >
                  Tất cả các buổi học ({logs.length})
                </button>

                <div className="flex items-center justify-between">
                  <button
                    type="button"
                    onClick={() => setViewMonth(new Date(year, month - 1, 1))}
                    aria-label="Tháng trước"
                    className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-sky-2 text-muted"
                  >
                    <ChevronLeft size={16} aria-hidden="true" />
                  </button>
                  <span className="text-xs font-black text-ink capitalize">
                    {viewMonth.toLocaleDateString("vi-VN", { month: "long", year: "numeric" })}
                  </span>
                  <button
                    type="button"
                    onClick={() => setViewMonth(new Date(year, month + 1, 1))}
                    aria-label="Tháng sau"
                    className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-sky-2 text-muted"
                  >
                    <ChevronRight size={16} aria-hidden="true" />
                  </button>
                </div>

                <div className="grid grid-cols-7 gap-1 text-center">
                  {WEEKDAY_LABELS.map((w) => (
                    <span key={w} className="text-[10px] font-bold text-muted py-1">
                      {w}
                    </span>
                  ))}
                  {calendarCells.map((day, i) => {
                    if (day == null) return <span key={`blank-${i}`} />;
                    const dayLogs = logsByDate.get(dateStrFor(day));
                    const hasLogs = !!dayLogs && dayLogs.length > 0;
                    const isSelected = !!dayLogs && dayLogs.some((l) => l.id === selectedSessionId);
                    return (
                      <button
                        key={day}
                        type="button"
                        disabled={!hasLogs}
                        onClick={() => handleDayClick(day)}
                        aria-label={hasLogs ? `Buổi học ngày ${day}/${month + 1}` : undefined}
                        className={`relative w-9 h-9 mx-auto flex items-center justify-center rounded-lg text-xs font-bold transition-colors ${isSelected
                          ? "bg-teal text-white"
                          : hasLogs
                            ? "bg-teal/10 text-teal hover:bg-teal hover:text-white cursor-pointer"
                            : "text-slate-300 cursor-default"
                          }`}
                      >
                        {day}
                      </button>
                    );
                  })}
                </div>

                {multiDayLogs && (
                  <div className="border-t border-line/60 pt-2 space-y-1">
                    <p className="text-[10px] font-bold text-muted uppercase">Chọn đúng buổi học trong ngày</p>
                    {multiDayLogs.map((log) => (
                      <button
                        key={log.id}
                        type="button"
                        onClick={() => {
                          setSelectedSessionId(log.id);
                          closeCalendar();
                        }}
                        className="w-full text-left px-2.5 py-1.5 rounded-lg text-xs font-bold bg-slate-50 hover:bg-sky-2 text-ink"
                      >
                        {log.timeSlot ?? log.commentDate} — {log.sessionTypeLabel ?? "Buổi học"}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          <div className="relative" ref={rangeRef}>
            <button
              type="button"
              onClick={toggleRange}
              aria-label="Lọc theo khoảng thời gian"
              aria-haspopup="dialog"
              aria-expanded={rangeOpen}
              className="flex items-center gap-2 min-h-[44px] bg-white border border-line rounded-xl pl-3.5 pr-3 py-2.5 text-xs font-bold text-ink focus:outline-none focus:ring-2 focus:ring-teal/50 shadow-sm cursor-pointer"
            >
              <Clock size={14} className="text-teal shrink-0" aria-hidden="true" />
              <span className="max-w-[180px] truncate">{rangeLabel}</span>
              <ChevronDown size={14} className={`text-muted shrink-0 transition-transform ${rangeOpen ? "rotate-180" : ""}`} aria-hidden="true" />
            </button>

            {rangeOpen && (
              <div
                role="dialog"
                aria-label="Chọn khoảng thời gian"
                className="absolute right-0 top-full mt-2 z-30 w-[300px] bg-white border border-line rounded-2xl shadow-lg p-3 space-y-3"
              >
                <p className="text-[10px] font-bold text-muted">
                  {!dateFrom ? "Chọn ngày bắt đầu" : !dateTo ? "Chọn ngày kết thúc" : `${dateFrom} → ${dateTo}`}
                </p>

                <div className="flex items-center justify-between">
                  <button
                    type="button"
                    onClick={() => setRangeViewMonth(new Date(rYear, rMonth - 1, 1))}
                    aria-label="Tháng trước"
                    className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-sky-2 text-muted"
                  >
                    <ChevronLeft size={16} aria-hidden="true" />
                  </button>
                  <span className="text-xs font-black text-ink capitalize">
                    {rangeViewMonth.toLocaleDateString("vi-VN", { month: "long", year: "numeric" })}
                  </span>
                  <button
                    type="button"
                    onClick={() => setRangeViewMonth(new Date(rYear, rMonth + 1, 1))}
                    aria-label="Tháng sau"
                    className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-sky-2 text-muted"
                  >
                    <ChevronRight size={16} aria-hidden="true" />
                  </button>
                </div>

                <div className="grid grid-cols-7 gap-1 text-center">
                  {WEEKDAY_LABELS.map((w) => (
                    <span key={w} className="text-[10px] font-bold text-muted py-1">
                      {w}
                    </span>
                  ))}
                  {rangeCalendarCells.map((day, i) => {
                    if (day == null) return <span key={`blank-${i}`} />;
                    const dateStr = rDateStrFor(day);
                    const state = rangeCellState(dateStr);
                    return (
                      <button
                        key={day}
                        type="button"
                        onClick={() => handleRangeDayClick(day)}
                        aria-label={`Chọn ngày ${day}/${rMonth + 1}`}
                        className={`relative w-9 h-9 mx-auto flex items-center justify-center text-xs font-bold transition-colors cursor-pointer ${state === "start" || state === "end" || state === "single"
                          ? "bg-teal text-white rounded-full"
                          : state === "in-range"
                            ? "bg-teal/15 text-teal-deep rounded-none"
                            : "text-ink hover:bg-teal/10 rounded-full"
                          }`}
                      >
                        {day}
                      </button>
                    );
                  })}
                </div>

                <div className="flex items-center justify-between gap-2 pt-1 border-t border-line/60">
                  <button
                    type="button"
                    onClick={() => {
                      setDateFrom("");
                      setDateTo("");
                    }}
                    className="text-[11px] font-bold text-muted hover:text-ink"
                  >
                    Xóa lọc
                  </button>
                  <button
                    type="button"
                    onClick={() => setRangeOpen(false)}
                    className="px-3 py-1.5 bg-teal text-white text-[11px] font-bold rounded-lg hover:bg-teal-deep"
                  >
                    Xong
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* <div className="relative" ref={attitudeRef}>
            <button
              type="button"
              onClick={() => setAttitudeOpen((v) => !v)}
              aria-label="Lọc theo thái độ học tập"
              aria-haspopup="dialog"
              aria-expanded={attitudeOpen}
              className="flex items-center gap-2 min-h-[44px] bg-white border border-line rounded-xl pl-3.5 pr-3 py-2.5 text-xs font-bold text-ink focus:outline-none focus:ring-2 focus:ring-teal/50 shadow-sm cursor-pointer"
            >
              <Filter size={14} className="text-teal shrink-0" aria-hidden="true" />
              <span className="max-w-[140px] truncate">{attitudeFilter === "ALL" ? "Tất cả thái độ học tập" : attitudeLabels[attitudeFilter]}</span>
              <ChevronDown size={14} className={`text-muted shrink-0 transition-transform ${attitudeOpen ? "rotate-180" : ""}`} aria-hidden="true" />
            </button>

            {attitudeOpen && (
              <div
                role="dialog"
                aria-label="Chọn thái độ học tập"
                className="absolute right-0 top-full mt-2 z-30 w-[220px] bg-white border border-line rounded-2xl shadow-lg p-1.5 space-y-0.5"
              >
                <button
                  type="button"
                  onClick={() => {
                    setAttitudeFilter("ALL");
                    setAttitudeOpen(false);
                  }}
                  className={`w-full flex items-center justify-between gap-2 px-3 py-2 rounded-xl text-xs font-bold text-left transition-colors ${attitudeFilter === "ALL" ? "bg-teal/10 text-teal-deep font-extrabold" : "text-ink hover:bg-sky-2"
                    }`}
                >
                  <span>Tất cả thái độ</span>
                  {attitudeFilter === "ALL" && <Check size={14} className="text-teal-deep shrink-0" aria-hidden="true" />}
                </button>
                {(Object.entries(attitudeLabels) as [NonNullable<StudentCommentResponse["attitude"]>, string][]).map(([value, label]) => (
                  <button
                    key={value}
                    type="button"
                    onClick={() => {
                      setAttitudeFilter(value);
                      setAttitudeOpen(false);
                    }}
                    className={`w-full flex items-center justify-between gap-2 px-3 py-2 rounded-xl text-xs font-bold text-left transition-colors ${attitudeFilter === value ? "bg-teal/10 text-teal-deep font-extrabold" : "text-ink hover:bg-sky-2"
                      }`}
                  >
                    <span>{label}</span>
                    {attitudeFilter === value && <Check size={14} className="text-teal-deep shrink-0" aria-hidden="true" />}
                  </button>
                ))}
              </div>
            )}
          </div> */}
        </div>
      </div>

      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      {/* Summary KPI Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <div className="p-4 bg-sky-2 rounded-2xl border border-line/60 flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-teal/10 text-teal flex items-center justify-center shrink-0">
            <TrendingUp size={20} aria-hidden="true" />
          </div>
          <div>
            <p className="text-[10px] text-muted font-extrabold uppercase">Thái độ chung</p>
            <p className="text-sm font-black text-ink tabular-nums">
              {mostCommonAttitude && positiveAttitudeShare != null ? `Đạt loại ${attitudeLabels[mostCommonAttitude]} (${positiveAttitudeShare}%)` : "Chưa có dữ liệu"}
            </p>
          </div>
        </div>

        <div className="p-4 bg-sky-2 rounded-2xl border border-line/60 flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
            <ShieldCheck size={20} aria-hidden="true" />
          </div>
          <div>
            <p className="text-[10px] text-muted font-extrabold uppercase">Tỷ lệ hoàn thành BTVN</p>
            <p className="text-sm font-black text-emerald-600 tabular-nums">{avgHomeworkCompletion != null ? `${avgHomeworkCompletion}% Trung bình` : "Chưa có dữ liệu"}</p>
          </div>
        </div>

        <div className="p-4 bg-sky-2 rounded-2xl border border-line/60 flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center shrink-0">
            <Award size={20} aria-hidden="true" />
          </div>
          <div>
            <p className="text-[10px] text-muted font-extrabold uppercase">Buổi học ghi nhận</p>
            <p className="text-sm font-black text-ink">{logs.length} Buổi</p>
          </div>
        </div>

        <div className="p-4 bg-sky-2 rounded-2xl border border-line/60 flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center shrink-0">
            <Sparkles size={20} aria-hidden="true" />
          </div>
          <div>
            <p className="text-[10px] text-muted font-extrabold uppercase">Tình trạng chuyên cần</p>
            <p className="text-sm font-black text-purple-600 tabular-nums">
              {attendanceRate != null ? `${attendanceRate >= 90 ? "Đạt Chuẩn" : "Cần cải thiện"} (${attendanceRate}%)` : "Chưa có dữ liệu"}
            </p>
          </div>
        </div>
      </div>

      {/* Main Table */}
      <div className="border border-line/80 rounded-2xl overflow-hidden">
        <div className="p-4 border-b border-line bg-slate-50/50 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
          <div className="flex items-center gap-2">
            <MessageSquareText size={18} className="text-teal" aria-hidden="true" />
            <h3 className="text-sm font-black text-ink font-display uppercase tracking-wider">
              Bảng theo dõi nhật ký học tập học viên {studentName}
            </h3>
          </div>
          <div className="text-xs font-bold text-muted">
            Hiển thị <span className="text-teal font-black">{filteredLogs.length}</span> nhật ký buổi học
          </div>
        </div>

        {filteredLogs.length === 0 ? (
          <p className="text-xs text-muted font-bold italic text-center py-10">Chưa có nhận xét nào được duyệt cho lớp này.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-100/80 border-b border-line text-[11px] font-black uppercase text-[#6e7c93] tracking-wider whitespace-nowrap">
                  <th className="p-3.5 pl-4">Buổi Học &amp; Thời Gian</th>
                  <th className="p-3.5 min-w-[120px]">Thái Độ Học Tập</th>
                  <th className="p-3.5 min-w-[140px]">BTVN Ngữ Pháp Buổi Trước</th>
                  <th className="p-3.5 min-w-[140px]">BTVN Nghe-Nói Buổi Trước</th>
                  <th className="p-3.5 min-w-[220px]">Nhận Xét Học Sinh</th>
                  <th className="p-3.5 min-w-[240px]">BTVN Buổi Sau</th>
                  <th className="p-3.5 pr-4">Ghi Chú</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line/60 text-xs font-semibold text-ink">
                {filteredLogs.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-50/80 transition-colors align-top">
                    {/* Buổi Học & Thời Gian */}
                    <td className="p-3.5 pl-4 pt-4">
                      <div className="font-bold text-teal-deep text-xs leading-tight min-w-[120px] max-w-[160px]">{log.sessionTypeLabel ?? "Buổi học"}</div>
                      <div className="text-[10px] text-muted font-mono flex items-center gap-1 mt-1">
                        <Clock size={10} aria-hidden="true" /> {log.commentDate} {log.timeSlot ? `(${log.timeSlot})` : ""}
                      </div>
                    </td>

                    {/* Thái Độ Học Tập — kiểu dáng dropdown nhưng KHÔNG chỉnh sửa được (chỉ xem) */}
                    <td className="p-3.5 pt-3">
                      <div
                        className={`w-full flex items-center justify-between gap-1.5 px-2.5 py-1.5 rounded-xl border text-xs font-black shadow-sm select-none ${log.attitude ? attitudeStyles[log.attitude] : "bg-slate-50 text-slate-500 border-slate-300"
                          }`}
                      >
                        <span>{log.attitude ? attitudeLabels[log.attitude] : "—"}</span>
                        <ChevronDown size={13} className="opacity-50" aria-hidden="true" />
                      </div>
                    </td>

                    {/* BTVN Ngữ Pháp Buổi Trước */}
                    <td className="p-3.5 pt-3">
                      <div className="w-full bg-slate-50 border border-line rounded-xl px-2.5 py-1.5 text-xs font-bold text-ink tabular-nums">
                        {log.homeworkPreviousScore || log.grammarPreviousProgress || "—"}
                      </div>
                    </td>

                    {/* BTVN Nghe-Nói Buổi Trước */}
                    <td className="p-3.5 pt-3">
                      <div className="w-full bg-purple-50/50 border border-purple-200 rounded-xl px-2.5 py-1.5 text-xs font-bold text-purple-900 tabular-nums">
                        {log.homeworkPreviousSpeakingScore || log.videoPreviousProgress || "—"}
                      </div>
                    </td>

                    {/* Nhận Xét Học Sinh — hiện đủ nguyên văn, không cắt dòng. */}
                    <td className="p-3.5 pt-3">
                      <div className="p-2.5 bg-slate-50 rounded-xl border border-line/60 text-xs text-ink/90 leading-relaxed font-sans italic">"{log.content}"</div>
                    </td>

                    {/* BTVN Buổi Sau (Ngữ pháp + Video ôn tập gộp 1 cột) */}
                    <td className="p-3.5 pt-3">
                      <div className="p-3 bg-slate-50/80 rounded-2xl border border-line/80 space-y-2.5 shadow-2xs">
                        <div className="space-y-1">
                          <div className="flex items-center gap-1 text-[10px] font-black text-blue-700 uppercase tracking-wider">Ngữ pháp</div>
                          <div className="w-full bg-blue-50/70 border border-blue-200 rounded-xl px-2 py-1 text-xs font-bold text-blue-900">
                            {log.homeworkNextGrammarLabel || "—"}
                          </div>
                        </div>

                        <hr className="border-line/60 my-1" />

                        <div className="space-y-1">
                          <div className="flex items-center gap-1 text-[10px] font-black text-amber-800 uppercase tracking-wider">
                            <Video size={11} className="text-amber-600" aria-hidden="true" /> Video ôn tập
                          </div>
                          <div className="w-full bg-amber-50/80 border border-amber-200 rounded-xl px-2 py-1 text-xs font-bold text-amber-950">
                            {log.homeworkNextVideoLabel || "—"}
                          </div>
                        </div>
                      </div>
                    </td>

                    {/* Ghi Chú */}
                    <td className="p-3.5 pr-4 whitespace-nowrap pt-4">
                      {log.note ? (
                        <span className="text-[11px] font-bold text-teal bg-teal/10 px-2 py-1 rounded-md border border-teal/20 inline-block">{log.note}</span>
                      ) : (
                        <span className="text-muted text-[11px] italic">--</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
