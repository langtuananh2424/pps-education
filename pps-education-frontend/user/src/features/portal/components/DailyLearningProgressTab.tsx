import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  Award,
  BookOpen,
  Calendar,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clock,
  Filter,
  LayoutGrid,
  MapPin,
  ShieldCheck,
  Sparkles,
  Table as TableIcon,
  UserCheck,
  Video
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { formatDate, formatDateTimeHm, formatHm, toLocaleTag } from "@/lib/format";
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

const pad2 = (n: number) => String(n).padStart(2, "0");

/** % quy đổi cố định theo mức (đã xác nhận với người dùng 2026-08-12) — dùng để tính "Thái độ học
 * tập" trung bình = trung bình cộng % của mọi buổi đã chấm trong khoảng đang xem. */
const attitudePercent: Record<NonNullable<StudentCommentResponse["attitude"]>, number> = {
  WEAK: 20,
  AVERAGE: 50,
  FAIR: 70,
  GOOD: 90,
  EXCELLENT: 100
};

/** Suy ngược % trung bình về đúng 1 mức để hiện "Đạt loại X" — lấy mức CAO NHẤT mà % trung bình đã
 * đạt/vượt ngưỡng (floor), mirror cách quy đổi %-sang-xếp loại thông thường (VD 82% đã vượt Khá 70%
 * nhưng chưa tới Tốt 90% → "Khá"). Nhất quán với đúng 1 con số % đang hiển thị, không dùng "mức phổ
 * biến nhất" (mode) như trước — 2 chỉ số có thể lệch nhau (VD 3 buổi Khá + 1 buổi Xuất sắc: mode là
 * Khá nhưng trung bình 77.5% cũng vẫn rơi vào Khá nên trùng, nhưng không phải lúc nào cũng trùng).
 */
function attitudeLevelFromPercent(percent: number): NonNullable<StudentCommentResponse["attitude"]> {
  const levels: NonNullable<StudentCommentResponse["attitude"]>[] = ["EXCELLENT", "GOOD", "FAIR", "AVERAGE", "WEAK"];
  return levels.find((level) => percent >= attitudePercent[level]) ?? "WEAK";
}

/** "Loại giáo viên" của buổi — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06. */
const teacherTypeStyles: Record<"VIETNAMESE" | "FOREIGN", string> = {
  VIETNAMESE: "bg-sky-50 text-sky-800 border-sky-300",
  FOREIGN: "bg-purple-50 text-purple-800 border-purple-300"
};

