import { apiRequest } from "@/lib/apiClient";
import type { Page } from "@/types";

/** Khớp MediaModule thật của backend. REVIEW_VIDEO_SUBMISSION (UC-23b): audio Học sinh nộp trả lời video REFLEX. */
export type MediaUploadModule = "STUDENT" | "PARENT" | "REVIEW_VIDEO_SUBMISSION";

/** UC-63: upload ảnh đại diện thật lên Cloudflare R2 qua API dùng chung, trả về URL public để lưu vào portraitUrl. */
export function uploadMedia(file: File, module: MediaUploadModule): Promise<{ url: string }> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("module", module);
  return apiRequest<{ url: string }>("/media/upload", { method: "POST", body: formData });
}

/** UC-63: Học sinh tự xem/sửa hồ sơ của chính mình — chỉ portraitUrl được phép sửa (field khác vẫn do Giáo vụ quản lý). */
export interface MyStudentProfileResponse {
  id: number;
  fullName: string;
  portraitUrl: string | null;
}

export interface UpdateOwnStudentProfileRequest {
  portraitUrl?: string;
}

export function getMyStudentProfile(): Promise<MyStudentProfileResponse> {
  return apiRequest<MyStudentProfileResponse>("/students/me");
}

export function updateMyStudentProfile(request: UpdateOwnStudentProfileRequest): Promise<MyStudentProfileResponse> {
  return apiRequest<MyStudentProfileResponse>("/students/me", { method: "PUT", body: JSON.stringify(request) });
}

/** UC-63: Phụ huynh tự xem/sửa hồ sơ của chính mình (khác hồ sơ con em — xem ChildResponse). */
export interface MyParentProfileResponse {
  id: number;
  fullName: string;
  occupation: string | null;
  workplace: string | null;
  address: string | null;
  portraitUrl: string | null;
}

export interface UpdateOwnParentProfileRequest {
  occupation?: string;
  workplace?: string;
  address?: string;
  portraitUrl?: string;
}

export function getMyParentProfile(): Promise<MyParentProfileResponse> {
  return apiRequest<MyParentProfileResponse>("/parents/me");
}

export function updateMyParentProfile(request: UpdateOwnParentProfileRequest): Promise<MyParentProfileResponse> {
  return apiRequest<MyParentProfileResponse>("/parents/me", { method: "PUT", body: JSON.stringify(request) });
}

/** UC-25 Main Flow bước 2 — khớp ChildResponse thật. */
export interface ChildResponse {
  studentId: number;
  studentFullName: string;
  studentCode: string;
}

/** UC-42 — khớp PortalClassOptionResponse thật. */
export interface PortalClassOptionResponse {
  classEnrollmentId: number;
  classId: number;
  className: string;
  classCode: string;
  enrolledDate: string;
  withdrawnDate: string | null;
  status: "ACTIVE" | "COMPLETED" | "SUSPENDED" | "WITHDRAWN";
  recommended: boolean;
}

/**
 * UC-20 (V43 — 4 trạng thái + phúc khảo UC-62, thay hẳn DRAFT/PUBLISHED của V39) —
 * khớp GradeEntryResponse thật (chỉ điểm khác DRAFT được BE lọc sẵn ở portal, tức
 * PROVISIONAL_PUBLISHED/APPEAL/OFFICIAL đều xem được, không chỉ "đã công bố xong").
 */
export type GradeStatus = "DRAFT" | "PROVISIONAL_PUBLISHED" | "APPEAL" | "OFFICIAL";

export interface GradeEntryResponse {
  id: number;
  classId: number;
  studentId: number;
  studentFullName: string;
  studentCode: string;
  gradeComponentId: number;
  score: number;
  absenceFlag: boolean;
  teacherNote: string | null;
  status: GradeStatus;
  enteredBy: number;
  publishedBy: number | null;
  publishedAt: string | null;
  finalizedAt: string | null;
}

