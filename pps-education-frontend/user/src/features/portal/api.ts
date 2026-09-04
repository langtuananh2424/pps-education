import { apiRequest } from "@/lib/apiClient";
import type { Page } from "@/types";

/**
 * Khớp MediaModule thật của backend. REVIEW_VIDEO_SUBMISSION (UC-23b): audio Học sinh nộp trả lời
 * video REFLEX. EXERCISE_ANSWER_SUBMISSION (V78, bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-04): audio Học sinh nộp cho câu hỏi SPEAKING (Speaking oral / "Nghe & nộp audio").
 */
export type MediaUploadModule = "STUDENT" | "PARENT" | "REVIEW_VIDEO_SUBMISSION" | "EXERCISE_ANSWER_SUBMISSION";

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
  studentCode: string;
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

/** UC-63: Học sinh tự tra danh sách phụ huynh liên kết với chính mình. */
export interface ParentStudentResponse {
  id: number;
  parentId: number;
  parentFullName: string;
  parentPhone: string | null;
  studentId: number;
  relationship: string;
  isPrimaryContact: boolean;
  isFinancialResponsible: boolean;
  notes: string | null;
}

export function getMyParents(): Promise<ParentStudentResponse[]> {
  return apiRequest<ParentStudentResponse[]>("/students/me/parents");
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
  /** Bổ sung 2026-08-12 — dùng để gọi GET /academic-terms?siteId=... lọc "Lịch học & Chuyên cần" theo học kỳ. */
  siteId: number;
}

/** Khớp AcademicTermResponse thật (academic_terms độc lập với classes, chỉ gắn theo site). */
export interface AcademicTermResponse {
  id: number;
  siteId: number;
  siteName: string;
  code: string;
  name: string;
  startDate: string;
  endDate: string;
}

/**
 * UC-20 (V44 — 4 trạng thái, thay hẳn luồng "công bố dự kiến + phúc khảo" V43) —
 * khớp GradeEntryResponse thật. Portal (listMyGrades/listGrades) chỉ trả về bản ghi
 * đã OFFICIAL (Quản lý điểm trường đã duyệt) — DRAFT/SUBMITTED/REJECTED không hiển
 * thị cho Phụ huynh/Học sinh.
 */
export type GradeStatus = "DRAFT" | "SUBMITTED" | "OFFICIAL" | "REJECTED";

export interface GradeEntryResponse {
  id: number;
  classId: number;
  studentId: number;
  studentFullName: string;
  studentCode: string;
  gradeEvaluationComponentId: number;
  academicTermId: number;
  evaluationType: "MID_TERM" | "END_TERM";
  score: number;
  absenceFlag: boolean;
  teacherNote: string | null;
  status: GradeStatus;
  enteredBy: number;
  publishedBy: number | null;
  publishedAt: string | null;
  finalizedAt: string | null;
}

/** UC-53 — Overall/Level/Nhận xét/Ghi chú (V94)/Lưu ý (V100) theo (kỳ học, Giữa/Cuối kỳ), khác DRAFT mới trả về (BE lọc sẵn — xem GradeEntryResponse). */
export interface GradeEvaluationResultResponse {
  id: number;
  classId: number;
  studentId: number;
  studentFullName: string;
  studentCode: string;
  academicTermId: number;
  evaluationType: "MID_TERM" | "END_TERM";
  overallScore: number | null;
  scaleType: "NUMERIC" | "PERCENTAGE" | "BAND";
  level: string | null;
  comment: string | null;
  note: string | null;
  disclaimer: string | null;
  source: "MANUAL" | "EXCEL_IMPORT";
  importJobId: number | null;
  status: GradeStatus;
  enteredBy: number;
  publishedBy: number | null;
  publishedAt: string | null;
  finalizedAt: string | null;
}

/** Chỉ cần đúng field dùng ở Portal. */
export interface PortalClassResponse {
  id: number;
  curriculumId: number;
}

/** V94 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — consolidate vào academic_terms): thay GradePeriodResponse, gắn (lớp, kỳ học, Giữa/Cuối kỳ) thay vì theo curriculum. */
export interface GradeComponentSetupResponse {
  id: number;
  classId: number;
  academicTermId: number;
  academicTermName: string;
  evaluationType: "MID_TERM" | "END_TERM";
  scaleType: "POINT_10" | "PERCENT" | "IELTS";
  rosterAsOfDate: string;
  commentRequired: boolean;
}

