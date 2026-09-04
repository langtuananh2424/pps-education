import React, { useEffect, useRef, useState } from "react";
import { ChevronDown, ChevronUp, Download, History, Save, Send, ShieldAlert, UploadCloud } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import { useApp, UnsavedSaveResult } from "@/context/AppContext";
import Modal from "@/components/ui/Modal";
import {
  AutoProgressPreviewResponse,
  ClassEnrollmentResponse,
  ClassSessionResponse,
  StudentCommentResponse,
  downloadDailyCommentTemplate,
  previewAutoProgress,
  previewImportDailyComments,
  DailyCommentImportPreviewResponse,
  listClassEnrollments,
  listClassSessions,
  listCommentsForClass,
  listTodaySessions,
  bulkUpdatePendingDueDate,
  submitComments,
  updateComment,
  updateActualTeacherName,
  updateLessonContent,
  updateSessionTeacherType,
  writeComment
} from "../api";
import {
  HomeworkSkillGroupResponse,
  ReviewVideoAssignmentResponse,
  ReviewVideoSetResponse,
  listHomeworkSkillGroupsForClass,
  listReviewVideoAssignmentsForClass,
  listReviewVideoSetsByClass
} from "@/features/lms/api";
import { useEligibleClasses } from "../hooks/useEligibleClasses";
import NotificationBanner from "@/features/student/components/NotificationBanner";
import AttendanceReminderBanner from "@/features/hrm/components/AttendanceReminderBanner";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import CommentHistoryList from "./CommentHistoryList";
import SessionVersionHistoryModal from "./SessionVersionHistoryModal";
import StudentNameLink from "@/features/reports/components/StudentNameLink";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";
import { formatTimeHm, toLocaleTag } from "@/lib/i18nFormat";
import Time24Input from "@/components/ui/Time24Input";

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
 * Ôn tập). Mirror ClassSession.TeacherType/Exam.TeacherType/ReviewVideoSet.VideoType (BE). Nhãn hiển
 * thị lấy qua i18n key shared.teacherType/shared.grammarChannel/shared.videoChannel — KHÔNG dùng lại
 * "Video từ kết nối"/"Video phản xạ" của Kho Video Ôn tập (LecturesPage.tsx), 2 bộ nhãn độc lập.
 */
type TeacherType = "VIETNAMESE" | "FOREIGN";

interface Row {
  studentId: number;
  studentFullName: string;
  studentCode: string;
  studentDateOfBirth: string | null;
  attitude: "" | NonNullable<StudentCommentResponse["attitude"]>;
  homeworkPreviousScore: string;
  homeworkPreviousSpeakingScore: string;
  /** V130 — chỉ dùng khi buổi teacherType=VIETNAMESE (thay cho homeworkPreviousScore/khoản "Offline" cũ, xem Javadoc StudentCommentResponse). */
  homeworkPreviousReadingScore: string;
  homeworkPreviousWritingScore: string;
  content: string;
  /** Chữ tự do (BTVN offline) — bổ sung ngoài SDD gốc, xác nhận 2026-08-18: giao ĐỒNG THỜI được với homeworkNextExerciseId (không còn loại trừ lẫn nhau). Buổi FOREIGN dùng field này; buổi VIETNAMESE (V130) dùng homeworkNextReading/homeworkNextWriting bên dưới thay thế. */
  homeworkNext: string;
  /** V130 — chỉ dùng khi buổi teacherType=VIETNAMESE. */
  homeworkNextReading: string;
  homeworkNextWriting: string;
  /**
   * V65: id của Exercise NGUỒN đã Publish (không phải id bản giao như trước V65) — chọn từ
   * grammarOptions đã lọc theo teacherType. V151 (revert V146, đã xác nhận với người dùng
   * 2026-08-25) — kênh "Ngữ pháp"/"Nghe" dùng CHUNG field này: buổi FOREIGN chọn từ listeningOptions
   * (nhãn đổi thành "Bài nghe"), buổi VIETNAMESE chọn từ grammarOptions — không còn field/cột riêng.
   */
  homeworkNextExerciseId: number | "";
  /** id của ReviewVideoSet NGUỒN đã Publish — không đổi tên qua V65 (request field vẫn nhận thẳng set id). */
  homeworkNextReviewVideoSetId: number | "";
  /** V137 — kênh "BTVN online" Reading/Writing mới, id Exercise NGUỒN skillCategory=READING/WRITING — chỉ dùng khi buổi teacherType=VIETNAMESE. */
  homeworkNextReadingExerciseId: number | "";
  homeworkNextWritingExerciseId: number | "";
  note: string;
}

/** Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — dùng cho "Lưu nháp": khác handleSend (chỉ cần content),
 *  lưu nháp chấp nhận BẤT KỲ trường nào đã điền (Thái độ/BTVN/Ghi chú...), không bắt buộc đã gõ Nhận xét. */
function rowHasAnyData(r: Row): boolean {
  return !!(
    r.content.trim() ||
    r.attitude ||
    r.homeworkPreviousScore.trim() ||
    r.homeworkPreviousSpeakingScore.trim() ||
    r.homeworkPreviousReadingScore.trim() ||
    r.homeworkPreviousWritingScore.trim() ||
    r.homeworkNext.trim() ||
    r.homeworkNextReading.trim() ||
    r.homeworkNextWriting.trim() ||
    r.homeworkNextExerciseId !== "" ||
    r.homeworkNextReviewVideoSetId !== "" ||
    r.homeworkNextReadingExerciseId !== "" ||
    r.homeworkNextWritingExerciseId !== "" ||
    r.note.trim()
  );
}

const EMPTY_ROW_HOMEWORK: Pick<Row, "homeworkNext" | "homeworkNextReading" | "homeworkNextWriting" | "homeworkNextExerciseId" | "homeworkNextReviewVideoSetId" | "homeworkNextReadingExerciseId" | "homeworkNextWritingExerciseId"> = {
  homeworkNext: "",
  homeworkNextReading: "",
  homeworkNextWriting: "",
  homeworkNextExerciseId: "",
  homeworkNextReviewVideoSetId: "",
  homeworkNextReadingExerciseId: "",
  homeworkNextWritingExerciseId: ""
};

/**
 * Bổ sung 2026-08-19, sửa bug hiển thị sai giờ hạn nộp — `StudentCommentResponse.homeworkNextDueAt`
 * là OffsetDateTime (cột DB `TIMESTAMPTZ`): Postgres/JDBC lưu đúng THỜI ĐIỂM tuyệt đối nhưng khi trả về
 * có thể mang offset KHÁC +07:00 lúc Giáo viên nhập (VD trả về dạng UTC "…T07:59:00Z" cho giờ đã nhập
 * 14:59 giờ Việt Nam — cùng 1 thời điểm, khác cách biểu diễn chuỗi). Cắt chuỗi thô
 * (`.slice(11, 16)`) đọc nhầm giờ UTC thành giờ Việt Nam. Phải parse qua `Date` rồi đọc lại theo giờ
 * LOCAL của trình duyệt (giống mọi chỗ khác trong file này đang dùng `toLocaleString` không truyền
 * `timeZone` — đã giả định trình duyệt đặt múi giờ Việt Nam) mới ra đúng ngày/giờ đã nhập.
 */