/** UC-53 — Overall/Level theo kỳ đánh giá, khác DRAFT mới trả về (BE lọc sẵn — xem GradeEntryResponse). */
export interface GradePeriodResultResponse {
  id: number;
  classId: number;
  studentId: number;
  studentFullName: string;
  studentCode: string;
  gradePeriodId: number;
  overallScore: number | null;
  scaleType: "NUMERIC" | "PERCENTAGE" | "BAND";
  level: string | null;
  source: "MANUAL" | "EXCEL_IMPORT";
  importJobId: number | null;
  status: GradeStatus;
  enteredBy: number;
  publishedBy: number | null;
  publishedAt: string | null;
  finalizedAt: string | null;
}

/** Chỉ cần đúng field dùng ở Portal (tra curriculumId để lấy danh sách kỳ đánh giá). */
export interface PortalClassResponse {
  id: number;
  curriculumId: number;
}

export interface GradePeriodResponse {
  id: number;
  curriculumId: number;
  code: string;
  name: string;
  displayOrder: number;
}

/** Tên đầu điểm (Listening/Reading/...) — GradeEntryResponse chỉ có gradeComponentId, phải tra tên qua đây để hiện đúng nhãn. */
export interface GradeComponentResponse {
  id: number;
  gradePeriodId: number;
  subjectId: number | null;
  skillId: number | null;
  code: string;
  name: string;
  maxScore: number | null;
  passThreshold: number | null;
  scaleType: "NUMERIC" | "PERCENTAGE" | "BAND";
  displayOrder: number;
}

export function getPortalClass(classId: number): Promise<PortalClassResponse> {
  return apiRequest<PortalClassResponse>(`/classes/${classId}`);
}

/** GET không gate quyền riêng (giống GradePeriodResponse) — Học sinh/Phụ huynh tự gọi được để tra tên đầu điểm. */
export function listGradeComponents(gradePeriodId: number): Promise<GradeComponentResponse[]> {
  return apiRequest<GradeComponentResponse[]>(`/grade-periods/${gradePeriodId}/components`);
}

export function listGradePeriods(curriculumId: number): Promise<GradePeriodResponse[]> {
  return apiRequest<GradePeriodResponse[]>(`/curriculums/${curriculumId}/grade-periods`);
}

/** UC-53/UC-25: Overall/Level đã công bố — 404 nếu chưa có/chưa công bố (bắt ở nơi gọi, không phải lỗi thật). */
export function getPeriodResult(studentId: number, classId: number, gradePeriodId: number): Promise<GradePeriodResultResponse> {
  return apiRequest<GradePeriodResultResponse>(`/portal/parent/children/${studentId}/classes/${classId}/periods/${gradePeriodId}/result`);
}

/** UC-61: Học sinh tự xem điểm của chính mình (self-service, khác listGrades — Phụ huynh xem theo con+lớp cụ thể). */
export function listMyGrades(classId?: number): Promise<GradeEntryResponse[]> {
  return apiRequest<GradeEntryResponse[]>(`/students/me/grades${classId ? `?classId=${classId}` : ""}`);
}

/** UC-61: Overall/Level của chính mình — 404 nếu chưa có/chưa công bố (bắt ở nơi gọi, không phải lỗi thật). */
export function getMyPeriodResult(classId: number, gradePeriodId: number): Promise<GradePeriodResultResponse> {
  return apiRequest<GradePeriodResultResponse>(`/students/me/classes/${classId}/periods/${gradePeriodId}/result`);
}

/** UC-62: Phúc khảo điểm — Học sinh/Phụ huynh gửi yêu cầu trên 1 bản ghi đang PROVISIONAL_PUBLISHED. */
export interface GradeAppealResponse {
  id: number;
  entityType: "GRADE_ENTRY" | "GRADE_PERIOD_RESULT";
  entityId: number;
  classId: number;
  studentId: number;
  studentFullName: string;
  requestedByUserId: number;
  reason: string | null;
  status: "PENDING" | "ACCEPTED" | "RESOLVED";
  acceptedByUserId: number | null;
  acceptedAt: string | null;
  resolvedAt: string | null;
  createdAt: string;
}

