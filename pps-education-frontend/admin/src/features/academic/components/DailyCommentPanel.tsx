import React, { useEffect, useRef, useState } from "react";
import { ChevronDown, ChevronUp, Download, Save, Send, UploadCloud } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import { useApp } from "@/context/AppContext";
import {
  ClassEnrollmentResponse,
  ClassSessionResponse,
  StudentCommentResponse,
  downloadDailyCommentTemplate,
  previewImportDailyComments,
  DailyCommentImportPreviewResponse,
  listClassEnrollments,
  listClassSessions,
  listCommentsForClass,
  listTodaySessions,
  submitComments,
  updateComment,
  updateActualTeacherName,
  updateLessonContent,
  updateSessionTeacherType,
  writeComment
} from "../api";
import {
  ExerciseAssignmentResponse,
  ExerciseResponse,
  ReviewVideoAssignmentResponse,
  ReviewVideoSetResponse,
  listAssignmentsForClass,
  listPublishedExercisesForClass,
  listReviewVideoAssignmentsForClass,
  listReviewVideoSetsByClass
} from "@/features/lms/api";
import { useEligibleClasses } from "../hooks/useEligibleClasses";
import NotificationBanner from "@/features/student/components/NotificationBanner";
import AttendanceReminderBanner from "@/features/hrm/components/AttendanceReminderBanner";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import CommentHistoryList from "./CommentHistoryList";
import StudentNameLink from "@/features/reports/components/StudentNameLink";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";

const statusLabels: Record<StudentCommentResponse["status"], string> = { DRAFT: "Nháp", PENDING: "Chờ duyệt", APPROVED: "Đã duyệt", REJECTED: "Bị từ chối" };
const readOnlyFieldClass = "w-full bg-emerald-50/60 border border-emerald-200 text-xs p-2 rounded-lg text-slate-700 min-h-[34px]";
/**
 * Cố định 3 cột đầu (Mã học viên/Họ và tên/Ngày sinh, bổ sung ngoài SDD gốc, 2026-08-14) — cần width
 * CỐ ĐỊNH để tính đúng left offset cộng dồn cho position:sticky. Phải ép CẢ width/minWidth/maxWidth
 * bằng nhau (không chỉ width) — table-layout auto (mặc định) vẫn co giãn cột theo nội dung nếu chỉ đặt
 * width; table-layout:fixed + <colgroup> đã thử nhưng position:sticky trên ô có rowSpan không tôn
 * trọng width khai báo ở đó (đo thực tế bằng DevTools 2026-08-14, giới hạn/bug trình duyệt) — ép cứng
 * min/max trực tiếp trên từng ô là cách duy nhất buộc trình duyệt giữ đúng width.
 */
const STICKY_COL_WIDTHS = [110, 170, 110];
const STICKY_COL_LEFT = [0, STICKY_COL_WIDTHS[0], STICKY_COL_WIDTHS[0] + STICKY_COL_WIDTHS[1]];
const STICKY_COL_STYLE: React.CSSProperties[] = STICKY_COL_WIDTHS.map((w, i) => ({
  width: w,
  minWidth: w,
  maxWidth: w,
  left: STICKY_COL_LEFT[i]
}));

/**
 * "Loại giáo viên" của buổi học (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05) — ăn
 * theo để lọc + đổi nhãn 2 kênh BTVN buổi sau (Ngữ pháp/Bài nghe ở Soạn & giao đề, Video ở Kho Video
 * Ôn tập). Mirror ClassSession.TeacherType/Exam.TeacherType/ReviewVideoSet.VideoType (BE).
 */
type TeacherType = "VIETNAMESE" | "FOREIGN";
const teacherTypeLabels: Record<TeacherType, string> = { VIETNAMESE: "Giáo viên Việt Nam", FOREIGN: "Giáo viên nước ngoài" };
/** Đúng y nhãn cột đã chốt với người dùng 2026-08-05 — KHÔNG dùng lại "Video từ kết nối"/"Video phản xạ" của Kho Video Ôn tập (LecturesPage.tsx), 2 bộ nhãn độc lập. */
const grammarChannelLabel: Record<TeacherType, string> = { VIETNAMESE: "Ngữ pháp", FOREIGN: "Bài nghe" };
const videoChannelLabel: Record<TeacherType, string> = { VIETNAMESE: "Từ Vựng (TKN)", FOREIGN: "Clip phản xạ" };

interface Row {
  studentId: number;
  studentFullName: string;
  studentCode: string;
  studentDateOfBirth: string | null;
  attitude: "" | NonNullable<StudentCommentResponse["attitude"]>;
  homeworkPreviousScore: string;
  homeworkPreviousSpeakingScore: string;
  content: string;
  /** Chữ tự do (BTVN offline) — loại trừ lẫn nhau với homeworkNextExerciseId (chọn cái này thì cái kia rỗng). */
  homeworkNext: string;
  /** V65: id của Exercise NGUỒN đã Publish (không phải id bản giao như trước V65) — chọn từ grammarOptions đã lọc theo teacherType. */
  homeworkNextExerciseId: number | "";
  /** id của ReviewVideoSet NGUỒN đã Publish — không đổi tên qua V65 (request field vẫn nhận thẳng set id). */
  homeworkNextReviewVideoSetId: number | "";
  note: string;
}

const EMPTY_ROW_HOMEWORK: Pick<Row, "homeworkNext" | "homeworkNextExerciseId" | "homeworkNextReviewVideoSetId"> = {
  homeworkNext: "",
  homeworkNextExerciseId: "",
  homeworkNextReviewVideoSetId: ""
};

/** Dòng chưa có dữ liệu gì (kể cả từ Excel import) — an toàn để tự điền lại từ nhận xét DRAFT/REJECTED đã có mà không đè lên nội dung giáo viên đang gõ dở. */
const isRowBlank = (r: Row) =>
  !r.content.trim() &&
  !r.attitude &&
  !r.homeworkPreviousScore.trim() &&
  !r.homeworkPreviousSpeakingScore.trim() &&
  !r.homeworkNext.trim() &&
  r.homeworkNextExerciseId === "" &&
  r.homeworkNextReviewVideoSetId === "" &&
  !r.note.trim();

/** Thang thái độ chốt lại 2026-08-12 — % đi kèm là quy đổi dùng tính "Thái độ học tập" trung bình ở Portal (StudentComment.Attitude). */
const attitudeLabels: Record<NonNullable<StudentCommentResponse["attitude"]>, string> = {
  WEAK: "Yếu (20%)",
  AVERAGE: "Trung bình (50%)",
  FAIR: "Khá (70%)",
  GOOD: "Tốt (90%)",
  EXCELLENT: "Xuất sắc (100%)"
};

/**
 * Ô hiện "BTVN buổi trước" cho dòng ĐÃ GỬI (locked) — ưu tiên % TỰ ĐỘNG (grammarPreviousProgress/
 * videoPreviousProgress, backend tính từ exercise_attempts/review_video_progress|submissions thật —
 * xem HomeworkProgressService), chỉ fallback về giá trị nhập tay khi tự động = null (VD BTVN Ngữ pháp
 * giao Offline thì không có gì để tự tính, video luôn Online nên hầu như luôn có % tự động).
 */
function PreviousProgressCell({ auto, manual }: { auto: string | null; manual: string | null }) {
  if (auto) {
    return (
      <div className={`${readOnlyFieldClass} flex items-center justify-between gap-1.5`}>
        <span>{auto}</span>
        <span className="text-[9px] font-bold text-emerald-600 uppercase tracking-wide shrink-0">Tự động</span>
      </div>
    );
  }
  return <div className={readOnlyFieldClass}>{manual || "—"}</div>;
}