function isoToLocalDateInput(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
function isoToLocalTimeInput(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

/** Dòng chưa có dữ liệu gì (kể cả từ Excel import) — an toàn để tự điền lại từ nhận xét DRAFT/REJECTED đã có mà không đè lên nội dung giáo viên đang gõ dở. */
const isRowBlank = (r: Row) =>
  !r.content.trim() &&
  !r.attitude &&
  !r.homeworkPreviousScore.trim() &&
  !r.homeworkPreviousSpeakingScore.trim() &&
  !r.homeworkPreviousReadingScore.trim() &&
  !r.homeworkPreviousWritingScore.trim() &&
  !r.homeworkNext.trim() &&
  !r.homeworkNextReading.trim() &&
  !r.homeworkNextWriting.trim() &&
  r.homeworkNextExerciseId === "" &&
  r.homeworkNextReviewVideoSetId === "" &&
  r.homeworkNextReadingExerciseId === "" &&
  r.homeworkNextWritingExerciseId === "" &&
  !r.note.trim();

/**
 * Ô hiện "BTVN buổi trước" cho dòng ĐÃ GỬI (locked) — ưu tiên % TỰ ĐỘNG (grammarPreviousProgress/
 * videoPreviousProgress, backend tính từ exercise_attempts/review_video_progress|submissions thật —
 * xem HomeworkProgressService), chỉ fallback về giá trị nhập tay khi tự động = null (VD BTVN Ngữ pháp
 * giao Offline thì không có gì để tự tính, video luôn Online nên hầu như luôn có % tự động).
 */
function PreviousProgressCell({ auto, manual, autoLabel }: { auto: string | null; manual: string | null; autoLabel: string }) {
  if (auto) {
    return (
      <div className={`${readOnlyFieldClass} flex items-center justify-between gap-1.5`}>
        <span>{auto}</span>
        <span className="text-[9px] font-bold text-emerald-600 uppercase tracking-wide shrink-0">{autoLabel}</span>
      </div>
    );
  }
  return <div className={readOnlyFieldClass}>{manual || "—"}</div>;
}

/** UC-21 Main Flow (nhánh DAILY): viết nhận xét hàng ngày theo buổi học — cùng khuôn thao tác với Điểm danh nhanh. */
export default function DailyCommentPanel() {
  const { t, i18n } = useTranslation("academic-comments");
  const { selectedClassId, setUnsavedChanges } = useApp();
  const { classes } = useEligibleClasses();
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null);
  const [rows, setRows] = useState<Row[]>([]);
  const [loadingRows, setLoadingRows] = useState(false);
  const [history, setHistory] = useState<StudentCommentResponse[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  // V146 — % tự động "BTVN buổi trước" tính sẵn cho cả lớp, dùng khi buổi đang xem CHƯA có
  // StudentComment nào (sent undefined) để vẫn hiện được % thay vì bỏ trống, xem previewAutoProgress.
  const [autoProgress, setAutoProgress] = useState<Record<number, AutoProgressPreviewResponse>>({});
  const [sending, setSending] = useState(false);
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — "Gửi nhận xét" gộp cả 2 bước (ghi
   * DRAFT + gửi duyệt, xem Javadoc handleSend) và tự động khoá read-only mọi dòng vừa gửi ngay khi
   * xong (không sửa lại được nữa, kể cả điểm/BTVN online đã tạo bản giao thật) — trước đây bấm PHÁT
   * GỬI LUÔN không có bước xác nhận nào, dễ gửi nhầm hàng loạt (VD chưa kiểm tra kỹ % BTVN online vừa
   * điền). Hỏi lại 1 lần, nêu rõ số dòng sẽ gửi, trước khi thực sự gọi handleSend.
   */
  const [confirmingSend, setConfirmingSend] = useState(false);
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
  /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — version history kiểu Google Sheets, xem cả bảng. */
  const [showSessionHistory, setShowSessionHistory] = useState(false);
  /**
   * V150 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — nguồn khả dụng cho dropdown
   * "BTVN Ngữ pháp buổi sau" đổi từ danh sách Exercise lẻ sang danh sách "nhóm kỹ năng" (1 entry/Lesson,
   * xem HomeworkSkillGroupResponse) — chọn 1 nhóm giao TOÀN BỘ Bài Published cùng skillCategory trong
   * Lesson đó (xem HomeworkSkillBatchService), không còn chọn đúng 1 Bài như trước.
   */
  const [grammarOptions, setGrammarOptions] = useState<HomeworkSkillGroupResponse[]>([]);
  const [videoOptions, setVideoOptions] = useState<ReviewVideoSetResponse[]>([]);
  /** V137/V150: mirror grammarOptions cho kênh Reading/Writing (kênh online mới) — đã lọc sẵn skillCategory ở BE, không lọc teacherType (giống BE StudentCommentService#buildTemplate). */
  const [readingOptions, setReadingOptions] = useState<HomeworkSkillGroupResponse[]>([]);
  const [writingOptions, setWritingOptions] = useState<HomeworkSkillGroupResponse[]>([]);
  /** V150/V151: nguồn cho dropdown "Ngữ pháp"/"Bài nghe" khi buổi teacherType=FOREIGN (skillCategory=LISTENING) — dùng CHUNG 1 dropdown với grammarOptions, xem filteredGrammarOptions. */
  const [listeningOptions, setListeningOptions] = useState<HomeworkSkillGroupResponse[]>([]);
  /**
   * V65: bản giao ACTIVE hiện có của lớp — CHỈ dùng để tra ngược "comment đã lưu trước đó chọn video
   * nguồn nào" (response StudentCommentResponse chỉ trả id bản giao, không trả thẳng id nguồn), KHÔNG
   * dùng làm nguồn dropdown (đã đổi sang videoOptions ở trên). V150 — kênh Ngữ pháp/Reading/Writing/
   * Nghe không còn cần tra ngược kiểu này nữa (homeworkNext*ExerciseAssignmentId giờ tự nó đã là examId).
   */
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
  // từng dòng học sinh; offline/exerciseId ĐỘC LẬP (giao đồng thời được cả 2, xem 2026-08-18).
  const [quickOffline, setQuickOffline] = useState("");
  const [quickExerciseId, setQuickExerciseId] = useState<number | "">("");
  const [quickVideoId, setQuickVideoId] = useState<number | "">("");
  /** V130 — mirror quickOffline, chỉ dùng khi buổi teacherType=VIETNAMESE (thay quickOffline bằng 2 ô Reading/Writing). */
  const [quickReading, setQuickReading] = useState("");
  const [quickWriting, setQuickWriting] = useState("");
  /** V137 — mirror quickExerciseId/quickVideoId, kênh "BTVN online" Reading/Writing mới. */
  const [quickReadingExerciseId, setQuickReadingExerciseId] = useState<number | "">("");
  const [quickWritingExerciseId, setQuickWritingExerciseId] = useState<number | "">("");
  const [applyingDueDate, setApplyingDueDate] = useState(false);

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
  // V150 — BE (listHomeworkSkillGroupsForClass) đã lọc đúng skillCategory sẵn, ở đây chỉ còn lọc theo
  // teacherType đang chọn. V151 (revert V146, đã xác nhận với người dùng 2026-08-25) — kênh "Ngữ pháp"/
  // "Nghe" dùng CHUNG 1 dropdown: buổi FOREIGN lấy từ listeningOptions (skillCategory=LISTENING), buổi
  // VIETNAMESE lấy từ grammarOptions (skillCategory=VOCAB_GRAMMAR) — mirror đúng grammarChannelSkillCategory bên BE.
  const filteredGrammarOptions = teacherType
    ? (teacherType === "VIETNAMESE" ? grammarOptions : listeningOptions).filter((g) => g.examTeacherType === teacherType)
    : [];
  const filteredVideoOptions = teacherType ? videoOptions.filter((s) => s.teacherType === teacherType) : [];
  const grammarLabel = teacherType ? t(`shared.grammarChannel.${teacherType}`) : t("shared.grammarChannelFallback");
  const videoLabel = teacherType ? t(`shared.videoChannel.${teacherType}`) : t("shared.videoChannelFallback");
  /**
   * V130 — nhóm "BTVN buổi trước"/"BTVN" tách thêm Offline{Reading,Writing}/Online{TV+NP,TKN} CHỈ khi
   * buổi teacherType=VIETNAMESE (đã xác nhận với người dùng 2026-08-21, dựa trên 2 ảnh mẫu Excel GV
   * Việt Nam/nước ngoài) — buổi FOREIGN (hoặc chưa xác định teacherType) giữ NGUYÊN khuôn 2 dòng header
   * hiện có (chỉ gộp header "BTVN offline"+"BTVN online" cũ thành 1 header "BTVN", xem thead bên dưới).
   * Nhãn "TV+NP"/"TKN" CHỈ dùng riêng ở màn này — KHÔNG đổi grammarLabel/videoLabel dùng chung ở trên
   * (Soạn & giao đề/Kho Video Ôn tập vẫn hiện "Ngữ pháp"/"Từ Vựng (TKN)" như cũ).
   */
  const isVietnamese = teacherType === "VIETNAMESE";
  const onlineGrammarLabel = isVietnamese ? t("dailyCommentPanel.columns.onlineGrammarShort") : grammarLabel;
  const onlineVideoLabel = isVietnamese ? t("dailyCommentPanel.columns.onlineVideoShort") : videoLabel;
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
    setReadingOptions([]);
    setWritingOptions([]);
    setListeningOptions([]);
    setVideoAssignments([]);
    setSessionCommentStats({});
    if (!selectedClassId) {
      setSessions([]);
      return;
    }
    listClassSessions(selectedClassId)
      .then(setSessions)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("dailyCommentPanel.errors.loadSessionsFailed")));
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
          setNotification(
            t("dailyCommentPanel.notifications.todayNotStarted", { start: todaySessions[0].startTime, end: todaySessions[0].endTime })
          );
        } else {
          setNotification(t("dailyCommentPanel.notifications.noTodaySession"));
        }
      })
      .catch(() => undefined);
    // V150: BTVN Ngữ pháp/Reading/Writing/Nghe ONLINE giờ chọn theo "nhóm kỹ năng" (1 entry/Lesson, xem
    // HomeworkSkillGroupResponse) thay vì 1 Exercise đơn — mỗi kênh gọi đúng skillCategory cố định của
    // nó; BTVN Video Ôn tập giữ nguyên chọn từ bộ đã CÔNG BỐ (PUBLISHED).
    listHomeworkSkillGroupsForClass(selectedClassId, "VOCAB_GRAMMAR").then(setGrammarOptions).catch(() => undefined);
    listHomeworkSkillGroupsForClass(selectedClassId, "READING").then(setReadingOptions).catch(() => undefined);
    listHomeworkSkillGroupsForClass(selectedClassId, "WRITING").then(setWritingOptions).catch(() => undefined);
    listHomeworkSkillGroupsForClass(selectedClassId, "LISTENING").then(setListeningOptions).catch(() => undefined);
    listReviewVideoSetsByClass(selectedClassId)
      .then((sets) => setVideoOptions(sets.filter((s) => s.status === "PUBLISHED")))
      .catch(() => undefined);
    // Bản giao ACTIVE hiện có — chỉ để tra ngược lựa chọn đã lưu trước đó ra id nguồn khi prefill (xem loadHistory).
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
      setAutoProgress({});
      return;
    }
    setAutoProgress({});
    previewAutoProgress(selectedSessionId)
      .then((list) => setAutoProgress(Object.fromEntries(list.map((p) => [p.studentId, p]))))
      .catch(() => undefined);
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
            homeworkPreviousReadingScore: "",
            homeworkPreviousWritingScore: "",
            content: "",
            ...EMPTY_ROW_HOMEWORK,
            note: ""
          }))
        );
        return loadHistory(selectedClassId, selectedSessionId, active.map((en) => en.studentId));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("dailyCommentPanel.errors.loadStudentsFailed")))
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
      // V127 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19, sửa bug thật đã gặp: các
      // dòng CÙNG buổi hiện hạn nộp KHÁC nhau dù không ai chủ ý đổi) — ưu tiên pendingHomeworkNextDueDate
      // (hạn CHƯA giao, LocalDateTime thô không kèm offset — cắt chuỗi trực tiếp AN TOÀN, không như
      // homeworkNextDueAt là OffsetDateTime đã resolve, phải qua isoToLocalDateInput/isoToLocalTimeInput).
      // Thiếu field pending này (bug bỏ sót lúc thêm V127) khiến panel "Gán nhanh cho cả lớp" luôn hiện
      // trống dù nhiều học sinh đã có sẵn hạn nộp lưu tạm — Giáo viên tưởng chưa ai có hạn, tự gõ hạn
      // MỚI cho vài học sinh, ra 2 hạn khác nhau trong cùng buổi → 409 khi Gửi.
      const draftWithDueDate = filtered.find(
        (h) => (h.status === "DRAFT" || h.status === "REJECTED") && (h.pendingHomeworkNextDueDate || h.homeworkNextDueAt)
      );
      if (draftWithDueDate?.pendingHomeworkNextDueDate) {
        setDueDate((prev) => prev || draftWithDueDate.pendingHomeworkNextDueDate!.slice(0, 10));
        setDueTime((prev) => prev || draftWithDueDate.pendingHomeworkNextDueDate!.slice(11, 16));
      } else if (draftWithDueDate?.homeworkNextDueAt) {
        setDueDate((prev) => prev || isoToLocalDateInput(draftWithDueDate.homeworkNextDueAt!));
        setDueTime((prev) => prev || isoToLocalTimeInput(draftWithDueDate.homeworkNextDueAt!));
      }
      // Nhận xét DRAFT/REJECTED (nhập tay chưa gửi hoặc nhập từ Excel) — điền vào ô nhập trên màn hình để
      // giáo viên xem/sửa tiếp trước khi bấm "Gửi nhận xét", KHÔNG khoá read-only như PENDING/APPROVED.
      // Chỉ điền vào dòng còn trống để không đè lên nội dung đang gõ dở (đã xác nhận với người dùng 2026-07-29).
      setRows((prev) =>
        prev.map((r) => {
          const draft = filtered.find((h) => h.studentId === r.studentId && (h.status === "DRAFT" || h.status === "REJECTED"));
          if (!draft || !isRowBlank(r)) return r;
          // V127: response trả thẳng id NGUỒN (pendingHomeworkNextExerciseId/ReviewVideoSetId) cho lựa
          // chọn CHƯA Gửi — đọc trực tiếp, không cần tra ngược. V150: fallback cho dòng REJECTED CHƯA
          // sửa gì (2 field pending đã null) cũng đọc trực tiếp — homeworkNext*ExerciseAssignmentId giờ
          // TỰ nó đã là examId (Lesson) của Lô đã giao lần trước (xem Javadoc StudentCommentResponse),
          // không còn cần tra ngược qua danh sách bản giao (grammarAssignments) như trước V150.
          const exerciseId = draft.pendingHomeworkNextExerciseId ?? draft.homeworkNextExerciseAssignmentId ?? "";
          const videoSetId =
            draft.pendingHomeworkNextReviewVideoSetId ??
            (draft.homeworkNextReviewVideoAssignmentId != null
              ? videoAssignments.find((a) => a.id === draft.homeworkNextReviewVideoAssignmentId)?.reviewVideoSetId ?? ""
              : "");
          const readingExerciseId = draft.pendingHomeworkNextReadingExerciseId ?? draft.homeworkNextReadingExerciseAssignmentId ?? "";
          const writingExerciseId = draft.pendingHomeworkNextWritingExerciseId ?? draft.homeworkNextWritingExerciseAssignmentId ?? "";
          return {
            ...r,
            attitude: draft.attitude ?? "",
            // Sửa lại 2026-08-19 (đã xác nhận với người dùng, fix bug thật) — trước đây ưu tiên chèn
            // thẳng % TỰ ĐỘNG (grammarPreviousProgress/videoPreviousProgress) vào 2 ô nhập tay này khi mở
            // lại 1 nhận xét DRAFT/REJECTED, khiến cả 2 cột "Offline" và "{grammarLabel}"/"{videoLabel}"
            // hiện TRÙNG y hệt chữ tự động (VD cả 2 cùng "Chưa làm bài") — sai từ sau khi 2026-08-14 tách
            // hẳn cột Offline (chỉ nhập tay, xem PreviousProgressCell/dòng ~1244) khỏi cột tự động. Giờ
            // chỉ khôi phục đúng giá trị GIÁO VIÊN đã tự gõ trước đó — không còn tự chèn % tự động vào ô
            // nhập (ô readonly bên cạnh đã tự hiện % tự động độc lập, không cần trùng lặp ở đây).
            homeworkPreviousScore: draft.homeworkPreviousScore ?? "",
            homeworkPreviousSpeakingScore: draft.homeworkPreviousSpeakingScore ?? "",
            homeworkPreviousReadingScore: draft.homeworkPreviousReadingScore ?? "",
            homeworkPreviousWritingScore: draft.homeworkPreviousWritingScore ?? "",
            content: draft.content ?? "",
            homeworkNext: draft.homeworkNext ?? "",
            homeworkNextReading: draft.homeworkNextReading ?? "",
            homeworkNextWriting: draft.homeworkNextWriting ?? "",
            homeworkNextExerciseId: exerciseId,
            homeworkNextReviewVideoSetId: videoSetId,
            homeworkNextReadingExerciseId: readingExerciseId,
            homeworkNextWritingExerciseId: writingExerciseId,
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
      setNotification(t("dailyCommentPanel.notifications.lessonContentSaved"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("dailyCommentPanel.errors.lessonContentSaveFailed"));
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
      setNotification(t("dailyCommentPanel.notifications.actualTeacherNameSaved"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("dailyCommentPanel.errors.actualTeacherNameSaveFailed"));
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
      setError(err instanceof ApiError ? err.message : t("dailyCommentPanel.errors.teacherTypeSaveFailed"));
    } finally {
      setSavingTeacherType(false);
    }
  };

  /**
   * "Gán nhanh cho cả lớp" (2026-08-05) — áp panel gán nhanh vào mọi dòng CHƯA khoá, thay vì phải
   * chọn từng dòng. Chỉ đổi state cục bộ (chưa lưu) cho các lựa chọn Ngữ pháp/Video/Offline —
   * những dòng CHƯA gõ Nhận xét vẫn cần bấm "Lưu nháp"/"Gửi nhận xét" sau đó mới thực sự ghi DB.
   *
   * Bổ sung 2026-08-24 (xác nhận với người dùng) — RIÊNG Hạn nộp thì ghi thẳng xuống DB ngay ở đây
   * qua bulkUpdatePendingDueDate(), áp dụng cho TOÀN BỘ nhận xét NHÁP/Bị từ chối đã có sẵn của buổi
   * (kể cả những dòng chưa gõ Nhận xét nên "Lưu nháp" thường không đụng tới) — sửa đúng bug thực tế:
   * trước đây các dòng NHÁP có sẵn (VD từ 1 lần gán+lưu hàng loạt trước đó, chưa từng chọn hạn nộp
   * tường minh) âm thầm giữ hạn nộp mặc định cũ (= buổi kế tiếp), khoá cứng hạn nộp chung của buổi
   * mà không cách nào tự sửa được qua "Lưu nháp"/"Gửi nhận xét" (luôn báo xung đột 0/N — xem Javadoc
   * BE StudentCommentService#bulkUpdatePendingDueDate).
   */
  const handleApplyQuickAssign = async () => {
    const lockedIds = new Set(history.filter((h) => h.status === "PENDING" || h.status === "APPROVED").map((h) => h.studentId));
    setRows((prev) =>
      prev.map((r) =>
        lockedIds.has(r.studentId)
          ? r
          : {
              ...r,
              ...(isVietnamese
                ? {
                    homeworkNextReading: quickReading,
                    homeworkNextWriting: quickWriting,
                    homeworkNextReadingExerciseId: quickReadingExerciseId,
                    homeworkNextWritingExerciseId: quickWritingExerciseId
                  }
                : { homeworkNext: quickOffline }),
              homeworkNextExerciseId: quickExerciseId,
              homeworkNextReviewVideoSetId: quickVideoId
            }
      )
    );
    setDirty(true);

    if (dueDateTime && selectedSessionId && selectedClassId) {
      setApplyingDueDate(true);
      setError(null);
      try {
        await bulkUpdatePendingDueDate(selectedSessionId, dueDateTime);
        setNotification(t("dailyCommentPanel.notifications.dueDateAppliedSuccess"));
        await loadHistory(selectedClassId, selectedSessionId, rows.map((r) => r.studentId));
        refreshSessionCommentStats(selectedClassId);
      } catch (err) {
        setError(err instanceof ApiError ? err.message : t("dailyCommentPanel.errors.applyDueDateFailed"));
      } finally {
        setApplyingDueDate(false);
      }
    }
  };

  /** Payload dùng chung cho "Lưu nháp"/autosave/"Gửi nhận xét" — xem buildPayload+saveFilledRows bên dưới. */
  const buildCommentPayload = (r: Row) => ({
    content: r.content.trim(),
    severity: "NORMAL" as const,
    isWarning: false,
    attitude: r.attitude || undefined,
    homeworkPreviousScore: r.homeworkPreviousScore.trim() || undefined,
    homeworkPreviousSpeakingScore: r.homeworkPreviousSpeakingScore.trim() || undefined,
    // V130 — chỉ gửi khi buổi teacherType=VIETNAMESE (thay homeworkPreviousScore/khoản "Offline" cũ).
    homeworkPreviousReadingScore: r.homeworkPreviousReadingScore.trim() || undefined,
    homeworkPreviousWritingScore: r.homeworkPreviousWritingScore.trim() || undefined,
    // Bổ sung ngoài SDD gốc, xác nhận 2026-08-18 — offline (chữ tự do) và Exercise online giờ ĐỘC LẬP, gửi cả 2 nếu đã điền.
    homeworkNext: r.homeworkNext.trim() || undefined,
    // V130 — chỉ gửi khi buổi teacherType=VIETNAMESE (thay homeworkNext cũ).
    homeworkNextReading: r.homeworkNextReading.trim() || undefined,
    homeworkNextWriting: r.homeworkNextWriting.trim() || undefined,
    homeworkNextExerciseId: r.homeworkNextExerciseId !== "" ? r.homeworkNextExerciseId : undefined,
    homeworkNextReviewVideoSetId: r.homeworkNextReviewVideoSetId !== "" ? r.homeworkNextReviewVideoSetId : undefined,
    // V137 — kênh "BTVN online" Reading/Writing mới, chỉ gửi khi buổi teacherType=VIETNAMESE.
    homeworkNextReadingExerciseId: r.homeworkNextReadingExerciseId !== "" ? r.homeworkNextReadingExerciseId : undefined,
    homeworkNextWritingExerciseId: r.homeworkNextWritingExerciseId !== "" ? r.homeworkNextWritingExerciseId : undefined,
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
   * "Bài học hôm nay"/đủ học sinh — lưu được dở dang, chỉ cần có ít nhất 1 dòng có dữ liệu (xem
   * rowHasAnyData — bổ sung 2026-08-17: KHÔNG còn bắt buộc đã gõ Nhận xét, chỉ điền Thái độ/BTVN/Ghi
   * chú vẫn lưu được, khác `handleSend` "Gửi nhận xét" vẫn cần content).
   *
   * Bổ sung 2026-08-19, đã xác nhận với người dùng — BỎ autosave (từng tự gọi hàm này ngầm 18s sau lần
   * gõ cuối): autosave chạy song song với "Gửi nhận xét" là NGUYÊN NHÂN chính gây race tạo trùng
   * StudentComment cho cùng 1 (buổi, học sinh) — writeComment() ở BE không tự chặn trùng (xem Javadoc
   * writeComment ở StudentCommentService), khiến bấm "Gửi nhận xét" phải bấm 2 lần mới thấy "Đã gửi hết
   * nhận xét buổi này". Giờ CHỈ còn 2 điểm lưu tường minh do giáo viên chủ động bấm: "Lưu nháp" (hàm
   * này) và "Gửi nhận xét" (handleSend) — không còn hàm nào tự gọi ngầm nữa.
   *
   * Trả về UnsavedSaveResult (bổ sung 2026-08-17, sửa bug mất dữ liệu âm thầm khi bấm "Lưu tạm & rời
   * đi" ở Sidebar) — ok=true CHỈ khi thực sự lưu xong hết (an toàn để điều hướng đi); ok=false kèm
   * message cụ thể nếu không lưu được gì hoặc lưu lỗi/lỗi từng phần — Sidebar hiện message này thẳng
   * trong popup xác nhận. Trước đây luôn coi như thành công (không trả gì, Sidebar cứ điều hướng đi
   * bất kể), khiến dữ liệu dở bị mất mà người dùng tưởng đã lưu.
   */
  const handleSaveDraft = async (): Promise<UnsavedSaveResult> => {
    if (!selectedClassId || !selectedSession) return { ok: true };
    // Chặn bấm chồng (VD double-click nhanh trước khi React kịp render lại nút disabled) — cùng cơ chế
    // idempotent với handleSend bên dưới, phòng race tạo trùng StudentComment (2026-08-19).
    if (savingDraft) return { ok: false, message: t("dailyCommentPanel.errors.savingDraftInProgress") };
    const filled = rows.filter(rowHasAnyData);
    if (filled.length === 0) {
      const message = t("dailyCommentPanel.errors.noDataToSave");
      setError(message);
      return { ok: false, message };
    }
    setSavingDraft(true);
    setError(null);
    try {
      const results = await saveFilledRows(filled, selectedClassId, selectedSession);
      const failed = results
        .map((r, i) => ({ result: r, row: filled[i] }))
        .filter((x): x is { result: PromiseRejectedResult; row: Row } => x.result.status === "rejected");
      const failedCount = failed.length;
      // Lộ ĐÚNG lý do lỗi thật của học sinh đầu tiên thất bại (bổ sung 2026-08-17) — trước đây chỉ đếm
      // số lượng, vứt bỏ nội dung lỗi thật (reason của Promise.allSettled), khiến không biết vì sao lỗi.
      const firstFailedReason = failed[0]?.result.reason;
      const firstFailedMessage = firstFailedReason instanceof ApiError ? firstFailedReason.message : t("dailyCommentPanel.errors.unknownReason");
      const extraStudents = failedCount > 1 ? t("dailyCommentPanel.notifications.extraStudents", { count: failedCount - 1 }) : "";
      setDirty(false);
      setLastSavedAt(new Date());
      setNotification(
        failedCount > 0
          ? t("dailyCommentPanel.notifications.draftSavedFailurePart", {
              saved: filled.length - failedCount,
              total: filled.length,
              studentName: failed[0].row.studentFullName,
              extra: extraStudents,
              reason: firstFailedMessage
            })
          : t("dailyCommentPanel.notifications.draftSavedSuccess", { count: filled.length })
      );
      await loadHistory(selectedClassId, selectedSession.id, rows.map((r) => r.studentId));
      refreshSessionCommentStats(selectedClassId);
      listReviewVideoAssignmentsForClass(selectedClassId).then(setVideoAssignments).catch(() => undefined);
      return failedCount > 0
        ? {
            ok: false,
            message: t("dailyCommentPanel.notifications.draftSavedFailurePart", {
              saved: filled.length - failedCount,
              total: filled.length,
              studentName: failed[0].row.studentFullName,
              extra: extraStudents,
              reason: firstFailedMessage
            })
          }
        : { ok: true };
    } catch (err) {
      const message = err instanceof ApiError ? err.message : t("dailyCommentPanel.errors.saveDraftFailed");
      setError(message);
      return { ok: false, message };
    } finally {
      setSavingDraft(false);
    }
  };

  // Cảnh báo rời trang khi dữ liệu chưa lưu (2026-08-15, bổ sung ngoài SDD gốc, theo yêu cầu người
  // dùng) — 2 kênh: (1) đóng tab/reload trình duyệt qua beforeunload (nội dung hộp thoại do trình duyệt
  // tự quyết, không tùy biến được — mọi trình duyệt hiện đại đều vậy); (2) điều hướng TRONG app (bấm
  // mục khác ở Sidebar) qua AppContext.setUnsavedChanges — Sidebar sẽ chặn điều hướng + hỏi "Lưu tạm
  // trước khi rời đi?" khi cờ này bật. Không còn kênh autosave ngầm (bỏ 2026-08-19) — giáo viên phải tự
  // bấm "Lưu nháp" hoặc chọn "Lưu tạm & rời đi" ở popup này.
  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (!dirty) return;
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [dirty]);

  // handleSaveDraft đổi theo từng lần gõ phím (đóng rows/selectedSession trong closure) — giữ bản mới
  // nhất qua ref để hàm đăng ký với AppContext (có thể được Sidebar gọi rất lâu sau khi effect dưới
  // chạy lần cuối, VD gõ thêm nhiều dòng sau khi dirty đã lên true) luôn lưu đúng dữ liệu hiện tại,
  // không lưu nhầm bản cũ tại thời điểm dirty vừa bật.
  const handleSaveDraftRef = useRef(handleSaveDraft);
  handleSaveDraftRef.current = handleSaveDraft;

  useEffect(() => {
    // Bấm "Lưu tạm & rời đi" là hành động chủ động, nếu không lưu được phải hiện lỗi cụ thể ngay trên
    // trang (Sidebar sẽ ở lại trang khi hàm này trả false).
    setUnsavedChanges(dirty, dirty ? () => handleSaveDraftRef.current() : null);
    return () => setUnsavedChanges(false, null);
  }, [dirty]);

  const handleSend = async () => {
    if (!selectedClassId || !selectedSession) return;
    // Chặn bấm chồng (VD double-click nhanh trước khi React kịp render lại nút disabled) — cùng lý do
    // với guard ở đầu handleSaveDraft, phòng gọi saveFilledRows 2 lần chồng nhau sinh trùng bản ghi (2026-08-19).
    if (sending) return;
    if (!selectedSession.lessonContent?.trim()) {
      setLessonContentMissingError(true);
      lessonContentInputRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
      lessonContentInputRef.current?.focus();
      return;
    }
    const filled = rows.filter((r) => r.content.trim());
    if (filled.length === 0) {
      setError(t("dailyCommentPanel.errors.enterAtLeastOneComment"));
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
        const msg = r.reason instanceof ApiError ? r.reason.message : t("dailyCommentPanel.errors.unknownError");
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
          submitFailedMessage = err instanceof ApiError ? err.message : t("dailyCommentPanel.errors.submitFailed");
        }
      }

      let message = submitFailedMessage
        ? t("dailyCommentPanel.notifications.sentButSubmitFailed", { count: succeededIds.length, reason: submitFailedMessage })
        : t("dailyCommentPanel.notifications.sentSuccess", { count: succeededIds.length });
      if (failedCount > 0) {
        failedByMessage.forEach((students, msg) => {
          message += `\n- ${msg} (${students.join(", ")})`;
        });
      }
      setNotification(message);
      setRows((prev) =>
        prev.map((r) =>
          r.content.trim() ? { ...r, attitude: "", homeworkPreviousScore: "", homeworkPreviousSpeakingScore: "", homeworkPreviousReadingScore: "", homeworkPreviousWritingScore: "", content: "", ...EMPTY_ROW_HOMEWORK, note: "" } : r
        )
      );
      setDirty(false);
      setLastSavedAt(new Date());
      await loadHistory(selectedClassId, selectedSession.id, rows.map((r) => r.studentId));
      refreshSessionCommentStats(selectedClassId);
      // Gửi xong có thể vừa tạo bản giao mới (chọn đề/video Online) — tải lại map tra ngược để lần sửa kế tiếp resolve đúng.
      listReviewVideoAssignmentsForClass(selectedClassId).then(setVideoAssignments).catch(() => undefined);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("dailyCommentPanel.errors.sendFailed"));
    } finally {
      setSending(false);
    }
  };

  /** UC-21 (2026-07-29): học sinh chỉ nhận xét DAILY được 1 lần/buổi — bấm vào dòng đã có nhận xét thì báo rõ thay vì im lặng khoá ô. */
  const notifyAlreadySent = (r: Row, sent: StudentCommentResponse) => {
    setNotification(t("dailyCommentPanel.notifications.alreadySent", { name: r.studentFullName, status: t(`shared.status.${sent.status}`) }));
  };

  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-20 — buildTemplate() ở BE đọc thẳng từ DB
   * (student_comments đã lưu), không biết gì về `rows` đang gõ dở trên UI. Từ khi bỏ autosave
   * (2026-08-19, xem handleSaveDraft), gõ xong bấm "Tải mẫu Excel" ngay mà chưa bấm "Lưu nháp" sẽ ra
   * file THIẾU đúng dữ liệu vừa gõ. Tự gọi handleSaveDraft() trước khi tải nếu đang dirty (có dữ liệu
   * chưa lưu) — gộp "Lưu nháp" + "Tải mẫu Excel" thành 1 lần bấm, Excel luôn khớp UI. Lỗi lưu thì DỪNG
   * hẳn, không tải file (tránh tải nhầm bản DB cũ trong khi tưởng đã có dữ liệu mới).
   */
  const handleDownloadTemplate = async () => {
    if (!selectedSessionId) return;
    setDownloadingTemplate(true);
    setError(null);
    try {
      if (dirty && rows.some(rowHasAnyData)) {
        const saveResult = await handleSaveDraft();
        if (!saveResult.ok) {
          setError(t("dailyCommentPanel.errors.saveDraftBeforeDownloadFailed", { reason: saveResult.message }));
          return;
        }
      }
      const blob = await downloadDailyCommentTemplate(selectedSessionId);
      // Sửa 2026-08-19 (đã xác nhận với người dùng, fix bug thật): trước đây đặt tên file theo
      // selectedSessionId (id kỹ thuật trong DB, VD 27) — không khớp "Buổi 7" giáo viên thấy trên dropdown
      // ngay phía trên, gây nhầm lẫn. Đổi sang sessionNumber (đúng số buổi hiển thị trên UI) + ngày buổi học
      // (yêu cầu format mau-nhan-xet-buoi-(...)-(ngày).xlsx — origin/develop có 1 fix độc lập cho cùng bug
      // này qua PR #244 nhưng KHÔNG kèm ngày, giữ lại bản đầy đủ hơn ở đây khi merge 2026-08-19).
      const fileSuffix = selectedSession ? `${selectedSession.sessionNumber}-${selectedSession.sessionDate}` : selectedSessionId;
      downloadBlob(blob, `mau-nhan-xet-buoi-${fileSuffix}.xlsx`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("dailyCommentPanel.errors.downloadTemplateFailed"));
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
      setError(t("dailyCommentPanel.errors.xlsxOnly"));
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
              homeworkPreviousReadingScore: parsed.homeworkPreviousReadingScore ?? "",
              homeworkPreviousWritingScore: parsed.homeworkPreviousWritingScore ?? "",
              content: parsed.content ?? "",
              homeworkNext: parsed.homeworkNext ?? "",
              homeworkNextReading: parsed.homeworkNextReading ?? "",
              homeworkNextWriting: parsed.homeworkNextWriting ?? "",
              homeworkNextExerciseId: parsed.homeworkNextExerciseId ?? "",
              homeworkNextReviewVideoSetId: parsed.homeworkNextReviewVideoSetId ?? "",
              homeworkNextReadingExerciseId: parsed.homeworkNextReadingExerciseId ?? "",
              homeworkNextWritingExerciseId: parsed.homeworkNextWritingExerciseId ?? "",
              note: parsed.note ?? ""
            };
          })
        );
        setDirty(true);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("dailyCommentPanel.errors.importFailed"));
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
            <span className="text-xs font-bold text-slate-700 font-display">{t("dailyCommentPanel.title")}</span>
            <p className="text-[10px] text-slate-400 mt-0.5">
              {selectedClass ? `${selectedClass.name} (${selectedClass.classCode})` : t("dailyCommentPanel.noClassSelected")}
            </p>
          </div>
          {selectedClass && (
            <div className="flex flex-wrap items-center gap-2">
              <Select
                value={selectedSessionId ?? ""}
                onChange={(e) => setSelectedSessionId(e.target.value ? Number(e.target.value) : null)}
                className="bg-white border text-[10px] font-bold text-slate-700 px-2 py-1 rounded focus:outline-none"
              >
                <option value="">{t("dailyCommentPanel.sessionSelectPlaceholder")}</option>
                {selectableSessions.map((s) => {
                  const status = getSessionCommentStatus(s.id);
                  const stat = sessionCommentStats[s.id];
                  return (
                    <option key={s.id} value={s.id}>
                      {status === "DONE" ? "✓ " : status === "PARTIAL" ? "◐ " : ""}
                      {t("dailyCommentPanel.sessionOptionLabel", { number: s.sessionNumber, date: s.sessionDate, start: s.startTime, end: s.endTime })}
                      {status === "PARTIAL" && stat ? t("dailyCommentPanel.sessionOptionPartialCount", { commented: stat.commented, total: stat.total }) : ""}
                    </option>
                  );
                })}
              </Select>
              {/* "Loại giáo viên" (2026-08-05) — ăn theo để lọc/đổi nhãn BTVN buổi sau (Ngữ pháp/Bài nghe, Từ Vựng (TKN)/Clip phản xạ). */}
              {selectedSessionId && (
                <div className="flex items-center rounded-lg border border-slate-200 bg-white overflow-hidden shrink-0">
                  {(["VIETNAMESE", "FOREIGN"] as TeacherType[]).map((type) => (
                    <button
                      key={type}
                      type="button"
                      disabled={savingTeacherType || sessionHasSentComment}
                      title={sessionHasSentComment ? t("dailyCommentPanel.teacherTypeLockedTitle") : undefined}
                      onClick={() => handleChangeTeacherType(type)}
                      className={`px-2.5 py-1 text-[10px] font-bold whitespace-nowrap disabled:opacity-50 disabled:cursor-not-allowed ${
                        teacherType === type ? "bg-brand-orange text-white" : "text-slate-600 hover:bg-slate-50"
                      }`}
                    >
                      {t(`shared.teacherType.${type}`)}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        {selectedSessionId && !teacherType && (
          <div className="px-5 py-2.5 border-b border-slate-100 bg-amber-50 text-[11px] text-amber-700">
            {t("dailyCommentPanel.teacherTypeMissingWarning")}
          </div>
        )}

        {selectedSessionId && sessionHasSentComment && (
          <div className="px-5 py-2.5 border-b border-slate-100 bg-slate-50 text-[11px] text-slate-500">
            {t("dailyCommentPanel.sessionLockedNotice")}
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
              <label className="text-[11px] font-bold text-slate-600 shrink-0">{t("dailyCommentPanel.lessonContentLabel")}</label>
              <input
                ref={lessonContentInputRef}
                value={lessonContentInput}
                disabled={sessionHasSentComment}
                onChange={(e) => {
                  setLessonContentInput(e.target.value);
                  setLessonContentMissingError(false);
                }}
                placeholder={t("dailyCommentPanel.lessonContentPlaceholder")}
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
                {savingLessonContent ? t("dailyCommentPanel.savingButton") : t("dailyCommentPanel.saveButton")}
              </button>
              <label className="text-[11px] font-bold text-slate-600 shrink-0">{t("dailyCommentPanel.actualTeacherNameLabel")}</label>
              <input
                value={actualTeacherNameInput}
                disabled={sessionHasSentComment}
                onChange={(e) => setActualTeacherNameInput(e.target.value)}
                placeholder={t("dailyCommentPanel.actualTeacherNamePlaceholder")}
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
                {savingActualTeacherName ? t("dailyCommentPanel.savingButton") : t("dailyCommentPanel.saveButton")}
              </button>
            </div>
            {lessonContentMissingError ? (
              <p className="mt-1.5 text-[10px] text-rose-600 font-bold">
                {t("dailyCommentPanel.lessonContentMissingError")}
              </p>
            ) : (
              !selectedSession?.lessonContent && (
                <p className="mt-1.5 text-[10px] text-amber-700 italic">
                  {t("dailyCommentPanel.lessonContentMissingHint")}
                </p>
              )
            )}
          </div>
        )}

        {selectedSessionId && blockOnlineHomework && (
          <div className="px-5 py-2.5 border-b border-slate-100 bg-rose-50/60 text-[11px] text-rose-700">
            {t("dailyCommentPanel.blockOnlineHomeworkWarning", { grammarLabel, videoLabel })}
          </div>
        )}

        {selectedSessionId && teacherType && (
          <div className="px-5 py-3 border-b border-slate-100 bg-orange-50/40 space-y-2">
            <span className="text-[10px] font-bold uppercase text-slate-500">
              {t("dailyCommentPanel.quickAssign.title")}
            </span>
            <div className="flex flex-wrap items-end gap-2">
              {isVietnamese ? (
                <>
                  <div className="min-w-[140px]">
                    <label className="text-[9px] font-bold uppercase text-slate-400 block mb-0.5">{t("dailyCommentPanel.quickAssign.readingLabel")}</label>
                    <input
                      value={quickReading}
                      onChange={(e) => setQuickReading(e.target.value)}
                      placeholder={t("dailyCommentPanel.quickAssign.offlinePlaceholder")}
                      className="w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                    />
                  </div>
                  <div className="min-w-[140px]">
                    <label className="text-[9px] font-bold uppercase text-slate-400 block mb-0.5">{t("dailyCommentPanel.quickAssign.writingLabel")}</label>
                    <input
                      value={quickWriting}
                      onChange={(e) => setQuickWriting(e.target.value)}
                      placeholder={t("dailyCommentPanel.quickAssign.offlinePlaceholder")}
                      className="w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                    />
                  </div>
                  {/* V137 — kênh "BTVN online" Reading/Writing mới, mirror ô Ngữ pháp/Video TKN bên dưới. */}
                  <div className="min-w-[200px]">
                    <label className="text-[9px] font-bold uppercase text-slate-400 block mb-0.5">{t("dailyCommentPanel.quickAssign.onlineReadingLabel")}</label>
                    <Select
                      value={quickReadingExerciseId}
                      disabled={blockOnlineHomework}
                      onChange={(e) => setQuickReadingExerciseId(e.target.value ? Number(e.target.value) : "")}
                      className="w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-40"
                    >
                      <option value="">{t("dailyCommentPanel.quickAssign.noAssign")}</option>
                      {readingOptions.map((ex) => (
                        <option key={ex.examId} value={ex.examId}>
                          {/* Bổ sung 2026-09-04 (đã xác nhận với người dùng) — fix bug thật: Lesson đánh số lặp
                              lại (Lesson 1, 2, 3...) giữa nhiều Unit/SubTopic khác nhau, trước đây dropdown chỉ
                              hiện examTitle nên giáo viên rất dễ giao NHẦM Lesson. */}
                          {ex.examCode} - {ex.examTitle}
                          {(ex.unitTitle || ex.subTopicTitle) && ` [${[ex.unitTitle, ex.subTopicTitle].filter(Boolean).join(" · ")}]`} (
                          {ex.exerciseCount} bài, {ex.questionCount} câu)
                        </option>
                      ))}
                    </Select>
                  </div>
                  <div className="min-w-[200px]">
                    <label className="text-[9px] font-bold uppercase text-slate-400 block mb-0.5">{t("dailyCommentPanel.quickAssign.onlineWritingLabel")}</label>
                    <Select
                      value={quickWritingExerciseId}
                      disabled={blockOnlineHomework}
                      onChange={(e) => setQuickWritingExerciseId(e.target.value ? Number(e.target.value) : "")}
                      className="w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-40"
                    >
                      <option value="">{t("dailyCommentPanel.quickAssign.noAssign")}</option>
                      {writingOptions.map((ex) => (
                        <option key={ex.examId} value={ex.examId}>
                          {/* Bổ sung 2026-09-04 (đã xác nhận với người dùng) — fix bug thật: Lesson đánh số lặp
                              lại (Lesson 1, 2, 3...) giữa nhiều Unit/SubTopic khác nhau, trước đây dropdown chỉ
                              hiện examTitle nên giáo viên rất dễ giao NHẦM Lesson. */}
                          {ex.examCode} - {ex.examTitle}
                          {(ex.unitTitle || ex.subTopicTitle) && ` [${[ex.unitTitle, ex.subTopicTitle].filter(Boolean).join(" · ")}]`} (
                          {ex.exerciseCount} bài, {ex.questionCount} câu)
                        </option>
                      ))}
                    </Select>
                  </div>
                </>
              ) : (
                <>
                  <div className="min-w-[160px]">
                    <label className="text-[9px] font-bold uppercase text-slate-400 block mb-0.5">{t("dailyCommentPanel.quickAssign.offlineLabel")}</label>
                    <input
                      value={quickOffline}
                      onChange={(e) => setQuickOffline(e.target.value)}
                      placeholder={t("dailyCommentPanel.quickAssign.offlinePlaceholder")}
                      className="w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                    />
                  </div>
                </>
              )}
              <div className="min-w-[200px]">
                <label className="text-[9px] font-bold uppercase text-slate-400 block mb-0.5">
                  {t("dailyCommentPanel.quickAssign.onlineGrammarLabel", { grammarLabel: onlineGrammarLabel })}
                </label>
                <Select
                  value={quickExerciseId}
                  disabled={blockOnlineHomework}
                  onChange={(e) => setQuickExerciseId(e.target.value ? Number(e.target.value) : "")}
                  className="w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-40"
                >
                  <option value="">{t("dailyCommentPanel.quickAssign.noAssign")}</option>
                  {filteredGrammarOptions.map((ex) => (
                    <option key={ex.examId} value={ex.examId}>
                      {ex.examCode} - {ex.examTitle} ({ex.exerciseCount} bài, {ex.questionCount} câu)
                    </option>
                  ))}
                </Select>
              </div>
              <div className="min-w-[200px]">
                <label className="text-[9px] font-bold uppercase text-slate-400 block mb-0.5">
                  {t("dailyCommentPanel.quickAssign.onlineVideoLabel", { videoLabel: onlineVideoLabel })}
                </label>
                <Select
                  value={quickVideoId}
                  disabled={blockOnlineHomework}
                  onChange={(e) => setQuickVideoId(e.target.value ? Number(e.target.value) : "")}
                  className="w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-40"
                >
                  <option value="">{t("dailyCommentPanel.quickAssign.noAssign")}</option>
                  {filteredVideoOptions.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.title} ({s.code})
                    </option>
                  ))}
                </Select>
              </div>
              <div className="min-w-[150px]">
                <label className="text-[9px] font-bold uppercase text-slate-400 block mb-0.5">{t("dailyCommentPanel.quickAssign.dueDateLabel")}</label>
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
                <label className="text-[9px] font-bold uppercase text-slate-400 block mb-0.5">{t("dailyCommentPanel.quickAssign.dueTimeLabel")}</label>
                <Time24Input
                  value={dueTime}
                  disabled={!dueDate}
                  onChange={setDueTime}
                  className="w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-40"
                />
              </div>
              <button
                type="button"
                onClick={handleApplyQuickAssign}
                disabled={
                  applyingDueDate ||
                  (isVietnamese
                    ? !quickReading &&
                      !quickWriting &&
                      quickExerciseId === "" &&
                      quickVideoId === "" &&
                      quickReadingExerciseId === "" &&
                      quickWritingExerciseId === "" &&
                      !dueDateTime
                    : !quickOffline && quickExerciseId === "" && quickVideoId === "" && !dueDateTime)
                }
                className="px-3 py-2 bg-brand-orange hover:bg-brand-orange/90 text-white text-[11px] font-bold rounded-lg disabled:opacity-40"
              >
                {applyingDueDate ? t("dailyCommentPanel.quickAssign.applying") : t("dailyCommentPanel.quickAssign.applyButton")}
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
              {downloadingTemplate ? t("dailyCommentPanel.downloadingTemplate") : t("dailyCommentPanel.downloadTemplateButton")}
            </button>
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={importing}
              className="flex items-center gap-1.5 border-2 border-dashed border-slate-200 rounded-lg px-3 py-2 text-[11px] font-semibold text-slate-600 hover:border-brand-orange hover:bg-orange-50/30 disabled:opacity-50"
            >
              <UploadCloud className="w-3.5 h-3.5 text-brand-orange" />
              {importing ? t("dailyCommentPanel.importing") : t("dailyCommentPanel.importExcelButton")}
            </button>
            <input ref={fileInputRef} type="file" accept=".xlsx" className="hidden" onChange={(e) => handleImportFile(e.target.files?.[0] ?? null)} />
            {/* Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — version history kiểu Google
                Sheets: 1 nút xem lại TOÀN BỘ bảng (mọi học sinh buổi này) tại 1 mốc thời gian trong quá
                khứ, không phải xem riêng từng dòng (xem CommentVersionHistoryModal ở "Lịch sử nhận xét
                buổi này" bên dưới cho case xem theo 1 học sinh). */}
            <button
              type="button"
              onClick={() => setShowSessionHistory(true)}
              className="flex items-center gap-1.5 border border-dashed border-slate-300 rounded-lg px-3 py-2 text-[11px] font-semibold text-slate-600 hover:bg-white"
            >
              <History className="w-3.5 h-3.5" />
              {t("dailyCommentPanel.versionHistoryButton")}
            </button>

            {/* "Lưu nháp" (2026-08-14) — phòng giáo viên vô tình thoát khi chưa "Gửi nhận xét" (chỉ ghi
                DRAFT, không gửi duyệt). Cùng hàng với 2 nút Excel nhưng đẩy sang PHẢI (ml-auto) — tách
                nhóm "nhập liệu" (trái) khỏi nhóm "lưu" (phải) theo đúng yêu cầu. */}
            <div className="ml-auto flex items-center gap-2">
              {lastSavedAt && (
                <span className="text-[10px] text-slate-400">
                  {t("dailyCommentPanel.savedAt", { time: formatTimeHm(lastSavedAt.toISOString(), i18n.language) })}
                </span>
              )}
              <button
                type="button"
                onClick={() => handleSaveDraft()}
                // Khớp đúng điều kiện thật của handleSaveDraft (rowHasAnyData — Thái độ/BTVN/Ghi chú
                // cũng lưu được, không bắt buộc đã gõ Nhận xét) — trước đây yêu cầu content khiến nút
                // bị disable sai dù đã điền dữ liệu khác, không bấm "Lưu nháp" được (sửa 2026-08-19).
                disabled={savingDraft || !rows.some(rowHasAnyData)}
                className="flex items-center gap-1.5 border border-dashed border-slate-300 rounded-lg px-3 py-2 text-[11px] font-semibold text-slate-600 hover:bg-white disabled:opacity-50"
              >
                <Save className="w-3.5 h-3.5" />
                {savingDraft ? t("dailyCommentPanel.savingDraft") : t("dailyCommentPanel.saveDraftButton")}
              </button>
            </div>

            {importResult && (
              <div className="w-full flex flex-wrap items-center gap-2 text-[11px] mt-1">
                <span className="bg-slate-100 border border-slate-200 text-slate-700 font-semibold px-2 py-1 rounded-lg">
                  {t("dailyCommentPanel.importResult.total", { count: importResult.totalRows ?? "—" })}
                </span>
                <span className="bg-emerald-50 border border-emerald-100 text-emerald-600 font-semibold px-2 py-1 rounded-lg">
                  {t("dailyCommentPanel.importResult.success", { count: importResult.successRows })}
                </span>
                <span className="bg-rose-50 border border-rose-100 text-rose-600 font-semibold px-2 py-1 rounded-lg">
                  {t("dailyCommentPanel.importResult.failed", { count: importResult.failedRows })}
                </span>
                {importResult.errorSummary.length > 0 && (
                  <div className="w-full border border-rose-100 rounded-lg overflow-hidden mt-1">
                    <div className="max-h-40 overflow-y-auto divide-y divide-slate-100">
                      {importResult.errorSummary.map((e, i) => (
                        <div key={i} className="px-3 py-1.5 flex gap-2 bg-white">
                          <span className="font-mono font-bold text-slate-400 shrink-0">{t("dailyCommentPanel.importResult.row", { row: e.row })}</span>
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
          {/* w-full — bảng <table> mặc định co theo nội dung (auto width), không tự giãn hết chiều
              rộng vùng chứa, để lại khoảng trắng thừa bên phải khi vùng chứa rộng hơn tổng các cột.
              table-layout vẫn giữ "auto" (không đổi sang "fixed") nên không ảnh hưởng cơ chế khoá
              width 3 cột sticky ở trên — w-full chỉ đặt SÀN 100% cho tổng bề rộng bảng, cột không
              sticky (đặc biệt "Ghi chú" cuối bảng) giãn ra hấp thụ phần dư. */}
          <table className="w-full text-xs text-left border-separate border-spacing-0">
          {/* sticky trực tiếp trên <thead> (thay vì tính top offset riêng cho từng <tr>) — trình duyệt tự
              ghim NGUYÊN CẢ 2 dòng header làm 1 khối, không cần đoán chiều cao dòng 1 để lệch dòng 2. 3 ô
              góc (Mã học viên/Họ và tên/Ngày sinh) cần thêm sticky left riêng (trục ngang, độc lập với
              trục dọc đã ghim ở thead) để vừa dính trên vừa dính trái khi cuộn cả 2 chiều. */}
          <thead className="sticky top-0 z-20 bg-slate-50">
            {/* Border rõ giữa các cột/dòng header (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
                2026-08-06) — Th mặc định không có border, bảng nhóm cột (BTVN buổi trước/online) khó
                phân biệt ranh giới nếu không kẻ thêm. */}
            {/*
              V130 (đã xác nhận với người dùng 2026-08-21, dựa trên 2 ảnh mẫu Excel GV Việt Nam/nước
              ngoài): buổi teacherType=VIETNAMESE dùng header 3 CẤP — "BTVN buổi trước"/"BTVN" (cấp 1) >
              Offline/Online (cấp 2) > Reading/Writing/TV+NP/TKN (cấp 3, Offline tách 2 field mới
              thay vì 1 field gộp, Online giữ nguyên field/chức năng chỉ đổi nhãn). Buổi FOREIGN (hoặc
              chưa xác định teacherType) giữ khuôn 2 CẤP cũ — chỉ gộp header "BTVN offline"+"BTVN online"
              cũ (2 <Th> tách rời) thành 1 header "BTVN" (colSpan=3, mirror ảnh mẫu Excel GV nước ngoài),
              không đổi field/vị trí cột nào khác.
            */}
            <tr className="border-b border-slate-300 [&>th]:text-center">
              <Th rowSpan={isVietnamese ? 3 : 2} style={STICKY_COL_STYLE[0]} className="sticky left-0 z-30 bg-slate-50 border-r border-b border-slate-300">{t("dailyCommentPanel.columns.studentCode")}</Th>
              <Th rowSpan={isVietnamese ? 3 : 2} style={STICKY_COL_STYLE[1]} className="sticky z-30 bg-slate-50 border-r border-b border-slate-300">{t("dailyCommentPanel.columns.fullName")}</Th>
              <Th rowSpan={isVietnamese ? 3 : 2} style={STICKY_COL_STYLE[2]} className="sticky z-30 bg-slate-50 border-r border-b border-slate-300">{t("dailyCommentPanel.columns.dateOfBirth")}</Th>
              <Th colSpan={isVietnamese ? 6 : 3} className="text-center border-r border-b border-slate-300">{t("dailyCommentPanel.columns.homeworkPrevious")}</Th>
              <Th colSpan={isVietnamese ? 6 : 3} className="text-center border-r border-b border-slate-300">{t("dailyCommentPanel.columns.homeworkNextGroup")}</Th>
              <Th rowSpan={isVietnamese ? 3 : 2} className="border-r border-b border-slate-300">{t("dailyCommentPanel.columns.dueDate")}</Th>
              <Th rowSpan={isVietnamese ? 3 : 2} className="border-r border-b border-slate-300">{t("dailyCommentPanel.columns.attitude")}</Th>
              <Th rowSpan={isVietnamese ? 3 : 2} className="border-r border-b border-slate-300">{t("dailyCommentPanel.columns.studentComment")}</Th>
              <Th rowSpan={isVietnamese ? 3 : 2} className="border-b border-slate-300">{t("dailyCommentPanel.columns.note")}</Th>
            </tr>
            {isVietnamese ? (
              <>
                <tr className="border-b border-slate-300 [&>th]:text-center">
                  <Th colSpan={2} className="border-r border-b border-slate-300 text-center">{t("dailyCommentPanel.columns.offline")}</Th>
                  <Th colSpan={4} className="border-r border-b border-slate-300 text-center">{t("dailyCommentPanel.columns.online")}</Th>
                  <Th colSpan={2} className="border-r border-b border-slate-300 text-center">{t("dailyCommentPanel.columns.offline")}</Th>
                  <Th colSpan={4} className="border-r border-b border-slate-300 text-center">{t("dailyCommentPanel.columns.online")}</Th>
                </tr>
                <tr className="border-b border-slate-300 [&>th]:text-center">
                  <Th className="border-r border-b border-slate-300 text-center">{t("dailyCommentPanel.columns.reading")}</Th>
                  <Th className="border-r border-b border-slate-300 text-center">{t("dailyCommentPanel.columns.writing")}</Th>
                  {/* V137 — kênh "BTVN online" mới: Reading/Writing (giao Exercise skillCategory=READING/WRITING), thêm 2 cột trước Ngữ pháp/Video TKN cũ. */}
                  <Th className="border-r border-b border-slate-300 text-center">{t("dailyCommentPanel.columns.reading")}</Th>
                  <Th className="border-r border-b border-slate-300 text-center">{t("dailyCommentPanel.columns.writing")}</Th>
                  <Th className="border-r border-b border-slate-300 text-center">{onlineGrammarLabel}</Th>
                  <Th className="border-r border-b border-slate-300 text-center">{onlineVideoLabel}</Th>
                  <Th className="border-r border-b border-slate-300 text-center">{t("dailyCommentPanel.columns.reading")}</Th>
                  <Th className="border-r border-b border-slate-300 text-center">{t("dailyCommentPanel.columns.writing")}</Th>
                  <Th className="border-r border-b border-slate-300 text-center">{t("dailyCommentPanel.columns.reading")}</Th>
                  <Th className="border-r border-b border-slate-300 text-center">{t("dailyCommentPanel.columns.writing")}</Th>
                  <Th className="border-r border-b border-slate-300 text-center">{onlineGrammarLabel}</Th>
                  <Th className="border-r border-b border-slate-300 text-center">{onlineVideoLabel}</Th>
                </tr>
              </>
            ) : (
              <tr className="border-b border-slate-300 [&>th]:text-center">
                <Th className="border-r border-b border-slate-300 text-center">{t("dailyCommentPanel.columns.offline")}</Th>
                <Th className="border-r border-b border-slate-300 text-center">{grammarLabel}</Th>
                <Th className="border-r border-b border-slate-300 text-center">{videoLabel}</Th>
                <Th className="border-r border-b border-slate-300 text-center">{t("dailyCommentPanel.columns.offline")}</Th>
                <Th className="border-r border-b border-slate-300 text-center">{grammarLabel}</Th>
                <Th className="border-r border-b border-slate-300 text-center">{videoLabel}</Th>
              </tr>
            )}
          </thead>
          {/* KHÔNG dùng divide-y (border-top trên <tr>) — bảng dùng border-separate (bắt buộc cho sticky
              ở trên) nên border khai trực tiếp trên <tr> không render được; mọi viền ngang giữa các dòng
              phải khai trên từng <Td>/<Th> (border-b, xem 27 chỗ border-r border-b ở trên) — sửa
              2026-08-19 (đã xác nhận với người dùng, fix bug thật: bảng thiếu hẳn viền ngang do lớp CSS
              chết này). */}
          <tbody>
            {!selectedSessionId ? (
              <tr>
                <td colSpan={12} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                  {selectedClass ? t("dailyCommentPanel.emptyNoSession") : t("dailyCommentPanel.emptyNoClass")}
                </td>
              </tr>
            ) : loadingRows ? (
              <tr>
                <td colSpan={12} className="px-6 py-12 text-center text-xs text-slate-400">
                  {t("dailyCommentPanel.loadingRows")}
                </td>
              </tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={12} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                  {t("dailyCommentPanel.emptyNoStudents")}
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
                // V146 — fallback % tự động khi buổi CHƯA có StudentComment nào (sent undefined), xem
                // previewAutoProgress/AutoProgressPreviewResponse.
                const auto = autoProgress[r.studentId];
                const autoVideo = sent?.videoPreviousProgress ?? auto?.videoPreviousProgress ?? null;
                // Nền đặc cho 3 cột cố định (khác Td mặc định trong suốt) — cuộn ngang thì nội dung cột
                // sau không được lộ ra qua cột cố định phía trên (bổ sung ngoài SDD gốc, 2026-08-14).
                const stickyBg = locked ? "bg-emerald-50" : "bg-white";
                return (
                  <tr
                    key={r.studentId}
                    onClick={locked ? () => notifyAlreadySent(r, sent) : undefined}
                    className={`transition-colors ${locked ? "bg-emerald-50/20 cursor-pointer hover:bg-emerald-50/40" : "hover:bg-slate-50/40"}`}
                  >
                    <Td style={STICKY_COL_STYLE[0]} className={`sticky left-0 z-10 ${stickyBg} font-mono font-bold text-slate-500 border-r border-b border-slate-300`}>{r.studentCode}</Td>
                    <Td style={STICKY_COL_STYLE[1]} className={`sticky z-10 ${stickyBg} font-bold text-slate-900 whitespace-nowrap border-r border-b border-slate-300`}>
                      <StudentNameLink studentId={r.studentId} name={r.studentFullName} />
                    </Td>
                    <Td style={STICKY_COL_STYLE[2]} className={`sticky z-10 ${stickyBg} whitespace-nowrap text-slate-500 border-r border-b border-slate-300`}>{r.studentDateOfBirth ?? "—"}</Td>
                    {isVietnamese ? (
                      <>
                        {/* V130 — "BTVN buổi trước - Offline - Reading/Writing": GV nhập tay điểm % chấm bài giấy,
                            thay cho ô "Offline" gộp cũ (homeworkPreviousScore) — 2 field độc lập homeworkPreviousReadingScore/
                            WritingScore, không liên quan tới field homeworkPreviousScore (đã chuyển hẳn sang buổi FOREIGN). */}
                        <Td className="min-w-[110px] border-r border-b border-slate-300">
                          {locked ? (
                            <div className={readOnlyFieldClass}>{sent!.homeworkPreviousReadingScore || "—"}</div>
                          ) : (
                            <input
                              value={r.homeworkPreviousReadingScore}
                              onChange={(e) => updateRow({ homeworkPreviousReadingScore: e.target.value })}
                              placeholder={t("dailyCommentPanel.homeworkPreviousOfflinePlaceholder")}
                              className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                            />
                          )}
                        </Td>
                        <Td className="min-w-[110px] border-r border-b border-slate-300">
                          {locked ? (
                            <div className={readOnlyFieldClass}>{sent!.homeworkPreviousWritingScore || "—"}</div>
                          ) : (
                            <input
                              value={r.homeworkPreviousWritingScore}
                              onChange={(e) => updateRow({ homeworkPreviousWritingScore: e.target.value })}
                              placeholder={t("dailyCommentPanel.homeworkPreviousOfflinePlaceholder")}
                              className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                            />
                          )}
                        </Td>
                        {/* V137 — "BTVN buổi trước - Online - Reading/Writing": CHỈ hiện % TỰ ĐỘNG (BE tính từ
                            exercise_attempts lọc skillCategory=READING/WRITING, giống cột {onlineGrammarLabel} bên
                            phải) — không có nhập tay (giao Online thì luôn tính được tự động). */}
                        <Td className="min-w-[150px] border-r border-b border-slate-300">
                          <PreviousProgressCell auto={sent?.readingPreviousProgress ?? auto?.readingPreviousProgress ?? null} manual={null} autoLabel={t("dailyCommentPanel.autoBadge")} />
                        </Td>
                        <Td className="min-w-[150px] border-r border-b border-slate-300">
                          <PreviousProgressCell auto={sent?.writingPreviousProgress ?? auto?.writingPreviousProgress ?? null} manual={null} autoLabel={t("dailyCommentPanel.autoBadge")} />
                        </Td>
                      </>
                    ) : (
                      <Td className="min-w-[130px] border-r border-b border-slate-300">
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
                            placeholder={t("dailyCommentPanel.homeworkPreviousOfflinePlaceholder")}
                            className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                          />
                        )}
                      </Td>
                    )}
                    <Td className="min-w-[150px] border-r border-b border-slate-300">
                      {/* {grammarLabel} buổi trước — CHỈ hiện % TỰ ĐỘNG (buổi trước giao Online, BE tính từ
                          exercise_attempts) — nhập tay đã chuyển hẳn sang cột "Offline" bên trái, không còn fallback
                          nhập tay ở đây nữa (tránh 2 cột cùng nhập được 1 giá trị gây nhầm lẫn cho giáo viên). */}
                      <PreviousProgressCell auto={sent?.grammarPreviousProgress ?? auto?.grammarPreviousProgress ?? null} manual={null} autoLabel={t("dailyCommentPanel.autoBadge")} />
                    </Td>
                    <Td className="min-w-[150px] border-r border-b border-slate-300">
                      {locked ? (
                        <PreviousProgressCell auto={autoVideo} manual={sent!.homeworkPreviousSpeakingScore} autoLabel={t("dailyCommentPanel.autoBadge")} />
                      ) : autoVideo ? (
                        // V146 — buổi CHƯA có bản nháp nhưng đã tính được % tự động (video luôn Online nên
                        // hầu như luôn có) — ưu tiên hiện luôn, mirror đúng cách PreviousProgressCell xử lý
                        // khi đã locked, thay vì bắt giáo viên phải Lưu nháp 1 lần mới thấy được số.
                        <PreviousProgressCell auto={autoVideo} manual={null} autoLabel={t("dailyCommentPanel.autoBadge")} />
                      ) : (
                        <input
                          value={r.homeworkPreviousSpeakingScore}
                          onChange={(e) => updateRow({ homeworkPreviousSpeakingScore: e.target.value })}
                          placeholder={t("dailyCommentPanel.homeworkPreviousSpeakingPlaceholder")}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      )}
                    </Td>
                    {isVietnamese ? (
                      <>
                        {/* V130 — "BTVN - Offline - Reading/Writing" (giao buổi sau): 2 field text tự do độc lập
                            (bài + trang), thay cho ô "BTVN offline" gộp cũ (homeworkNext) — buổi FOREIGN vẫn dùng
                            homeworkNext như trước, xem nhánh else bên dưới. */}
                        <Td className="min-w-[140px] border-r border-b border-slate-300">
                          {locked ? (
                            <div className={readOnlyFieldClass}>{sent!.homeworkNextReading || "—"}</div>
                          ) : (
                            <input
                              value={r.homeworkNextReading}
                              onChange={(e) => updateRow({ homeworkNextReading: e.target.value })}
                              placeholder={t("dailyCommentPanel.quickAssign.offlinePlaceholder")}
                              className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                            />
                          )}
                        </Td>
                        <Td className="min-w-[140px] border-r border-b border-slate-300">
                          {locked ? (
                            <div className={readOnlyFieldClass}>{sent!.homeworkNextWriting || "—"}</div>
                          ) : (
                            <input
                              value={r.homeworkNextWriting}
                              onChange={(e) => updateRow({ homeworkNextWriting: e.target.value })}
                              placeholder={t("dailyCommentPanel.quickAssign.offlinePlaceholder")}
                              className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                            />
                          )}
                        </Td>
                        {/* V137 — "BTVN - Online - Reading/Writing" (giao buổi sau): chọn Exercise NGUỒN đã Publish,
                            lọc theo skillCategory=READING/WRITING (readingOptions/writingOptions — KHÔNG lọc
                            teacherType, giống BE StudentCommentService#buildTemplate). */}
                        <Td className="min-w-[200px] border-r border-b border-slate-300">
                          {locked ? (
                            <div className={readOnlyFieldClass}>{sent!.homeworkNextReadingExerciseTitle || "—"}</div>
                          ) : (
                            <Select
                              value={r.homeworkNextReadingExerciseId}
                              disabled={blockOnlineHomework || !teacherType}
                              onChange={(e) => updateRow({ homeworkNextReadingExerciseId: e.target.value ? Number(e.target.value) : "" })}
                              aria-label={!teacherType ? t("dailyCommentPanel.ariaChooseTeacherTypeFirst") : blockOnlineHomework ? t("dailyCommentPanel.ariaNoUpcomingSession") : undefined}
                              className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-40 disabled:cursor-not-allowed"
                            >
                              <option value="">{t("dailyCommentPanel.chooseExercisePlaceholder")}</option>
                              {readingOptions.map((ex) => (
                                <option key={ex.examId} value={ex.examId}>
                                  {/* Bổ sung 2026-09-04 (đã xác nhận với người dùng) — fix bug thật: Lesson đánh số lặp
                              lại (Lesson 1, 2, 3...) giữa nhiều Unit/SubTopic khác nhau, trước đây dropdown chỉ
                              hiện examTitle nên giáo viên rất dễ giao NHẦM Lesson. */}
                          {ex.examCode} - {ex.examTitle}
                          {(ex.unitTitle || ex.subTopicTitle) && ` [${[ex.unitTitle, ex.subTopicTitle].filter(Boolean).join(" · ")}]`} (
                          {ex.exerciseCount} bài, {ex.questionCount} câu)
                                </option>
                              ))}
                            </Select>
                          )}
                        </Td>
                        <Td className="min-w-[200px] border-r border-b border-slate-300">
                          {locked ? (
                            <div className={readOnlyFieldClass}>{sent!.homeworkNextWritingExerciseTitle || "—"}</div>
                          ) : (
                            <Select
                              value={r.homeworkNextWritingExerciseId}
                              disabled={blockOnlineHomework || !teacherType}
                              onChange={(e) => updateRow({ homeworkNextWritingExerciseId: e.target.value ? Number(e.target.value) : "" })}
                              aria-label={!teacherType ? t("dailyCommentPanel.ariaChooseTeacherTypeFirst") : blockOnlineHomework ? t("dailyCommentPanel.ariaNoUpcomingSession") : undefined}
                              className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-40 disabled:cursor-not-allowed"
                            >
                              <option value="">{t("dailyCommentPanel.chooseExercisePlaceholder")}</option>
                              {writingOptions.map((ex) => (
                                <option key={ex.examId} value={ex.examId}>
                                  {/* Bổ sung 2026-09-04 (đã xác nhận với người dùng) — fix bug thật: Lesson đánh số lặp
                              lại (Lesson 1, 2, 3...) giữa nhiều Unit/SubTopic khác nhau, trước đây dropdown chỉ
                              hiện examTitle nên giáo viên rất dễ giao NHẦM Lesson. */}
                          {ex.examCode} - {ex.examTitle}
                          {(ex.unitTitle || ex.subTopicTitle) && ` [${[ex.unitTitle, ex.subTopicTitle].filter(Boolean).join(" · ")}]`} (
                          {ex.exerciseCount} bài, {ex.questionCount} câu)
                                </option>
                              ))}
                            </Select>
                          )}
                        </Td>
                      </>
                    ) : (
                      <Td className="min-w-[160px] border-r border-b border-slate-300">
                        {locked ? (
                          <div className={readOnlyFieldClass}>{sent!.homeworkNext || "—"}</div>
                        ) : (
                          <input
                            value={r.homeworkNext}
                            onChange={(e) => updateRow({ homeworkNext: e.target.value })}
                            placeholder={t("dailyCommentPanel.quickAssign.offlinePlaceholder")}
                            className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                          />
                        )}
                      </Td>
                    )}
                    <Td className="min-w-[200px] border-r border-b border-slate-300">
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.homeworkNextExerciseTitle || "—"}</div>
                      ) : (
                        <Select
                          value={r.homeworkNextExerciseId}
                          disabled={blockOnlineHomework || !teacherType}
                          onChange={(e) => updateRow({ homeworkNextExerciseId: e.target.value ? Number(e.target.value) : "" })}
                          aria-label={!teacherType ? t("dailyCommentPanel.ariaChooseTeacherTypeFirst") : blockOnlineHomework ? t("dailyCommentPanel.ariaNoUpcomingSession") : undefined}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                          <option value="">{t("dailyCommentPanel.chooseExercisePlaceholder")}</option>
                          {filteredGrammarOptions.map((ex) => (
                            <option key={ex.examId} value={ex.examId}>
                              {/* Bổ sung 2026-09-04 (đã xác nhận với người dùng) — fix bug thật: Lesson đánh số lặp
                              lại (Lesson 1, 2, 3...) giữa nhiều Unit/SubTopic khác nhau, trước đây dropdown chỉ
                              hiện examTitle nên giáo viên rất dễ giao NHẦM Lesson. */}
                          {ex.examCode} - {ex.examTitle}
                          {(ex.unitTitle || ex.subTopicTitle) && ` [${[ex.unitTitle, ex.subTopicTitle].filter(Boolean).join(" · ")}]`} (
                          {ex.exerciseCount} bài, {ex.questionCount} câu)
                            </option>
                          ))}
                        </Select>
                      )}
                    </Td>
                    <Td className="min-w-[200px] border-r border-b border-slate-300">
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.homeworkNextReviewVideoSetTitle || "—"}</div>
                      ) : (
                        <Select
                          value={r.homeworkNextReviewVideoSetId}
                          onChange={(e) => updateRow({ homeworkNextReviewVideoSetId: e.target.value ? Number(e.target.value) : "" })}
                          disabled={blockOnlineHomework || !teacherType}
                          aria-label={!teacherType ? t("dailyCommentPanel.ariaChooseTeacherTypeFirst") : blockOnlineHomework ? t("dailyCommentPanel.ariaNoUpcomingSession") : undefined}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                          <option value="">{t("dailyCommentPanel.quickAssign.noAssign")}</option>
                          {filteredVideoOptions.map((s) => (
                            <option key={s.id} value={s.id}>
                              {s.title} ({s.code})
                            </option>
                          ))}
                        </Select>
                      )}
                    </Td>
                    <Td className="min-w-[120px] whitespace-nowrap border-r border-b border-slate-300">
                      {locked
                        ? sent!.homeworkNextDueAt
                          ? new Date(sent!.homeworkNextDueAt).toLocaleString(toLocaleTag(i18n.language), { dateStyle: "short", timeStyle: "short" })
                          : "—"
                        : dueDateTime
                          ? new Date(dueDateTime).toLocaleString(toLocaleTag(i18n.language), { dateStyle: "short", timeStyle: "short" })
                          : "—"}
                    </Td>
                    <Td className="min-w-[130px] border-r border-b border-slate-300">
                      {locked ? (
                        <div className={readOnlyFieldClass}>{sent!.attitude ? t(`shared.attitudeWithPercent.${sent!.attitude}`) : "—"}</div>
                      ) : (
                        <Select
                          value={r.attitude}
                          onChange={(e) => updateRow({ attitude: e.target.value as Row["attitude"] })}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        >
                          <option value="">{t("dailyCommentPanel.attitudePlaceholder")}</option>
                          {(["WEAK", "AVERAGE", "FAIR", "GOOD", "EXCELLENT"] as const).map((value) => (
                            <option key={value} value={value}>
                              {t(`shared.attitudeWithPercent.${value}`)}
                            </option>
                          ))}
                        </Select>
                      )}
                    </Td>
                    <Td className="min-w-[320px] border-r border-b border-slate-300">
                      {locked ? (
                        <div className={`${readOnlyFieldClass} whitespace-pre-wrap`}>{sent!.content}</div>
                      ) : (
                        <textarea
                          value={r.content}
                          onChange={(e) => updateRow({ content: e.target.value })}
                          placeholder={t("dailyCommentPanel.contentPlaceholder")}
                          rows={2}
                          className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                      )}
                    </Td>
                    <Td className="min-w-[140px] border-r border-b border-slate-300">
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
              onClick={() => setConfirmingSend(true)}
              disabled={sending || !rows.some((r) => r.content.trim())}
              className="bg-brand-orange hover:bg-brand-orange/90 text-white font-semibold text-xs px-4 py-2 rounded-lg flex items-center gap-1.5 shadow-soft transition-all disabled:opacity-50"
            >
              <Send className="w-4 h-4 text-white" />
              <span>
                {sending
                  ? t("dailyCommentPanel.sending")
                  : rows.some((r) => r.content.trim())
                    ? t("dailyCommentPanel.sendButton")
                    : t("dailyCommentPanel.sendButtonDoneAll")}
              </span>
            </button>
          </div>
        )}

        {/* Xác nhận trước khi Gửi nhận xét (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
            2026-08-25) — xem Javadoc confirmingSend ở trên. */}
        <Modal
          open={confirmingSend}
          onClose={() => setConfirmingSend(false)}
          title={t("dailyCommentPanel.confirmSend.title")}
          footer={
            <>
              <button
                onClick={() => setConfirmingSend(false)}
                className="bg-white hover:bg-slate-50 border border-slate-200 text-slate-700 font-semibold text-xs px-4 py-2 rounded-lg transition-all"
              >
                {t("dailyCommentPanel.confirmSend.cancel")}
              </button>
              <button
                onClick={() => {
                  setConfirmingSend(false);
                  handleSend();
                }}
                disabled={sending}
                className="bg-brand-orange hover:bg-brand-orange/90 text-white font-semibold text-xs px-4 py-2 rounded-lg flex items-center gap-1.5 shadow-soft transition-all disabled:opacity-50"
              >
                <Send className="w-3.5 h-3.5 text-white" />
                {t("dailyCommentPanel.confirmSend.confirmButton")}
              </button>
            </>
          }
        >
          <div className="flex items-start gap-3">
            <ShieldAlert className="w-8 h-8 text-amber-500 shrink-0" />
            <p className="text-xs text-slate-600 leading-relaxed">
              {t("dailyCommentPanel.confirmSend.description", { count: rows.filter((r) => r.content.trim()).length })}
            </p>
          </div>
        </Modal>

        {/* {selectedClassId && selectedSessionId && (
          <div className="px-6 py-4 border-t border-slate-100 space-y-2">
            <button
              type="button"
              onClick={() => setShowHistory((v) => !v)}
              className="flex items-center gap-1.5 text-[10px] font-bold uppercase text-slate-500 hover:text-slate-700"
            >
              {showHistory ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
              {!showHistory && history.length > 0
                ? t("dailyCommentPanel.historyToggleCount", { count: history.length })
                : t("dailyCommentPanel.historyToggle")}
            </button>
            {showHistory && (loadingHistory ? (
              <p className="text-xs text-slate-400">{t("dailyCommentPanel.loadingHistory")}</p>
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
        )} */}
        {showSessionHistory && selectedSessionId && (
          <SessionVersionHistoryModal
            classSessionId={selectedSessionId}
            students={rows.map((r) => ({
              studentId: r.studentId,
              studentCode: r.studentCode,
              studentFullName: r.studentFullName,
              studentDateOfBirth: r.studentDateOfBirth
            }))}
            grammarLabel={grammarLabel}
            videoLabel={videoLabel}
            isVietnamese={isVietnamese}
            onClose={() => setShowSessionHistory(false)}
          />
        )}
      </div>
    </div>
  );
}