export interface SubmitGradeAppealRequest {
  entityType: "GRADE_ENTRY" | "GRADE_PERIOD_RESULT";
  entityId: number;
  reason?: string;
}

export function submitGradeAppeal(request: SubmitGradeAppealRequest): Promise<GradeAppealResponse> {
  return apiRequest<GradeAppealResponse>("/grade-appeals", { method: "POST", body: JSON.stringify(request) });
}

/** Lịch sử phúc khảo đã gửi (Học sinh/Phụ huynh, tự-phục vụ theo actor đang đăng nhập). */
export function listMyGradeAppeals(): Promise<GradeAppealResponse[]> {
  return apiRequest<GradeAppealResponse[]>("/grade-appeals/me");
}

/** UC-15 — khớp AttendanceMarkResponse thật. */
export interface AttendanceMarkResponse {
  id: number;
  attendanceSessionId: number;
  studentId: number;
  studentFullName: string;
  studentCode: string;
  status: "PRESENT" | "ABSENT" | "LATE" | "EXCUSED";
  minutesLate: number | null;
  minutesEarlyLeave: number | null;
  absenceReason: string | null;
  notifiedParentAt: string | null;
}

/** UC-22 — khớp StudentCommentResponse thật (chỉ nhận xét đã duyệt được BE lọc sẵn ở portal). */
export interface StudentCommentResponse {
  id: number;
  studentId: number;
  studentFullName: string;
  classId: number;
  teacherId: number;
  commentType: "DAILY" | "MID_TERM" | "END_TERM";
  classSessionId: number | null;
  gradePeriodId: number | null;
  commentDate: string;
  content: string;
  structuredContent: Record<string, unknown> | null;
  severity: string;
  isWarning: boolean;
  status: string;
  submittedAt: string | null;
  approvedAt: string | null;
  approvedBy: number | null;
  visibleToParentAt: string | null;
  rejectionReason: string | null;
}

/** UC-18 — khớp ClassSessionResponse thật. */
export interface ClassSessionResponse {
  id: number;
  classId: number;
  sessionDate: string;
  startTime: string;
  endTime: string;
  roomId: number | null;
  roomName: string | null;
  primaryTeacherId: number | null;
  primaryTeacherName: string | null;
  sessionType: string;
  status: string;
  cancellationReason: string | null;
  rescheduledToSessionId: number | null;
}

/** UC-30 — khớp InvoiceResponse thật. */
export interface InvoiceItemResponse {
  id: number;
  description: string;
  amount: number;
}

export interface InvoiceResponse {
  id: number;
  invoiceNumber: string;
  studentId: number;
  studentFullName: string;
  studentCode: string;
  classEnrollmentId: number | null;
  payerParentId: number | null;
  billingPeriodFrom: string;
  billingPeriodTo: string;
  issueDate: string;
  dueDate: string;
  subtotal: number;
  discountTotal: number;
  taxAmount: number;
  totalAmount: number;
  paidAmount: number;
  outstandingAmount: number;
  status: "UNPAID" | "PARTIALLY_PAID" | "PAID" | "OVERDUE" | "CANCELLED";
  qrCodeData: string | null;
  items: InvoiceItemResponse[];
}

/** Bảng tin — khớp NotificationResponse thật (thông báo hệ thống, không phải tin trường chung). */
export interface NotificationResponse {
  id: number;
  notificationType: string;
  title: string;
  content: string;
  entityType: string | null;
  entityId: number | null;
  priority: string;
  createdAt: string;
  readAt: string | null;
}

export function listMyChildren(): Promise<ChildResponse[]> {
  return apiRequest<ChildResponse[]>("/portal/parent/children");
}

export function listClassOptions(studentId: number): Promise<PortalClassOptionResponse[]> {
  return apiRequest<PortalClassOptionResponse[]>(`/portal/students/${studentId}/class-options`);
}

export function listGrades(studentId: number, classId: number): Promise<GradeEntryResponse[]> {
  return apiRequest<GradeEntryResponse[]>(`/portal/parent/children/${studentId}/classes/${classId}/grades`);
}