/** UC-21 Main Flow (nhánh DAILY): viết nhận xét hàng ngày theo buổi học — cùng khuôn thao tác với Điểm danh nhanh. */
export default function DailyCommentPanel() {
  const { selectedClassId } = useApp();
  const { classes } = useEligibleClasses();
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null);
  const [rows, setRows] = useState<Row[]>([]);
  const [loadingRows, setLoadingRows] = useState(false);
  const [history, setHistory] = useState<StudentCommentResponse[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [sending, setSending] = useState(false);
  const [notification, setNotification] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<DailyCommentImportPreviewResponse | null>(null);
  // "Lưu nháp"/autosave (2026-08-14, bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — phòng giáo
  // viên vô tình thoát khi chưa "Gửi nhận xét". dirty=true bất cứ lúc nào rows đổi do người dùng gõ
  // (updateRow/Áp dụng cho cả lớp) hoặc do nhập Excel fill vào bảng — KHÔNG bật lại khi loadHistory tự
  // patch rows từ dữ liệu server (tránh autosave lặp vô ích ngay sau khi vừa lưu/gửi xong).
  const [dirty, setDirty] = useState(false);
  const [savingDraft, setSavingDraft] = useState(false);
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);
  // "Lịch sử nhận xét buổi này" có thể ẩn/hiện (2026-08-14, bổ sung ngoài SDD gốc) — mặc định hiện
  // (giữ hành vi cũ), giáo viên tự ẩn bớt khi bảng nhận xét chính đã đủ dài, đỡ phải cuộn qua khối lặp
  // lại gần như y hệt dữ liệu ở bảng trên.
  const [showHistory, setShowHistory] = useState(true);
  /** V65: nguồn khả dụng cho dropdown "BTVN Ngữ pháp buổi sau" — Exercise đã Publish (không phải bản giao). */
  const [grammarOptions, setGrammarOptions] = useState<ExerciseResponse[]>([]);
  const [videoOptions, setVideoOptions] = useState<ReviewVideoSetResponse[]>([]);
  /**
   * V65: bản giao ACTIVE hiện có của lớp — CHỈ dùng để tra ngược "comment đã lưu trước đó chọn đề/
   * video nguồn nào" (response StudentCommentResponse chỉ trả id bản giao, không trả thẳng id nguồn),
   * KHÔNG dùng làm nguồn dropdown (đã đổi sang grammarOptions/videoOptions ở trên).
   */
  const [grammarAssignments, setGrammarAssignments] = useState<ExerciseAssignmentResponse[]>([]);
  const [videoAssignments, setVideoAssignments] = useState<ReviewVideoAssignmentResponse[]>([]);
  // Trạng thái "đã nhận xét chưa" của TỪNG buổi trong lớp (không riêng buổi đang chọn) — phục vụ đẩy
  // buổi chưa nhận xét lên đầu dropdown + hiện dấu ✓/◐ (2026-07-30). Tính số học sinh ACTIVE đã có ít
  // nhất 1 comment DAILY (bất kể DRAFT/PENDING/APPROVED/REJECTED — chỉ cần "đã động tới") / tổng học
  // sinh ACTIVE của lớp.
  const [sessionCommentStats, setSessionCommentStats] = useState<Record<number, { commented: number; total: number }>>({});
  const fileInputRef = useRef<HTMLInputElement>(null);
  // "Bài học hôm nay" (2026-07-29, chuyển từ Điểm danh sang đây) — bắt buộc điền trước khi Gửi nhận
  // xét DAILY (backend chặn 422 nếu trống), nên đặt ngay đầu màn hình để giáo viên điền trước tiên.
  const [lessonContentInput, setLessonContentInput] = useState("");
  const [savingLessonContent, setSavingLessonContent] = useState(false);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-03 — trước đây bấm "Gửi nhận xét" khi
  // quên điền Bài học hôm nay vẫn cứ gửi lên backend, bị từ chối rồi báo lỗi qua banner đen tự ẩn sau
  // vài giây (dễ bỏ lỡ) + xóa sạch nội dung đang gõ dở trên các dòng học sinh (loadHistory tải lại).
  // Giờ chặn NGAY ở FE trước khi gọi API nào — không đụng `rows`, không tải lại gì cả, chỉ hiện viền đỏ
  // + cảnh báo ngay tại ô nhập, giáo viên điền xong bấm Gửi lại là xong, không mất nội dung đã gõ.
  const [lessonContentMissingError, setLessonContentMissingError] = useState(false);
  const lessonContentInputRef = useRef<HTMLInputElement>(null);
  // "Tên giáo viên giảng dạy" (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) — nhập
  // tay (KHÁC primaryTeacherName là FK hệ thống), dùng khi GV nước ngoài không tự thao tác hệ thống,
  // nhân sự chăm sóc lớp nhập hộ. Mirror y hệt "Bài học hôm nay" — không bắt buộc, không chặn Gửi.
  const [actualTeacherNameInput, setActualTeacherNameInput] = useState("");
  const [savingActualTeacherName, setSavingActualTeacherName] = useState(false);
  // "Loại giáo viên" (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05) — ăn theo để lọc/
  // đổi nhãn 2 kênh BTVN buổi sau, mặc định lấy theo session.teacherType khi buổi đã có sẵn (lịch dạy).
  const [teacherType, setTeacherType] = useState<TeacherType | "">("");
  const [savingTeacherType, setSavingTeacherType] = useState(false);
  // Hạn nộp BTVN buổi sau (Giáo viên tự chọn, 2026-08-05; chọn thêm GIỜ 2026-08-06) — 1 giá trị DUY
  // NHẤT cho cả buổi (đúng ràng buộc BE: mọi nhận xét DAILY cùng buổi phải khớp cùng hạn nộp), không
  // phải theo từng dòng học sinh. Để trống thì BE tự tính = ngày buổi kế tiếp (hành vi cũ).
  const [dueDate, setDueDate] = useState("");
  const [dueTime, setDueTime] = useState("");
  /** yyyy-MM-ddTHH:mm gửi lên BE — chỉ có giá trị khi đã chọn cả ngày lẫn giờ. */
  const dueDateTime = dueDate && dueTime ? `${dueDate}T${dueTime}` : "";
  // "Gán nhanh cho cả lớp" (2026-08-05) — điền 1 lần, áp dụng cho mọi dòng chưa khoá thay vì phải chọn
  // từng dòng học sinh; offline/exerciseId loại trừ lẫn nhau giống ô nhập từng dòng.
  const [quickOffline, setQuickOffline] = useState("");
  const [quickExerciseId, setQuickExerciseId] = useState<number | "">("");
  const [quickVideoId, setQuickVideoId] = useState<number | "">("");

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;
  const selectedSession = sessions.find((s) => s.id === selectedSessionId) ?? null;
  // Mirror đúng điều kiện findUpcomingSessions() bên BE (ClassSessionRepository) — buổi có sessionDate
  // SAU buổi đang chọn, loại CANCELLED/RESCHEDULED — để báo TRƯỚC cho Giáo viên biết lớp chưa có buổi
  // kế tiếp trong lịch (thay vì để họ chọn Online/Video xong bấm Gửi mới gặp lỗi 422, đã xác nhận với
  // người dùng 2026-07-31: đây là buổi học CUỐI CÙNG đã lên lịch của lớp). Sửa lại 2026-08-14: khi buổi
  // đang chọn CÓ xác định teacherType, chỉ tính buổi kế tiếp CÙNG loại GV (BE cũng đã sửa tương tự —
  // xem ClassSessionRepository#findUpcomingSessions) — tránh báo "còn buổi kế tiếp" (buổi khác loại GV
  // xen giữa) trong khi BE thật ra chặn 422 vì không có buổi kế tiếp CÙNG loại.
  const hasUpcomingSession =
    !!selectedSession &&
    sessions.some(
      (s) =>
        s.sessionDate > selectedSession.sessionDate &&
        s.status !== "CANCELLED" &&
        s.status !== "RESCHEDULED" &&
        (!selectedSession.teacherType || s.teacherType === selectedSession.teacherType)
    );
  const isLastScheduledSession = !!selectedSession && !hasUpcomingSession;
  // Lớp chưa có buổi kế tiếp CHỈ còn chặn khi chưa tự chọn hạn nộp (dueDate) — có hạn nộp tùy chỉnh thì
  // BE bỏ qua điều kiện "phải có buổi kế tiếp" (2026-08-05, xem StudentCommentService#resolveDueAt).
  const blockOnlineHomework = isLastScheduledSession && !dueDateTime;
  const filteredGrammarOptions = teacherType ? grammarOptions.filter((ex) => ex.examTeacherType === teacherType) : [];
  const filteredVideoOptions = teacherType ? videoOptions.filter((s) => s.teacherType === teacherType) : [];
  const grammarLabel = teacherType ? grammarChannelLabel[teacherType] : "Bài";
  const videoLabel = teacherType ? videoChannelLabel[teacherType] : "Video";
  // Buổi đã có ít nhất 1 nhận xét ĐANG chờ duyệt/ĐÃ duyệt (bổ sung ngoài SDD gốc, đã xác nhận với
  // người dùng 2026-08-06) — khoá 3 thông tin dùng CHUNG cả buổi (Loại giáo viên/Bài học hôm nay/Tên
  // giáo viên giảng dạy) để không đổi ngược sau khi đã gửi, gây lệch với nội dung đã duyệt. Chỉ mở lại
  // khi Quản lý điểm trường TỪ CHỐI (REJECTED) toàn bộ nhận xét của buổi — quay về DRAFT mới sửa được.
  const sessionHasSentComment = history.some((h) => h.status === "PENDING" || h.status === "APPROVED");

  /** "NONE" (chưa ai được nhận xét) / "PARTIAL" (dở dang) / "DONE" (đủ toàn bộ học sinh ACTIVE). */
  const getSessionCommentStatus = (sessionId: number): "NONE" | "PARTIAL" | "DONE" => {
    const stat = sessionCommentStats[sessionId];
    if (!stat || stat.commented === 0) return "NONE";
    return stat.commented >= stat.total ? "DONE" : "PARTIAL";
  };

  /**
   * UC-21 áp cùng nguyên tắc "đến giờ học mới nhận xét" như UC-15 (Điểm danh) — chặn chọn buổi tương lai.
   * Đẩy buổi CHƯA nhận xét đủ (NONE/PARTIAL) lên trước buổi đã DONE — sort ổn định (giữ nguyên thứ tự
   * theo ngày trong từng nhóm), đỡ Giáo viên phải dò cả danh sách dài mới thấy buổi còn thiếu (2026-07-30).
   */
  const selectableSessions = sessions
    .filter((s) => new Date(`${s.sessionDate}T${s.startTime}`) <= new Date())
    .map((s, index) => ({ s, index, rank: getSessionCommentStatus(s.id) === "DONE" ? 1 : 0 }))
    .sort((a, b) => a.rank - b.rank || a.index - b.index)
    .map((x) => x.s);

  // Tính "buổi nào đã nhận xét" cho CẢ LỚP (không chỉ buổi đang chọn) — dùng listCommentsForClass(classId)
  // (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — thay N request/học sinh bằng 1
  // request duy nhất, lọc DAILY rồi gom theo classSessionId. Gọi lại sau khi gửi/nhập Excel để dropdown
  // cập nhật ngay dấu ✓/◐ của buổi vừa nhận xét, không phải đổi lớp mới thấy (2026-07-30).
  const refreshSessionCommentStats = (classId: number) => {
    listClassEnrollments(classId)
      .then((enrollments) => {
        const activeIds = new Set(enrollments.filter((en) => en.status === "ACTIVE").map((en) => en.studentId));
        if (activeIds.size === 0) return;
        return listCommentsForClass(classId).then((all) => {
          const commentedBySession: Record<number, Set<number>> = {};
          all
            .filter((c) => c.commentType === "DAILY" && c.classSessionId != null && activeIds.has(c.studentId))
            .forEach((c) => {
              const sessionId = c.classSessionId as number;
              (commentedBySession[sessionId] ??= new Set()).add(c.studentId);
            });
          const stats: Record<number, { commented: number; total: number }> = {};
          Object.entries(commentedBySession).forEach(([sessionId, studentSet]) => {
            stats[Number(sessionId)] = { commented: studentSet.size, total: activeIds.size };
          });
          setSessionCommentStats(stats);
        });
      })
      .catch(() => undefined);
  };

  useEffect(() => {
    setSelectedSessionId(null);
    setRows([]);
    setGrammarOptions([]);
    setVideoOptions([]);
    setGrammarAssignments([]);
    setVideoAssignments([]);
    setSessionCommentStats({});
    if (!selectedClassId) {
      setSessions([]);
      return;
    }
    listClassSessions(selectedClassId)
      .then(setSessions)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách buổi học."));
    refreshSessionCommentStats(selectedClassId);
    // UC-48 (2026-07-29): tự chọn buổi hôm nay khi vào tab, đỡ GV phải tự tìm trong dropdown — chỉ tự
    // chọn nếu buổi đã tới giờ bắt đầu (đúng nguyên tắc "đến giờ học mới nhận xét" ở dưới), buổi hôm
    // nay chưa bắt đầu hoặc không có buổi nào thì báo rõ, để GV tự chọn buổi khác nếu cần.
    listTodaySessions(selectedClassId)
      .then((todaySessions) => {
        const started = todaySessions.find((s) => new Date(`${s.sessionDate}T${s.startTime}`) <= new Date());
        if (started) {
          setSelectedSessionId(started.id);
        } else if (todaySessions.length > 0) {
          setNotification(`📅 Buổi học hôm nay (${todaySessions[0].startTime}–${todaySessions[0].endTime}) chưa bắt đầu — chưa thể nhận xét, có thể tự chọn buổi khác ở trên.`);
        } else {
          setNotification("📅 Hôm nay không có buổi học nào của lớp này — vui lòng tự chọn buổi ở trên.");
        }
      })
      .catch(() => undefined);
    // V65: BTVN Ngữ pháp ONLINE chọn từ Exercise đã Publish đúng khung chương trình của lớp (nguồn,
    // KHÔNG phải bản giao sẵn có như trước V65); BTVN Video Ôn tập chọn từ bộ đã CÔNG BỐ (PUBLISHED)
    // — khớp đúng điều kiện buildTemplate ở BE.
    listPublishedExercisesForClass(selectedClassId).then(setGrammarOptions).catch(() => undefined);
    listReviewVideoSetsByClass(selectedClassId)
      .then((sets) => setVideoOptions(sets.filter((s) => s.status === "PUBLISHED")))
      .catch(() => undefined);
    // Bản giao ACTIVE hiện có — chỉ để tra ngược lựa chọn đã lưu trước đó ra id nguồn khi prefill (xem loadHistory).
    listAssignmentsForClass(selectedClassId).then(setGrammarAssignments).catch(() => undefined);
    listReviewVideoAssignmentsForClass(selectedClassId).then(setVideoAssignments).catch(() => undefined);
  }, [selectedClassId]);

  useEffect(() => {
    setLessonContentInput(selectedSession?.lessonContent ?? "");
    setLessonContentMissingError(false);
    setActualTeacherNameInput(selectedSession?.actualTeacherName ?? "");
    setTeacherType((selectedSession?.teacherType as TeacherType | null) ?? "");
    setDueDate("");
    setDueTime("");
    setQuickOffline("");
    setQuickExerciseId("");
    setQuickVideoId("");
    setDirty(false);
    setLastSavedAt(null);
  }, [selectedSession?.id, selectedSession?.lessonContent, selectedSession?.actualTeacherName, selectedSession?.teacherType]);

  useEffect(() => {
    if (!selectedClassId || !selectedSessionId) {
      setRows([]);
      setHistory([]);
      return;
    }
    setLoadingRows(true);
    setError(null);
    listClassEnrollments(selectedClassId)
      .then((enrollments: ClassEnrollmentResponse[]) => {
        // Sắp theo mã học viên — BE chưa có ORDER BY cố định ở đây (findBySchoolClassId), nên tự
        // sắp ở FE để bảng không đổi thứ tự lung tung mỗi lần tải lại (đã báo BE bổ sung ORDER BY
        // để file mẫu Excel tải về cũng khớp đúng thứ tự này).
        const active = enrollments
          .filter((en) => en.status === "ACTIVE")
          .sort((a, b) => a.studentCode.localeCompare(b.studentCode));
        setRows(
          active.map((en) => ({
            studentId: en.studentId,
            studentFullName: en.studentFullName,
            studentCode: en.studentCode,
            studentDateOfBirth: en.studentDateOfBirth,
            attitude: "",
            homeworkPreviousScore: "",
            homeworkPreviousSpeakingScore: "",
            content: "",
            ...EMPTY_ROW_HOMEWORK,
            note: ""
          }))
        );
        return loadHistory(selectedClassId, selectedSessionId, active.map((en) => en.studentId));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách học sinh."))
      .finally(() => setLoadingRows(false));
  }, [selectedClassId, selectedSessionId]);

  const loadHistory = async (classId: number, sessionId: number, studentIds: number[]) => {
    setLoadingHistory(true);
    try {
      // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — 1 request duy nhất cho cả lớp
      // thay N request/học sinh (xem listCommentsForClass), lọc lại theo studentIds (chỉ học sinh ACTIVE
      // truyền vào) để giữ đúng phạm vi như trước.
      const studentIdSet = new Set(studentIds);
      const all = await listCommentsForClass(classId);
      const filtered = all
        .filter((c) => c.commentType === "DAILY" && c.classSessionId === sessionId && studentIdSet.has(c.studentId))
        .sort((a, b) => (a.studentFullName > b.studentFullName ? 1 : -1));
      setHistory(filtered);
      // Prefill hạn nộp (ngày + giờ) từ 1 nhận xét DRAFT/REJECTED đã có sẵn (VD nhập từ Excel, hoặc mở
      // lại buổi đang soạn dở) — chỉ khi Giáo viên chưa tự gõ gì ở panel "Gán nhanh cho cả lớp" (2026-08-05).
      const draftWithDueDate = filtered.find((h) => (h.status === "DRAFT" || h.status === "REJECTED") && h.homeworkNextDueAt);
      if (draftWithDueDate?.homeworkNextDueAt) {
        setDueDate((prev) => prev || draftWithDueDate.homeworkNextDueAt!.slice(0, 10));
        setDueTime((prev) => prev || draftWithDueDate.homeworkNextDueAt!.slice(11, 16));
      }
      // Nhận xét DRAFT/REJECTED (nhập tay chưa gửi hoặc nhập từ Excel) — điền vào ô nhập trên màn hình để
      // giáo viên xem/sửa tiếp trước khi bấm "Gửi nhận xét", KHÔNG khoá read-only như PENDING/APPROVED.
      // Chỉ điền vào dòng còn trống để không đè lên nội dung đang gõ dở (đã xác nhận với người dùng 2026-07-29).
      setRows((prev) =>
        prev.map((r) => {
          const draft = filtered.find((h) => h.studentId === r.studentId && (h.status === "DRAFT" || h.status === "REJECTED"));
          if (!draft || !isRowBlank(r)) return r;
          // V65: response chỉ trả id BẢN GIAO (homeworkNextExerciseAssignmentId/homeworkNextReviewVideoAssignmentId)
          // — tra ngược qua grammarAssignments/videoAssignments (đã tải sẵn theo lớp) để lấy đúng id NGUỒN
          // (Exercise/ReviewVideoSet) cần hiện chọn sẵn trong dropdown. Không tìm thấy (VD bản giao đã bị huỷ,
          // hoặc chưa tải xong danh sách) thì để trống — Giáo viên tự chọn lại.
          const exerciseId =
            draft.homeworkNextExerciseAssignmentId != null
              ? grammarAssignments.find((a) => a.id === draft.homeworkNextExerciseAssignmentId)?.exerciseId ?? ""
              : "";
          const videoSetId =
            draft.homeworkNextReviewVideoAssignmentId != null
              ? videoAssignments.find((a) => a.id === draft.homeworkNextReviewVideoAssignmentId)?.reviewVideoSetId ?? ""
              : "";
          return {
            ...r,
            attitude: draft.attitude ?? "",
            // Ưu tiên % TỰ ĐỘNG (grammarPreviousProgress/videoPreviousProgress, BE đã tính sẵn) khi mở
            // lại 1 nhận xét DRAFT/REJECTED — chỉ fallback về giá trị nhập tay cũ khi tự động = null
            // (VD giao Offline). Vẫn là input thường, GV có thể sửa đè trước khi Gửi.
            homeworkPreviousScore: draft.grammarPreviousProgress ?? draft.homeworkPreviousScore ?? "",
            homeworkPreviousSpeakingScore: draft.videoPreviousProgress ?? draft.homeworkPreviousSpeakingScore ?? "",
            content: draft.content ?? "",
            homeworkNext: draft.homeworkNext ?? "",
            homeworkNextExerciseId: exerciseId,
            homeworkNextReviewVideoSetId: videoSetId,
            note: draft.note ?? ""
          };
        })
      );
    } finally {
      setLoadingHistory(false);
    }
  };

  const handleSaveLessonContent = async () => {
    if (!selectedSessionId || !lessonContentInput.trim()) return;
    setSavingLessonContent(true);
    setError(null);
    try {
      const updated = await updateLessonContent(selectedSessionId, lessonContentInput.trim());
      setSessions((prev) => prev.map((s) => (s.id === selectedSessionId ? { ...s, lessonContent: updated.lessonContent } : s)));
      setNotification("✅ Đã lưu Bài học hôm nay.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu Bài học hôm nay thất bại.");
    } finally {
      setSavingLessonContent(false);
    }
  };

  /** "Tên giáo viên giảng dạy" (2026-08-06) — nhập tay, lưu ngược vào buổi học, mirror handleSaveLessonContent. */
  const handleSaveActualTeacherName = async () => {
    if (!selectedSessionId || !actualTeacherNameInput.trim()) return;
    setSavingActualTeacherName(true);
    setError(null);
    try {
      const updated = await updateActualTeacherName(selectedSessionId, actualTeacherNameInput.trim());
      setSessions((prev) => prev.map((s) => (s.id === selectedSessionId ? { ...s, actualTeacherName: updated.actualTeacherName } : s)));
      setNotification("✅ Đã lưu Tên giáo viên giảng dạy.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu Tên giáo viên giảng dạy thất bại.");
    } finally {
      setSavingActualTeacherName(false);
    }
  };

  /** "Loại giáo viên" (2026-08-05) — lưu ngược vào buổi học, mirror handleSaveLessonContent. */
  const handleChangeTeacherType = async (type: TeacherType) => {
    if (!selectedSessionId || savingTeacherType || type === teacherType) return;
    setSavingTeacherType(true);
    setError(null);
    try {
      await updateSessionTeacherType(selectedSessionId, type);
      setSessions((prev) => prev.map((s) => (s.id === selectedSessionId ? { ...s, teacherType: type } : s)));
      setTeacherType(type);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu loại giáo viên thất bại.");
    } finally {
      setSavingTeacherType(false);
    }
  };

  /** "Gán nhanh cho cả lớp" (2026-08-05) — áp panel gán nhanh vào mọi dòng CHƯA khoá, thay vì phải chọn từng dòng. */
  const handleApplyQuickAssign = () => {
    const lockedIds = new Set(history.filter((h) => h.status === "PENDING" || h.status === "APPROVED").map((h) => h.studentId));
    setRows((prev) =>
      prev.map((r) =>
        lockedIds.has(r.studentId)
          ? r
          : {
              ...r,
              homeworkNext: quickExerciseId === "" ? quickOffline : "",
              homeworkNextExerciseId: quickExerciseId,
              homeworkNextReviewVideoSetId: quickVideoId
            }
      )
    );
    setDirty(true);
  };

  /** Payload dùng chung cho "Lưu nháp"/autosave/"Gửi nhận xét" — xem buildPayload+saveFilledRows bên dưới. */
  const buildCommentPayload = (r: Row) => ({
    content: r.content.trim(),
    severity: "NORMAL" as const,
    isWarning: false,
    attitude: r.attitude || undefined,
    homeworkPreviousScore: r.homeworkPreviousScore.trim() || undefined,
    homeworkPreviousSpeakingScore: r.homeworkPreviousSpeakingScore.trim() || undefined,
    // offline (chữ tự do) và chọn Exercise loại trừ lẫn nhau ngay từ lúc nhập (xem updateRow ở bảng/quick-assign) — chỉ 1 trong 2 khác rỗng.
    homeworkNext: r.homeworkNextExerciseId === "" ? r.homeworkNext.trim() || undefined : undefined,
    homeworkNextExerciseId: r.homeworkNextExerciseId !== "" ? r.homeworkNextExerciseId : undefined,
    homeworkNextReviewVideoSetId: r.homeworkNextReviewVideoSetId !== "" ? r.homeworkNextReviewVideoSetId : undefined,
    // Hạn nộp buổi sau (ngày + giờ) — 1 giá trị chung cho cả buổi (xem dueDateTime), để trống thì BE tự tính = buổi kế tiếp.
    homeworkNextDueDate: dueDateTime || undefined,
    note: r.note.trim() || undefined
  });

  /**
   * Ghi DRAFT cho các dòng đã có nội dung — dùng chung cho "Lưu nháp"/autosave/"Gửi nhận xét" (bổ sung
   * ngoài SDD gốc, 2026-08-14). Dòng đã có nhận xét DRAFT/REJECTED (gõ tay lưu dở hoặc nhập Excel) —
   * SỬA bản ghi đã có qua updateComment(), không tạo mới qua writeComment() (tránh sinh 2 bản ghi trùng
   * cùng 1 buổi+học sinh — đúng bug 500 đã gặp trước đây, backend hiện chưa tự chặn trùng ở writeComment()).
   */
  const saveFilledRows = (filled: Row[], classId: number, session: ClassSessionResponse) =>
    Promise.allSettled(
      filled.map((r) => {
        const payload = buildCommentPayload(r);
        const existing = history.find((h) => h.studentId === r.studentId && (h.status === "DRAFT" || h.status === "REJECTED"));
        return existing
          ? updateComment(existing.id, payload)
          : writeComment(classId, {
              studentId: r.studentId,
              classSessionId: session.id,
              commentDate: session.sessionDate,
              ...payload
            });
      })
    );

  /**
   * "Lưu nháp" (2026-08-14, bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — phòng giáo viên vô
   * tình thoát khi chưa "Gửi nhận xét" (chỉ ghi DRAFT, KHÔNG gọi submitComments). Không bắt buộc điền
   * "Bài học hôm nay"/đủ học sinh — lưu được dở dang, chỉ cần có ít nhất 1 dòng đã gõ nội dung (ràng
   * buộc @NotBlank content ở BE). `silent=true` dùng cho autosave — không hiện banner/lỗi làm phiền.
   */
  const handleSaveDraft = async (silent: boolean) => {
    if (!selectedClassId || !selectedSession) return;
    const filled = rows.filter((r) => r.content.trim());
    if (filled.length === 0) {
      if (!silent) setError("Vui lòng nhập nhận xét cho ít nhất 1 học sinh trước khi lưu.");
      return;
    }
    setSavingDraft(true);
    if (!silent) setError(null);
    try {
      const results = await saveFilledRows(filled, selectedClassId, selectedSession);
      const failedCount = results.filter((r) => r.status === "rejected").length;
      setDirty(false);
      setLastSavedAt(new Date());
      if (!silent) {
        setNotification(
          failedCount > 0
            ? `⚠️ Đã lưu nháp ${filled.length - failedCount}/${filled.length} nhận xét — ${failedCount} học sinh lỗi, thử lại sau.`
            : `💾 Đã lưu nháp ${filled.length} nhận xét (chưa gửi duyệt).`
        );
      }
      await loadHistory(selectedClassId, selectedSession.id, rows.map((r) => r.studentId));
      refreshSessionCommentStats(selectedClassId);
      listAssignmentsForClass(selectedClassId).then(setGrammarAssignments).catch(() => undefined);
      listReviewVideoAssignmentsForClass(selectedClassId).then(setVideoAssignments).catch(() => undefined);
    } catch (err) {
      if (!silent) setError(err instanceof ApiError ? err.message : "Lưu nháp thất bại.");
    } finally {
      setSavingDraft(false);
    }
  };

  // Autosave (2026-08-14, bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — debounce ~18s sau lần gõ
  // cuối, dùng chung handleSaveDraft(silent=true) nên không hiện banner/lỗi làm phiền. Chỉ chạy khi có
  // thay đổi thật (dirty) và không đang lưu/gửi tay — effect re-tạo timer mỗi lần `rows` đổi (gõ phím)
  // nên tự nhiên có hành vi debounce (huỷ timer cũ, đặt timer mới).
  useEffect(() => {
    if (!dirty || !selectedSessionId || sending || savingDraft) return;
    const timer = setTimeout(() => handleSaveDraft(true), 18000);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dirty, rows, selectedSessionId]);

  const handleSend = async () => {
    if (!selectedClassId || !selectedSession) return;
    if (!selectedSession.lessonContent?.trim()) {
      setLessonContentMissingError(true);
      lessonContentInputRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
      lessonContentInputRef.current?.focus();
      return;
    }
    const filled = rows.filter((r) => r.content.trim());
    if (filled.length === 0) {
      setError("Vui lòng nhập nhận xét cho ít nhất 1 học sinh.");
      return;
    }
    setSending(true);
    setError(null);
    try {
      const created = await saveFilledRows(filled, selectedClassId, selectedSession);
      const succeededIds = created.filter((r): r is PromiseFulfilledResult<Awaited<ReturnType<typeof writeComment>>> => r.status === "fulfilled").map((r) => r.value.id);
      // Gom lý do lỗi thật từ từng promise bị reject (VD 422 "Lớp id=X chưa có buổi học kế tiếp...")
      // theo đúng học sinh — trước đây chỉ đếm failedCount, không hiện rõ NGUYÊN NHÂN khiến Giáo viên
      // không biết sửa gì để thử lại (2026-07-31). Gộp theo message giống nhau (thường cùng 1 lý do,
      // VD cả lớp cùng chọn 1 đề Online mà lớp chưa có buổi sau) để không lặp lại dài dòng.
      const failedByMessage = new Map<string, string[]>();
      created.forEach((r, i) => {
        if (r.status !== "rejected") return;
        const msg = r.reason instanceof ApiError ? r.reason.message : "Lỗi không xác định.";
        const studentName = filled[i]?.studentFullName ?? "?";
        failedByMessage.set(msg, [...(failedByMessage.get(msg) ?? []), studentName]);
      });
      const failedCount = created.length - succeededIds.length;

      // UC-21 (2026-07-29, BE PR #113 khôi phục DRAFT cho DAILY): writeComment() giờ chỉ lưu DRAFT —
      // phải gọi thêm submitComments() mới thật sự chuyển sang PENDING (chờ Quản lý điểm trường duyệt).
      // Nút "Gửi nhận xét" ở đây gộp cả 2 bước (ghi nháp + gửi duyệt) trong 1 lần bấm, không tách riêng
      // bước "Lưu nháp" — nếu bước gửi thất bại (hiếm), dữ liệu vẫn an toàn ở DRAFT, có thể "Nộp duyệt"
      // lại ở "Lịch sử nhận xét" bên dưới.
      let submitFailedMessage: string | null = null;
      if (succeededIds.length > 0) {
        try {
          await submitComments(selectedClassId, succeededIds);
        } catch (err) {
          submitFailedMessage = err instanceof ApiError ? err.message : "Gửi duyệt thất bại.";
        }
      }

      let message = submitFailedMessage
        ? `⚠️ Đã lưu nháp ${succeededIds.length} nhận xét nhưng gửi duyệt thất bại: ${submitFailedMessage} — vào "Lịch sử nhận xét" bên dưới bấm "Nộp duyệt" để thử lại.`
        : `🔔 Đã gửi nhận xét ${succeededIds.length} học sinh lên Quản lý điểm trường rà soát duyệt.`;
      if (failedCount > 0) {
        failedByMessage.forEach((students, msg) => {
          message += `\n- ${msg} (${students.join(", ")})`;
        });
      }
      setNotification(message);
      setRows((prev) =>
        prev.map((r) =>
          r.content.trim() ? { ...r, attitude: "", homeworkPreviousScore: "", homeworkPreviousSpeakingScore: "", content: "", ...EMPTY_ROW_HOMEWORK, note: "" } : r
        )
      );
      setDirty(false);
      setLastSavedAt(new Date());
      await loadHistory(selectedClassId, selectedSession.id, rows.map((r) => r.studentId));
      refreshSessionCommentStats(selectedClassId);
      // Gửi xong có thể vừa tạo bản giao mới (chọn đề/video Online) — tải lại map tra ngược để lần sửa kế tiếp resolve đúng.
      listAssignmentsForClass(selectedClassId).then(setGrammarAssignments).catch(() => undefined);
      listReviewVideoAssignmentsForClass(selectedClassId).then(setVideoAssignments).catch(() => undefined);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gửi nhận xét thất bại.");
    } finally {
      setSending(false);
    }
  };

  /** UC-21 (2026-07-29): học sinh chỉ nhận xét DAILY được 1 lần/buổi — bấm vào dòng đã có nhận xét thì báo rõ thay vì im lặng khoá ô. */
  const notifyAlreadySent = (r: Row, sent: StudentCommentResponse) => {
    setNotification(`⚠️ Học sinh ${r.studentFullName} đã có nhận xét cho buổi này rồi (trạng thái: ${statusLabels[sent.status]}) — xem/sửa ở "Lịch sử nhận xét" bên dưới.`);
  };

  const handleDownloadTemplate = async () => {
    if (!selectedSessionId) return;
    setDownloadingTemplate(true);
    setError(null);
    try {
      const blob = await downloadDailyCommentTemplate(selectedSessionId);
      downloadBlob(blob, `mau-nhan-xet-buoi-${selectedSessionId}.xlsx`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tải file mẫu thất bại.");
    } finally {
      setDownloadingTemplate(false);
    }
  };

  /**
   * Nhập Excel (sửa lại luồng 2026-08-14, đã xác nhận với người dùng) — CHỈ parse & fill vào bảng nhận
   * xét trên UI (previewImportDailyComments, KHÔNG ghi StudentComment/Bài học hôm nay/Tên GV/Hạn nộp
   * vào DB — điểm danh vẫn ghi ngay ở BE, nghiệp vụ độc lập). Trước đây ghi thẳng DRAFT vào DB khiến dữ
   * liệu chỉ hiện ở "Lịch sử nhận xét" thay vì fill vào bảng để giáo viên xem/sửa tiếp — giờ giáo viên
   * tự bấm "Lưu" (hoặc chờ autosave) mới thật sự ghi DRAFT, sau đó "Gửi nhận xét" mới gửi duyệt.
   */
  const handleImportFile = async (file: File | null) => {
    if (!file || !selectedClassId || !selectedSessionId) return;
    if (!file.name.toLowerCase().endsWith(".xlsx")) {
      setError("Chỉ hỗ trợ file .xlsx.");
      return;
    }
    setImporting(true);
    setError(null);
    setImportResult(null);
    try {
      const res = await previewImportDailyComments(selectedSessionId, file);
      setImportResult(res);

      // "Bài học hôm nay"/"Tên GV giảng dạy"/"Hạn nộp" chỉ fill vào ô nhập ở đầu trang — giáo viên tự
      // bấm nút "Lưu" riêng của từng ô (đã có sẵn) để ghi DB, giống hệt khi gõ tay. Không đè lên nếu
      // giáo viên đã tự gõ trước khi nhập Excel.
      if (res.lessonContent) setLessonContentInput((prev) => prev || res.lessonContent!);
      if (res.teacherName) setActualTeacherNameInput((prev) => prev || res.teacherName!);
      if (res.dueDate) {
        setDueDate((prev) => prev || res.dueDate!.slice(0, 10));
        setDueTime((prev) => prev || res.dueDate!.slice(11, 16));
      }

      if (res.rows.length > 0) {
        const parsedByStudent = new Map(res.rows.map((row) => [row.studentId, row]));
        setRows((prev) =>
          prev.map((r) => {
            const parsed = parsedByStudent.get(r.studentId);
            // Chỉ fill dòng còn trống — không đè lên nội dung giáo viên đang gõ dở (mirror loadHistory).
            if (!parsed || !isRowBlank(r)) return r;
            return {
              ...r,
              attitude: (parsed.attitude ?? "") as Row["attitude"],
              homeworkPreviousScore: parsed.homeworkPreviousScore ?? "",
              homeworkPreviousSpeakingScore: parsed.homeworkPreviousSpeakingScore ?? "",
              content: parsed.content ?? "",
              homeworkNext: parsed.homeworkNext ?? "",
              homeworkNextExerciseId: parsed.homeworkNextExerciseId ?? "",
              homeworkNextReviewVideoSetId: parsed.homeworkNextReviewVideoSetId ?? "",
              note: parsed.note ?? ""
            };
          })
        );
        setDirty(true);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Nhập từ Excel thất bại.");
    } finally {
      setImporting(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  return (
    <div className="space-y-4">
      <NotificationBanner message={notification} onClose={() => setNotification(null)} />
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      <AttendanceReminderBanner />

      {/* Bỏ overflow-hidden ở đây (trước dùng để bo góc rounded-xl cho header bg-slate-50 bên dưới) —
          overflow-hidden trên tổ tiên sẽ VÔ HIỆU HÓA position:sticky của khối "Bài học hôm nay" bên
          trong (đã gặp thực tế 2026-08-03: sticky không ghim dù đặt đúng top-0). Bo góc header trực
          tiếp bằng rounded-t-xl thay thế — hiệu ứng thị giác giống hệt, không cần overflow-hidden. */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-soft">
        <div className="px-5 py-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50 rounded-t-xl">
          <div>
            <span className="text-xs font-bold text-slate-700 font-display">Nhận xét hàng ngày theo buổi học</span>
            <p className="text-[10px] text-slate-400 mt-0.5">
              {selectedClass ? `${selectedClass.name} (${selectedClass.classCode})` : "Chưa chọn lớp — chọn ở góc trên bên phải (Header) để bắt đầu."}
            </p>
          </div>
          {selectedClass && (
            <div className="flex flex-wrap items-center gap-2">
              <Select
                value={selectedSessionId ?? ""}
                onChange={(e) => setSelectedSessionId(e.target.value ? Number(e.target.value) : null)}
                className="bg-white border text-[10px] font-bold text-slate-700 px-2 py-1 rounded focus:outline-none"
              >
                <option value="">-- Chọn buổi học --</option>
                {selectableSessions.map((s) => {
                  const status = getSessionCommentStatus(s.id);
                  const stat = sessionCommentStats[s.id];
                  return (
                    <option key={s.id} value={s.id}>
                      {status === "DONE" ? "✓ " : status === "PARTIAL" ? "◐ " : ""}
                      Buổi {s.sessionNumber} — {s.sessionDate} ({s.startTime}–{s.endTime})
                      {status === "PARTIAL" && stat ? ` (${stat.commented}/${stat.total})` : ""}
                    </option>
                  );
                })}
              </Select>
              {/* "Loại giáo viên" (2026-08-05) — ăn theo để lọc/đổi nhãn BTVN buổi sau (Ngữ pháp/Bài nghe, Từ Vựng (TKN)/Clip phản xạ). */}
              {selectedSessionId && (
                <div className="flex items-center rounded-lg border border-slate-200 bg-white overflow-hidden shrink-0">
                  {(Object.keys(teacherTypeLabels) as TeacherType[]).map((type) => (
                    <button
                      key={type}
                      type="button"
                      disabled={savingTeacherType || sessionHasSentComment}
                      title={sessionHasSentComment ? "Buổi này đã có nhận xét gửi duyệt — không đổi được Loại giáo viên nữa." : undefined}
                      onClick={() => handleChangeTeacherType(type)}
                      className={`px-2.5 py-1 text-[10px] font-bold whitespace-nowrap disabled:opacity-50 disabled:cursor-not-allowed ${
                        teacherType === type ? "bg-brand-orange text-white" : "text-slate-600 hover:bg-slate-50"
                      }`}
                    >
                      {teacherTypeLabels[type]}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        {selectedSessionId && !teacherType && (
          <div className="px-5 py-2.5 border-b border-slate-100 bg-amber-50 text-[11px] text-amber-700">
            ⚠️ Chưa chọn "Loại giáo viên" ở trên — chọn trước để bảng BTVN buổi sau hiện đúng Bài/Video theo Giáo viên Việt Nam hay nước ngoài.
          </div>
        )}

        {selectedSessionId && sessionHasSentComment && (
          <div className="px-5 py-2.5 border-b border-slate-100 bg-slate-50 text-[11px] text-slate-500">
            🔒 Buổi này đã có nhận xét gửi duyệt. Muốn sửa lại, nhờ Quản lý điểm trường "Từ chối" toàn bộ nhận xét của buổi để mở khoá.
          </div>
        )}

        {selectedSessionId && (
          <div
            className={`sticky top-0 z-20 px-5 py-3 border-b shadow-sm ${
              lessonContentMissingError ? "bg-rose-50 border-rose-200" : "bg-amber-50 border-slate-100"
            }`}
          >
            {/* 2 nhóm "Bài học hôm nay"/"Tên giáo viên giảng dạy" LUÔN cùng 1 hàng (bổ sung ngoài SDD gốc,
                2026-08-14) — dòng cảnh báo thiếu bài học tách RIÊNG ra bên dưới (w-full ở đây trước đây
                nằm CHUNG hàng flex-wrap, tự ép 2 nhóm xuống 2 hàng khác nhau khi cảnh báo hiện ra). */}
            <div className="flex flex-wrap items-center gap-2">
              <label className="text-[11px] font-bold text-slate-600 shrink-0">Bài học hôm nay *</label>
              <input
                ref={lessonContentInputRef}
                value={lessonContentInput}
                disabled={sessionHasSentComment}
                onChange={(e) => {
                  setLessonContentInput(e.target.value);
                  setLessonContentMissingError(false);
                }}
                placeholder="VD: Unit 3 - Free time activities"
                className={`flex-1 min-w-[220px] bg-white border text-xs p-2 rounded-lg focus:outline-none disabled:opacity-60 disabled:cursor-not-allowed ${
                  lessonContentMissingError ? "border-rose-400 ring-1 ring-rose-300" : "border-slate-200"
                }`}
              />
              <button
                type="button"
                onClick={handleSaveLessonContent}
                disabled={
                  sessionHasSentComment ||
                  savingLessonContent ||
                  !lessonContentInput.trim() ||
                  lessonContentInput.trim() === (selectedSession?.lessonContent ?? "")
                }
                className="px-3 py-2 bg-brand-orange hover:bg-brand-orange/90 text-white text-[11px] font-bold rounded-lg disabled:opacity-40"
              >
                {savingLessonContent ? "Đang lưu..." : "Lưu"}
              </button>
              <label className="text-[11px] font-bold text-slate-600 shrink-0">Tên giáo viên giảng dạy</label>
              <input
                value={actualTeacherNameInput}
                disabled={sessionHasSentComment}
                onChange={(e) => setActualTeacherNameInput(e.target.value)}
                placeholder="VD: Nguyễn Văn A (điền hộ nếu Giáo viên nước ngoài không tự thao tác)"
                className="flex-1 min-w-[220px] bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-60 disabled:cursor-not-allowed"
              />
              <button
                type="button"
                onClick={handleSaveActualTeacherName}
                disabled={
                  sessionHasSentComment ||
                  savingActualTeacherName ||
                  !actualTeacherNameInput.trim() ||
                  actualTeacherNameInput.trim() === (selectedSession?.actualTeacherName ?? "")
                }
                className="px-3 py-2 bg-brand-orange hover:bg-brand-orange/90 text-white text-[11px] font-bold rounded-lg disabled:opacity-40"
              >
                {savingActualTeacherName ? "Đang lưu..." : "Lưu"}
              </button>
            </div>
            {lessonContentMissingError ? (
              <p className="mt-1.5 text-[10px] text-rose-600 font-bold">
                ⚠️ Bắt buộc điền + Lưu Bài học hôm nay trước khi Gửi nhận xét — nội dung học sinh bạn đã gõ vẫn còn nguyên, điền xong bấm "Gửi nhận xét" lại là được.
              </p>
            ) : (
              !selectedSession?.lessonContent && (
                <p className="mt-1.5 text-[10px] text-amber-700 italic">
                  Chưa điền bài học hôm nay — bắt buộc điền trước khi Gửi nhận xét (buổi chưa điền sẽ bị từ chối khi gửi duyệt).
                </p>
              )
            )}
          </div>
        )}

        {selectedSessionId && blockOnlineHomework && (
          <div className="px-5 py-2.5 border-b border-slate-100 bg-rose-50/60 text-[11px] text-rose-700">
            ⚠️ Đây là buổi học cuối cùng đã lên lịch của lớp — lớp chưa có buổi kế tiếp nào trong lịch nên
            chưa thể giao BTVN {grammarLabel} (Online)/{videoLabel} buổi sau (cần hạn nộp = buổi kế tiếp).
            Tự chọn hạn nộp ở panel "Gán nhanh cho cả lớp" bên dưới để bỏ qua điều kiện này, hoặc chỉ nhập
            BTVN offline (chữ tự do, không cần hạn nộp).
          </div>
        )}

        {selectedSessionId && teacherType && (
          <div className="px-5 py-3 border-b border-slate-100 bg-orange-50/40 space-y-2">
            <span className="text-[10px] font-bold uppercase text-slate-500">
              Gán nhanh cho cả lớp
            </span>
            <div className="flex flex-wrap items-end gap-2">
              <div className="min-w-[160px]">
                <label className="text-[9px] font-bold uppercase text-slate-400 block mb-0.5">BTVN offline</label>
                <input
                  value={quickOffline}
                  onChange={(e) => {
                    setQuickOffline(e.target.value);
                    if (e.target.value) setQuickExerciseId("");
                  }}
                  placeholder="VD: Unit 2 trang 10"
                  className="w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                />
              </div>
              <div className="min-w-[200px]">
                <label className="text-[9px] font-bold uppercase text-slate-400 block mb-0.5">BTVN online — {grammarLabel}</label>
                <Select
                  value={quickExerciseId}
                  disabled={blockOnlineHomework}
                  onChange={(e) => {
                    const value = e.target.value ? Number(e.target.value) : "";
                    setQuickExerciseId(value);
                    if (value !== "") setQuickOffline("");
                  }}
                  className="w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-40"
                >
                  <option value="">-- Không giao --</option>
                  {filteredGrammarOptions.map((ex) => (
                    <option key={ex.id} value={ex.id}>
                      {ex.examCode} - {ex.title}
                    </option>
                  ))}
                </Select>
              </div>
              <div className="min-w-[200px]">
                <label className="text-[9px] font-bold uppercase text-slate-400 block mb-0.5">BTVN online — {videoLabel}</label>
                <Select
                  value={quickVideoId}
                  disabled={blockOnlineHomework}
                  onChange={(e) => setQuickVideoId(e.target.value ? Number(e.target.value) : "")}
                  className="w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-40"
                >
                  <option value="">-- Không giao --</option>
                  {filteredVideoOptions.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.title} ({s.code})
                    </option>
                  ))}
                </Select>
              </div>
              <div className="min-w-[150px]">
                <label className="text-[9px] font-bold uppercase text-slate-400 block mb-0.5">Hạn nộp bài — ngày</label>
                <DatePicker
                  value={dueDate}
                  min={selectedSession?.sessionDate}
                  onChange={(value) => {
                    setDueDate(value);
                    // Chọn ngày lần đầu (chưa có giờ) — tự điền cuối ngày cho tiện, Giáo viên vẫn sửa được ngay bên cạnh.
                    if (value && !dueTime) setDueTime("23:59");
                  }}
                />
              </div>
              <div className="min-w-[110px]">
                <label className="text-[9px] font-bold uppercase text-slate-400 block mb-0.5">Hạn nộp bài — giờ</label>
                <input
                  type="time"
                  value={dueTime}
                  disabled={!dueDate}
                  onChange={(e) => setDueTime(e.target.value)}
                  className="w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-40"
                />
              </div>
              <button
                type="button"
                onClick={handleApplyQuickAssign}
                disabled={!quickOffline && quickExerciseId === "" && quickVideoId === ""}
                className="px-3 py-2 bg-brand-orange hover:bg-brand-orange/90 text-white text-[11px] font-bold rounded-lg disabled:opacity-40"
              >
                Áp dụng cho cả lớp
              </button>
            </div>
          </div>
        )}

        {selectedSessionId && (
          <div className="px-5 py-3 border-b border-slate-100 bg-slate-50/60 flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={handleDownloadTemplate}
              disabled={downloadingTemplate}
              className="flex items-center gap-1.5 border border-dashed border-slate-300 rounded-lg px-3 py-2 text-[11px] font-semibold text-slate-600 hover:bg-white disabled:opacity-50"
            >
              <Download className="w-3.5 h-3.5" />
              {downloadingTemplate ? "Đang tải..." : "Tải mẫu Excel"}
            </button>
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={importing}
              className="flex items-center gap-1.5 border-2 border-dashed border-slate-200 rounded-lg px-3 py-2 text-[11px] font-semibold text-slate-600 hover:border-brand-orange hover:bg-orange-50/30 disabled:opacity-50"
            >
              <UploadCloud className="w-3.5 h-3.5 text-brand-orange" />
              {importing ? "Đang nhập..." : "Nhập từ Excel"}
            </button>
            <input ref={fileInputRef} type="file" accept=".xlsx" className="hidden" onChange={(e) => handleImportFile(e.target.files?.[0] ?? null)} />

            {/* "Lưu nháp" (2026-08-14) — phòng giáo viên vô tình thoát khi chưa "Gửi nhận xét" (chỉ ghi
                DRAFT, không gửi duyệt). Cùng hàng với 2 nút Excel nhưng đẩy sang PHẢI (ml-auto) — tách
                nhóm "nhập liệu" (trái) khỏi nhóm "lưu" (phải) theo đúng yêu cầu. */}
            <div className="ml-auto flex items-center gap-2">
              {lastSavedAt && (
                <span className="text-[10px] text-slate-400">
                  Đã lưu lúc {lastSavedAt.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })}
                </span>
              )}
              <button
                type="button"
                onClick={() => handleSaveDraft(false)}
                disabled={savingDraft || !rows.some((r) => r.content.trim())}
                className="flex items-center gap-1.5 border border-dashed border-slate-300 rounded-lg px-3 py-2 text-[11px] font-semibold text-slate-600 hover:bg-white disabled:opacity-50"
              >
                <Save className="w-3.5 h-3.5" />
                {savingDraft ? "Đang lưu..." : "Lưu nháp"}
              </button>
            </div>

            {importResult && (
              <div className="w-full flex flex-wrap items-center gap-2 text-[11px] mt-1">
                <span className="bg-slate-100 border border-slate-200 text-slate-700 font-semibold px-2 py-1 rounded-lg">
                  Tổng: {importResult.totalRows ?? "—"}
                </span>
                <span className="bg-emerald-50 border border-emerald-100 text-emerald-600 font-semibold px-2 py-1 rounded-lg">
                  Thành công: {importResult.successRows}
                </span>
                <span className="bg-rose-50 border border-rose-100 text-rose-600 font-semibold px-2 py-1 rounded-lg">
                  Lỗi: {importResult.failedRows}
                </span>
                {importResult.errorSummary.length > 0 && (
                  <div className="w-full border border-rose-100 rounded-lg overflow-hidden mt-1">
                    <div className="max-h-40 overflow-y-auto divide-y divide-slate-100">
                      {importResult.errorSummary.map((e, i) => (
                        <div key={i} className="px-3 py-1.5 flex gap-2 bg-white">
                          <span className="font-mono font-bold text-slate-400 shrink-0">Dòng {e.row}</span>
                          <span className="text-slate-600">{e.reason}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* Cố định 3 cột đầu (Mã học viên/Họ và tên/Ngày sinh) + 2 hàng header (bổ sung ngoài SDD gốc,
            đã xác nhận với người dùng 2026-08-14) — kéo bảng sang phải/xuống dưới vẫn biết đang nhận
            xét học sinh nào. Bọc thêm max-h + overflow-y-auto (khác các TableContainer khác trong repo,
            vốn chỉ cuộn ngang) để sticky top có 1 scroll container CỐ ĐỊNH ngay trong bảng.
            KHÔNG dùng <TableContainer> chung (table border-collapse) ở đây — border-collapse phá vỡ
            position:sticky trên từng <td>/<th> riêng lẻ. Table riêng dùng border-separate +
            border-spacing-0 để sticky định vị đúng, viền vẫn liền mạch như border-collapse (spacing=0).
            KHÔNG dùng table-fixed + <colgroup> (đã thử, vẫn lệch) — đo thực tế bằng DevTools phát hiện
            position:sticky trên <td>/<th> có rowSpan không tôn trọng width khai báo ở colgroup/hàng đầu
            (giới hạn/bug trình duyệt), khiến cột lệch vị trí. Cách ĐÚNG: ép cứng width+minWidth+maxWidth
            BẰNG NHAU trực tiếp trên từng ô CỦA MỌI DÒNG (không qua colgroup) — buộc trình duyệt không co
            giãn cột đó bất kể nội dung, làm việc ổn định với sticky. Các cột KHÔNG sticky vẫn giữ auto
            layout + min-w như cũ, không ảnh hưởng. */}
        <div className="overflow-x-auto overflow-y-auto max-h-[65vh]">
          <table className="text-xs text-left border-separate border-spacing-0">
          {/* sticky trực tiếp trên <thead> (thay vì tính top offset riêng cho từng <tr>) — trình duyệt tự
              ghim NGUYÊN CẢ 2 dòng header làm 1 khối, không cần đoán chiều cao dòng 1 để lệch dòng 2. 3 ô
              góc (Mã học viên/Họ và tên/Ngày sinh) cần thêm sticky left riêng (trục ngang, độc lập với
              trục dọc đã ghim ở thead) để vừa dính trên vừa dính trái khi cuộn cả 2 chiều. */}
          <thead className="sticky top-0 z-20 bg-slate-50">
            {/* Border rõ giữa các cột/dòng header (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
                2026-08-06) — Th mặc định không có border, bảng nhóm cột (BTVN buổi trước/online) khó
                phân biệt ranh giới nếu không kẻ thêm. */}
            <tr className="border-b border-slate-300 [&>th]:text-center">
              <Th rowSpan={2} style={STICKY_COL_STYLE[0]} className="sticky left-0 z-30 bg-slate-50 border-r border-slate-300">Mã học viên</Th>
              <Th rowSpan={2} style={STICKY_COL_STYLE[1]} className="sticky z-30 bg-slate-50 border-r border-slate-300">Họ và tên</Th>
              <Th rowSpan={2} style={STICKY_COL_STYLE[2]} className="sticky z-30 bg-slate-50 border-r border-slate-300">Ngày sinh</Th>
              <Th colSpan={3} className="text-center border-r border-slate-300">BTVN buổi trước</Th>
              <Th rowSpan={2} className="border-r border-slate-300">BTVN offline</Th>
              <Th colSpan={2} className="text-center border-r border-slate-300">BTVN online</Th>
              <Th rowSpan={2} className="border-r border-slate-300">Hạn nộp bài</Th>
              <Th rowSpan={2} className="border-r border-slate-300">Thái độ học tập</Th>
              <Th rowSpan={2} className="border-r border-slate-300">Nhận xét học sinh *</Th>
              <Th rowSpan={2}>Ghi chú</Th>
            </tr>
            <tr className="border-b border-slate-300 [&>th]:text-center">
              <Th className="border-r border-slate-300 text-center">Offline</Th>
              <Th className="border-r border-slate-300 text-center">{grammarLabel}</Th>
              <Th className="border-r border-slate-300 text-center">{videoLabel}</Th>
              <Th className="border-r border-slate-300 text-center">{grammarLabel}</Th>
              <Th className="border-r border-slate-300 text-center">{videoLabel}</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-300">
            {!selectedSessionId ? (
              <tr>
                <td colSpan={12} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                  {selectedClass ? "Chọn buổi học ở trên để tải danh sách học sinh." : "Chọn 1 lớp ở Header (góc trên bên phải)."}
                </td>
              </tr>
            ) : loadingRows ? (
              <tr>
                <td colSpan={12} className="px-6 py-12 text-center text-xs text-slate-400">
                  Đang tải...
                </td>
              </tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={12} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                  Không tìm thấy học sinh nào thuộc lớp học này.
                </td>
              </tr>
            ) : (
              rows.map((r) => {
                const updateRow = (patch: Partial<Row>) => {
                  setRows((prev) => prev.map((row) => (row.studentId === r.studentId ? { ...row, ...patch } : row)));
                  setDirty(true);
                };
                // Học sinh này đã CÓ nhận xét DAILY cho đúng buổi đang chọn (gửi tay hoặc nhập Excel).
                // Chỉ khoá read-only khi đã PENDING/APPROVED (không còn sửa được ở đây nữa — backend chỉ
                // cho sửa khi DRAFT/REJECTED) — DRAFT/REJECTED vẫn hiện ô nhập bình thường (đã điền sẵn
                // dữ liệu ở loadHistory) để giáo viên xem/sửa tiếp trước khi bấm "Gửi nhận xét" (đã xác
                // nhận với người dùng 2026-07-29).
                const sent = history.find((h) => h.studentId === r.studentId);
                const locked = !!sent && (sent.status === "PENDING" || sent.status === "APPROVED");
                // Nền đặc cho 3 cột cố định (khác Td mặc định trong suốt) — cuộn ngang thì nội dung cột
                // sau không được lộ ra qua cột cố định phía trên (bổ sung ngoài SDD gốc, 2026-08-14).
                const stickyBg = locked ? "bg-emerald-50" : "bg-white";
                return (
                  <tr
                    key={r.studentId}
                    onClick={locked ? () => notifyAlreadySent(r, sent) : undefined}
                    className={`transition-colors ${locked ? "bg-emerald-50/20 cursor-pointer hover:bg-emerald-50/40" : "hover:bg-slate-50/40"}`}
                  >
                    <Td style={STICKY_COL_STYLE[0]} className={`sticky left-0 z-10 ${stickyBg} font-mono font-bold text-slate-500 border-r border-slate-300`}>{r.studentCode}</Td>
                    <Td style={STICKY_COL_STYLE[1]} className={`sticky z-10 ${stickyBg} font-bold text-slate-900 whitespace-nowrap border-r border-slate-300`}>
                      <StudentNameLink studentId={r.studentId} name={r.studentFullName} />
                    </Td>
                    <Td style={STICKY_COL_STYLE[2]} className={`sticky z-10 ${stickyBg} whitespace-nowrap text-slate-500 border-r border-slate-300`}>{r.studentDateOfBirth ?? "—"}</Td>
                    <Td className="min-w-[130px] border-r border-slate-300">
                      {/* BTVN buổi trước — Offline (sửa lại 2026-08-14, đúng luồng đã xác nhận với người dùng): ô để
                          GIÁO VIÊN NHẬP ĐIỂM % tự chấm tay cho BTVN offline (giao làm trên giấy ở buổi trước — không có
                          cách nào tính % tự động, BE không track được bài làm trên giấy). Dùng chung field
                          homeworkPreviousScore với cột "{grammarLabel}" bên phải (backend không phân biệt 2 cột — cùng
                          là điểm chấm tay cho kênh Ngữ pháp/Bài nghe buổi trước, chỉ khác chỗ hiển thị trên UI). */}
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.homeworkPreviousScore || "—"}</div>
                      ) : (
                        <input
                          value={r.homeworkPreviousScore}
                          onChange={(e) => updateRow({ homeworkPreviousScore: e.target.value })}
                          placeholder="VD: 80%"
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      )}
                    </Td>
                    <Td className="min-w-[150px] border-r border-slate-300">
                      {/* {grammarLabel} buổi trước — CHỈ hiện % TỰ ĐỘNG (buổi trước giao Online, BE tính từ
                          exercise_attempts) — nhập tay đã chuyển hẳn sang cột "Offline" bên trái, không còn fallback
                          nhập tay ở đây nữa (tránh 2 cột cùng nhập được 1 giá trị gây nhầm lẫn cho giáo viên). */}
                      <PreviousProgressCell auto={sent?.grammarPreviousProgress ?? null} manual={null} />
                    </Td>
                    <Td className="min-w-[150px] border-r border-slate-300">
                      {locked ? (
                        <PreviousProgressCell auto={sent!.videoPreviousProgress} manual={sent!.homeworkPreviousSpeakingScore} />
                      ) : (
                        <input
                          value={r.homeworkPreviousSpeakingScore}
                          onChange={(e) => updateRow({ homeworkPreviousSpeakingScore: e.target.value })}
                          placeholder="VD: Đã thực hiện 85% (kênh Video luôn Online — % tự động sẽ hiện sau khi Gửi)"
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      )}
                    </Td>
                    <Td className="min-w-[160px] border-r border-slate-300">
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.homeworkNext || "—"}</div>
                      ) : (
                        <input
                          value={r.homeworkNext}
                          onChange={(e) => updateRow({ homeworkNext: e.target.value, ...(e.target.value ? { homeworkNextExerciseId: "" as const } : {}) })}
                          placeholder="VD: Unit 2 trang 10"
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      )}
                    </Td>
                    <Td className="min-w-[200px] border-r border-slate-300">
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.homeworkNextExerciseTitle || "—"}</div>
                      ) : (
                        <Select
                          value={r.homeworkNextExerciseId}
                          disabled={blockOnlineHomework || !teacherType}
                          onChange={(e) => {
                            const value = e.target.value ? Number(e.target.value) : "";
                            updateRow({ homeworkNextExerciseId: value, ...(value !== "" ? { homeworkNext: "" } : {}) });
                          }}
                          aria-label={!teacherType ? "Chọn Loại giáo viên ở trên trước." : blockOnlineHomework ? "Lớp chưa có buổi kế tiếp trong lịch — tự chọn hạn nộp để bỏ qua." : undefined}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                          <option value="">-- Chọn đề đã Publish --</option>
                          {filteredGrammarOptions.map((ex) => (
                            <option key={ex.id} value={ex.id}>
                              {ex.examCode} - {ex.title}
                            </option>
                          ))}
                        </Select>
                      )}
                    </Td>
                    <Td className="min-w-[200px] border-r border-slate-300">
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.homeworkNextReviewVideoSetTitle || "—"}</div>
                      ) : (
                        <Select
                          value={r.homeworkNextReviewVideoSetId}
                          onChange={(e) => updateRow({ homeworkNextReviewVideoSetId: e.target.value ? Number(e.target.value) : "" })}
                          disabled={blockOnlineHomework || !teacherType}
                          aria-label={!teacherType ? "Chọn Loại giáo viên ở trên trước." : blockOnlineHomework ? "Lớp chưa có buổi kế tiếp trong lịch — tự chọn hạn nộp để bỏ qua." : undefined}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                          <option value="">-- Không giao --</option>
                          {filteredVideoOptions.map((s) => (
                            <option key={s.id} value={s.id}>
                              {s.title} ({s.code})
                            </option>
                          ))}
                        </Select>
                      )}
                    </Td>
                    <Td className="min-w-[120px] whitespace-nowrap border-r border-slate-300">
                      {locked
                        ? sent!.homeworkNextDueAt
                          ? new Date(sent!.homeworkNextDueAt).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" })
                          : "—"
                        : dueDateTime
                          ? new Date(dueDateTime).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" })
                          : "—"}
                    </Td>
                    <Td className="min-w-[130px] border-r border-slate-300">
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.attitude ? attitudeLabels[sent!.attitude!] : "—"}</div>
                      ) : (
                        <Select
                          value={r.attitude}
                          onChange={(e) => updateRow({ attitude: e.target.value as Row["attitude"] })}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        >
                          <option value="">-- Chưa chọn --</option>
                          {Object.entries(attitudeLabels).map(([value, label]) => (
                            <option key={value} value={value}>
                              {label}
                            </option>
                          ))}
                        </Select>
                      )}
                    </Td>
                    <Td className="min-w-[320px] border-r border-slate-300">
                      {locked ? (
                        <div className={`${readOnlyFieldClass} whitespace-pre-wrap`}>{sent!.content}</div>
                      ) : (
                        <textarea
                          value={r.content}
                          onChange={(e) => updateRow({ content: e.target.value })}
                          placeholder="Viết nhận xét cho học sinh này..."
                          rows={2}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      )}
                    </Td>
                    <Td className="min-w-[140px] border-r border-slate-300">
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.note || "—"}</div>
                      ) : (
                        <input
                          value={r.note}
                          onChange={(e) => updateRow({ note: e.target.value })}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      )}
                    </Td>
                  </tr>
                );
              })
            )}
          </tbody>
          </table>
        </div>

        {selectedSessionId && rows.length > 0 && (
          <div className="px-6 py-4 bg-slate-50 border-t flex justify-end">
            {/* Không còn dòng nào có nội dung để gửi (VD cả lớp đã Gửi nhận xét xong, mọi dòng đều
                khoá/rỗng) — tự disable thay vì để bấm được rồi báo lỗi "chưa nhập gì" (2026-07-30). */}
            <button
              onClick={handleSend}
              disabled={sending || !rows.some((r) => r.content.trim())}
              className="bg-brand-orange hover:bg-brand-orange/90 text-white font-semibold text-xs px-4 py-2 rounded-lg flex items-center gap-1.5 shadow-soft transition-all disabled:opacity-50"
            >
              <Send className="w-4 h-4 text-white" />
              <span>{sending ? "Đang gửi..." : rows.some((r) => r.content.trim()) ? "Gửi nhận xét" : "Đã gửi hết nhận xét buổi này"}</span>
            </button>
          </div>
        )}

        {selectedClassId && selectedSessionId && (
          <div className="px-6 py-4 border-t border-slate-100 space-y-2">
            <button
              type="button"
              onClick={() => setShowHistory((v) => !v)}
              className="flex items-center gap-1.5 text-[10px] font-bold uppercase text-slate-500 hover:text-slate-700"
            >
              {showHistory ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
              Lịch sử nhận xét buổi này{!showHistory && history.length > 0 ? ` (${history.length})` : ""}
            </button>
            {showHistory && (loadingHistory ? (
              <p className="text-xs text-slate-400">Đang tải...</p>
            ) : (
              <CommentHistoryList
                classId={selectedClassId}
                history={history}
                onChanged={() => loadHistory(selectedClassId, selectedSessionId, rows.map((r) => r.studentId))}
                layout="table"
                studentCodeById={Object.fromEntries(rows.map((r) => [r.studentId, r.studentCode]))}
                grammarLabel={grammarLabel}
                videoLabel={videoLabel}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
