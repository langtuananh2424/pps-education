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
  semester: string | null;
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
  semester?: string;
}

export interface UpdateClassRequest {
  name: string;
  maxStudents: number;
  minStudents?: number;
  startDate: string;
  endDate?: string;
  academicYear?: string;
  semester?: string;
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

export interface ClassEnrollmentResponse {
  id: number;
  classId: number;
  studentId: number;
  studentFullName: string;
  studentCode: string;
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
}

export interface CreateClassSessionRequest {
  sessionDate: string;
  startTime: string;
  endTime: string;
  roomId?: number;
  primaryTeacherId: number;
  sessionType: string;
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
 * V43: 4 trạng thái DRAFT → PROVISIONAL_PUBLISHED → (APPEAL) → OFFICIAL (thay hẳn
 * DRAFT/PUBLISHED của V39) — xem GradeAppealResponse/UC-62 (phúc khảo) bên dưới.
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

export interface EnterGradeRequest {
  studentId: number;
  score: number;
  absenceFlag: boolean;
  teacherNote?: string;
}

/**
 * UC-19 Main Flow bước 1-3: Giáo viên nhập điểm 1 học sinh cho 1 đầu điểm của lớp.
 * V43: sửa/xoá được khi bản ghi DRAFT (không giới hạn thời gian), hoặc đang APPEAL
 * và chính actor là GV đã tiếp nhận yêu cầu phúc khảo đó (UC-62), hoặc actor có quyền
 * academic.grade.edit.override. PROVISIONAL_PUBLISHED/OFFICIAL luôn bị chặn với actor
 * thường — backend trả lỗi rõ (bắt qua ApiError như bình thường), FE không tự đoán
 * trước điều kiện editable, cứ để nhập rồi để BE quyết định.
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
 * V43: đổi nghĩa — X ngày này giờ CHỈ còn là độ trễ tự động "công bố dự kiến"
 * (DRAFT → PROVISIONAL_PUBLISHED, UC-20 A3) nếu không ai công bố tay, KHÔNG còn là
 * hạn chỉnh sửa như V39 (hạn sửa giờ theo TRẠNG THÁI — xem enterGrade).
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

/**
 * V43: hạn phúc khảo (UC-62) tính từ lúc "Công bố dự kiến" (publishedAt) — hết hạn thì
 * GradeSchedulerService tự khoá OFFICIAL bất kể còn PROVISIONAL_PUBLISHED hay APPEAL.
 * Khác hẳn GradeEditWindowResponse (X ngày = độ trễ tự động CÔNG BỐ, không phải khoá).
 */
export interface GradeAppealWindowResponse {
  days: number;
}

export function getGradeAppealWindow(): Promise<GradeAppealWindowResponse> {
  return apiRequest<GradeAppealWindowResponse>("/academic/settings/grade-appeal-window-days");
}

export function updateGradeAppealWindow(days: number): Promise<GradeAppealWindowResponse> {
  return apiRequest<GradeAppealWindowResponse>("/academic/settings/grade-appeal-window-days", { method: "PUT", body: JSON.stringify({ days }) });
}

/** UC-20: Quản lý điểm trường xem điểm chưa công bố (DRAFT) — của (các) site mình phụ trách. */
export function listUnpublishedGrades(): Promise<GradeEntryResponse[]> {
  return apiRequest<GradeEntryResponse[]>("/grades/pending");
}

/**
 * UC-20: công bố điểm dự kiến (DRAFT → PROVISIONAL_PUBLISHED) — gradeEntryIds và/hoặc
 * gradePeriodResultIds, ít nhất 1 danh sách phải có phần tử. Bắt đầu tính hạn Y ngày
 * phúc khảo (UC-62) kể từ lúc này.
 */
export function publishGrades(request: { gradeEntryIds?: number[]; gradePeriodResultIds?: number[] }): Promise<GradeEntryResponse[]> {
  return apiRequest<GradeEntryResponse[]>("/grades/decision", { method: "POST", body: JSON.stringify(request) });
}

// ===================== UC-62: Phúc khảo điểm =====================

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

/** Hàng chờ phúc khảo (PENDING) của (các) lớp Giáo viên đang phụ trách. */
export function listPendingGradeAppeals(): Promise<GradeAppealResponse[]> {
  return apiRequest<GradeAppealResponse[]>("/grade-appeals/pending");
}

/** Giáo viên tiếp nhận — sau khi tiếp nhận mới sửa được điểm của đúng học sinh này (UC-19, enterGrade). */
export function acceptGradeAppeal(id: number): Promise<GradeAppealResponse> {
  return apiRequest<GradeAppealResponse>(`/grade-appeals/${id}/accept`, { method: "POST" });
}

// ===================== Nhận xét học viên (UC-21/22) =====================

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
  homeworkNext: string | null;
  // BTVN online/offline theo từng học sinh (V55, PR UC-21-giao-btvn-online-offline, 2026-07-28) —
  // kênh ngữ pháp ONLINE (homeworkNextExerciseAssignmentId khác null) hoặc OFFLINE (dùng homeworkNext
  // ở trên); kênh Video Ôn tập luôn ONLINE. grammarPreviousProgress/videoPreviousProgress tự tính từ
  // exercise_attempts/review_video_progress|submissions của buổi TRƯỚC, không nhập tay được.
  homeworkNextExerciseAssignmentId: number | null;
  homeworkNextExerciseTitle: string | null;
  homeworkNextReviewVideoSetId: number | null;
  homeworkNextReviewVideoSetTitle: string | null;
  grammarPreviousProgress: string | null;
  videoPreviousProgress: string | null;
  note: string | null;
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
  homeworkNext?: string;
  /** Kênh ngữ pháp ONLINE — để trống nếu dùng homeworkNext (OFFLINE) hoặc không giao gì. */
  homeworkNextExerciseAssignmentId?: number;
  /** Kênh Video Ôn tập (luôn ONLINE) — để trống nếu không giao. */
  homeworkNextReviewVideoSetId?: number;
  note?: string;
}

export interface UpdateStudentCommentRequest {
  content: string;
  structuredContent?: Record<string, unknown>;
  severity?: StudentCommentResponse["severity"];
  isWarning: boolean;
  attitude?: NonNullable<StudentCommentResponse["attitude"]>;
  homeworkPreviousScore?: string;
  homeworkNext?: string;
  homeworkNextExerciseAssignmentId?: number;
  homeworkNextReviewVideoSetId?: number;
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