export function listAttendance(studentId: number, classId: number): Promise<AttendanceMarkResponse[]> {
  return apiRequest<AttendanceMarkResponse[]>(`/portal/parent/children/${studentId}/classes/${classId}/attendance`);
}

export function listComments(studentId: number, classId: number): Promise<StudentCommentResponse[]> {
  return apiRequest<StudentCommentResponse[]>(`/portal/parent/children/${studentId}/classes/${classId}/comments`);
}

export function listSchedule(studentId: number, classId: number): Promise<ClassSessionResponse[]> {
  return apiRequest<ClassSessionResponse[]>(`/portal/parent/children/${studentId}/classes/${classId}/schedule`);
}

/** UC-59: Học sinh tự xem lịch học của chính mình (self-service, không cần studentId/classId — suy từ JWT) — khác listSchedule (Phụ huynh xem theo con+lớp cụ thể). */
export function listMySessions(fromDate?: string, toDate?: string, classId?: number): Promise<ClassSessionResponse[]> {
  const params = new URLSearchParams();
  if (fromDate) params.set("fromDate", fromDate);
  if (toDate) params.set("toDate", toDate);
  if (classId) params.set("classId", String(classId));
  const query = params.toString();
  return apiRequest<ClassSessionResponse[]>(`/students/me/sessions${query ? `?${query}` : ""}`);
}

export function listMyInvoices(): Promise<InvoiceResponse[]> {
  return apiRequest<InvoiceResponse[]>("/finance/invoices/my");
}

export function listMyNotifications(page = 0, size = 20): Promise<Page<NotificationResponse>> {
  return apiRequest<Page<NotificationResponse>>(`/notifications?page=${page}&size=${size}`);
}

/**
 * UC-23a — khớp ReviewVideoSetResponse thật. GET /api/classes/{classId}/review-video-sets tự trả
 * đúng phạm vi nhìn thấy của học sinh (findVisibleForClass — bộ riêng lớp NÀY HOẶC bộ dùng chung
 * theo khung của lớp NÀY, chỉ status=PUBLISHED — BE tự lọc, Portal không cần gọi thêm endpoint
 * theo curriculum). 404 (không 403) nếu học sinh không thuộc lớp — không lộ tồn tại ngoài phạm vi.
 */
export interface ReviewVideoSetResponse {
  id: number;
  code: string;
  title: string;
  videoType: "CONNECTION" | "REFLEX";
  curriculumId: number | null;
  classId: number | null;
  subjectId: number | null;
  displayOrder: number;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  publishedAt: string | null;
  createdBy: number;
}

export interface ReviewVideoResponse {
  id: number;
  reviewVideoSetId: number;
  sourceType: "YOUTUBE_URL" | "R2_VIDEO" | "R2_AUDIO";
  title: string;
  fileUrl: string;
  fileSizeBytes: number | null;
  durationSeconds: number;
  displayOrder: number;
}

export interface ReviewVideoProgressResponse {
  reviewVideoId: number;
  watchedSeconds: number;
  durationSeconds: number;
  watchedPercent: number;
  completed: boolean;
}

export function listReviewVideoSetsByClass(classId: number): Promise<ReviewVideoSetResponse[]> {
  return apiRequest<ReviewVideoSetResponse[]>(`/classes/${classId}/review-video-sets`);
}

export function listReviewVideos(setId: number): Promise<ReviewVideoResponse[]> {
  return apiRequest<ReviewVideoResponse[]>(`/review-video-sets/${setId}/videos`);
}

/** UC-23a Main Flow bước 3: báo tiến độ xem (giây) — BE tự lấy max(cũ, mới), không bao giờ giảm dù báo giá trị thấp hơn. */
export function reportReviewVideoProgress(videoId: number, watchedSeconds: number): Promise<ReviewVideoProgressResponse> {
  return apiRequest<ReviewVideoProgressResponse>(`/review-videos/${videoId}/progress`, {
    method: "PUT",
    body: JSON.stringify({ watchedSeconds })
  });
}

