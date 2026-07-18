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

/** UC-20 — khớp GradeEntryResponse thật (chỉ điểm đã duyệt được BE lọc sẵn ở portal). */
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
  status: string;
  enteredBy: number;
  submittedAt: string | null;
  approvedBy: number | null;
  approvedAt: string | null;
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
