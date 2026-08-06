import { apiRequest, apiRequestBlob } from "@/lib/apiClient";

// ===================== Khung chương trình (UC-16/17) =====================

export interface CurriculumResponse {
  id: number;
  code: string;
  name: string;
  siteId: number | null;
  parentCurriculumId: number | null;
  classCategory: "MAIN" | "SUPPLEMENTARY" | "EXAM_PREP" | "OTHER" | null;
  level: string | null;
  totalPeriods: number | null;
  defaultGradePassThreshold: number | null;
  status: string;
  createdBy: number;
  approvedBy: number | null;
}

export function listCurriculums(): Promise<CurriculumResponse[]> {
  return apiRequest<CurriculumResponse[]>("/curriculums");
}

export function getCurriculum(id: number): Promise<CurriculumResponse> {
  return apiRequest<CurriculumResponse>(`/curriculums/${id}`);
}

export interface CreateCurriculumRequest {
  code: string;
  name: string;
  classCategory: string;
  level?: string;
  totalPeriods?: number;
  defaultGradePassThreshold?: number;
}

/** UC-16 Main Flow bước 1-3: khởi tạo khung chương trình chuẩn mới (siteId luôn null). */
export function createCurriculum(request: CreateCurriculumRequest): Promise<CurriculumResponse> {
  return apiRequest<CurriculumResponse>("/curriculums", { method: "POST", body: JSON.stringify(request) });
}

export interface UpdateCurriculumRequest {
  name: string;
  level?: string;
  totalPeriods?: number;
  defaultGradePassThreshold?: number;
  status: string;
  confirm: boolean;
}