/** UC-23b — khớp ReviewVideoSubmissionResponse thật (dùng chung Học sinh xem bài của mình + Giáo viên chấm). */
export interface ReviewVideoSubmissionResponse {
  id: number;
  reviewVideoId: number;
  studentId: number;
  studentFullName: string;
  audioUrl: string;
  submittedAt: string;
  score: number | null;
  maxScore: number | null;
  feedback: string | null;
  gradedByUserId: number | null;
  gradedAt: string | null;
}

/** UC-23b Main Flow bước 3: nộp/nộp lại audio trả lời cho video REFLEX — nộp lại xoá sạch điểm cũ (BE tự xử lý), chỉ nhận videoType=REFLEX. */
export function submitReviewVideoAudio(videoId: number, audioUrl: string): Promise<ReviewVideoSubmissionResponse> {
  return apiRequest<ReviewVideoSubmissionResponse>(`/review-videos/${videoId}/submission`, {
    method: "PUT",
    body: JSON.stringify({ audioUrl })
  });
}

/** Trả về undefined (204) nếu học sinh chưa nộp bài cho video này. */
export function getMyReviewVideoSubmission(videoId: number): Promise<ReviewVideoSubmissionResponse | undefined> {
  return apiRequest<ReviewVideoSubmissionResponse | undefined>(`/review-videos/${videoId}/submission`);
}

/**
 * UC-40 (phía học viên): đề đã được giao cho (các) lớp tôi đang học —
 * self-service thật (KHÁC `GET /api/classes/{classId}/exercises`, hàm đó
 * chỉ dành cho Giáo viên của lớp, học sinh gọi vào luôn bị 403). Khớp
 * AssignedExerciseResponse thật — 3 field myLatestAttempt... cho biết
 * ngay đã làm/đang làm dở/đã có điểm, không cần gọi thêm API nào.
 */
export interface AssignedExerciseResponse {
  exerciseId: number;
  exerciseCode: string;
  title: string;
  exerciseType: "SELF_PRACTICE" | "ASSIGNED";
  assignmentId: number;
  classId: number;
  className: string;
  availableFrom: string;
  dueAt: string | null;
  lateSubmissionAllowed: boolean;
  myLatestAttemptId: number | null;
  myLatestAttemptStatus: "IN_PROGRESS" | "AUTO_GRADED" | "FULLY_GRADED" | null;
  myLatestTotalScore: number | null;
}

export function listMyAssignedExercises(classId?: number): Promise<AssignedExerciseResponse[]> {
  return apiRequest<AssignedExerciseResponse[]>(`/students/me/exercises${classId ? `?classId=${classId}` : ""}`);
}

/** UC-24/UC-27 (BE bổ sung): phương án chọn cho câu trắc nghiệm — CHỦ Ý không có isCorrect, chỉ lộ qua StudentAnswerResponse.correctChoiceIds sau khi nộp bài. */
export interface ExerciseQuestionChoiceResponse {
  id: number;
  choiceLabel: string;
  content: string;
  displayOrder: number;
}

/** choices chỉ có giá trị với câu MULTIPLE_CHOICE/MULTIPLE_ANSWER/TRUE_FALSE — rỗng với ESSAY/SPEAKING/FILL_IN_BLANK. */
export interface ExerciseQuestionResponse {
  id: number;
  exerciseId: number;
  questionId: number;
  questionType: string;
  questionContent: string;
  displayOrder: number;
  points: number;
  choices: ExerciseQuestionChoiceResponse[];
}

export function listExerciseQuestions(exerciseId: number): Promise<ExerciseQuestionResponse[]> {
  return apiRequest<ExerciseQuestionResponse[]>(`/exercises/${exerciseId}/questions`);
}

// ===================== UC-24/UC-27: Làm bài + nộp bài =====================

export interface ExerciseAttemptResponse {
  id: number;
  exerciseId: number;
  exerciseAssignmentId: number | null;
  studentId: number;
  attemptNumber: number;
  startedAt: string;
  submittedAt: string | null;
  autoGradeScore: number | null;
  manualGradeScore: number | null;
  totalScore: number | null;
  status: "IN_PROGRESS" | "AUTO_GRADED" | "FULLY_GRADED";
  isLateSubmission: boolean;
}

