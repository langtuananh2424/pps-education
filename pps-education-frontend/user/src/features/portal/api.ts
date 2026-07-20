import { apiRequest } from "@/lib/apiClient";
import type { Page } from "@/types";

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

/** UC-20 (V39 — công bố thay duyệt) — khớp GradeEntryResponse thật (chỉ điểm PUBLISHED được BE lọc sẵn ở portal). */
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
  status: "DRAFT" | "PUBLISHED";
  enteredBy: number;
  publishedBy: number | null;
  publishedAt: string | null;
}

/** UC-53 — Overall/Level theo kỳ đánh giá, chỉ trả về khi đã PUBLISHED (BE lọc sẵn). */
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
  status: "DRAFT" | "PUBLISHED";
  enteredBy: number;
  publishedBy: number | null;
  publishedAt: string | null;
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

export function getPortalClass(classId: number): Promise<PortalClassResponse> {
  return apiRequest<PortalClassResponse>(`/classes/${classId}`);
}

export function listGradePeriods(curriculumId: number): Promise<GradePeriodResponse[]> {
  return apiRequest<GradePeriodResponse[]>(`/curriculums/${curriculumId}/grade-periods`);
}

/** UC-53/UC-25: Overall/Level đã công bố — 404 nếu chưa có/chưa công bố (bắt ở nơi gọi, không phải lỗi thật). */
export function getPeriodResult(studentId: number, classId: number, gradePeriodId: number): Promise<GradePeriodResultResponse> {
  return apiRequest<GradePeriodResultResponse>(`/portal/parent/children/${studentId}/classes/${classId}/periods/${gradePeriodId}/result`);
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

/** UC-23 — khớp LessonResponse thật. Chỉ hiển thị status=PUBLISHED ở phía Portal (lọc client-side). */
export interface LessonResponse {
  id: number;
  code: string;
  title: string;
  curriculumId: number;
  classId: number;
  subjectId: number | null;
  lessonOrder: number;
  lessonType: string;
  durationMinutes: number | null;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  publishedAt: string | null;
  createdBy: number;
}

export interface LessonMaterialResponse {
  id: number;
  lessonId: number;
  materialType: string;
  title: string;
  fileUrl: string;
  fileSizeBytes: number | null;
  durationSeconds: number | null;
  displayOrder: number;
  isDownloadable: boolean;
}

export function listLessonsByClass(classId: number): Promise<LessonResponse[]> {
  return apiRequest<LessonResponse[]>(`/classes/${classId}/lessons`);
}

export function listLessonMaterials(lessonId: number): Promise<LessonMaterialResponse[]> {
  return apiRequest<LessonMaterialResponse[]>(`/lessons/${lessonId}/materials`);
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

/** questionContent/points chỉ mang tính xem trước — KHÔNG có choices/đáp án đúng (field đó chỉ lộ ra qua GET /api/questions/{id}, endpoint riêng có gate quyền lms.exercise.manage, học viên không gọi được). */
export interface ExerciseQuestionResponse {
  id: number;
  exerciseId: number;
  questionId: number;
  questionType: string;
  questionContent: string;
  displayOrder: number;
  points: number;
}

export function listExerciseQuestions(exerciseId: number): Promise<ExerciseQuestionResponse[]> {
  return apiRequest<ExerciseQuestionResponse[]>(`/exercises/${exerciseId}/questions`);
}