/** Tên đầu điểm (Listening/Reading/...) — GradeEntryResponse chỉ có gradeEvaluationComponentId, phải tra tên qua đây để hiện đúng nhãn. */
export interface GradeEvaluationComponentResponse {
  id: number;
  gradeComponentSetupId: number;
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

/** GET không gate quyền riêng — Học sinh/Phụ huynh tự gọi được để tra danh sách setup sổ điểm của lớp. */
export function listGradeComponentSetups(classId: number): Promise<GradeComponentSetupResponse[]> {
  return apiRequest<GradeComponentSetupResponse[]>(`/classes/${classId}/grade-component-setups`);
}

/** GET không gate quyền riêng — Học sinh/Phụ huynh tự gọi được để tra tên đầu điểm. */
export function listGradeEvaluationComponents(setupId: number): Promise<GradeEvaluationComponentResponse[]> {
  return apiRequest<GradeEvaluationComponentResponse[]>(`/grade-component-setups/${setupId}/components`);
}

/** UC-53/UC-25: Overall/Level/Nhận xét/Ghi chú đã duyệt (OFFICIAL) — 404 nếu chưa có/chưa duyệt (bắt ở nơi gọi, không phải lỗi thật). */
export function getEvaluationResult(studentId: number, classId: number, academicTermId: number, evaluationType: string): Promise<GradeEvaluationResultResponse> {
  return apiRequest<GradeEvaluationResultResponse>(
    `/portal/parent/children/${studentId}/classes/${classId}/academic-terms/${academicTermId}/evaluation/${evaluationType}/result`
  );
}

/** UC-61: Học sinh tự xem điểm của chính mình (self-service, khác listGrades — Phụ huynh xem theo con+lớp cụ thể). */
export function listMyGrades(classId?: number): Promise<GradeEntryResponse[]> {
  return apiRequest<GradeEntryResponse[]>(`/students/me/grades${classId ? `?classId=${classId}` : ""}`);
}

/** UC-61: Overall/Level/Nhận xét/Ghi chú của chính mình — 404 nếu chưa có/chưa duyệt (bắt ở nơi gọi, không phải lỗi thật). */
export function getMyEvaluationResult(classId: number, academicTermId: number, evaluationType: string): Promise<GradeEvaluationResultResponse> {
  return apiRequest<GradeEvaluationResultResponse>(`/students/me/classes/${classId}/academic-terms/${academicTermId}/evaluation/${evaluationType}/result`);
}

/** UC-15 — khớp AttendanceMarkResponse thật. */
export interface AttendanceMarkResponse {
  id: number;
  attendanceSessionId: number;
  /** UC-64: nối đúng điểm danh vào buổi học cụ thể — BE đã trả sẵn, dùng để hiện ngày/giờ/số buổi ở "Nhật ký chuyên cần". */
  classSessionId: number;
  studentId: number;
  studentFullName: string;
  studentCode: string;
  status: "PRESENT" | "ABSENT" | "LATE" | "EXCUSED" | "EARLY_LEAVE";
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
  commentType: "DAILY";
  classSessionId: number;
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
  /** Nhận xét Hàng ngày kiểu mới (chỉ có ý nghĩa khi commentType=DAILY) — bổ sung ngoài SDD gốc.
   * Thang thái độ chốt lại 2026-08-12 (StudentComment.Attitude) — Yếu 20%/Trung bình 50%/Khá 70%/
   * Tốt 90%/Xuất sắc 100%. */
  attitude: "WEAK" | "AVERAGE" | "FAIR" | "GOOD" | "EXCELLENT" | null;
  homeworkPreviousScore: string | null;
  homeworkPreviousSpeakingScore: string | null;
  /**
   * V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — fix bug thật: interface này
   * (Portal học sinh/phụ huynh) thiếu hẳn 10 field Reading/Writing đã có sẵn ở backend từ V130/V137 và
   * đã dùng ở admin/src/features/academic/api.ts từ lâu — 2 bản interface trùng lặp bị lệch nhau, phía
   * Portal chưa bao giờ được cập nhật theo, khiến FE không đọc được dữ liệu Reading/Writing dù backend
   * đã trả về đầy đủ. V130 — "BTVN buổi trước - Offline" tách Reading/Writing (điểm % GV tự chấm tay),
   * chỉ khác null với buổi teacherType=VIETNAMESE.
   */
  homeworkPreviousReadingScore: string | null;
  homeworkPreviousWritingScore: string | null;
  homeworkNext: string | null;
  /** V130 — "BTVN - Offline" (giao buổi sau) tách Reading/Writing, chỉ khác null với buổi teacherType=VIETNAMESE. */
  homeworkNextReading: string | null;
  homeworkNextWriting: string | null;
  /** V65 (2026-07-30, bổ sung ngoài SDD gốc): id BẢN GIAO (ExerciseAssignment), không phải id Exercise nguồn. */
  homeworkNextExerciseAssignmentId: number | null;
  homeworkNextExerciseTitle: string | null;
  /** V65: id BẢN GIAO (ReviewVideoAssignment, đổi tên từ homeworkNextReviewVideoSetId — trước V65 trỏ thẳng ReviewVideoSet). */
  homeworkNextReviewVideoAssignmentId: number | null;
  homeworkNextReviewVideoSetTitle: string | null;
  /** V137/V150 — "BTVN - Online - Reading/Writing" (mirror homeworkNextExerciseAssignmentId/Title), chỉ khác null với buổi teacherType=VIETNAMESE. */
  homeworkNextReadingExerciseAssignmentId: number | null;
  homeworkNextReadingExerciseTitle: string | null;
  homeworkNextWritingExerciseAssignmentId: number | null;
  homeworkNextWritingExerciseTitle: string | null;
  /** Hạn nộp BTVN buổi sau (lấy từ dueAt của bản giao) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05. */
  homeworkNextDueAt: string | null;
  /** % tự tính từ exercise_attempts của buổi trước — không nhập tay được. */
  grammarPreviousProgress: string | null;
  /** % tự tính từ review_video_progress/submissions của buổi trước — không nhập tay được. */
  videoPreviousProgress: string | null;
  /** V137 — % tự động "BTVN buổi trước - Online - Reading/Writing" (mirror grammarPreviousProgress/videoPreviousProgress), chỉ khác null với buổi teacherType=VIETNAMESE. */
  readingPreviousProgress: string | null;
  writingPreviousProgress: string | null;
  /** BTVN buổi trước từng giao Offline (chữ tự do) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06, phân biệt "BTVN buổi trước" có 3 loại (Offline/kênh Bài/kênh Video). Loại trừ với grammarPreviousProgress. */
  homeworkPreviousOfflineText: string | null;
  note: string | null;
  /** "Bài học hôm nay" của buổi (chỉ có ý nghĩa khi commentType=DAILY) — bổ sung ngoài SDD gốc, 2026-07-29. */
  lessonContent: string | null;
}

/** UC-64 (bổ sung ngoài SDD gốc, 2026-07-29) — Cổng phụ huynh xem tiến độ BTVN đã giao cho con, chỉ xem không phải giao diện làm bài. */
export interface HomeworkProgressResponse {
  commentId: number;
  classSessionId: number | null;
  commentDate: string;
  grammarAssignmentId: number | null;
  grammarTitle: string | null;
  grammarOfflineText: string | null;
  grammarProgress: string | null;
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — null khi grammarAssignmentId null (chưa giao/giao offline), phân biệt "đạt"/"chưa đạt" thay vì chỉ nhìn %. */
  grammarPassed: boolean | null;
  /** V65 (2026-07-30, bổ sung ngoài SDD gốc): đổi tên từ videoSetId — giờ là id bản giao (ReviewVideoAssignment), không phải id ReviewVideoSet nguồn. */
  videoAssignmentId: number | null;
  videoTitle: string | null;
  videoProgress: string | null;
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — mirror grammarPassed. */
  videoPassed: boolean | null;
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
  // V60 (bổ sung ngoài SDD gốc, 2026-07-29): loại GV (VIETNAMESE/FOREIGN, null nếu chưa xác định).
  // sessionNumber tính động (1-based, đếm cả CANCELLED) — dùng hiện "Buổi N".
  teacherType: "VIETNAMESE" | "FOREIGN" | null;
  sessionNumber: number;
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

/** Dùng cho dropdown "Học kỳ" ở tab Lịch học & Chuyên cần — lọc buổi học/điểm danh theo [startDate, endDate] của kỳ chọn. */
export function listAcademicTerms(siteId: number): Promise<AcademicTermResponse[]> {
  return apiRequest<AcademicTermResponse[]>(`/academic-terms?siteId=${siteId}`);
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

/** UC-64 (2026-07-29): Cổng phụ huynh xem tiến độ BTVN đã giao cho con (chỉ các buổi có giao BTVN). */
export function listHomeworkProgress(studentId: number, classId: number): Promise<HomeworkProgressResponse[]> {
  return apiRequest<HomeworkProgressResponse[]>(`/portal/parent/children/${studentId}/classes/${classId}/homework`);
}

/** UC-64 (2026-07-29): Học sinh tự xem điểm danh của chính mình theo lớp (self-service, suy studentId từ JWT) — khác listAttendance (Phụ huynh xem theo con+lớp cụ thể). */
export function listMyAttendance(classId: number): Promise<AttendanceMarkResponse[]> {
  return apiRequest<AttendanceMarkResponse[]>(`/students/me/classes/${classId}/attendance`);
}

/** UC-64 (2026-07-29): Học sinh tự xem nhận xét ĐÃ DUYỆT của chính mình theo lớp (self-service, suy studentId từ JWT) — khác listComments (Phụ huynh xem theo con+lớp cụ thể). */
export function listMyComments(classId: number): Promise<StudentCommentResponse[]> {
  return apiRequest<StudentCommentResponse[]>(`/students/me/classes/${classId}/comments`);
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

export function markNotificationRead(id: number): Promise<NotificationResponse> {
  return apiRequest<NotificationResponse>(`/notifications/${id}/read`, { method: "POST" });
}

/**
 * UC-23a — khớp ReviewVideoSetResponse thật. GET /api/classes/{classId}/review-video-sets tự trả
 * đúng phạm vi nhìn thấy của học sinh (V98: bộ đã gán tường minh cho lớp NÀY qua
 * ReviewVideoSetClassAssignment, chỉ status=PUBLISHED — BE tự lọc, Portal không cần gọi thêm
 * endpoint theo curriculum). 404 (không 403) nếu học sinh không thuộc lớp — không lộ tồn tại ngoài
 * phạm vi. curriculumId nay luôn khác null (V98, chỉ dùng lọc/tìm kiếm ở Kho Video, không phải điều
 * kiện hiển thị) — classId không còn trên response.
 */
export interface ReviewVideoSetResponse {
  id: number;
  code: string;
  title: string;
  videoType: "CONNECTION" | "REFLEX";
  curriculumId: number;
  subjectId: number | null;
  displayOrder: number;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  publishedAt: string | null;
  createdBy: number;
  /** V98 — GV Việt Nam/nước ngoài phụ trách bộ video này. */
  teacherType: "VIETNAMESE" | "FOREIGN";
  /**
   * Bổ sung 2026-09-04 — tên Unit/SubTopic chứa Bộ video này (VD "UNIT 1: MY NEW SCHOOL" / "SUB TOPIC
   * 1: SCHOOL ACTIVITIES AND SUBJECTS"), mirror ExamResponse#unitTitle/subTopicTitle — Bộ đặt tên dễ
   * trùng lặp hình thức giữa nhiều Unit/SubTopic khác nhau. NULL khi Bộ chưa phân loại.
   */
  subTopicId: number | null;
  subTopicTitle: string | null;
  unitTitle: string | null;
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
  /** V59 — chỉ có ý nghĩa với videoType=CONNECTION, mặc định 80. */
  completionThresholdPercent: number;
  /** V59 — chỉ có ý nghĩa với videoType=CONNECTION, mặc định 1. */
  requiredViewCount: number;
}

export interface ReviewVideoProgressResponse {
  reviewVideoId: number;
  watchedSeconds: number;
  durationSeconds: number;
  watchedPercent: number;
  completed: boolean;
  /** V59 — số lượt xem đã đạt completionThresholdPercent (rollup từ review_video_watch_sessions). */
  viewCount: number;
  requiredViewCount: number;
}

export function listReviewVideoSetsByClass(classId: number): Promise<ReviewVideoSetResponse[]> {
  return apiRequest<ReviewVideoSetResponse[]>(`/classes/${classId}/review-video-sets`);
}

/**
 * V65/V70 self-service (fix bug thiếu hạn nộp ở Portal, 2026-07-31) — hạn nộp (dueAt) của (các)
 * bộ Video Ôn tập đã được giao "BTVN buổi sau" cho lớp tôi đang học ACTIVE. Trước đây chỉ có
 * GET /api/classes/{classId}/review-video-assignments (yêu cầu quyền Giáo viên, Học sinh gọi bị
 * chặn) — không có nguồn nào đọc được dueAt cho Portal. Mirror AssignedExerciseResponse (bài ngữ pháp).
 */
export interface MyReviewVideoAssignmentResponse {
  assignmentId: number;
  reviewVideoSetId: number;
  reviewVideoSetTitle: string;
  videoType: "CONNECTION" | "REFLEX";
  classId: number;
  className: string;
  availableFrom: string;
  dueAt: string;
  /** V123 — ngày buổi học GV đã giao BTVN này — null với bản giao TRƯỚC V123. */
  sessionDate: string | null;
}

export function listMyReviewVideoAssignments(classId?: number): Promise<MyReviewVideoAssignmentResponse[]> {
  return apiRequest<MyReviewVideoAssignmentResponse[]>(`/students/me/review-video-assignments${classId ? `?classId=${classId}` : ""}`);
}

export function listReviewVideos(setId: number): Promise<ReviewVideoResponse[]> {
  return apiRequest<ReviewVideoResponse[]>(`/review-video-sets/${setId}/videos`);
}

/** UC-23a (V59): mở 1 LƯỢT xem mới — gọi khi mở/mở lại video CONNECTION, TRƯỚC lần reportProgress đầu tiên. sessionId dùng cho mọi lần reportProgress tiếp theo của lượt này. */
export interface StartWatchSessionResponse {
  sessionId: number;
}

export function startReviewVideoWatchSession(videoId: number, assignmentId: number): Promise<StartWatchSessionResponse> {
  return apiRequest<StartWatchSessionResponse>(`/review-videos/${videoId}/watch-sessions?assignmentId=${assignmentId}`, { method: "POST" });
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — đọc lại tiến độ ĐÃ LƯU (không mở
 * lượt xem mới) — dùng để hiện đúng trạng thái đạt/chưa đạt ngay khi mở modal (không đợi report
 * sống) và để danh sách "Bài tập về nhà" tính đúng CONNECTION vào bộ đếm Cần hoàn thành/Đã nộp.
 */
export function getReviewVideoProgress(videoId: number, assignmentId: number): Promise<ReviewVideoProgressResponse> {
  return apiRequest<ReviewVideoProgressResponse>(`/review-videos/${videoId}/progress?assignmentId=${assignmentId}`);
}

/** UC-23a Main Flow bước 3 (V59): báo tiến độ xem (giây) cho ĐÚNG 1 lượt xem (watchSessionId) — BE tự lấy max(cũ, mới) trong phạm vi lượt đó, không bao giờ giảm. */
export function reportReviewVideoProgress(videoId: number, watchSessionId: number, watchedSeconds: number): Promise<ReviewVideoProgressResponse> {
  return apiRequest<ReviewVideoProgressResponse>(`/review-videos/${videoId}/progress`, {
    method: "PUT",
    body: JSON.stringify({ watchSessionId, watchedSeconds })
  });
}

/** UC-23b (V57) — câu hỏi gắn 1 mốc thời gian trong video REFLEX, mỗi câu tự có thời lượng ghi âm/số lần nộp lại riêng. */
export interface ReviewVideoQuestionResponse {
  id: number;
  reviewVideoId: number;
  timestampSeconds: number;
  prompt: string | null;
  maxRecordingSeconds: number;
  /** null = không giới hạn số lần nộp lại. */
  maxAttempts: number | null;
  displayOrder: number;
}

export function listReviewVideoQuestions(videoId: number): Promise<ReviewVideoQuestionResponse[]> {
  return apiRequest<ReviewVideoQuestionResponse[]>(`/review-videos/${videoId}/questions`);
}

/** UC-23b (V57) — khớp ReviewVideoSubmissionResponse thật (dùng chung Học sinh xem bài của mình + Giáo viên chấm). 1 dòng = 1 attempt, giữ lịch sử. */
export interface ReviewVideoSubmissionResponse {
  id: number;
  reviewVideoQuestionId: number;
  attemptNumber: number;
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

/**
 * UC-23b Main Flow bước 3 (V57): nộp audio trả lời cho 1 CÂU HỎI — nộp lại tạo attempt MỚI, giữ lịch sử
 * (không ghi đè); từ chối nếu đã hết maxAttempts của câu hỏi đó (409/422 từ BE).
 * integrityEvents (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31, tùy chọn): UC-23b không có
 * "phiên bắt đầu ghi âm" ở backend nên sự kiện giám sát thoát ra ngoài trong lúc ghi âm được đệm ở client
 * (xem useIntegrityMonitor) rồi gửi kèm cùng lúc nộp, thay vì real-time như Exercise — xem
 * recordIntegrityEvents cho nhánh Exercise.
 */
export function submitReviewVideoQuestionAudio(
  questionId: number,
  assignmentId: number,
  audioUrl: string,
  integrityEvents?: RecordIntegrityEventsRequest
): Promise<ReviewVideoSubmissionResponse> {
  return apiRequest<ReviewVideoSubmissionResponse>(`/review-video-questions/${questionId}/submissions?assignmentId=${assignmentId}`, {
    method: "PUT",
    body: JSON.stringify({ audioUrl, integrityEvents: integrityEvents ?? null })
  });
}

/** Attempt MỚI NHẤT đã nộp cho 1 câu hỏi — trả về undefined (204) nếu chưa nộp lần nào. */
export function getMyLatestReviewVideoSubmission(questionId: number, assignmentId: number): Promise<ReviewVideoSubmissionResponse | undefined> {
  return apiRequest<ReviewVideoSubmissionResponse | undefined>(`/review-video-questions/${questionId}/submissions/latest?assignmentId=${assignmentId}`);
}

/**
 * V139 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22) — UC-23b V2: tiến trình tuần tự
 * viết → AI chấm ngữ pháp → đạt → ghi âm → AI chấm nội dung → đạt của 1 câu hỏi Video phản xạ (REFLEX).
 * Không giới hạn số lần thử lại — nộp lại chỉ sửa đè dòng tiến trình hiện có (không giữ lịch sử như
 * ReviewVideoSubmissionResponse ở luồng cũ).
 */
export interface ReflexQuestionProgressResponse {
  questionId: number;
  answerText: string | null;
  writingScorePercent: number | null;
  writingFeedback: string | null;
  writingPassed: boolean;
  writingAttemptCount: number;
  /**
   * V141 — gợi ý câu trả lời đã sửa lỗi ngữ pháp (CHỈ sửa lỗi trong câu học sinh viết, không phải câu
   * mẫu tự bịa). NULL khi đã đạt hoặc chưa nộp/chưa chấm được — chỉ hiện khi writingAttemptCount >= 3
   * VÀ vẫn chưa đạt.
   */
  writingCorrectedAnswer: string | null;
  audioUrl: string | null;
  speakingScorePercent: number | null;
  speakingFeedback: string | null;
  speakingPassed: boolean;
  speakingAttemptCount: number;
  /** true khi CẢ 2 bước đã đạt — câu tiếp theo được mở khoá (BE không tự chặn nộp câu sau, FE tự khoá UI theo cờ này). */
  questionPassed: boolean;
  updatedAt: string;
}

/** Bước 1: nộp câu trả lời viết, AI chấm ngữ pháp ngay (>=70% mới đạt). */
export function submitReflexWrittenAnswer(questionId: number, assignmentId: number, answerText: string): Promise<ReflexQuestionProgressResponse> {
  return apiRequest<ReflexQuestionProgressResponse>(`/review-video-questions/${questionId}/reflex-progress/writing?assignmentId=${assignmentId}`, {
    method: "PUT",
    body: JSON.stringify({ answerText })
  });
}

/** Bước 2 — CHỈ chấp nhận khi bước 1 đã đạt: nộp audio (đã upload sẵn qua uploadMedia), AI transcribe + chấm nội dung ngay. */
export function submitReflexSpokenAnswer(questionId: number, assignmentId: number, audioUrl: string): Promise<ReflexQuestionProgressResponse> {
  return apiRequest<ReflexQuestionProgressResponse>(`/review-video-questions/${questionId}/reflex-progress/speaking?assignmentId=${assignmentId}`, {
    method: "PUT",
    body: JSON.stringify({ audioUrl })
  });
}

/** Tiến trình đã lưu của MỌI câu hỏi thuộc video này trong lần giao đang mở — dùng để dựng lại đúng trạng thái khoá/mở khi vào/tải lại trang. */
export function listMyReflexProgress(assignmentId: number): Promise<ReflexQuestionProgressResponse[]> {
  return apiRequest<ReflexQuestionProgressResponse[]>(`/review-video-assignments/${assignmentId}/reflex-progress`);
}

/**
 * V76 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — câu hỏi trắc nghiệm tự chấm
 * của video CONNECTION, hiện SAU KHI xem xong 1 lượt (khác REFLEX gắn mốc thời gian giữa video).
 * isCorrect luôn null ở đây (chưa nộp bài) — chỉ lộ đúng/sai sau khi submitReviewVideoConnectionAnswers.
 */
export interface ReviewVideoConnectionChoiceResponse {
  id: number;
  choiceLabel: string;
  content: string;
  isCorrect: boolean | null;
  displayOrder: number;
}

export interface ReviewVideoConnectionQuestionResponse {
  id: number;
  reviewVideoId: number;
  prompt: string;
  displayOrder: number;
  choices: ReviewVideoConnectionChoiceResponse[];
}

export function listReviewVideoConnectionQuestions(videoId: number): Promise<ReviewVideoConnectionQuestionResponse[]> {
  return apiRequest<ReviewVideoConnectionQuestionResponse[]>(`/review-videos/${videoId}/connection-questions`);
}

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — chỉ trả về NHÓM câu hỏi đã gán cho
 * đúng lượt xem này (khác listReviewVideoConnectionQuestions trả toàn bộ ngân hàng câu hỏi của video).
 */
export function listReviewVideoConnectionQuestionsForSession(watchSessionId: number): Promise<ReviewVideoConnectionQuestionResponse[]> {
  return apiRequest<ReviewVideoConnectionQuestionResponse[]>(`/review-video-watch-sessions/${watchSessionId}/connection-questions`);
}

export interface ConnectionAnswerItem {
  questionId: number;
  selectedChoiceId: number;
}

export interface ConnectionAnswerResult {
  questionId: number;
  selectedChoiceId: number;
  correct: boolean;
  correctChoiceId: number | null;
}

export interface ReviewVideoConnectionQuizResultResponse {
  results: ConnectionAnswerResult[];
  progress: ReviewVideoProgressResponse;
}

/**
 * Nộp TOÀN BỘ câu trả lời cho ĐÚNG 1 lượt xem (watchSessionId) — khớp cặp 1-1 "xem lượt nào, trả
 * lời lượt đó". BE chặn (422) nếu lượt chưa đạt ngưỡng xem hoặc lượt đó đã nộp đủ rồi.
 */
export function submitReviewVideoConnectionAnswers(
  watchSessionId: number,
  answers: ConnectionAnswerItem[]
): Promise<ReviewVideoConnectionQuizResultResponse> {
  return apiRequest<ReviewVideoConnectionQuizResultResponse>(`/review-video-watch-sessions/${watchSessionId}/connection-answers`, {
    method: "PUT",
    body: JSON.stringify({ answers })
  });
}

/** Toàn bộ lịch sử các lần đã nộp cho 1 câu hỏi (mới nhất trước). */
export function listMyReviewVideoSubmissionHistory(questionId: number, assignmentId: number): Promise<ReviewVideoSubmissionResponse[]> {
  return apiRequest<ReviewVideoSubmissionResponse[]>(`/review-video-questions/${questionId}/submissions/history?assignmentId=${assignmentId}`);
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
  /** V89, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05: BTVN <ngưỡng đạt phải làm lại — NULL khi chưa chấm xong (totalScore null). */
  myLatestPercentage: number | null;
  myLatestPassed: boolean | null;
  /** V123 — GV Việt Nam/nước ngoài phụ trách Đề chứa Bài này (Exam.teacherType). */
  teacherType: "VIETNAMESE" | "FOREIGN";
  /** V123 — ngày buổi học GV đã giao BTVN này (chọn ở Nhận xét học viên UC-21) — null với bản giao TRƯỚC V123. */
  sessionDate: string | null;
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — true khi bấm làm lại được NGAY
   * (còn lượt theo allowRetake/maxAttempts, bản giao còn ACTIVE) — KHÔNG đòi hỏi lượt gần nhất phải
   * FULLY_GRADED trước, khác với suy luận cũ của FE (xem needsRetake/canRetryWhilePending ở
   * AssignmentsTab.tsx).
   */
  canStartNewAttempt: boolean;
  /**
   * V150 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — NULL = bản giao lẻ 1 Bài
   * (hành vi cũ). Có giá trị = 1 trong N thẻ BTVN cùng thuộc 1 "Lô giao theo kỹ năng" (giáo viên chọn
   * 1 Lesson ở Nhận xét học viên, hệ thống giao TOÀN BỘ Bài Published cùng kỹ năng trong đó) — FE gom
   * các thẻ cùng homeworkBatchId thành 1 thẻ/1 màn làm bài liên tục, xem AssignmentsTab.tsx.
   */
  homeworkBatchId: number | null;
  /** V150 — điểm tối đa của Bài, dùng để cộng dồn % gộp trên thẻ/modal 1 Lô (xem BatchTakeExerciseModal). */
  exerciseTotalPoints: number;
  /** V150 — id/tên Lesson (Exam) + nhóm kỹ năng chứa Bài này, dùng dựng tiêu đề gộp cho 1 Lô (VD "Ngữ pháp — Lesson 1"). */
  examId: number;
  examTitle: string;
  skillCategory: "READING" | "WRITING" | "VOCAB_GRAMMAR" | "LISTENING" | null;
  /**
   * Bổ sung 2026-09-04 — tên Unit/SubTopic chứa Lesson (Exam) này (VD "UNIT 1: MY NEW SCHOOL" / "SUB
   * TOPIC 1: SCHOOL ACTIVITIES AND SUBJECTS") — Lesson đánh số lặp lại (Lesson 1, 2, 3...) giữa các
   * Unit/SubTopic khác nhau nên chỉ hiện examTitle dễ nhầm lẫn. NULL khi Exam chưa phân loại.
   */
  unitTitle: string | null;
  subTopicTitle: string | null;
}

export function listMyAssignedExercises(classId?: number): Promise<AssignedExerciseResponse[]> {
  return apiRequest<AssignedExerciseResponse[]>(`/students/me/exercises${classId ? `?classId=${classId}` : ""}`);
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — chỉ lấy
 * allowRetake/maxAttempts/passThresholdPercent để hiện popup kết quả sau
 * khi nộp bài (số lượt còn lại để làm lại). Học sinh xem được đề đã được
 * giao cho mình (rào requireCanViewExercise ở BE, cùng rào listExerciseQuestions).
 */
export interface ExerciseMetaResponse {
  id: number;
  title: string;
  allowRetake: boolean;
  maxAttempts: number | null;
  passThresholdPercent: number;
  /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-22) — thời gian làm bài tính từ lúc mở
   * bài (ExerciseAttempt.startedAt), khác hạn nộp (dueAt). NULL = không giới hạn. */
  timeLimitMinutes: number | null;
}

export function getExercise(exerciseId: number): Promise<ExerciseMetaResponse> {
  return apiRequest<ExerciseMetaResponse>(`/exercises/${exerciseId}`);
}

/** UC-24/UC-27 (BE bổ sung): phương án chọn cho câu trắc nghiệm — CHỦ Ý không có isCorrect, chỉ lộ qua StudentAnswerResponse.correctChoiceIds sau khi nộp bài. */
export interface ExerciseQuestionChoiceResponse {
  id: number;
  choiceLabel: string;
  content: string;
  /** V143 — ảnh riêng cho lựa chọn (câu hỏi Listening dạng chọn đáp án bằng hình), NULL = đáp án chữ. */
  imageUrl: string | null;
  displayOrder: number;
}

/**
 * choices chỉ có giá trị với câu MULTIPLE_CHOICE/MULTIPLE_ANSWER/TRUE_FALSE — rỗng với
 * ESSAY/SPEAKING/FILL_IN_BLANK/WORD_BANK/SENTENCE_BUILDING. skill/audioUrl/referencePassage/
 * structuredContent/groupKey (V78, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04):
 * dùng để render Điền từ - Hộp từ vựng/Sắp xếp câu (structuredContent), audio prompt của Nghe
 * (skill=LISTENING), và gộp "Đọc hiểu — lưới" theo groupKey.
 */
export interface ExerciseQuestionResponse {
  id: number;
  exerciseId: number;
  questionId: number;
  questionType: string;
  questionContent: string;
  displayOrder: number;
  points: number;
  choices: ExerciseQuestionChoiceResponse[];
  skill: string | null;
  audioUrl: string | null;
  referencePassage: string | null;
  structuredContent: { blanks?: string[]; chunks?: string[]; wordBankOptions?: string[]; wordBox?: string[] } | null;
  groupKey: string | null;
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — ảnh minh họa dùng cho ESSAY/WORD_BANK/SENTENCE_BUILDING. */
  imageUrl: string | null;
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
  /** V89, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05: NULL khi totalScore chưa có (chưa chấm xong). */
  percentage: number | null;
  passed: boolean | null;
}

/** Main Flow bước 1: mở lượt làm mới — LUÔN tạo attempt mới (không tự resume), chỉ gọi khi thật sự chưa có attempt nào hoặc muốn làm lại. */
export function startAttempt(exerciseId: number, assignmentId: number): Promise<ExerciseAttemptResponse> {
  return apiRequest<ExerciseAttemptResponse>(`/exercises/${exerciseId}/attempts?assignmentId=${assignmentId}`, { method: "POST" });
}

export function getAttempt(attemptId: number): Promise<ExerciseAttemptResponse> {
  return apiRequest<ExerciseAttemptResponse>(`/attempts/${attemptId}`);
}

export interface SaveAnswerRequest {
  questionId: number;
  answerText?: string;
  selectedChoiceIds?: number[];
  audioAnswerUrl?: string;
  /** V78 — WORD_BANK (đáp án theo đúng thứ tự chỗ trống) / SENTENCE_BUILDING (thứ tự khối đã chọn). */
  structuredAnswer?: string[];
}

/**
 * correctChoiceIds/correctAnswerText/correctStructuredContent chỉ được điền khi attempt đã nộp
 * (không còn IN_PROGRESS) VÀ exercise.showCorrectAnswers=true. explanation điền thêm khi: câu KHÔNG
 * tự chấm được (ESSAY/SPEAKING, luôn hiện) HOẶC câu tự chấm được nhưng trả lời SAI (isCorrect=false)
 * — V54. structuredAnswer/correctStructuredContent (V78, bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-08-04): WORD_BANK/SENTENCE_BUILDING.
 *
 * UC-24/A4, UC-27/A2 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05): nếu đề có
 * exercises.max_attempts, 3 field correctChoiceIds/correctAnswerText/correctStructuredContent (và
 * explanation cho câu tự chấm) CHỈ được điền từ lượt làm CUỐI CÙNG (attemptNumber == maxAttempts)
 * trở đi — các lượt trước dù isAutoGradable đã có isCorrect vẫn null hết 3 field trên. Chỉ áp dụng
 * cho câu tự chấm được (isAutoGradable=true) — ESSAY/SPEAKING không bị gate, giữ hành vi cũ.
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
  structuredAnswer: string[] | null;
  correctStructuredContent: { blanks?: string[]; chunks?: string[] } | null;
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — điểm/nhận xét câu tự luận/nói
   * (ESSAY/SPEAKING) đã chấm (tay hoặc AI). NULL khi câu tự chấm được (xem autoScore/isCorrect thay)
   * HOẶC chưa được chấm (VD đang chờ AI/GV).
   */
  gradingScore: number | null;
  gradingMaxScore: number | null;
  gradingFeedback: string | null;
  gradingSource: "HUMAN" | "AI" | null;
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
 * V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — UC-24/A4, UC-27/A2: học sinh
 * ĐÃ ĐẠT nhưng còn lượt làm lại (retake) tự nguyện dừng lại NGAY, đổi lại được xem đáp án đúng của
 * lượt vừa đạt (bình thường phải làm hết maxAttempts mới xem được). Đóng LUÔN bản giao — không hoàn
 * tác được, FE phải hỏi xác nhận trước khi gọi (xem TakeExerciseModal/BatchTakeExerciseModal).
 */
export function revealAndCloseAttempt(attemptId: number): Promise<ExerciseAttemptResponse> {
  return apiRequest<ExerciseAttemptResponse>(`/attempts/${attemptId}/reveal-and-close`, { method: "POST" });
}

// ===================== Giám sát thoát màn hình khi làm bài (bổ sung ngoài SDD gốc, xác nhận 2026-07-31) =====================

/** Khớp AttemptIntegrityEvent.EventType thật ở backend. */
export type IntegrityEventType = "OUT_OF_FOCUS" | "FULLSCREEN_EXITED";

export interface IntegrityEventInput {
  eventType: IntegrityEventType;
  startedAt: string;
  endedAt: string;
  userAgent?: string;
}

/** Khớp RecordIntegrityEventsRequest thật — chỉ gửi khoảng "thoát ra ngoài" ĐÃ KẾT THÚC, không gửi trạng thái đang diễn ra. */
export interface RecordIntegrityEventsRequest {
  events: IntegrityEventInput[];
}

export interface IntegrityEventBatchResponse {
  savedCount: number;
  totalViolationCount: number;
  totalViolationDurationSeconds: number;
  notifiedByThisBatch: boolean;
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — true = bài làm vừa bị hệ thống dừng ép do vượt ngưỡng vi phạm. */
  attemptStopped: boolean;
}

/** Học sinh gửi theo lô các sự kiện thoát ra ngoài khi đang làm 1 lượt Exercise — dùng chung với useIntegrityMonitor. */
export function recordIntegrityEvents(attemptId: number, request: RecordIntegrityEventsRequest): Promise<IntegrityEventBatchResponse> {
  return apiRequest<IntegrityEventBatchResponse>(`/attempts/${attemptId}/integrity-events`, {
    method: "POST",
    body: JSON.stringify(request)
  });
}

// ===================== Gợi ý tapescript khi Nghe (bổ sung ngoài SDD gốc, xác nhận 2026-08-06) =====================

export interface ListeningPlayProgressResponse {
  playCount: number;
  hintUnlockThreshold: number;
  hintUnlocked: boolean;
}

/** V144 — gợi ý chỉ còn transcript (không còn lộ đáp án đúng/giải thích), xem Javadoc ListeningHintService#getHint. */
export interface ListeningHintResponse {
  transcript: string | null;
}

/** Gọi mỗi khi audio của 1 câu hỏi Nghe phát tới cuối (sự kiện `ended`) — KHÔNG gọi khi chỉ bấm Play/tạm dừng giữa chừng. */
export function recordListeningPlay(attemptId: number, questionId: number): Promise<ListeningPlayProgressResponse> {
  return apiRequest<ListeningPlayProgressResponse>(`/attempts/${attemptId}/listening-plays`, {
    method: "POST",
    body: JSON.stringify({ questionId })
  });
}

/** Chỉ gọi khi hintUnlocked=true (từ recordListeningPlay) — gọi thành công sẽ được backend ghi 1 lượt dùng gợi ý để thống kê. */
export function getListeningHint(attemptId: number, questionId: number): Promise<ListeningHintResponse> {
  return apiRequest<ListeningHintResponse>(`/attempts/${attemptId}/listening-hint?questionId=${questionId}`);
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