export function updateCurriculum(id: number, request: UpdateCurriculumRequest): Promise<CurriculumResponse> {
  return apiRequest<CurriculumResponse>(`/curriculums/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export interface CurriculumSubjectResponse {
  id: number;
  curriculumId: number;
  subjectCode: string;
  name: string;
  periodCount: number | null;
  displayOrder: number;
}

export interface CreateCurriculumSubjectRequest {
  subjectCode: string;
  name: string;
  periodCount?: number;
  displayOrder?: number;
}

export function listCurriculumSubjects(curriculumId: number): Promise<CurriculumSubjectResponse[]> {
  return apiRequest<CurriculumSubjectResponse[]>(`/curriculums/${curriculumId}/subjects`);
}

export function addCurriculumSubject(curriculumId: number, request: CreateCurriculumSubjectRequest): Promise<CurriculumSubjectResponse> {
  return apiRequest<CurriculumSubjectResponse>(`/curriculums/${curriculumId}/subjects`, { method: "POST", body: JSON.stringify(request) });
}

export interface CreateCustomCurriculumRequest {
  code: string;
  parentCurriculumId: number;
  siteId: number;
  name?: string;
}

/** UC-16b Main Flow bước 1-2: tạo bản sao tùy biến từ khung chương trình gốc cho 1 điểm trường. */
export function createCustomCurriculum(request: CreateCustomCurriculumRequest): Promise<CurriculumResponse> {
  return apiRequest<CurriculumResponse>("/curriculums/custom", { method: "POST", body: JSON.stringify(request) });
}

export interface UpdateCustomCurriculumRequest {
  name: string;
  level?: string;
  totalPeriods?: number;
  defaultGradePassThreshold?: number;
}

export function updateCustomCurriculum(id: number, request: UpdateCustomCurriculumRequest): Promise<CurriculumResponse> {
  return apiRequest<CurriculumResponse>(`/curriculums/custom/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export interface CurriculumApprovalResponse {
  id: number;
  curriculumId: number;
  curriculumCode: string;
  curriculumName: string;
  status: string;
  submittedBy: number;
  submittedAt: string;
  approverId: number | null;
  decision: string | null;
  comment: string | null;
  decidedAt: string | null;
}

/** UC-16b Main Flow bước 4-5: nộp bản tùy biến lên hàng chờ duyệt (cũng dùng lại cho A1 UC-17 — đề xuất lại sau từ chối). */
export function submitCurriculumForApproval(id: number): Promise<CurriculumApprovalResponse> {
  return apiRequest<CurriculumApprovalResponse>(`/curriculums/custom/${id}/submit`, { method: "POST" });
}

/** UC-17 Main Flow bước 1: Trưởng phòng đào tạo xem hàng chờ duyệt tùy biến. */
export function listPendingCurriculumApprovals(): Promise<CurriculumApprovalResponse[]> {
  return apiRequest<CurriculumApprovalResponse[]>("/curriculums/approvals/pending");
}

/** UC-17 Main Flow bước 3-5: duyệt/từ chối. */
export function decideCurriculumApproval(approvalFlowId: number, decision: "APPROVED" | "REJECTED", comment?: string): Promise<CurriculumApprovalResponse> {
  return apiRequest<CurriculumApprovalResponse>(`/curriculums/approvals/${approvalFlowId}/decision`, { method: "POST", body: JSON.stringify({ decision, comment }) });
}

// ===================== Lớp học (UC-18) =====================

export interface ClassResponse {
  id: number;
  classCode: string;
  name: string;
  siteId: number;
  siteName: string;
  curriculumId: number;
  curriculumCode: string;
  classType: "LINKED" | "OPEN";
  classCategory: string | null;
  maxStudents: number;
  minStudents: number | null;
  startDate: string;
  endDate: string | null;
  academicYear: string | null;
  status: "PLANNED" | "OPEN_ENROLLMENT" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
}

export interface CreateClassRequest {
  classCode: string;
  name: string;
  siteId: number;
  curriculumId: number;
  classType: "LINKED" | "OPEN";
  maxStudents: number;
  minStudents?: number;
  startDate: string;
  endDate?: string;
  academicYear?: string;
}

export interface UpdateClassRequest {
  name: string;
  maxStudents: number;
  minStudents?: number;
  startDate: string;
  endDate?: string;
  academicYear?: string;
  status: ClassResponse["status"];
}

/** UC-18 Main Flow bước 3: dropdown site -> lớp của site đó; lọc thêm theo curriculum. Giáo viên chỉ thấy lớp thuộc site được gán (site_teachers). */
export function listClasses(params?: { query?: string; siteId?: number; curriculumId?: number; classCategory?: string }): Promise<ClassResponse[]> {
  const qs = new URLSearchParams();
  if (params?.query?.trim()) qs.set("query", params.query.trim());
  if (params?.siteId) qs.set("siteId", String(params.siteId));
  if (params?.curriculumId) qs.set("curriculumId", String(params.curriculumId));
  if (params?.classCategory) qs.set("classCategory", params.classCategory);
  const suffix = qs.toString() ? `?${qs.toString()}` : "";
  return apiRequest<ClassResponse[]>(`/classes${suffix}`);
}

export function getClass(id: number): Promise<ClassResponse> {
  return apiRequest<ClassResponse>(`/classes/${id}`);
}

export function createClass(request: CreateClassRequest): Promise<ClassResponse> {
  return apiRequest<ClassResponse>("/classes", { method: "POST", body: JSON.stringify(request) });
}

export function updateClass(id: number, request: UpdateClassRequest): Promise<ClassResponse> {
  return apiRequest<ClassResponse>(`/classes/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export interface ClassTeacherResponse {
  id: number;
  classId: number;
  teacherUserId: number;
  teacherFullName: string;
  teacherRole: "PRIMARY" | "ASSISTANT" | "SUBSTITUTE";
  subjectId: number | null;
  assignedFrom: string | null;
  assignedTo: string | null;
}

export interface AssignTeacherRequest {
  teacherUserId: number;
  teacherRole?: ClassTeacherResponse["teacherRole"];
  subjectId?: number;
  assignedFrom?: string;
}

export function listClassTeachers(classId: number): Promise<ClassTeacherResponse[]> {
  return apiRequest<ClassTeacherResponse[]>(`/classes/${classId}/teachers`);
}

/** UC-18 A3: nếu giáo viên chưa được gán vào điểm trường của lớp, backend tự tạo liên kết site_teachers, không chặn thao tác. */
export function assignClassTeacher(classId: number, request: AssignTeacherRequest): Promise<ClassTeacherResponse> {
  return apiRequest<ClassTeacherResponse>(`/classes/${classId}/teachers`, { method: "POST", body: JSON.stringify(request) });
}

export interface EndTeacherAssignmentRequest {
  assignedTo: string;
}

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — kết thúc phụ trách (giáo viên lớp đổi theo kỳ). */
export function endClassTeacherAssignment(
  classId: number,
  classTeacherId: number,
  request: EndTeacherAssignmentRequest
): Promise<ClassTeacherResponse> {
  return apiRequest<ClassTeacherResponse>(`/classes/${classId}/teachers/${classTeacherId}/end`, {
    method: "PUT",
    body: JSON.stringify(request)
  });
}

// ===================== Giai đoạn/Học kỳ (UC-18, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31) =====================
// Giới hạn theo điểm trường (site), độc lập với lớp học — 1 lớp tồn tại xuyên suốt nhiều kỳ. Hồ sơ lớp/học
// sinh theo kỳ (báo cáo & thống kê) là dữ liệu tính ra từ các bảng đã có ngày tháng, chưa triển khai ở đây.

export interface AcademicTermResponse {
  id: number;
  siteId: number;
  siteName: string;
  code: string;
  name: string;
  startDate: string;
  endDate: string;
}

export interface CreateAcademicTermRequest {
  siteId: number;
  code: string;
  name: string;
  startDate: string;
  endDate: string;
}

export interface UpdateAcademicTermRequest {
  name: string;
  startDate: string;
  endDate: string;
}

export function listAcademicTerms(siteId: number): Promise<AcademicTermResponse[]> {
  return apiRequest<AcademicTermResponse[]>(`/academic-terms?siteId=${siteId}`);
}

export function createAcademicTerm(request: CreateAcademicTermRequest): Promise<AcademicTermResponse> {
  return apiRequest<AcademicTermResponse>("/academic-terms", { method: "POST", body: JSON.stringify(request) });
}

export function updateAcademicTerm(id: number, request: UpdateAcademicTermRequest): Promise<AcademicTermResponse> {
  return apiRequest<AcademicTermResponse>(`/academic-terms/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export interface ClassEnrollmentResponse {
  id: number;
  classId: number;
  studentId: number;
  studentFullName: string;
  studentCode: string;
  studentDateOfBirth: string | null;
  enrolledDate: string;
  withdrawnDate: string | null;
  status: string;
  withdrawReason: string | null;
}

export interface EnrollStudentRequest {
  studentId: number;
  enrolledDate: string;
}

export interface WithdrawEnrollmentRequest {
  withdrawnDate: string;
  reason?: string;
}

export function listClassEnrollments(classId: number): Promise<ClassEnrollmentResponse[]> {
  return apiRequest<ClassEnrollmentResponse[]>(`/classes/${classId}/enrollments`);
}

export function enrollStudent(classId: number, request: EnrollStudentRequest): Promise<ClassEnrollmentResponse> {
  return apiRequest<ClassEnrollmentResponse>(`/classes/${classId}/enrollments`, { method: "POST", body: JSON.stringify(request) });
}

export function withdrawEnrollment(classId: number, enrollmentId: number, request: WithdrawEnrollmentRequest): Promise<ClassEnrollmentResponse> {
  return apiRequest<ClassEnrollmentResponse>(`/classes/${classId}/enrollments/${enrollmentId}/withdraw`, { method: "POST", body: JSON.stringify(request) });
}

/** Khớp ClassEnrollmentBatchImportResponse thật (UC-65, bổ sung ngoài SDD gốc). Ghi danh học sinh ĐÃ TỒN TẠI SẴN theo lô — không tạo học sinh mới. */
export interface ClassEnrollmentBatchImportResponse {
  id: number;
  sourceFileName: string;
  totalRows: number | null;
  successRows: number;
  failedRows: number;
  status: string;
  errorSummary: Record<string, unknown>[];
}

/** UC-65 (bổ sung): tải file mẫu ghi danh học sinh theo lô — 2 cột (mã học sinh*, ngày ghi danh). */
export function downloadEnrollmentImportTemplate(classId: number): Promise<Blob> {
  return apiRequestBlob(`/classes/${classId}/enrollments/import-template`);
}

/** UC-65 (bổ sung): nhập file đã điền -- ghi danh hàng loạt học sinh có sẵn vào ĐÚNG lớp classId. */
export function importClassEnrollments(classId: number, file: File): Promise<ClassEnrollmentBatchImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<ClassEnrollmentBatchImportResponse>(`/classes/${classId}/enrollments/import`, { method: "POST", body: formData });
}

// ===================== Buổi học (UC-48) =====================

export interface ClassSessionResponse {
  id: number;
  classId: number;
  sessionDate: string;
  startTime: string;
  endTime: string;
  roomId: number | null;
  roomName: string | null;
  primaryTeacherId: number;
  primaryTeacherName: string;
  sessionType: string;
  status: string;
  cancellationReason: string | null;
  rescheduledToSessionId: number | null;
  // V60 (bổ sung ngoài SDD gốc, 2026-07-29): loại GV (VIETNAMESE/FOREIGN, null nếu chưa xác định) —
  // chỉ ở cấp buổi học, không đụng hồ sơ nhân sự. sessionNumber tính động (1-based, đếm cả CANCELLED).
  teacherType: "VIETNAMESE" | "FOREIGN" | null;
  /** Tên GV thực tế dạy buổi (nhập tay, khác primaryTeacherName là FK hệ thống — dùng khi GV nước ngoài không tự thao tác hệ thống) — bổ sung ngoài SDD gốc, 2026-08-06. */
  actualTeacherName: string | null;
  sessionNumber: number;
  /** "Bài học hôm nay" (đã có từ V50, chưa từng lộ ra FE) — nhập ở tab Nhận xét học viên (UC-21), không phải Điểm danh. */
  lessonContent: string | null;
  /** V61 (bổ sung ngoài SDD gốc, 2026-07-29) — chỉ có ý nghĩa khi sessionType=MAKEUP: id buổi CANCELLED mà buổi này bù cho. */
  makeupForSessionId: number | null;
}

export interface CreateClassSessionRequest {
  sessionDate: string;
  startTime: string;
  endTime: string;
  roomId?: number;
  primaryTeacherId: number;
  sessionType: string;
  teacherType?: "VIETNAMESE" | "FOREIGN";
  /** Bắt buộc khi sessionType=MAKEUP (buổi này bù cho buổi nào) — phải để trống với loại khác. Chỉ áp dụng tạo 1 buổi lẻ, không áp dụng bulk/Excel. */
  makeupForSessionId?: number;
}

export interface RescheduleClassSessionRequest {
  newSessionDate: string;
  newStartTime: string;
  newEndTime: string;
  newRoomId?: number;
  newPrimaryTeacherId: number;
  reason?: string;
}

export function listClassSessions(classId: number): Promise<ClassSessionResponse[]> {
  return apiRequest<ClassSessionResponse[]>(`/classes/${classId}/sessions`);
}

export function createClassSession(classId: number, request: CreateClassSessionRequest): Promise<ClassSessionResponse> {
  return apiRequest<ClassSessionResponse>(`/classes/${classId}/sessions`, { method: "POST", body: JSON.stringify(request) });
}

export function cancelClassSession(classId: number, sessionId: number, reason?: string): Promise<ClassSessionResponse> {
  return apiRequest<ClassSessionResponse>(`/classes/${classId}/sessions/${sessionId}/cancel`, { method: "POST", body: JSON.stringify({ reason }) });
}

export function rescheduleClassSession(classId: number, sessionId: number, request: RescheduleClassSessionRequest): Promise<ClassSessionResponse> {
  return apiRequest<ClassSessionResponse>(`/classes/${classId}/sessions/${sessionId}/reschedule`, { method: "POST", body: JSON.stringify(request) });
}

/** UC-48 (bổ sung ngoài SDD gốc, 2026-07-29): buổi học hôm nay của lớp — dùng để tự chọn buổi khi vào tab Nhận xét học viên. Loại CANCELLED/RESCHEDULED, trả rỗng nếu hôm nay không có buổi. */
export function listTodaySessions(classId: number): Promise<ClassSessionResponse[]> {
  return apiRequest<ClassSessionResponse[]>(`/classes/${classId}/sessions/today`);
}

/** V61 (bổ sung ngoài SDD gốc, 2026-07-29): buổi CANCELLED của lớp chưa có buổi bù nào liên kết — phục vụ chọn "buổi cần bù" khi tạo buổi MAKEUP. */
export function listCancelledSessionsPendingMakeup(classId: number): Promise<ClassSessionResponse[]> {
  return apiRequest<ClassSessionResponse[]>(`/classes/${classId}/sessions/cancelled-pending-makeup`);
}

// ===================== Sinh lịch hàng loạt (UC-56) =====================

/** daysOfWeek dùng đúng tên hằng số java.time.DayOfWeek: MONDAY..SUNDAY. */
export interface BulkCreateClassSessionRequest {
  startDate: string;
  endDate: string;
  daysOfWeek: string[];
  startTime: string;
  endTime: string;
  roomId?: number;
  primaryTeacherId: number;
  sessionType: string;
  /** Dùng chung cho cả lô buổi tạo trong lời gọi này — muốn khác nhau theo thứ thì gọi bulkCreateClassSessions nhiều lần. */
  teacherType?: "VIETNAMESE" | "FOREIGN";
}

export interface BulkCreateClassSessionResponse {
  totalDates: number;
  createdCount: number;
  skippedCount: number;
  created: ClassSessionResponse[];
  skipped: { date: string; reason: string }[];
}

export function bulkCreateClassSessions(classId: number, request: BulkCreateClassSessionRequest): Promise<BulkCreateClassSessionResponse> {
  return apiRequest<BulkCreateClassSessionResponse>(`/classes/${classId}/sessions/bulk`, { method: "POST", body: JSON.stringify(request) });
}

// ===================== Nhập lịch học từ Excel (UC-57) =====================

export interface ClassScheduleImportResponse {
  id: number;
  sourceFileName: string;
  totalRows: number | null;
  successRows: number;
  failedRows: number;
  status: string;
  errorSummary: Record<string, unknown>[] | null;
}

export function importClassSchedule(classId: number, file: File): Promise<ClassScheduleImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<ClassScheduleImportResponse>(`/classes/${classId}/session-imports`, { method: "POST", body: formData });
}

export function getScheduleImportJob(classId: number, jobId: number): Promise<ClassScheduleImportResponse> {
  return apiRequest<ClassScheduleImportResponse>(`/classes/${classId}/session-imports/${jobId}`);
}

// ===================== Lịch của tôi — Giáo viên (UC-58, self-service) =====================

/** GV tự xem mọi buổi dạy của chính mình qua mọi lớp — không cần quyền academic.class.manage. */
export function getMyTeachingSchedule(fromDate?: string, toDate?: string): Promise<ClassSessionResponse[]> {
  const params = new URLSearchParams();
  if (fromDate) params.set("fromDate", fromDate);
  if (toDate) params.set("toDate", toDate);
  const query = params.toString();
  return apiRequest<ClassSessionResponse[]>(`/teachers/me/sessions${query ? `?${query}` : ""}`);
}

// ===================== Điểm danh (UC-15) =====================

export interface AttendanceMarkResponse {
  id: number;
  attendanceSessionId: number;
  studentId: number;
  studentFullName: string;
  studentCode: string;
  status: "PRESENT" | "ABSENT" | "EXCUSED" | "LATE" | "EARLY_LEAVE";
  minutesLate: number | null;
  minutesEarlyLeave: number | null;
  absenceReason: string | null;
  notifiedParentAt: string | null;
}

export interface AttendanceSessionResponse {
  id: number;
  classSessionId: number;
  mode: "SESSION_LEVEL" | "PERIOD_LEVEL";
  markedBy: number | null;
  markedAt: string | null;
  status: "DRAFT" | "SUBMITTED" | "LOCKED";
  submittedAt: string | null;
  marks: AttendanceMarkResponse[];
}

export interface EnterAttendanceMarkRequest {
  studentId: number;
  status: AttendanceMarkResponse["status"];
  minutesLate?: number;
  minutesEarlyLeave?: number;
  absenceReason?: string;
}

export interface MarkAttendanceRequest {
  mode: "SESSION_LEVEL" | "PERIOD_LEVEL";
  marks: EnterAttendanceMarkRequest[];
}

export function getAttendanceSession(classSessionId: number): Promise<AttendanceSessionResponse> {
  return apiRequest<AttendanceSessionResponse>(`/class-sessions/${classSessionId}/attendance`);
}

export function markAttendance(classSessionId: number, request: MarkAttendanceRequest): Promise<AttendanceSessionResponse> {
  return apiRequest<AttendanceSessionResponse>(`/class-sessions/${classSessionId}/attendance`, { method: "POST", body: JSON.stringify(request) });
}

export function submitAttendance(classSessionId: number): Promise<AttendanceSessionResponse> {
  return apiRequest<AttendanceSessionResponse>(`/class-sessions/${classSessionId}/attendance/submit`, { method: "POST" });
}

// ===================== Sổ điểm (UC-19/20) =====================

export interface GradePeriodResponse {
  id: number;
  curriculumId: number;
  code: string;
  name: string;
  displayOrder: number;
  weightInFinal: number;
  startDate: string | null;
  endDate: string | null;
  status: "ACTIVE" | "ARCHIVED";
}

export interface CreateGradePeriodRequest {
  code: string;
  name: string;
  displayOrder?: number;
  weightInFinal: number;
  startDate?: string;
  endDate?: string;
}

export function listGradePeriods(curriculumId: number): Promise<GradePeriodResponse[]> {
  return apiRequest<GradePeriodResponse[]>(`/curriculums/${curriculumId}/grade-periods`);
}

export function createGradePeriod(curriculumId: number, request: CreateGradePeriodRequest): Promise<GradePeriodResponse> {
  return apiRequest<GradePeriodResponse>(`/curriculums/${curriculumId}/grade-periods`, { method: "POST", body: JSON.stringify(request) });
}

/** UC-19 (bổ sung): chỉ xoá được kỳ RỖNG — chưa có thành phần điểm, chưa có điểm tổng kết, chưa bắt đầu nhập điểm ở lớp nào (BE tự chặn 422 nếu không đủ điều kiện). */
export function deleteGradePeriod(id: number): Promise<void> {
  return apiRequest<void>(`/grade-periods/${id}`, { method: "DELETE" });
}

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

export interface CreateGradeComponentRequest {
  subjectId?: number;
  skillId?: number;
  code: string;
  name: string;
  maxScore?: number;
  passThreshold?: number;
  scaleType?: GradeComponentResponse["scaleType"];
  displayOrder?: number;
}

export function listGradeComponents(gradePeriodId: number): Promise<GradeComponentResponse[]> {
  return apiRequest<GradeComponentResponse[]>(`/grade-periods/${gradePeriodId}/components`);
}

export function addGradeComponent(gradePeriodId: number, request: CreateGradeComponentRequest): Promise<GradeComponentResponse> {
  return apiRequest<GradeComponentResponse>(`/grade-periods/${gradePeriodId}/components`, { method: "POST", body: JSON.stringify(request) });
}

/** UC-19 (bổ sung): chỉ xoá được đầu điểm CHƯA có điểm nhập nào (BE tự chặn 422 nếu đã có điểm). */
export function deleteGradeComponent(id: number): Promise<void> {
  return apiRequest<void>(`/grade-components/${id}`, { method: "DELETE" });
}

/**
 * V44 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — thay hẳn luồng "công bố dự
 * kiến + phúc khảo" V43): 4 trạng thái DRAFT → SUBMITTED (Giáo viên gửi duyệt) →
 * OFFICIAL (Quản lý duyệt, hiển thị ngay cho Phụ huynh) / REJECTED (Quản lý từ chối,
 * Giáo viên/Quản lý sửa lại rồi gửi/duyệt lại).
 */
export type GradeStatus = "DRAFT" | "SUBMITTED" | "OFFICIAL" | "REJECTED";

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

export interface EnterGradeRequest {
  studentId: number;
  score: number;
  absenceFlag: boolean;
  teacherNote?: string;
}

/**
 * UC-19 Main Flow bước 1-3: Giáo viên nhập điểm 1 học sinh cho 1 đầu điểm của lớp.
 * V44: sửa/xoá được khi bản ghi DRAFT hoặc REJECTED (không giới hạn thời gian), hoặc
 * actor có quyền academic.grade.edit.override. SUBMITTED/OFFICIAL luôn bị chặn với
 * actor thường — backend trả lỗi rõ (bắt qua ApiError như bình thường), FE không tự
 * đoán trước điều kiện editable, cứ để nhập rồi để BE quyết định.
 */
export function listGradeEntries(classId: number, gradeComponentId: number): Promise<GradeEntryResponse[]> {
  return apiRequest<GradeEntryResponse[]>(`/classes/${classId}/grades/components/${gradeComponentId}`);
}

export function enterGrade(classId: number, gradeComponentId: number, request: EnterGradeRequest): Promise<GradeEntryResponse> {
  return apiRequest<GradeEntryResponse>(`/classes/${classId}/grades/components/${gradeComponentId}`, { method: "POST", body: JSON.stringify(request) });
}

// ===================== UC-53: Overall/Level theo kỳ đánh giá + Nhập điểm qua Excel =====================

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

export interface EnterGradePeriodResultRequest {
  overallScore?: number;
  scaleType: GradePeriodResultResponse["scaleType"];
  level?: string;
}

/** UC-53: Overall/Level GV đã tính sẵn (nhập tay hoặc từ Excel) — hệ thống chỉ lưu, không tự tính lại. */
export function enterPeriodResult(classId: number, studentId: number, gradePeriodId: number, request: EnterGradePeriodResultRequest): Promise<GradePeriodResultResponse> {
  return apiRequest<GradePeriodResultResponse>(`/classes/${classId}/grades/students/${studentId}/periods/${gradePeriodId}/result`, {
    method: "POST",
    body: JSON.stringify(request)
  });
}

export function listPeriodResults(classId: number, gradePeriodId: number): Promise<GradePeriodResultResponse[]> {
  return apiRequest<GradePeriodResultResponse[]>(`/classes/${classId}/grade-periods/${gradePeriodId}/results`);
}

export interface GradeImportResponse {
  id: number;
  sourceFileName: string;
  totalRows: number | null;
  successRows: number;
  failedRows: number;
  status: string;
  errorSummary: { row: number; reason: string }[];
}

/** UC-53 Main Flow: tải lên 1 file .xlsx đã hoàn thiện điểm cho đúng lớp + kỳ đánh giá. */
export function importGrades(classId: number, gradePeriodId: number, file: File): Promise<GradeImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<GradeImportResponse>(`/classes/${classId}/grade-periods/${gradePeriodId}/grades/import`, { method: "POST", body: formData });
}

/**
 * V44: X ngày này giờ CHỈ còn ý nghĩa THÔNG TIN — mốc "lần đầu nhập điểm" cho 1 lớp +
 * kỳ đánh giá, không còn gắn job tự động nào (V43 dùng để tự động "công bố dự kiến",
 * đã bỏ cùng lúc với luồng phúc khảo UC-62). Hạn sửa/xoá giờ hoàn toàn theo TRẠNG THÁI
 * — xem enterGrade.
 */
export interface GradeEditWindowResponse {
  days: number;
}

export function getGradeEditWindow(): Promise<GradeEditWindowResponse> {
  return apiRequest<GradeEditWindowResponse>("/academic/settings/grade-edit-window-days");
}

export function updateGradeEditWindow(days: number): Promise<GradeEditWindowResponse> {
  return apiRequest<GradeEditWindowResponse>("/academic/settings/grade-edit-window-days", { method: "PUT", body: JSON.stringify({ days }) });
}

/** UC-19 Main Flow bước 4 (V44): Giáo viên gửi duyệt — DRAFT/REJECTED -> SUBMITTED, chờ Quản lý điểm trường duyệt qua UC-20. */
export function submitGradesForApproval(request: { gradeEntryIds?: number[]; gradePeriodResultIds?: number[] }): Promise<GradeEntryResponse[]> {
  return apiRequest<GradeEntryResponse[]>("/grades/submit", { method: "POST", body: JSON.stringify(request) });
}

/** UC-20 Main Flow bước 1: Quản lý điểm trường xem điểm chờ duyệt (SUBMITTED) — của (các) site mình phụ trách. */
export function listUnpublishedGrades(): Promise<GradeEntryResponse[]> {
  return apiRequest<GradeEntryResponse[]>("/grades/pending");
}

/**
 * UC-20 Main Flow bước 2-5 (V44): Duyệt (SUBMITTED/REJECTED -> OFFICIAL, hiển thị ngay
 * cho Phụ huynh) hoặc Từ chối (SUBMITTED -> REJECTED, kèm lý do tuỳ chọn) — gradeEntryIds
 * và/hoặc gradePeriodResultIds, ít nhất 1 danh sách phải có phần tử.
 */
export function publishGrades(request: {
  action: "APPROVE" | "REJECT";
  gradeEntryIds?: number[];
  gradePeriodResultIds?: number[];
  rejectReason?: string;
}): Promise<GradeEntryResponse[]> {
  return apiRequest<GradeEntryResponse[]>("/grades/decision", { method: "POST", body: JSON.stringify(request) });
}

// ===================== Nhận xét học viên (UC-21/22) =====================

export interface StudentCommentResponse {
  id: number;
  studentId: number;
  studentFullName: string;
  studentDateOfBirth: string | null;
  classId: number;
  teacherId: number;
  commentType: "DAILY" | "MID_TERM" | "END_TERM";
  classSessionId: number | null;
  gradePeriodId: number | null;
  commentDate: string;
  content: string;
  structuredContent: Record<string, unknown> | null;
  severity: "POSITIVE" | "NORMAL" | "CONCERN" | "WARNING" | null;
  isWarning: boolean;
  status: "DRAFT" | "PENDING" | "APPROVED" | "REJECTED";
  submittedAt: string | null;
  approvedAt: string | null;
  approvedBy: number | null;
  visibleToParentAt: string | null;
  rejectionReason: string | null;
  // Nhận xét Hàng ngày kiểu mới (chỉ có ý nghĩa khi commentType=DAILY) — bổ sung ngoài SDD gốc.
  // Attitude mở rộng từ 3 lên 6 mức 2026-07-27 (StudentComment.Attitude) — giữ nguyên tên hằng số
  // POOR/AVERAGE/GOOD của 3 mức cũ, thêm WEAK/ABOVE_AVERAGE/FAIR.
  attitude: "POOR" | "WEAK" | "AVERAGE" | "ABOVE_AVERAGE" | "FAIR" | "GOOD" | null;
  homeworkPreviousScore: string | null;
  // BTVN Nghe-nói buổi trước (V56, nhập tay, đối xứng với homeworkPreviousScore ở trên) — độc lập với
  // videoPreviousProgress (tự tính) bên dưới.
  homeworkPreviousSpeakingScore: string | null;
  homeworkNext: string | null;
  // BTVN online/offline theo từng học sinh (V55, PR UC-21-giao-btvn-online-offline, 2026-07-28) —
  // kênh ngữ pháp ONLINE (homeworkNextExerciseAssignmentId khác null) hoặc OFFLINE (dùng homeworkNext
  // ở trên); kênh Video Ôn tập luôn ONLINE. grammarPreviousProgress/videoPreviousProgress tự tính từ
  // exercise_attempts/review_video_progress|submissions của buổi TRƯỚC, không nhập tay được.
  // V65 (2026-07-30, bổ sung ngoài SDD gốc): 2 field *AssignmentId đều là id BẢN GIAO (ExerciseAssignment/
  // ReviewVideoAssignment) tự động phát sinh khi chọn nguồn ở Nhận xét — không phải id nguồn (Exercise/
  // ReviewVideoSet), dùng kèm *Title để hiện tên nguồn. Muốn tra ngược ra id nguồn phải gọi thêm
  // listAssignmentsForClass (Ngữ pháp)/listReviewVideoAssignmentsForClass (Video) bên lms/api.ts.
  homeworkNextExerciseAssignmentId: number | null;
  homeworkNextExerciseTitle: string | null;
  homeworkNextReviewVideoAssignmentId: number | null;
  homeworkNextReviewVideoSetTitle: string | null;
  /** Hạn nộp BTVN buổi sau (lấy từ dueAt của bản giao) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05. */
  homeworkNextDueAt: string | null;
  grammarPreviousProgress: string | null;
  videoPreviousProgress: string | null;
  /** BTVN buổi trước từng giao Offline (chữ tự do) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06, phân biệt "BTVN buổi trước" có 3 loại (Offline/kênh Bài/kênh Video). Loại trừ với grammarPreviousProgress. */
  homeworkPreviousOfflineText: string | null;
  note: string | null;
  /** "Bài học hôm nay" của buổi (class_sessions.lesson_content) — null nếu không phải DAILY. Bổ sung ngoài SDD gốc, 2026-07-29 — chuyển từ Điểm danh sang Nhận xét. */
  lessonContent: string | null;
}

export interface CreateStudentCommentRequest {
  studentId: number;
  commentType: StudentCommentResponse["commentType"];
  classSessionId?: number;
  gradePeriodId?: number;
  commentDate: string;
  content: string;
  structuredContent?: Record<string, unknown>;
  severity?: StudentCommentResponse["severity"];
  isWarning: boolean;
  // Chỉ áp dụng khi commentType=DAILY — bỏ qua với MID_TERM/END_TERM.
  attitude?: NonNullable<StudentCommentResponse["attitude"]>;
  homeworkPreviousScore?: string;
  homeworkPreviousSpeakingScore?: string;
  homeworkNext?: string;
  /**
   * V65 (2026-07-30, bổ sung ngoài SDD gốc): kênh ngữ pháp ONLINE — id của Exercise NGUỒN (đã
   * Publish), KHÔNG phải id bản giao như trước V65. Chọn khác null tự động giao đề cho CẢ LỚP ACTIVE,
   * hạn nộp = buổi học kế tiếp — để trống nếu dùng homeworkNext (OFFLINE) hoặc không giao gì (hủy bản
   * giao cũ nếu đang sửa 1 comment DRAFT đã chọn trước đó).
   */
  homeworkNextExerciseId?: number;
  /** Kênh Video Ôn tập (luôn ONLINE) — id của ReviewVideoSet NGUỒN (đã Publish), tự động giao cả lớp tương tự. Để trống nếu không giao. */
  homeworkNextReviewVideoSetId?: number;
  /**
   * Nhận xét học viên (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05, cho phép chọn
   * GIỜ 2026-08-06): hạn nộp BTVN buổi sau (ngày + giờ, format "yyyy-MM-ddTHH:mm" — khớp value của
   * <input type="datetime-local">/kết hợp DatePicker + input giờ) do Giáo viên tự chọn — để trống thì
   * BE giữ hành vi cũ (khoá cứng = ngày buổi kế tiếp). Chỉ có ý nghĩa khi có homeworkNextExerciseId
   * hoặc homeworkNextReviewVideoSetId. Mọi nhận xét DAILY cùng 1 buổi phải khớp cùng 1 hạn nộp (BE
   * chặn 409 nếu khác).
   */
  homeworkNextDueDate?: string;
  note?: string;
}

export interface UpdateStudentCommentRequest {
  content: string;
  structuredContent?: Record<string, unknown>;
  severity?: StudentCommentResponse["severity"];
  isWarning: boolean;
  attitude?: NonNullable<StudentCommentResponse["attitude"]>;
  homeworkPreviousScore?: string;
  homeworkPreviousSpeakingScore?: string;
  homeworkNext?: string;
  /** V65 — xem Javadoc CreateStudentCommentRequest.homeworkNextExerciseId. */
  homeworkNextExerciseId?: number;
  homeworkNextReviewVideoSetId?: number;
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05 — xem Javadoc CreateStudentCommentRequest.homeworkNextDueDate. */
  homeworkNextDueDate?: string;
  note?: string;
}

export function listComments(classId: number, studentId: number): Promise<StudentCommentResponse[]> {
  return apiRequest<StudentCommentResponse[]>(`/classes/${classId}/comments?studentId=${studentId}`);
}

export function writeComment(classId: number, request: CreateStudentCommentRequest): Promise<StudentCommentResponse> {
  return apiRequest<StudentCommentResponse>(`/classes/${classId}/comments`, { method: "POST", body: JSON.stringify(request) });
}

export function updateComment(id: number, request: UpdateStudentCommentRequest): Promise<StudentCommentResponse> {
  return apiRequest<StudentCommentResponse>(`/comments/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export interface UpdateStudentCommentContentRequest {
  content: string;
  structuredContent?: Record<string, unknown>;
}

/** UC-22 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-02): Quản lý điểm trường sửa trực tiếp nội dung nhận xét đang chờ duyệt. */
export function updatePendingCommentContent(id: number, request: UpdateStudentCommentContentRequest): Promise<StudentCommentResponse> {
  return apiRequest<StudentCommentResponse>(`/comments/pending/${id}/content`, { method: "PUT", body: JSON.stringify(request) });
}

export function submitComments(classId: number, commentIds: number[]): Promise<StudentCommentResponse[]> {
  return apiRequest<StudentCommentResponse[]>(`/classes/${classId}/comments/submit`, { method: "POST", body: JSON.stringify({ commentIds }) });
}

export interface DailyCommentImportResponse {
  id: number;
  sourceFileName: string;
  totalRows: number | null;
  successRows: number;
  failedRows: number;
  status: string;
  errorSummary: { row: number; reason: string }[];
}

/** "Bài học hôm nay" (2026-07-29, chuyển từ Điểm danh sang đây) — dùng chung rào ghi nhận xét DAILY (requireCanWriteDailyComment). */
export function updateLessonContent(classSessionId: number, lessonContent: string): Promise<{ classSessionId: number; lessonContent: string }> {
  return apiRequest(`/class-sessions/${classSessionId}/comments/lesson-content`, { method: "PUT", body: JSON.stringify({ lessonContent }) });
}

/**
 * "Loại giáo viên" của buổi học (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05) —
 * Nhận xét học viên dùng để lọc/đổi nhãn BTVN buổi sau theo GV Việt Nam/nước ngoài. Dùng chung rào
 * ghi nhận xét DAILY (requireCanWriteDailyComment), mirror updateLessonContent.
 */
export function updateSessionTeacherType(
  classSessionId: number,
  teacherType: "VIETNAMESE" | "FOREIGN"
): Promise<{ classSessionId: number; teacherType: "VIETNAMESE" | "FOREIGN" }> {
  return apiRequest(`/class-sessions/${classSessionId}/comments/teacher-type`, { method: "PUT", body: JSON.stringify({ teacherType }) });
}

/**
 * "Tên giáo viên giảng dạy" thực tế của buổi (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-06) — nhập tay, dùng chung rào ghi nhận xét DAILY, mirror updateLessonContent.
 */
export function updateActualTeacherName(classSessionId: number, actualTeacherName: string): Promise<{ classSessionId: number; actualTeacherName: string }> {
  return apiRequest(`/class-sessions/${classSessionId}/comments/teacher-name`, { method: "PUT", body: JSON.stringify({ actualTeacherName }) });
}

/** UC-21 (bổ sung): tải mẫu Excel theo buổi học — điền sẵn điểm danh + nhận xét Hàng ngày hiện có của từng học sinh ACTIVE. */
export function downloadDailyCommentTemplate(classSessionId: number): Promise<Blob> {
  return apiRequestBlob(`/class-sessions/${classSessionId}/comments/template`);
}

/** UC-21 (bổ sung): nhập lại file đã sửa — cập nhật cả điểm danh lẫn nhận xét Hàng ngày trong 1 lần. */
export function importDailyComments(classSessionId: number, file: File): Promise<DailyCommentImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<DailyCommentImportResponse>(`/class-sessions/${classSessionId}/comments/import`, { method: "POST", body: formData });
}

/** UC-22: Quản lý điểm trường duyệt nhận xét — hàng chờ của (các) site mình phụ trách. */
export function listPendingComments(): Promise<StudentCommentResponse[]> {
  return apiRequest<StudentCommentResponse[]>("/comments/pending");
}

export function decideComments(commentIds: number[], decision: "APPROVED" | "REJECTED", comment?: string): Promise<StudentCommentResponse[]> {
  return apiRequest<StudentCommentResponse[]>("/comments/decision", { method: "POST", body: JSON.stringify({ commentIds, decision, comment }) });
}

// ===================== UC-66: Thống kê BTVN theo lớp (FR-ACA-07) =====================

export interface ExerciseAssignmentStatsResponse {
  assignmentId: number;
  exerciseId: number;
  exerciseCode: string;
  exerciseTitle: string;
  exerciseType: "SELF_PRACTICE" | "ASSIGNED" | "MOCK_TEST" | "SKILL_PRACTICE";
  availableFrom: string;
  dueAt: string | null;
  status: "ACTIVE" | "COMPLETED";
  totalStudents: number;
  completedCount: number;
  completionPercent: number;
  passedCount: number;
  passRatePercent: number;
}

export interface ExerciseAssignmentStudentRow {
  studentId: number;
  studentCode: string;
  studentFullName: string;
  status: "CHUA_LAM" | "DANG_LAM" | "DA_NOP" | "TRE_HAN";
  totalScore: number | null;
  totalPoints: number | null;
  percentage: number | null;
  passed: boolean | null;
  submittedAt: string | null;
  attemptNumber: number | null;
  attemptId: number | null;
}

export interface ExerciseAssignmentStudentStatsResponse {
  assignment: ExerciseAssignmentStatsResponse;
  students: ExerciseAssignmentStudentRow[];
}

export interface ExerciseAssignmentWrongStudent {
  studentId: number;
  studentCode: string;
  studentFullName: string;
}

export interface ExerciseAssignmentQuestionRow {
  questionId: number;
  displayOrder: number;
  content: string;
  questionType: string;
  skill: string | null;
  answeredCount: number;
  wrongCount: number;
  wrongRatePercent: number;
  wrongStudents: ExerciseAssignmentWrongStudent[];
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — luôn 0 với câu không phải skill=LISTENING. */
  hintUsedCount: number;
  hintUsedStudentCount: number;
}

export interface ExerciseAssignmentQuestionStatsResponse {
  questions: ExerciseAssignmentQuestionRow[];
}

export function listExerciseAssignmentStats(classId: number): Promise<ExerciseAssignmentStatsResponse[]> {
  return apiRequest<ExerciseAssignmentStatsResponse[]>(`/classes/${classId}/exercise-assignments/stats`);
}

export function getExerciseAssignmentStudentStats(assignmentId: number): Promise<ExerciseAssignmentStudentStatsResponse> {
  return apiRequest<ExerciseAssignmentStudentStatsResponse>(`/exercise-assignments/${assignmentId}/stats/students`);
}

export function getExerciseAssignmentQuestionStats(assignmentId: number): Promise<ExerciseAssignmentQuestionStatsResponse> {
  return apiRequest<ExerciseAssignmentQuestionStatsResponse>(`/exercise-assignments/${assignmentId}/stats/questions`);
}

export function exportExerciseAssignmentStats(assignmentId: number): Promise<Blob> {
  return apiRequestBlob(`/exercise-assignments/${assignmentId}/stats/export`);
}

export interface StudentAnswerRow {
  id: number;
  exerciseAttemptId: number;
  questionId: number;
  answerText: string | null;
  selectedChoiceIds: number[];
  audioAnswerUrl: string | null;
  isAutoGradable: boolean;
  autoScore: number | null;
  isCorrect: boolean | null;
  correctChoiceIds: number[];
  correctAnswerText: string | null;
  explanation: string | null;
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — dùng endpoint riêng cho Giáo
 * viên (/answers/for-grading, không đòi hỏi actor có hồ sơ học sinh), KHÁC endpoint học sinh tự
 * xem lại bài của chính mình (/answers) bên app user — 2 endpoint tách để không đụng rào sở hữu.
 */
export function getAttemptAnswers(attemptId: number): Promise<StudentAnswerRow[]> {
  return apiRequest<StudentAnswerRow[]>(`/attempts/${attemptId}/answers/for-grading`);
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — Giáo viên xem TOÀN BỘ lịch sử
 * nhiều lượt làm bài của 1 học sinh (kể cả lượt bị hệ thống dừng ép do vi phạm giám sát) để tự
 * chọn lượt phù hợp, khác StudentAnswerRow (chỉ trả lời câu hỏi của ĐÚNG 1 lượt).
 */
export interface ExerciseAttemptRow {
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
  status: "IN_PROGRESS" | "AUTO_GRADED" | "FULLY_GRADED" | "EXPIRED";
  isLateSubmission: boolean;
  percentage: number | null;
  passed: boolean | null;
  stoppedByIntegrityViolation: boolean;
  /** V93, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: Giáo viên đã chọn lượt này làm kết quả chính thức chưa. */
  selectedForGrading: boolean;
}

export function listStudentExerciseAttempts(exerciseId: number, studentId: number): Promise<ExerciseAttemptRow[]> {
  return apiRequest<ExerciseAttemptRow[]>(`/exercises/${exerciseId}/students/${studentId}/attempts`);
}

/** V93, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: Giáo viên chọn 1 lượt làm điểm chính thức (loại trừ lẫn nhau trong cùng exercise+student). */
export function selectAttemptForGrading(attemptId: number): Promise<ExerciseAttemptRow[]> {
  return apiRequest<ExerciseAttemptRow[]>(`/attempts/${attemptId}/select-for-grading`, { method: "POST" });
}

export interface IntegritySummaryRow {
  violationCount: number;
  violationTotalDurationSeconds: number;
  parentAndTeacherNotified: boolean;
}

export function getAttemptIntegritySummary(attemptId: number): Promise<IntegritySummaryRow> {
  return apiRequest<IntegritySummaryRow>(`/attempts/${attemptId}/integrity-summary`);
}