/** Main Flow bước 1: mở lượt làm mới — LUÔN tạo attempt mới (không tự resume), chỉ gọi khi thật sự chưa có attempt nào hoặc muốn làm lại. */
export function startAttempt(exerciseId: number): Promise<ExerciseAttemptResponse> {
  return apiRequest<ExerciseAttemptResponse>(`/exercises/${exerciseId}/attempts`, { method: "POST" });
}

export function getAttempt(attemptId: number): Promise<ExerciseAttemptResponse> {
  return apiRequest<ExerciseAttemptResponse>(`/attempts/${attemptId}`);
}

export interface SaveAnswerRequest {
  questionId: number;
  answerText?: string;
  selectedChoiceIds?: number[];
  audioAnswerUrl?: string;
}

/**
 * correctChoiceIds/correctAnswerText chỉ được điền khi attempt đã nộp (không còn IN_PROGRESS) VÀ
 * exercise.showCorrectAnswers=true. explanation điền thêm khi: câu KHÔNG tự chấm được (ESSAY/SPEAKING,
 * luôn hiện) HOẶC câu tự chấm được nhưng trả lời SAI (isCorrect=false) — V54.
 */
export interface StudentAnswerResponse {
  id: number;
  exerciseAttemptId: number;
  questionId: number;
  answerText: string | null;
  selectedChoiceIds: number[] | null;
  audioAnswerUrl: string | null;
  isAutoGradable: boolean;
  autoScore: number | null;
  isCorrect: boolean | null;
  correctChoiceIds: number[] | null;
  /** V54 — chỉ có ý nghĩa với câu FILL_IN_BLANK. */
  correctAnswerText: string | null;
  explanation: string | null;
}

/** Main Flow bước 2: trả lời 1 câu — ghi/ghi đè, gọi lại nhiều lần trong lúc attempt còn IN_PROGRESS. */
export function saveAnswer(attemptId: number, request: SaveAnswerRequest): Promise<StudentAnswerResponse> {
  return apiRequest<StudentAnswerResponse>(`/attempts/${attemptId}/answers`, { method: "POST", body: JSON.stringify(request) });
}

export function listAnswers(attemptId: number): Promise<StudentAnswerResponse[]> {
  return apiRequest<StudentAnswerResponse[]>(`/attempts/${attemptId}/answers`);
}

/** Main Flow bước 3-4: nộp bài — BE tự chấm trắc nghiệm ngay, chuyển AUTO_GRADED (còn câu tự luận/nói chờ chấm) hoặc FULLY_GRADED (toàn trắc nghiệm). */
export function submitAttempt(attemptId: number): Promise<ExerciseAttemptResponse> {
  return apiRequest<ExerciseAttemptResponse>(`/attempts/${attemptId}/submit`, { method: "POST" });
}

/**
 * UC-60: Kho tài liệu tham khảo — độc lập với Kho bài giảng (UC-23, gắn 1 bài giảng cụ
 * thể), chỉ gắn theo khung chương trình để "tự học thêm", không qua bài giảng nào.
 */
export interface CurriculumDocumentResponse {
  id: number;
  curriculumId: number;
  title: string;
  description: string | null;
  documentType: "VIDEO" | "PDF" | "AUDIO" | "SLIDE" | "IMAGE" | "OTHER";
  fileUrl: string;
  coverImageUrl: string | null;
  displayOrder: number;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  createdBy: number;
}

/** Self-service — chỉ trả PUBLISHED, theo curriculum của (các) lớp tôi đang ghi danh ACTIVE (BE lọc sẵn). */
export function listMyDocuments(curriculumId?: number): Promise<CurriculumDocumentResponse[]> {
  return apiRequest<CurriculumDocumentResponse[]>(`/students/me/documents${curriculumId ? `?curriculumId=${curriculumId}` : ""}`);
}