const attitudeStyles: Record<NonNullable<StudentCommentResponse["attitude"]>, string> = {
  EXCELLENT: "bg-violet-50 text-violet-800 border-violet-300",
  GOOD: "bg-emerald-50 text-emerald-800 border-emerald-300",
  FAIR: "bg-teal-50 text-teal-800 border-teal-300",
  AVERAGE: "bg-amber-50 text-amber-800 border-amber-300",
  WEAK: "bg-orange-50 text-orange-800 border-orange-300"
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
  sessionNumber: number | null;
  roomName: string | null;
  lessonContent: string | null;
  /** "Loại giáo viên" của buổi (ClassSession.teacherType) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06, để phụ huynh/học sinh theo dõi buổi đó GV Việt Nam hay nước ngoài dạy. */
  teacherType: "VIETNAMESE" | "FOREIGN" | null;
  attitude: StudentCommentResponse["attitude"];
  // Đồng bộ đúng cấu trúc BTVN với form Giáo viên/màn Quản lý điểm trường (bổ sung ngoài SDD gốc, đã
  // xác nhận với người dùng 2026-08-06) — "BTVN buổi trước"/"buổi sau" đều tách 3 kênh: Offline/Bài/
  // Video (trước Offline không tách riêng, giờ có homeworkPreviousOfflineText; "sau" trước gộp chung
  // homeworkNext+homeworkNextExerciseTitle vào 1 field, giờ tách homeworkNextOfflineText riêng).
  homeworkPreviousOfflineText: string | null;
  homeworkPreviousScore: string | null;
  homeworkPreviousSpeakingScore: string | null;
  grammarPreviousProgress: string | null;
  videoPreviousProgress: string | null;
  content: string;
  homeworkNextOfflineText: string | null;
  homeworkNextGrammarLabel: string | null;
  homeworkNextVideoLabel: string | null;
  homeworkNextDueAt: string | null;
  note: string | null;
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — id bản giao đứng sau nhãn (chỉ có
  // ý nghĩa khi label tương ứng khác null), dùng để bấm nhảy thẳng tới bài làm/kết quả ở tab BTVN
  // (xem onOpenGrammarHomework/onOpenVideoHomework).
  homeworkNextExerciseAssignmentId: number | null;
  homeworkNextReviewVideoAssignmentId: number | null;
}

interface DailyLearningProgressTabProps {
  studentName: string;
  studentCode: string;
  classId: number;
  /** UC-64 (2026-07-29) — chỉ set khi xem qua Cổng Phụ huynh (dùng API .../parent/children/{studentId}/...). Không set thì Học sinh tự xem (self-service /students/me/...). */
  parentStudentId?: number;
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — bấm vào tên "Bài ngữ pháp/nghe"/
   * "Video TKN/PX" ở cột BTVN nhảy thẳng tới bài làm/kết quả tương ứng ở tab BTVN, không phải chỉ đọc
   * tên suông — KHÔNG mở modal làm bài, chỉ cuộn tới + nổi viền đúng card/dòng đó để nhận biết (theo
   * yêu cầu người dùng). Học sinh tự xem: PortalPage cuộn + highlight đúng card ở AssignmentsTab — dùng
   * exerciseAssignmentId/reviewVideoAssignmentId. Phụ huynh xem con: PortalPage cuộn + highlight đúng
   * dòng kết quả ở ParentHomeworkProgressTab — dùng commentId, bỏ qua assignmentId truyền kèm. Không
   * set (undefined) thì render tên suông như cũ (không phải mọi nơi dùng component này đều cần điều hướng).
   */
  onOpenGrammarHomework?: (commentId: number, exerciseAssignmentId: number) => void;
  onOpenVideoHomework?: (commentId: number, reviewVideoAssignmentId: number) => void;
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
export default function DailyLearningProgressTab({
  studentName,
  studentCode,
  classId,
  parentStudentId,
  onOpenGrammarHomework,
  onOpenVideoHomework
}: DailyLearningProgressTabProps) {
  const { t, i18n } = useTranslation("portal-progress");

  const sessionTypeLabels: Record<string, string> = {
    REGULAR: t("sessionType.regular"),
    REVIEW: t("sessionType.review"),
    EXAM: t("sessionType.exam"),
    MAKEUP: t("sessionType.makeup")
  };

  /** Thang thái độ chốt lại 2026-08-12 (StudentComment.Attitude), thay cho thang 6 mức cũ. */
  const attitudeLabels: Record<NonNullable<StudentCommentResponse["attitude"]>, string> = {
    WEAK: t("attitude.weak"),
    AVERAGE: t("attitude.average"),
    FAIR: t("attitude.fair"),
    GOOD: t("attitude.good"),
    EXCELLENT: t("attitude.excellent")
  };

  const teacherTypeLabels: Record<"VIETNAMESE" | "FOREIGN", string> = {
    VIETNAMESE: t("teacherType.vietnamese"),
    FOREIGN: t("teacherType.foreign")
  };

  const weekdayLabels = t("weekdayLabels", { returnObjects: true }) as string[];

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
      .catch((err) => setError(err instanceof ApiError ? err.message : t("loadError")))
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
            timeSlot: session ? `${formatHm(session.startTime)} - ${formatHm(session.endTime)}` : null,
            sessionTypeLabel: session ? sessionTypeLabels[session.sessionType] ?? session.sessionType : null,
            sessionNumber: session?.sessionNumber ?? null,
            roomName: session?.roomName ?? null,
            lessonContent: c.lessonContent,
            teacherType: session?.teacherType ?? null,
            attitude: c.attitude,
            homeworkPreviousOfflineText: c.homeworkPreviousOfflineText,
            homeworkPreviousScore: c.homeworkPreviousScore,
            homeworkPreviousSpeakingScore: c.homeworkPreviousSpeakingScore,
            grammarPreviousProgress: c.grammarPreviousProgress,
            videoPreviousProgress: c.videoPreviousProgress,
            content: c.content,
            homeworkNextOfflineText: c.homeworkNext,
            homeworkNextGrammarLabel: c.homeworkNextExerciseTitle,
            homeworkNextVideoLabel: c.homeworkNextReviewVideoSetTitle,
            homeworkNextDueAt: c.homeworkNextDueAt,
            note: c.note,
            homeworkNextExerciseAssignmentId: c.homeworkNextExerciseAssignmentId,
            homeworkNextReviewVideoAssignmentId: c.homeworkNextReviewVideoAssignmentId
          };
        }),
    [comments, sessionById]
  );

  // Xem dạng Bảng tổng quan (mặc định, tiện lướt nhanh nhiều buổi) hoặc Dạng thẻ (1 buổi/thẻ, dễ đọc
  // chi tiết từng buổi hơn) — thiết kế mới 2026-07-30, dữ liệu dùng chung, chỉ đổi cách trình bày.
  const [viewMode, setViewMode] = useState<"TABLE" | "CARDS">("TABLE");
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

  // Đồng bộ "Chuyên cần" với đúng bộ lọc "Các buổi"/"Từ → Đến" của bảng Nhật ký học tập (đã xác nhận
  // với người dùng 2026-08-12, cùng hướng với "Thái độ học tập" ở dưới) — attendance là dữ liệu tải
  // riêng (không đi qua comments), nên lọc lại theo đúng ngày buổi qua sessionById thay vì gộp thẳng
  // vào filteredLogs (tránh vô tình loại attendance của buổi CHƯA có nhận xét khi không lọc gì).
  // Không áp dụng attitudeFilter ở đây — điểm danh không có khái niệm "thái độ".
  const filteredAttendance = attendance.filter((a) => {
    const session = sessionById.get(a.classSessionId);
    if (!session) return false;
    if (selectedSessionId !== "ALL") return selectedLog != null && session.sessionDate === selectedLog.commentDate;
    if (dateFrom && session.sessionDate < dateFrom) return false;
    if (dateTo && session.sessionDate > dateTo) return false;
    return true;
  });

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

  const toggleRange = () => {
    setRangeOpen((v) => {
      const next = !v;
      if (next) setRangeViewMonth(new Date(dateFrom || dateTo || Date.now()));
      return next;
    });
  };

  const formatDateVN = (dateStr: string) => formatDate(dateStr, i18n.language);

  /** Panel hiện 2 tháng liền kề cạnh nhau (giống RangePicker chuẩn) thay vì 1 tháng chật hẹp — mỗi
   * tháng tự tính lưới ngày riêng theo (year, month) truyền vào, không còn cố định vào rangeViewMonth. */
  const buildMonthCells = (year: number, month: number): (number | null)[] => {
    const firstWeekday = (new Date(year, month, 1).getDay() + 6) % 7;
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    return [...Array.from({ length: firstWeekday }, () => null), ...Array.from({ length: daysInMonth }, (_, i) => i + 1)];
  };
  const dateStrForYM = (year: number, month: number, day: number) => `${year}-${pad2(month + 1)}-${pad2(day)}`;

  /** Bấm 1: đặt Từ ngày. Bấm 2 (sau ngày Từ): đặt Đến ngày. Bấm khi đã đủ cặp hoặc bấm ngày trước "Từ": bắt đầu chọn lại từ đầu. */
  const handleRangeDayClick = (dateStr: string) => {
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
      ? t("sessionsCount", { count: logs.length })
      : `${selectedLog.commentDate}${selectedLog.timeSlot ? ` · ${selectedLog.timeSlot}` : ""}`;

  /**
   * Mobile: gộp 2 nút lọc "Các buổi"/"Từ ngày - Đến ngày" thành 1 nút dropdown chọn loại lọc, đỡ
   * chiếm 2 hàng riêng trên màn hẹp (theo yêu cầu người dùng, 2026-07-31) — desktop (md+) vẫn giữ 2
   * nút riêng như cũ (xem 2 khối "hidden md:block" bên dưới). Dùng chung state lọc thật
   * (selectedSessionId/dateFrom/dateTo) với 2 nút desktop, chỉ khác UI trigger + panel gộp lại.
   */
  const [mobileFilterOpen, setMobileFilterOpen] = useState(false);
  const [mobileFilterMode, setMobileFilterMode] = useState<"SESSION" | "RANGE">("SESSION");
  const mobileFilterRef = useRef<HTMLDivElement>(null);
  const mobileFilterLabel =
    mobileFilterMode === "SESSION" ? triggerLabel : `${dateFrom ? formatDateVN(dateFrom) : t("fromDatePlaceholder")} → ${dateTo ? formatDateVN(dateTo) : t("toDatePlaceholder")}`;

  useEffect(() => {
    if (!mobileFilterOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (mobileFilterRef.current && !mobileFilterRef.current.contains(e.target as Node)) {
        setMobileFilterOpen(false);
        setMultiDayLogs(null);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [mobileFilterOpen]);

  // ===== KPI: tính từ dữ liệu thật =====
  // Thang thái độ chốt lại 2026-08-12 (đã xác nhận với người dùng) — "Thái độ học tập" = trung bình
  // cộng % quy đổi (attitudePercent) của mọi buổi đã chấm trong khoảng đang xem, KHÔNG còn dùng
  // "mức phổ biến nhất" (mode) + "tỷ lệ buổi Khá/Tốt" như trước. Nhãn "Đạt loại X" suy ngược từ chính
  // % trung bình này (attitudeLevelFromPercent) để luôn khớp nhau, không lệch giữa 2 chỉ số.
  // Dùng filteredLogs (đã qua bộ lọc "Các buổi"/"Từ → Đến" có sẵn của trang, xem dòng ~244) thay vì
  // logs thô — đã xác nhận với người dùng 2026-08-12: mặc định (chưa lọc gì) vẫn hiện trung bình toàn
  // bộ buổi, nhưng hễ chọn 1 khoảng ngày (VD đúng 1 kỳ) thì card tự thu hẹp theo đúng khoảng đó,
  // khớp với đúng những buổi đang hiện trong bảng bên dưới thay vì lệch nhau như trước.
  const ratedLogs = filteredLogs.filter((l) => l.attitude);
  const averageAttitudePercent = ratedLogs.length
    ? Math.round(ratedLogs.reduce((sum, l) => sum + attitudePercent[l.attitude!], 0) / ratedLogs.length)
    : null;
  const averageAttitudeLevel = averageAttitudePercent != null ? attitudeLevelFromPercent(averageAttitudePercent) : null;

  // Đồng bộ "BTVN hoàn thành"/"Số buổi đã học" theo đúng bộ lọc hiện có (dùng filteredLogs thay vì
  // logs — đã xác nhận với người dùng 2026-08-12, cùng hướng với "Thái độ học tập").
  const progressValues = filteredLogs
    .flatMap((l) => [parseProgressPercent(l.grammarPreviousProgress), parseProgressPercent(l.videoPreviousProgress)])
    .filter((v): v is number => v != null);
  const avgHomeworkCompletion = progressValues.length ? Math.round(progressValues.reduce((a, b) => a + b, 0) / progressValues.length) : null;

  // PRESENT/LATE/EARLY_LEAVE đều tính là "có tham dự" (đều có mặt tại buổi học, chỉ khác đúng giờ hay
  // không/rời sớm hay không) — chỉ ABSENT/EXCUSED mới không tính, 2026-08-11.
  const attendanceRate = filteredAttendance.length
    ? Math.round(
        (filteredAttendance.filter((a) => a.status === "PRESENT" || a.status === "LATE" || a.status === "EARLY_LEAVE").length /
          filteredAttendance.length) *
          100
      )
    : null;

  if (loading) return <p className="text-sm text-muted font-bold">{t("loading")}</p>;

  /**
   * Bấm tên bài (nếu có id bản giao + có callback tương ứng) nhảy thẳng tới bài làm/kết quả ở tab
   * BTVN — không phải mọi nơi dùng component đều truyền callback này (chỉ set ở PortalPage), nên vẫn
   * phải có fallback render tên suông như cũ khi thiếu 1 trong 2 điều kiện.
   */
  const renderGrammarLabel = (log: SessionFeedbackLog, className: string) => {
    if (!log.homeworkNextGrammarLabel) return <span className={className}>—</span>;
    if (log.homeworkNextExerciseAssignmentId == null || !onOpenGrammarHomework) {
      return <span className={className}>{log.homeworkNextGrammarLabel}</span>;
    }
    return (
      <button
        type="button"
        onClick={() => onOpenGrammarHomework(Number(log.id), log.homeworkNextExerciseAssignmentId!)}
        className={`${className} text-teal-deep underline decoration-teal/40 hover:decoration-teal underline-offset-2 text-left cursor-pointer`}
      >
        {log.homeworkNextGrammarLabel}
      </button>
    );
  };

  const renderVideoLabel = (log: SessionFeedbackLog, className: string) => {
    if (!log.homeworkNextVideoLabel) return <span className={className}>—</span>;
    if (log.homeworkNextReviewVideoAssignmentId == null || !onOpenVideoHomework) {
      return <span className={className}>{log.homeworkNextVideoLabel}</span>;
    }
    return (
      <button
        type="button"
        onClick={() => onOpenVideoHomework(Number(log.id), log.homeworkNextReviewVideoAssignmentId!)}
        className={`${className} text-teal-deep underline decoration-teal/40 hover:decoration-teal underline-offset-2 text-left cursor-pointer`}
      >
        {log.homeworkNextVideoLabel}
      </button>
    );
  };

  /** Nội dung panel chọn buổi — dùng chung cho nút desktop riêng lẫn dropdown gộp trên mobile. */
  const renderSessionPickerBody = (closeAll: () => void) => (
    <>
      <button
        type="button"
        onClick={() => {
          setSelectedSessionId("ALL");
          closeCalendar();
          closeAll();
        }}
        className={`w-full text-left px-3 py-2 rounded-xl text-xs font-bold border transition-colors ${selectedSessionId === "ALL" ? "bg-teal text-white border-teal" : "bg-slate-50 text-ink border-line/80 hover:bg-sky-2"
          }`}
      >
        {t("allSessions", { count: logs.length })}
      </button>

      <div className="flex items-center justify-between">
        <button
          type="button"
          onClick={() => setViewMonth(new Date(year, month - 1, 1))}
          aria-label={t("prevMonth")}
          className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-sky-2 text-muted"
        >
          <ChevronLeft size={16} aria-hidden="true" />
        </button>
        <span className="text-xs font-black text-ink capitalize">{viewMonth.toLocaleDateString(toLocaleTag(i18n.language), { month: "long", year: "numeric" })}</span>
        <button
          type="button"
          onClick={() => setViewMonth(new Date(year, month + 1, 1))}
          aria-label={t("nextMonth")}
          className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-sky-2 text-muted"
        >
          <ChevronRight size={16} aria-hidden="true" />
        </button>
      </div>

      <div className="grid grid-cols-7 gap-1 text-center">
        {weekdayLabels.map((w) => (
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
              aria-label={hasLogs ? t("sessionDayAriaLabel", { date: `${day}/${month + 1}` }) : undefined}
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
          <p className="text-[10px] font-bold text-muted uppercase">{t("pickExactSession")}</p>
          {multiDayLogs.map((log) => (
            <button
              key={log.id}
              type="button"
              onClick={() => {
                setSelectedSessionId(log.id);
                closeAll();
              }}
              className="w-full text-left px-2.5 py-1.5 rounded-lg text-xs font-bold bg-slate-50 hover:bg-sky-2 text-ink"
            >
              {log.timeSlot ?? log.commentDate} — {log.sessionTypeLabel ?? t("sessionFallback")}
            </button>
          ))}
        </div>
      )}
    </>
  );

  /** Nội dung panel chọn khoảng ngày — dùng chung cho nút desktop riêng lẫn dropdown gộp trên mobile. */
  const renderRangePickerBody = (onDone: () => void) => (
    <>
      <p className="text-[11px] font-bold text-muted">
        {!dateFrom ? t("chooseStartDate") : !dateTo ? t("chooseEndDate") : `${formatDateVN(dateFrom)} → ${formatDateVN(dateTo)}`}
      </p>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {[0, 1].map((offset) => {
          const panelDate = new Date(rangeViewMonth.getFullYear(), rangeViewMonth.getMonth() + offset, 1);
          const pYear = panelDate.getFullYear();
          const pMonth = panelDate.getMonth();
          const cells = buildMonthCells(pYear, pMonth);
          return (
            <div key={offset} className="space-y-2">
              <div className="flex items-center justify-between">
                <button
                  type="button"
                  onClick={() => setRangeViewMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() - 1, 1))}
                  aria-label={t("prevMonth")}
                  className={`w-7 h-7 flex items-center justify-center rounded-lg hover:bg-sky-2 text-muted ${offset === 1 ? "invisible" : ""}`}
                >
                  <ChevronLeft size={15} aria-hidden="true" />
                </button>
                <span className="text-xs font-black text-ink capitalize">{panelDate.toLocaleDateString(toLocaleTag(i18n.language), { month: "long", year: "numeric" })}</span>
                <button
                  type="button"
                  onClick={() => setRangeViewMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() + 1, 1))}
                  aria-label={t("nextMonth")}
                  className={`w-7 h-7 flex items-center justify-center rounded-lg hover:bg-sky-2 text-muted ${offset === 0 ? "invisible" : ""}`}
                >
                  <ChevronRight size={15} aria-hidden="true" />
                </button>
              </div>

              <div className="grid grid-cols-7 gap-1 text-center">
                {weekdayLabels.map((w) => (
                  <span key={w} className="text-[10px] font-bold text-muted py-1">
                    {w}
                  </span>
                ))}
                {cells.map((day, i) => {
                  if (day == null) return <span key={`blank-${offset}-${i}`} />;
                  const dateStr = dateStrForYM(pYear, pMonth, day);
                  const state = rangeCellState(dateStr);
                  return (
                    <button
                      key={day}
                      type="button"
                      onClick={() => handleRangeDayClick(dateStr)}
                      aria-label={t("pickDayAriaLabel", { date: `${day}/${pMonth + 1}` })}
                      className={`relative w-8 h-8 mx-auto flex items-center justify-center text-xs font-bold transition-colors cursor-pointer ${state === "start" || state === "end" || state === "single"
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
            </div>
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
          {t("clearFilter")}
        </button>
        <button type="button" onClick={onDone} className="px-3 py-1.5 bg-teal text-white text-[11px] font-bold rounded-lg hover:bg-teal-deep">
          {t("done")}
        </button>
      </div>
    </>
  );

  /**
   * Dạng thẻ — mỗi buổi 1 thẻ, cùng dữ liệu như bảng nhưng dễ đọc chi tiết từng buổi hơn (2026-07-30).
   * Tách thành biến dùng chung: mobile LUÔN ép hiện dạng này (bảng min-w-[1200px] không cách nào co vừa
   * màn hình hẹp, kể cả cuộn ngang cũng khó dùng — theo phản hồi người dùng "để trưng à?", 2026-07-31),
   * desktop vẫn theo đúng lựa chọn Bảng/Thẻ như cũ.
   */
  const cardsView = (
    <div className="space-y-4">
      {filteredLogs.length === 0 ? (
        <p className="text-xs text-muted font-bold italic text-center py-10 border border-line/80 rounded-2xl">
          {t("noApprovedComments")}
        </p>
      ) : (
        filteredLogs.map((log) => {
          // Đồng bộ đúng cấu trúc BTVN với bảng ở trên (bổ sung ngoài SDD gốc, đã xác nhận với người
          // dùng 2026-08-06) — giữ nhãn chung chung như bảng (không đổi theo teacherType) để nhất quán.
          const cardGrammarLabel = t("table.grammarListening");
          const cardVideoLabel = t("table.videoTkn");
          const prevGrammarDisplay = log.homeworkPreviousScore || log.grammarPreviousProgress;
          const prevSpeakingDisplay = log.homeworkPreviousSpeakingScore || log.videoPreviousProgress;
          const prevGrammarPercent = parseProgressPercent(prevGrammarDisplay ?? null);
          return (
            <div key={log.id} className="bg-white border border-line rounded-2xl p-5 shadow-2xs hover:shadow-xs transition-all space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-line/70 pb-3">
                <div className="space-y-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-800 text-[11px] font-black font-mono border border-slate-200">
                      {log.commentDate}
                    </span>
                    <span className="text-xs font-extrabold text-muted flex items-center gap-1">
                      <Clock size={12} className="text-teal" aria-hidden="true" /> {log.timeSlot ?? "—"}
                    </span>
                    {log.roomName && (
                      <span className="text-xs font-extrabold text-muted flex items-center gap-1">
                        <MapPin size={12} className="text-teal" aria-hidden="true" /> {log.roomName}
                      </span>
                    )}
                  </div>
                  <h3 className="text-base font-black text-ink font-display">
                    {log.sessionNumber != null ? t("sessionNumber", { number: log.sessionNumber }) : log.sessionTypeLabel ?? t("sessionFallback")}
                    {log.lessonContent && <span className="text-muted font-bold"> — {log.lessonContent}</span>}
                  </h3>
                </div>

                <div className="flex items-center gap-2 flex-wrap shrink-0">
                  {log.teacherType && (
                    <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold border ${teacherTypeStyles[log.teacherType]}`}>
                      {t("card.teacherLabel", { type: teacherTypeLabels[log.teacherType] })}
                    </span>
                  )}
                  <span
                    className={`px-2.5 py-0.5 rounded-full text-xs font-bold border ${log.attitude ? attitudeStyles[log.attitude] : "bg-slate-100 text-slate-700 border-slate-200"
                      }`}
                  >
                    {t("card.attitudeLabel", { attitude: log.attitude ? attitudeLabels[log.attitude] : "—" })}
                  </span>
                </div>
              </div>

              <div className="p-3.5 bg-slate-50/80 rounded-xl border-l-4 border-teal text-xs text-ink/90 font-medium leading-relaxed italic">
                <span className="font-extrabold not-italic text-slate-700 block mb-0.5">{t("card.teacherComment")}</span>"{log.content}"
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                <div className="p-3.5 bg-slate-50 rounded-xl border border-line/80 space-y-2">
                  <div className="flex items-center justify-between font-black text-slate-800 uppercase tracking-wider text-[11px]">
                    <span className="flex items-center gap-1">
                      <CheckCircle2 size={13} className="text-emerald-600" aria-hidden="true" /> {t("card.prevHomeworkResult")}
                    </span>
                    {prevGrammarPercent != null && <span className="text-emerald-700 font-extrabold">{t("card.passedPercent", { percent: prevGrammarPercent })}</span>}
                  </div>

                  <div className="space-y-1.5 pt-1">
                    <div className="flex justify-between text-[11px]">
                      <span className="text-slate-600 font-semibold">{t("card.offlineLabel")}</span>
                      <span className="font-bold text-slate-900">{log.homeworkPreviousOfflineText || "—"}</span>
                    </div>
                    <div className="flex justify-between text-[11px]">
                      <span className="text-slate-600 font-semibold">{cardGrammarLabel}:</span>
                      <span className="font-bold text-slate-900">{prevGrammarDisplay || "—"}</span>
                    </div>
                    {prevGrammarPercent != null && (
                      <div className="w-full bg-slate-200 h-1.5 rounded-full overflow-hidden">
                        <div className="bg-emerald-500 h-full rounded-full transition-all duration-500" style={{ width: `${Math.min(100, Math.max(0, prevGrammarPercent))}%` }} />
                      </div>
                    )}
                    <div className="flex justify-between text-[11px] pt-1">
                      <span className="text-slate-600 font-semibold">{cardVideoLabel}:</span>
                      <span className="font-bold text-purple-900">{prevSpeakingDisplay || "—"}</span>
                    </div>
                  </div>
                </div>

                <div className="p-3.5 bg-slate-50 rounded-xl border border-line/80 space-y-2">
                  <div className="flex items-center justify-between font-black text-slate-800 uppercase tracking-wider text-[11px]">
                    <span className="flex items-center gap-1">
                      <BookOpen size={13} className="text-blue-600" aria-hidden="true" /> {t("card.homework")}
                    </span>
                  </div>

                  <div className="space-y-2 pt-1">
                    <div className="flex items-start justify-between gap-2 text-[11px]">
                      <span className="font-bold text-slate-800 shrink-0">{t("card.offlineLabel")}</span>
                      <span className="font-semibold text-slate-700 text-right">{log.homeworkNextOfflineText || "—"}</span>
                    </div>
                    <div className="flex items-start justify-between gap-2 text-[11px]">
                      <span className="font-bold text-slate-800 shrink-0">{cardGrammarLabel}:</span>
                      {renderGrammarLabel(log, "font-semibold text-right")}
                    </div>
                    <div className="flex items-start justify-between gap-2 text-[11px]">
                      <span className="flex items-center gap-1.5 font-bold text-slate-800 shrink-0">
                        <Video size={12} className="text-amber-600 shrink-0" aria-hidden="true" /> {cardVideoLabel}:
                      </span>
                      {renderVideoLabel(log, "font-semibold text-right")}
                    </div>
                    <div className="pt-1 border-t border-line/60 flex items-start justify-between gap-2 text-[11px]">
                      <span className="font-bold text-slate-800 shrink-0">{t("card.dueDateLabel")}</span>
                      <span className="font-semibold text-slate-700 text-right">
                        {log.homeworkNextDueAt ? formatDateTimeHm(log.homeworkNextDueAt, i18n.language) : "—"}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {log.note && (
                <p className="text-[11px] font-bold text-teal bg-teal/10 px-3 py-1.5 rounded-lg border border-teal/20 inline-block">
                  {t("card.noteLabel", { note: log.note })}
                </p>
              )}
            </div>
          );
        })
      )}
    </div>
  );

  return (
    <div className="bg-white rounded-[24px] border border-line shadow-sm p-4 md:p-6 space-y-6">
      {/* Header Bar — padding gọn hơn trên mobile (p-4 thay vì p-5), giữ nguyên desktop. */}
      <div className="bg-slate-50/80 p-4 md:p-5 rounded-2xl border border-line/80 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="md:w-80 md:shrink-0">
          <div className="flex items-center gap-2">
            <h2 className="text-lg md:text-xl font-black text-ink font-display">{t("title")}</h2>
            {parentStudentId != null ? <span className="px-2.5 py-0.5 rounded-full bg-teal/10 text-teal text-xs font-bold border border-teal/20">
              {t("parentTrackingBadge")}
            </span> : null}
          </div>
          <p className="text-xs text-muted font-bold mt-1">
            {t("subtitle")}{" "}
            <span className="text-teal font-extrabold">
              {studentName}
            </span>
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="flex items-center p-1 bg-white rounded-xl border border-line shrink-0">
            <button
              type="button"
              onClick={() => setViewMode("TABLE")}
              aria-label={t("viewTable")}
              className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-bold transition-all cursor-pointer ${viewMode === "TABLE" ? "bg-teal text-white shadow-2xs" : "text-muted hover:text-ink"
                }`}
            >
              <TableIcon size={13} aria-hidden="true" /> <span className="hidden md:inline">{t("tableShort")}</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode("CARDS")}
              aria-label={t("viewCards")}
              className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-bold transition-all cursor-pointer ${viewMode === "CARDS" ? "bg-teal text-white shadow-2xs" : "text-muted hover:text-ink"
                }`}
            >
              <LayoutGrid size={13} aria-hidden="true" /> <span className="hidden md:inline">{t("cardsShort")}</span>
            </button>
          </div>

          {/* Mobile: 1 dropdown gộp chọn LOẠI lọc (Các buổi / Từ ngày - Đến ngày) rồi hiện đúng panel
              tương ứng bên trong, thay vì 2 nút riêng chiếm 2 hàng (theo yêu cầu người dùng,
              2026-07-31). Desktop (md+) vẫn 2 nút riêng như cũ — xem 2 khối "hidden md:block" dưới đây.
              "ml-auto" khóa nút này vào SÁT GÓC PHẢI của hàng — nếu để trôi nổi bên trái (vị trí tự
              nhiên sau khi "Bảng/Thẻ" chiếm chỗ) thì panel neo "right-0" theo nút sẽ tràn hẳn ra ngoài
              mép trái màn hình do nút cách mép phải màn hình quá xa (bug đã gặp, 2026-07-31). */}
          <div className="relative md:hidden ml-auto" ref={mobileFilterRef}>
            <button
              type="button"
              onClick={() => setMobileFilterOpen((v) => !v)}
              aria-label={t("mobileFilterAriaLabel")}
              aria-haspopup="dialog"
              aria-expanded={mobileFilterOpen}
              className="flex items-center gap-1.5 bg-white border border-line rounded-lg px-3 py-2 text-xs font-bold text-ink focus:outline-none focus:ring-2 focus:ring-teal/50 shadow-sm cursor-pointer"
            >
              {mobileFilterMode === "SESSION" ? (
                <Calendar size={14} className="text-teal shrink-0" aria-hidden="true" />
              ) : (
                <Clock size={14} className="text-teal shrink-0" aria-hidden="true" />
              )}
              {/* min-w-0 bắt buộc phải có — flex item mặc định có min-width:auto (co theo nội dung),
                  nếu thiếu thì "truncate" (ellipsis) không tác dụng, nút cứ giãn rộng theo text dài
                  thay vì cắt bớt (lỗi flexbox truncation kinh điển, đã gặp thực tế 2026-07-31). Giới
                  hạn hẹp hơn (100px) để nhãn khoảng ngày dài luôn cắt còn "01/07/2026 ..." thay vì hiện
                  vừa đủ cả "→ 15/08/2026" (theo đúng ví dụ người dùng mô tả). */}
              <span className="max-w-[100px] min-w-0 truncate">{mobileFilterLabel}</span>
              <ChevronDown size={14} className={`text-muted shrink-0 transition-transform ${mobileFilterOpen ? "rotate-180" : ""}`} aria-hidden="true" />
            </button>

            {mobileFilterOpen && (
              <div
                role="dialog"
                aria-label={t("filterDialogLabel")}
                className="absolute right-0 top-full mt-2 z-30 w-[min(280px,calc(100vw-2.5rem))] bg-white border border-line rounded-2xl shadow-lg p-3 space-y-3"
              >
                <div className="flex items-center p-1 bg-slate-100 rounded-xl">
                  <button
                    type="button"
                    onClick={() => setMobileFilterMode("SESSION")}
                    className={`flex-1 flex items-center justify-center gap-1.5 px-2 py-1.5 rounded-lg text-[11px] font-bold transition-all ${mobileFilterMode === "SESSION" ? "bg-white text-teal-deep shadow-2xs" : "text-muted"
                      }`}
                  >
                    <Calendar size={12} aria-hidden="true" /> {t("sessionsTab")}
                  </button>
                  <button
                    type="button"
                    onClick={() => setMobileFilterMode("RANGE")}
                    className={`flex-1 flex items-center justify-center gap-1.5 px-2 py-1.5 rounded-lg text-[11px] font-bold transition-all ${mobileFilterMode === "RANGE" ? "bg-white text-teal-deep shadow-2xs" : "text-muted"
                      }`}
                  >
                    <Clock size={12} aria-hidden="true" /> {t("rangeTab")}
                  </button>
                </div>

                {mobileFilterMode === "SESSION"
                  ? renderSessionPickerBody(() => setMobileFilterOpen(false))
                  : renderRangePickerBody(() => setMobileFilterOpen(false))}
              </div>
            )}
          </div>

          <div className="hidden md:block relative" ref={calendarRef}>
            <button
              type="button"
              onClick={toggleCalendar}
              aria-label={t("sessionFilterAriaLabel")}
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
                aria-label={t("sessionPickerDialogLabel")}
                className="absolute right-0 top-full mt-2 z-30 w-[300px] bg-white border border-line rounded-2xl shadow-lg p-3 space-y-3"
              >
                {renderSessionPickerBody(closeCalendar)}
              </div>
            )}
          </div>

          <div className="hidden md:block relative" ref={rangeRef}>
            <button
              type="button"
              onClick={toggleRange}
              aria-label={t("rangeFilterAriaLabel")}
              aria-haspopup="dialog"
              aria-expanded={rangeOpen}
              className="flex items-center gap-2 min-h-[44px] bg-white border border-line rounded-xl pl-3.5 pr-3 py-2.5 text-xs font-bold text-ink focus:outline-none focus:ring-2 focus:ring-teal/50 shadow-sm cursor-pointer"
            >
              <Clock size={14} className="text-teal shrink-0" aria-hidden="true" />
              <span className="whitespace-nowrap">
                <span className={dateFrom ? "text-ink" : "text-muted"}>{dateFrom ? formatDateVN(dateFrom) : t("from")}</span>
                <span className="text-muted mx-1">→</span>
                <span className={dateTo ? "text-ink" : "text-muted"}>{dateTo ? formatDateVN(dateTo) : t("to")}</span>
              </span>
              <ChevronDown size={14} className={`text-muted shrink-0 transition-transform ${rangeOpen ? "rotate-180" : ""}`} aria-hidden="true" />
            </button>

            {rangeOpen && (
              <div
                role="dialog"
                aria-label={t("rangePickerDialogLabel")}
                className="absolute right-0 top-full mt-2 z-30 w-[min(92vw,580px)] bg-white border border-line rounded-2xl shadow-lg p-4 space-y-3"
              >
                {renderRangePickerBody(() => setRangeOpen(false))}
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

      {/**
       * Summary KPI Cards — bố cục ngang cũ (icon trái, câu số liệu gộp bên phải) dễ vỡ dòng giữa chừng
       * khi chật (VD "Đạt Chuẩn (100%)" vỡ thành "Đạt Chuẩn\n(100%)" trên grid-cols-2 mobile). CHỈ đổi
       * bố cục cho MOBILE (thẻ thống kê: icon nhỏ+nhãn 1 hàng, SỐ LIỆU LỚN riêng dòng, chú thích dưới —
       * số liệu không còn bị cắt giữa chừng) — desktop (lg+) giữ nguyên bố cục ngang gốc, không đổi
       * (theo yêu cầu người dùng, 2026-07-31: mọi chỉnh UI mặc định chỉ áp dụng mobile).
       */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <div className="p-4 bg-white rounded-2xl border border-line shadow-sm border-l-4 border-l-teal lg:border-l lg:border-l-line lg:shadow-2xs">
          {/* Mobile — nhãn rút gọn 1 dòng để đồng đều chiều cao 4 thẻ (bản đủ chữ giữ nguyên ở desktop).
              Viền trái nhấn màu + shadow đậm hơn CHỈ trên mobile — card trắng viền xám mảnh + shadow-2xs
              gần như vô hình trên nền sáng của trang, nhìn phẳng/"không đẹp" (đã dùng skill UI-UX kiểm
              chứng, 2026-07-31). Desktop giữ nguyên border/shadow gốc. */}
          <div className="lg:hidden space-y-1.5">
            <div className="flex items-center gap-1.5">
              <div className="w-7 h-7 rounded-lg bg-teal/10 text-teal flex items-center justify-center shrink-0">
                <UserCheck size={14} aria-hidden="true" />
              </div>
              <p className="text-[10px] text-muted font-extrabold uppercase tracking-wider whitespace-nowrap">{t("kpi.attendance")}</p>
            </div>
            {attendanceRate != null ? (
              <div>
                <p className="text-xl font-black text-teal tabular-nums leading-tight">{attendanceRate}%</p>
                <p className="text-xs text-muted font-bold truncate">{attendanceRate >= 90 ? t("kpi.onTrack") : t("kpi.needsImprovement")}</p>
              </div>
            ) : (
              <p className="text-xs text-muted font-bold">{t("kpi.noData")}</p>
            )}
          </div>
          {/* Desktop */}
          <div className="hidden lg:flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-teal/10 text-teal flex items-center justify-center shrink-0">
              <UserCheck size={20} aria-hidden="true" />
            </div>
            <div>
              <p className="text-[10px] text-muted font-extrabold uppercase tracking-wider">{t("kpi.attendance")}</p>
              <p className="text-sm font-black text-teal tabular-nums">
                {attendanceRate != null ? `${attendanceRate >= 90 ? t("kpi.onTrackCaps") : t("kpi.needsImprovement")} (${attendanceRate}%)` : t("kpi.noData")}
              </p>
            </div>
          </div>
        </div>

        <div className="p-4 bg-white rounded-2xl border border-line shadow-sm border-l-4 border-l-emerald-500 lg:border-l lg:border-l-line lg:shadow-2xs">
          {/* Mobile — nhãn rút gọn 1 dòng để đồng đều chiều cao 4 thẻ (bản đủ chữ giữ nguyên ở desktop). */}
          <div className="lg:hidden space-y-1.5">
            <div className="flex items-center gap-1.5">
              <div className="w-7 h-7 rounded-lg bg-emerald-50 text-emerald-700 flex items-center justify-center shrink-0">
                <ShieldCheck size={14} aria-hidden="true" />
              </div>
              <p className="text-[11px] text-muted font-extrabold uppercase tracking-wider whitespace-nowrap">{t("kpi.attitudeShort")}</p>
            </div>
            {averageAttitudeLevel && averageAttitudePercent != null ? (
              <div>
                <p className="text-xl font-black text-ink tabular-nums leading-tight">{averageAttitudePercent}%</p>
                <p className="text-xs text-muted font-bold truncate">{t("kpi.gradeAchieved", { level: attitudeLabels[averageAttitudeLevel] })}</p>
              </div>
            ) : (
              <p className="text-xs text-muted font-bold">{t("kpi.noData")}</p>
            )}
          </div>
          {/* Desktop */}
          <div className="hidden lg:flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-700 flex items-center justify-center shrink-0">
              <ShieldCheck size={20} aria-hidden="true" />
            </div>
            <div>
              <p className="text-[11px] text-muted font-extrabold uppercase tracking-wider">{t("kpi.attitudeFull")}</p>
              <p className="text-sm font-black text-ink tabular-nums">
                {averageAttitudeLevel && averageAttitudePercent != null
                  ? t("kpi.gradeAchievedWithPercent", { level: attitudeLabels[averageAttitudeLevel], percent: averageAttitudePercent })
                  : t("kpi.noData")}
              </p>
            </div>
          </div>
        </div>

        <div className="p-4 bg-white rounded-2xl border border-line shadow-sm border-l-4 border-l-amber-500 lg:border-l lg:border-l-line lg:shadow-2xs">
          {/* Mobile — nhãn rút gọn 1 dòng để đồng đều chiều cao 4 thẻ (bản đủ chữ giữ nguyên ở desktop). */}
          <div className="lg:hidden space-y-1.5">
            <div className="flex items-center gap-1.5">
              <div className="w-7 h-7 rounded-lg bg-amber-50 text-amber-700 flex items-center justify-center shrink-0">
                <Award size={14} aria-hidden="true" />
              </div>
              <p className="text-[11px] text-muted font-extrabold uppercase tracking-wider whitespace-nowrap">{t("kpi.homeworkShort")}</p>
            </div>
            {avgHomeworkCompletion != null ? (
              <div>
                <p className="text-xl font-black text-amber-800 tabular-nums leading-tight">{avgHomeworkCompletion}%</p>
                <p className="text-xs text-muted font-bold truncate">{t("kpi.average")}</p>
              </div>
            ) : (
              <p className="text-xs text-muted font-bold">{t("kpi.noData")}</p>
            )}
          </div>
          {/* Desktop */}
          <div className="hidden lg:flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-amber-50 text-amber-700 flex items-center justify-center shrink-0">
              <Award size={20} aria-hidden="true" />
            </div>
            <div>
              <p className="text-[11px] text-muted font-extrabold uppercase tracking-wider">{t("kpi.homeworkFull")}</p>
              <p className="text-sm font-black text-amber-800 tabular-nums">{avgHomeworkCompletion != null ? `${avgHomeworkCompletion}%` : t("kpi.noData")}</p>
            </div>
          </div>
        </div>

        <div className="p-4 bg-white rounded-2xl border border-line shadow-sm border-l-4 border-l-purple-500 lg:border-l lg:border-l-line lg:shadow-2xs">
          {/* Mobile — nhãn rút gọn 1 dòng để đồng đều chiều cao 4 thẻ (bản đủ chữ giữ nguyên ở desktop). */}
          <div className="lg:hidden space-y-1.5">
            <div className="flex items-center gap-1.5">
              <div className="w-7 h-7 rounded-lg bg-purple-50 text-purple-700 flex items-center justify-center shrink-0">
                <Sparkles size={14} aria-hidden="true" />
              </div>
              <p className="text-[11px] text-muted font-extrabold uppercase tracking-wider whitespace-nowrap">{t("kpi.sessionsShort")}</p>
            </div>
            <div>
              <p className="text-xl font-black text-purple-800 tabular-nums leading-tight">{filteredLogs.length}</p>
              <p className="text-xs text-muted font-bold truncate">{t("kpi.recorded")}</p>
            </div>
          </div>
          {/* Desktop */}
          <div className="hidden lg:flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-purple-50 text-purple-700 flex items-center justify-center shrink-0">
              <Sparkles size={20} aria-hidden="true" />
            </div>
            <div>
              <p className="text-[10px] text-muted font-extrabold uppercase tracking-wider">{t("kpi.sessionsFull")}</p>
              <p className="text-sm font-black text-purple-800">{t("kpi.sessionsRecorded", { count: filteredLogs.length })}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Main content: Bảng tổng quan (mặc định) hoặc Dạng thẻ — người dùng tự chọn ở toggle trên, kể
          cả trên mobile (bảng có overflow-x-auto để cuộn ngang khi cần). */}
      {viewMode === "TABLE" ? (
        <div className="bg-white border border-line rounded-2xl shadow-2xs overflow-hidden">
          <div className="p-4 border-b border-line bg-slate-50/80 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
            <div className="flex items-center gap-2">
              <TableIcon size={16} className="text-teal" aria-hidden="true" />
              <h3 className="text-sm md:text-xs font-black text-ink uppercase tracking-wider font-display">{t("table.heading")}</h3>
            </div>
            <span className="text-sm md:text-xs font-bold text-muted">
              {t("table.showingPrefix")} <span className="text-teal font-black">{filteredLogs.length}</span> {t("table.showingSuffix")}
            </span>
          </div>

          {filteredLogs.length === 0 ? (
            <p className="text-xs text-muted font-bold italic text-center py-10">{t("noApprovedComments")}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse min-w-[1200px]">
                <thead>
                  {/* Cỡ chữ bảng tăng trên mobile (đọc rõ hơn theo phản hồi người dùng, 2026-07-31) —
                        desktop (md+) giữ nguyên cỡ gốc qua md:text-*. */}
                  {/* Đồng bộ đúng cấu trúc cột với form Giáo viên/màn Quản lý điểm trường (bổ sung
                      ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) — "BTVN buổi trước"/"buổi
                      sau" đều tách 3 kênh Offline/Bài/Video, thêm cột Hạn nộp bài. Nhãn kênh giữ chung
                      chung "Bài" (không đổi Ngữ pháp/Bài nghe theo Loại giáo viên như bên Admin) vì 1
                      bảng ở đây gộp nhiều buổi/nhiều ngày có thể khác Loại giáo viên nhau, 1 tiêu đề cột
                      cố định không phản ánh đúng hết từng dòng. */}
                  <tr className="bg-slate-100 border-b border-slate-300 [&>th]:text-center text-xs md:text-[11px] font-black uppercase text-slate-700 tracking-wider">
                    <th rowSpan={2} className="p-3 border-r border-slate-300 w-28 whitespace-nowrap">{t("table.date")}</th>
                    <th rowSpan={2} className="p-3 border-r border-slate-300 min-w-[200px]">{t("table.todayLesson")}</th>
                    <th rowSpan={2} className="p-3 border-r border-slate-300 w-28 whitespace-nowrap text-center">{t("table.teacher")}</th>
                    <th colSpan={3} className="p-3 border-r border-slate-300 text-center">{t("table.homeworkPrev")}</th>
                    <th rowSpan={2} className="p-3 border-r border-slate-300 w-32 whitespace-nowrap">{t("table.homeworkOffline")}</th>
                    <th colSpan={2} className="p-3 border-r border-slate-300 text-center">{t("table.homeworkOnline")}</th>
                    <th rowSpan={2} className="p-3 border-r border-slate-300 w-32 whitespace-nowrap">{t("table.dueDate")}</th>
                    <th rowSpan={2} className="p-3 border-r border-slate-300 w-28 whitespace-nowrap text-center">{t("table.attitude")}</th>
                    <th rowSpan={2} className="p-3 border-r border-slate-300 min-w-[220px]">{t("table.studentComment")}</th>
                    <th rowSpan={2} className="p-3 min-w-[140px]">{t("table.note")}</th>
                  </tr>
                  <tr className="bg-slate-100 border-b border-slate-300 [&>th]:text-center text-xs md:text-[11px] font-black uppercase text-slate-700 tracking-wider">
                    <th className="p-3 border-r border-slate-300 w-28 whitespace-nowrap text-center">{t("table.offline")}</th>
                    <th className="p-3 border-r border-slate-300 w-32 whitespace-nowrap text-center">{t("table.grammarListening")}</th>
                    <th className="p-3 border-r border-slate-300 w-32 whitespace-nowrap text-center">{t("table.videoTkn")}</th>
                    <th className="p-3 border-r border-slate-300 min-w-[160px] text-center">{t("table.grammarListening")}</th>
                    <th className="p-3 border-r border-slate-300 min-w-[160px] text-center">{t("table.videoTkn")}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-300 text-sm md:text-xs font-medium text-ink">
                  {filteredLogs.map((log) => (
                    <tr key={log.id} className="hover:bg-slate-50/80 transition-colors">
                      <td className="p-3 border-r border-slate-300 font-mono font-bold text-slate-700 whitespace-nowrap align-top">{log.commentDate}</td>
                      <td className="p-3 border-r border-slate-300 align-top">
                        <div className="font-bold text-teal-deep">
                          {log.sessionNumber != null ? t("sessionNumber", { number: log.sessionNumber }) : log.sessionTypeLabel ?? t("sessionFallback")}
                          {log.lessonContent && <span className="font-bold text-teal-deep">: {log.lessonContent}</span>}
                        </div>
                        <div className="text-xs md:text-[10px] text-muted font-mono flex items-center gap-1 mt-1">
                          <Clock size={10} aria-hidden="true" /> {log.timeSlot ?? "—"}
                          {log.roomName && (
                            <>
                              <span className="text-line">·</span>
                              <MapPin size={10} aria-hidden="true" /> {log.roomName}
                            </>
                          )}
                        </div>
                      </td>
                      <td className="p-3 border-r border-slate-300 text-center whitespace-nowrap align-top">
                        {log.teacherType ? (
                          <span className={`px-2 py-0.5 rounded text-xs md:text-[10px] border font-bold ${teacherTypeStyles[log.teacherType]}`}>
                            {teacherTypeLabels[log.teacherType]}
                          </span>
                        ) : (
                          "—"
                        )}
                      </td>
                      <td className="p-3 border-r border-slate-300 align-top">{log.homeworkPreviousOfflineText == null ? "—" : log.homeworkPreviousOfflineText + "%"}</td>
                      <td className="p-3 border-r border-slate-300 font-bold text-slate-800 align-top">{(log.homeworkPreviousScore || log.grammarPreviousProgress) ?? "—"}</td>
                      <td className="p-3 border-r border-slate-300 font-bold text-purple-900 align-top">{(log.homeworkPreviousSpeakingScore || log.videoPreviousProgress) ?? "—"}</td>
                      <td className="p-3 border-r border-slate-300 align-top">{log.homeworkNextOfflineText == null ? "—" : log.homeworkNextOfflineText + "%"}</td>
                      <td className="p-3 border-r border-slate-300 text-slate-800 font-semibold align-top">
                        {renderGrammarLabel(log, "font-semibold")}
                      </td>
                      <td className="p-3 border-r border-slate-300 text-slate-800 font-semibold align-top">
                        {renderVideoLabel(log, "font-semibold")}
                      </td>
                      <td className="p-3 border-r border-slate-300 whitespace-nowrap align-top">
                        {log.homeworkNextDueAt ? formatDateTimeHm(log.homeworkNextDueAt, i18n.language) : "—"}
                      </td>
                      <td className="p-3 border-r border-slate-300 text-center whitespace-nowrap align-top">
                        <span className={`px-2 py-0.5 rounded text-xs md:text-[10px] border ${log.attitude ? attitudeStyles[log.attitude] : "bg-slate-100 text-slate-700 border-slate-200"}`}>
                          {log.attitude ? attitudeLabels[log.attitude] : "—"}
                        </span>
                      </td>
                      <td className="p-3 border-r border-slate-300 text-slate-700 italic align-top">"{log.content}"</td>
                      <td className="p-3 text-slate-600 text-xs md:text-[11px] align-top">{log.note || "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : (
        cardsView
      )}
    </div>
  );
}
