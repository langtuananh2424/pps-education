import { apiRequest, apiRequestBlob } from "@/lib/apiClient";
import { DayPart } from "@/features/facility/api";

// ===================== Khung chương trình (UC-16/17) =====================

export interface CurriculumResponse {
  id: number;
  code: string;
  name: string;
  siteId: number | null;
  parentCurriculumId: number | null;
  classCategory: "MAIN" | "SUPPLEMENTARY" | "EXAM_PREP" | "OTHER" | null;
  level: string | null;
  /** V140 — null = chưa phân loại. Dùng để AI chấm Speaking/Writing chọn đúng rubric theo Khối/chương trình. */
  gradeLevel: "GRADE_6" | "GRADE_7" | "GRADE_8" | "GRADE_9" | null;
  track: "IELTS" | "CAMBRIDGE" | null;
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
  /** V140 — bỏ trống = chưa phân loại (AI chấm Speaking/Writing sẽ bỏ qua, rơi lại hàng chờ chấm tay). */
  gradeLevel?: string;
  track?: string;
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
  /** V140 — bỏ trống = chưa phân loại (AI chấm Speaking/Writing sẽ bỏ qua, rơi lại hàng chờ chấm tay). */
  gradeLevel?: string;
  track?: string;
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
  academicYearId: number | null;
  academicYear: string | null;
  status: "PLANNED" | "OPEN_ENROLLMENT" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
  /** Màu hiển thị trên lịch làm việc dạng lưới — tự chọn ngẫu nhiên khi tạo lớp, đổi được qua UpdateClassRequest (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-21). */
  color: string;
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
  academicYearId?: number;
}

export interface UpdateClassRequest {
  name: string;
  maxStudents: number;
  minStudents?: number;
  startDate: string;
  endDate?: string;
  academicYearId?: number;
  status: ClassResponse["status"];
  /** Bỏ trống thì giữ nguyên màu cũ. */
  color?: string;
}

/** UC-18 Main Flow bước 3: dropdown site -> lớp của site đó; lọc thêm theo curriculum/năm học. Giáo viên chỉ thấy lớp thuộc site được gán (site_teachers). */
export function listClasses(params?: {
  query?: string;
  siteId?: number;
  curriculumId?: number;
  classCategory?: string;
  academicYearId?: number;
}): Promise<ClassResponse[]> {
  const qs = new URLSearchParams();
  if (params?.query?.trim()) qs.set("query", params.query.trim());
  if (params?.siteId) qs.set("siteId", String(params.siteId));
  if (params?.curriculumId) qs.set("curriculumId", String(params.curriculumId));
  if (params?.classCategory) qs.set("classCategory", params.classCategory);
  if (params?.academicYearId) qs.set("academicYearId", String(params.academicYearId));
  const suffix = qs.toString() ? `?${qs.toString()}` : "";
  return apiRequest<ClassResponse[]>(`/classes${suffix}`);
}

/** Chuyển lớp hàng loạt cuối năm học (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-07) — xem ClassService#promoteClass. */
export interface PromoteClassRequest {
  classCode: string;
  name: string;
  curriculumId: number;
  academicYearId: number;
  startDate: string;
  endDate?: string;
  maxStudents: number;
  minStudents?: number;
}

export interface PromoteClassSkippedStudent {
  studentId: number;
  studentCode: string;
  studentFullName: string;
  reason: string;
}

export interface PromoteClassResponse {
  newClass: ClassResponse;
  oldClassId: number;
  movedStudentCount: number;
  skippedStudentCount: number;
  skippedStudents: PromoteClassSkippedStudent[];
}

export function promoteClass(oldClassId: number, request: PromoteClassRequest): Promise<PromoteClassResponse> {
  return apiRequest<PromoteClassResponse>(`/classes/${oldClassId}/promote`, { method: "POST", body: JSON.stringify(request) });
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
  teacherRole: "PRIMARY" | "ASSISTANT" | "SUBSTITUTE" | "CM";
  /** Bổ sung ngoài SDD gốc, xác nhận 2026-08-13 — chỉ có ý nghĩa khi teacherRole=PRIMARY (1 lớp có tối đa 1 PRIMARY active loại VIETNAMESE + 1 PRIMARY active loại FOREIGN). */
  teacherType: "VIETNAMESE" | "FOREIGN" | null;
  subjectId: number | null;
  assignedFrom: string | null;
  assignedTo: string | null;
}

export interface AssignTeacherRequest {
  teacherUserId: number;
  teacherRole?: ClassTeacherResponse["teacherRole"];
  subjectId?: number;
  assignedFrom?: string;
  /** Chỉ có ý nghĩa khi teacherRole=PRIMARY — bổ sung ngoài SDD gốc, xác nhận 2026-08-13. */
  teacherType?: "VIETNAMESE" | "FOREIGN";
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

export interface ChangeTeacherRequest {
  newTeacherUserId: number;
  effectiveDate: string;
}

/**
 * UC-18 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13) — đổi giáo viên chính (PRIMARY) đang phụ trách:
 * kết thúc phân công cũ + gán phân công mới trong 1 transaction, cascade cập nhật giáo viên phụ trách
 * các buổi học SCHEDULED tương lai cùng loại giáo viên (trừ buổi đang có GV dạy thay).
 */
export function changeClassTeacher(classId: number, classTeacherId: number, request: ChangeTeacherRequest): Promise<ClassTeacherResponse> {
  return apiRequest<ClassTeacherResponse>(`/classes/${classId}/teachers/${classTeacherId}/change`, {
    method: "PUT",
    body: JSON.stringify(request)
  });
}

/**
 * UC-18 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13): 1 dòng lịch sử thay đổi giáo viên phụ trách.
 * `details` giữ nguyên snapshot đã ghi ở backend — 2 dạng tuỳ action: CREATED = {teacherUserId,
 * teacherRole, teacherType}, UPDATED = {assignedTo}.
 */
export interface ClassTeacherHistoryResponse {
  id: number;
  classTeacherId: number;
  teacherUserId: number;
  teacherFullName: string;
  teacherRole: ClassTeacherResponse["teacherRole"];
  action: "CREATED" | "UPDATED";
  changedByUserId: number;
  changedByName: string;
  details: Record<string, unknown>;
  createdAt: string;
}

/** Lịch sử thay đổi giáo viên phụ trách của cả lớp (gộp mọi phân công từng/đang gắn với lớp), mới nhất trước. */
export function listClassTeacherHistory(classId: number): Promise<ClassTeacherHistoryResponse[]> {
  return apiRequest<ClassTeacherHistoryResponse[]>(`/classes/${classId}/teachers/history`);
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

// ===================== UC-69: Thống kê biến động học sinh các lớp theo kỳ (FR-ACA-09) =====================

/** classId=null -- dòng tổng cộng, xem EnrollmentMovementReportService#sumTotals. */
export interface EnrollmentMovementClassRow {
  classId: number | null;
  classCode: string;
  className: string;
  openingHeadcount: number;
  newEnrollments: number;
  withdrawnCount: number;
  transferredCount: number;
  completedCount: number;
  closingHeadcount: number;
}

/** periodType: "TERM" (theo kỳ) | "MONTH" (theo tháng) | "YEAR" (theo năm) — bổ sung ngoài SDD gốc, xác nhận 2026-08-20. */
export type EnrollmentMovementPeriodType = "TERM" | "MONTH" | "YEAR";

export interface EnrollmentMovementStatsResponse {
  periodType: EnrollmentMovementPeriodType;
  academicTermId: number | null;
  periodLabel: string;
  startDate: string;
  endDate: string;
  siteId: number;
  siteName: string;
  classes: EnrollmentMovementClassRow[];
  totals: EnrollmentMovementClassRow;
}

export function getEnrollmentMovementStats(academicTermId: number, classId?: number): Promise<EnrollmentMovementStatsResponse> {
  const query = classId ? `?classId=${classId}` : "";
  return apiRequest<EnrollmentMovementStatsResponse>(`/academic-terms/${academicTermId}/enrollment-movement-stats${query}`);
}

export function exportEnrollmentMovementStats(academicTermId: number, classId?: number): Promise<Blob> {
  const query = classId ? `?classId=${classId}` : "";
  return apiRequestBlob(`/academic-terms/${academicTermId}/enrollment-movement-stats/export${query}`);
}

/** Bổ sung ngoài SDD gốc, xác nhận 2026-08-20 — chế độ xem "theo tháng"/"theo năm" (khoảng ngày tuỳ ý, không gắn 1 academic_term cụ thể). */
export interface EnrollmentMovementRangeParams {
  siteId: number;
  fromDate: string;
  toDate: string;
  periodType: EnrollmentMovementPeriodType;
  periodLabel: string;
  classId?: number;
}

function rangeQuery(p: EnrollmentMovementRangeParams): string {
  const params = new URLSearchParams({
    fromDate: p.fromDate,
    toDate: p.toDate,
    periodType: p.periodType,
    periodLabel: p.periodLabel
  });
  if (p.classId) params.set("classId", String(p.classId));
  return params.toString();
}

export function getEnrollmentMovementStatsForRange(p: EnrollmentMovementRangeParams): Promise<EnrollmentMovementStatsResponse> {
  return apiRequest<EnrollmentMovementStatsResponse>(`/sites/${p.siteId}/enrollment-movement-stats?${rangeQuery(p)}`);
}

export function exportEnrollmentMovementStatsForRange(p: EnrollmentMovementRangeParams): Promise<Blob> {
  return apiRequestBlob(`/sites/${p.siteId}/enrollment-movement-stats/export?${rangeQuery(p)}`);
}

/** monthIndex = tháng thứ mấy CỦA KỲ (1-based), không phải tháng lịch tuyệt đối -- dùng để so sánh 2 kỳ khác độ dài/thời điểm. */
export interface EnrollmentMovementTrendPoint {
  monthIndex: number;
  periodStart: string;
  periodEnd: string;
  headcount: number;
  newEnrollments: number;
  withdrawnCount: number;
  transferredCount: number;
  completedCount: number;
}

export interface EnrollmentMovementTrendResponse {
  periodType: EnrollmentMovementPeriodType;
  academicTermId: number | null;
  periodLabel: string;
  startDate: string;
  endDate: string;
  siteId: number;
  siteName: string;
  points: EnrollmentMovementTrendPoint[];
}

export function getEnrollmentMovementTrend(academicTermId: number, classId?: number): Promise<EnrollmentMovementTrendResponse> {
  const query = classId ? `?classId=${classId}` : "";
  return apiRequest<EnrollmentMovementTrendResponse>(`/academic-terms/${academicTermId}/enrollment-movement-trend${query}`);
}

export function getEnrollmentMovementTrendForRange(p: EnrollmentMovementRangeParams): Promise<EnrollmentMovementTrendResponse> {
  return apiRequest<EnrollmentMovementTrendResponse>(`/sites/${p.siteId}/enrollment-movement-trend?${rangeQuery(p)}`);
}

/** Lưới tổng quan (bổ sung ngoài SDD gốc, xác nhận 2026-08-20) — hàng đầu là tháng/kỳ/năm, cột đầu là lớp. */
export interface EnrollmentMovementGridColumn {
  key: string;
  label: string;
  startDate: string;
  endDate: string;
}

export interface EnrollmentMovementGridRow {
  classId: number;
  classCode: string;
  className: string;
  /** Sĩ số cuối đoạn của lớp này, tra theo EnrollmentMovementGridColumn.key. */
  headcountByColumnKey: Record<string, number>;
}

export interface EnrollmentMovementGridResponse {
  periodType: EnrollmentMovementPeriodType;
  siteId: number;
  siteName: string;
  columns: EnrollmentMovementGridColumn[];
  rows: EnrollmentMovementGridRow[];
}

export function getEnrollmentMovementGrid(params: {
  siteId: number;
  periodType: EnrollmentMovementPeriodType;
  /** Chỉ dùng khi periodType=MONTH -- năm muốn xem 12 tháng, mặc định năm hiện tại nếu bỏ trống. */
  year?: number;
  classId?: number;
}): Promise<EnrollmentMovementGridResponse> {
  const query = new URLSearchParams({ periodType: params.periodType });
  if (params.year) query.set("year", String(params.year));
  if (params.classId) query.set("classId", String(params.classId));
  return apiRequest<EnrollmentMovementGridResponse>(`/sites/${params.siteId}/enrollment-movement-grid?${query.toString()}`);
}

// ===================== Số tiết thực tế theo lớp (V130, bổ sung ngoài SDD gốc, xác nhận 2026-08-20) =====================
// Số tiết ĐÃ DẠY thực tế (không tính buổi CANCELLED/RESCHEDULED) của từng lớp trong 1 khoảng ngày
// tuỳ ý (tuần/tháng/kỳ/năm) — thuần đọc/báo cáo, xem Javadoc ActualPeriodsReportService.

export interface ActualPeriodsClassRow {
  classId: number;
  classCode: string;
  className: string;
  actualPeriods: number;
}

export interface ActualPeriodsStatsResponse {
  periodType: EnrollmentMovementPeriodType | "WEEK";
  periodLabel: string;
  startDate: string;
  endDate: string;
  siteId: number;
  siteName: string;
  classes: ActualPeriodsClassRow[];
  totalActualPeriods: number;
}

export function getActualPeriodsStats(params: {
  siteId: number;
  fromDate: string;
  toDate: string;
  periodType: EnrollmentMovementPeriodType | "WEEK";
  periodLabel: string;
  classId?: number;
}): Promise<ActualPeriodsStatsResponse> {
  const query = new URLSearchParams({
    fromDate: params.fromDate,
    toDate: params.toDate,
    periodType: params.periodType,
    periodLabel: params.periodLabel
  });
  if (params.classId) query.set("classId", String(params.classId));
  return apiRequest<ActualPeriodsStatsResponse>(`/sites/${params.siteId}/actual-periods-stats?${query.toString()}`);
}

/** Lưới tổng quan (bổ sung ngoài SDD gốc, xác nhận 2026-08-20) — hàng đầu là tháng/kỳ/năm, cột đầu là lớp. Không áp dụng cho "Tuần" (không có tập cột hợp lý). */
export interface ActualPeriodsGridColumn {
  key: string;
  label: string;
  startDate: string;
  endDate: string;
}

export interface ActualPeriodsGridRow {
  classId: number;
  classCode: string;
  className: string;
  actualPeriodsByColumnKey: Record<string, number>;
}

export interface ActualPeriodsGridResponse {
  periodType: EnrollmentMovementPeriodType;
  siteId: number;
  siteName: string;
  columns: ActualPeriodsGridColumn[];
  rows: ActualPeriodsGridRow[];
}

export function getActualPeriodsGrid(params: {
  siteId: number;
  periodType: EnrollmentMovementPeriodType;
  /** Chỉ dùng khi periodType=MONTH -- năm muốn xem 12 tháng, mặc định năm hiện tại nếu bỏ trống. */
  year?: number;
  classId?: number;
}): Promise<ActualPeriodsGridResponse> {
  const query = new URLSearchParams({ periodType: params.periodType });
  if (params.year) query.set("year", String(params.year));
  if (params.classId) query.set("classId", String(params.classId));
  return apiRequest<ActualPeriodsGridResponse>(`/sites/${params.siteId}/actual-periods-grid?${query.toString()}`);
}

// ===================== Năm học (V102, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-07) =====================
// Danh mục DÙNG CHUNG TOÀN HỆ THỐNG (khác Kỳ học — giới hạn theo điểm trường). Nguồn cho
// academicYearId trên classes/grade_entries/student_comments/class_enrollments/teaching_plans.

export interface AcademicYearResponse {
  id: number;
  code: string;
  name: string;
  startDate: string | null;
  endDate: string | null;
  status: "PLANNED" | "ACTIVE" | "CLOSED";
}

export interface CreateAcademicYearRequest {
  code: string;
  name: string;
  startDate?: string;
  endDate?: string;
}

export interface UpdateAcademicYearRequest {
  name: string;
  startDate?: string;
  endDate?: string;
  status: AcademicYearResponse["status"];
}

export function listAcademicYears(): Promise<AcademicYearResponse[]> {
  return apiRequest<AcademicYearResponse[]>("/academic-years");
}

export function createAcademicYear(request: CreateAcademicYearRequest): Promise<AcademicYearResponse> {
  return apiRequest<AcademicYearResponse>("/academic-years", { method: "POST", body: JSON.stringify(request) });
}

export function updateAcademicYear(id: number, request: UpdateAcademicYearRequest): Promise<AcademicYearResponse> {
  return apiRequest<AcademicYearResponse>(`/academic-years/${id}`, { method: "PUT", body: JSON.stringify(request) });
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
  academicYearId: number | null;
  academicYear: string | null;
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
  className: string;
  sessionDate: string;
  startTime: string;
  endTime: string;
  /** Buổi Sáng/Chiều/Tối của các tiết bên dưới — bổ sung ngoài SDD gốc, 2026-08-20. */
  dayPart: DayPart | null;
  /** periodNumber (theo site_period_templates, trong phạm vi dayPart) buổi này chiếm, tăng dần — bổ sung ngoài SDD gốc, 2026-08-19. */
  periodNumbers: number[];
  roomId: number | null;
  roomName: string | null;
  primaryTeacherId: number;
  primaryTeacherName: string;
  /** GV phụ của buổi (tuỳ chọn) — gán riêng theo buổi, bổ sung ngoài SDD gốc, 2026-08-19. */
  assistantTeacherId: number | null;
  assistantTeacherName: string | null;
  /** CM (Class Manager) của buổi (tuỳ chọn) — gán riêng theo buổi, bổ sung ngoài SDD gốc, 2026-08-19. */
  cmTeacherId: number | null;
  cmTeacherName: string | null;
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
  /** Màu của lớp (SchoolClass.color) — tô thẻ buổi học trên lịch làm việc dạng lưới, bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-21. */
  classColor: string | null;
}

/**
 * ĐẢO NGƯỢC quyết định 2026-08-13 (xác nhận lại 2026-08-19): không còn nhập startTime/endTime tự
 * do — đổi sang chọn tiết (periodNumbers, theo site_period_templates của điểm trường lớp này).
 * Giáo viên chính/phụ/CM chọn tay riêng từng buổi, không còn tự động suy ra từ class_teachers.
 */
export interface CreateClassSessionRequest {
  sessionDate: string;
  dayPart: DayPart;
  periodNumbers: number[];
  roomId?: number;
  sessionType: string;
  teacherType: "VIETNAMESE" | "FOREIGN";
  primaryTeacherId: number;
  assistantTeacherId?: number;
  cmTeacherId?: number;
  /** Bắt buộc khi sessionType=MAKEUP (buổi này bù cho buổi nào) — phải để trống với loại khác. Chỉ áp dụng tạo 1 buổi lẻ, không áp dụng bulk/Excel. */
  makeupForSessionId?: number;
}

/** Đảo ngược 2026-08-13 (xác nhận lại 2026-08-19): newStartTime/newEndTime đổi sang newPeriodNumbers; GV chính/phụ/CM giữ nguyên từ buổi cũ (sửa GV dùng updateSessionAssignment riêng). */
export interface RescheduleClassSessionRequest {
  newSessionDate: string;
  newDayPart: DayPart;
  newPeriodNumbers: number[];
  newRoomId?: number;
  reason?: string;
}

/** Sửa nhanh tại chỗ 1 buổi SCHEDULED (bổ sung ngoài SDD gốc, xác nhận 2026-08-19) — phục vụ click-thẻ trên lưới thời khóa biểu. */
export interface UpdateSessionAssignmentRequest {
  roomId?: number;
  teacherType: "VIETNAMESE" | "FOREIGN";
  primaryTeacherId: number;
  assistantTeacherId?: number;
  cmTeacherId?: number;
  dayPart: DayPart;
  periodNumbers: number[];
}

export function updateSessionAssignment(
  classId: number,
  sessionId: number,
  request: UpdateSessionAssignmentRequest
): Promise<ClassSessionResponse> {
  return apiRequest<ClassSessionResponse>(`/classes/${classId}/sessions/${sessionId}/assignment`, { method: "PATCH", body: JSON.stringify(request) });
}

/** Lưới thời khóa biểu toàn điểm trường theo tuần (bổ sung ngoài SDD gốc, xác nhận 2026-08-19). */
export function listSessionsForSiteTimetable(siteId: number, fromDate: string, toDate: string): Promise<ClassSessionResponse[]> {
  return apiRequest<ClassSessionResponse[]>(`/sites/${siteId}/sessions?fromDate=${fromDate}&toDate=${toDate}`);
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

/**
 * daysOfWeek dùng đúng tên hằng số java.time.DayOfWeek: MONDAY..SUNDAY. Đảo ngược 2026-08-13 (xác
 * nhận lại 2026-08-19): startTime/endTime đổi sang periodNumbers; GV chính/phụ/CM chọn tay dùng
 * chung cho cả lô, không còn tự động suy ra.
 */
export interface BulkCreateClassSessionRequest {
  startDate: string;
  endDate: string;
  daysOfWeek: string[];
  dayPart: DayPart;
  periodNumbers: number[];
  roomId?: number;
  sessionType: string;
  teacherType: "VIETNAMESE" | "FOREIGN";
  primaryTeacherId: number;
  assistantTeacherId?: number;
  cmTeacherId?: number;
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

// ===================== Nhận lớp — Giáo viên (UC-71, bổ sung ngoài SDD gốc, xác nhận 2026-08-18) =====================

export interface ClassSessionCheckInResponse {
  id: number;
  classSessionId: number;
  teacherId: number;
  teacherName: string;
  checkInTime: string;
  /** ON_TIME | LATE */
  status: string;
}

export interface ClassSessionCheckInStatusResponse {
  classSessionId: number;
  /** NOT_YET_OPEN | PENDING | ON_TIME | LATE | ABSENT — tính khi đọc, xem ClassSessionCheckInService#listEffectiveStatus. */
  effectiveStatus: string;
  checkInTime: string | null;
}

/** GV nhận lớp (xác nhận có mặt dạy) cho 1 buổi học cụ thể — cửa sổ [giờ bắt đầu - 15p, giờ kết thúc]. */
export function checkInClassSession(classSessionId: number, latitude: number, longitude: number): Promise<ClassSessionCheckInResponse> {
  return apiRequest<ClassSessionCheckInResponse>(`/class-sessions/${classSessionId}/check-in`, {
    method: "POST",
    body: JSON.stringify({ latitude, longitude })
  });
}

/** Trạng thái nhận lớp (tính ra) của chính GV trong khoảng ngày — dùng cho "Lịch của tôi". */
export function getMyClassSessionCheckInStatus(fromDate?: string, toDate?: string): Promise<ClassSessionCheckInStatusResponse[]> {
  const params = new URLSearchParams();
  if (fromDate) params.set("from", fromDate);
  if (toDate) params.set("to", toDate);
  const query = params.toString();
  return apiRequest<ClassSessionCheckInStatusResponse[]>(`/class-sessions/my-check-in-status${query ? `?${query}` : ""}`);
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

// ===================== Sổ điểm (UC-19/20, V94 — gắn theo lớp+kỳ học+Giữa/Cuối kỳ) =====================

/** V94 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — consolidate vào academic_terms): thay GradePeriodResponse, gắn (lớp, kỳ học, Giữa/Cuối kỳ) thay vì theo curriculum dùng chung nhiều lớp. */
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

export interface CreateGradeComponentSetupRequest {
  academicTermId: number;
  evaluationType: GradeComponentSetupResponse["evaluationType"];
  scaleType: GradeComponentSetupResponse["scaleType"];
  rosterAsOfDate: string;
  commentRequired: boolean;
}

/** scaleType không sửa được sau khi tạo (V97) — component đã tạo theo maxScore của thang cũ. */
export interface UpdateGradeComponentSetupRequest {
  rosterAsOfDate: string;
  commentRequired: boolean;
}

export function listGradeComponentSetups(classId: number, academicTermId?: number): Promise<GradeComponentSetupResponse[]> {
  const query = academicTermId ? `?academicTermId=${academicTermId}` : "";
  return apiRequest<GradeComponentSetupResponse[]>(`/classes/${classId}/grade-component-setups${query}`);
}

export function createGradeComponentSetup(classId: number, request: CreateGradeComponentSetupRequest): Promise<GradeComponentSetupResponse> {
  return apiRequest<GradeComponentSetupResponse>(`/classes/${classId}/grade-component-setups`, { method: "POST", body: JSON.stringify(request) });
}

export function updateGradeComponentSetup(id: number, request: UpdateGradeComponentSetupRequest): Promise<GradeComponentSetupResponse> {
  return apiRequest<GradeComponentSetupResponse>(`/grade-component-setups/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

/** UC-19 (bổ sung): chỉ xoá được setup RỖNG — chưa có thành phần điểm, chưa có điểm tổng kết, chưa bắt đầu nhập điểm (BE tự chặn 422 nếu không đủ điều kiện). */
export function deleteGradeComponentSetup(id: number): Promise<void> {
  return apiRequest<void>(`/grade-component-setups/${id}`, { method: "DELETE" });
}

/** V94 (mới): 1 dòng roster của setup sổ điểm — chỉ các field cần cho bảng nhập điểm, không import chéo từ features/student. */
export interface GradeSetupRosterStudentResponse {
  id: number;
  userId: number;
  fullName: string;
  studentCode: string;
  dateOfBirth: string;
}

/** V94 (mới): danh sách học sinh của 1 setup — TÍNH RA theo rosterAsOfDate (class_enrollments active tại đúng ngày này), không phải bảng snapshot riêng. */
export function getGradeComponentSetupRoster(setupId: number): Promise<GradeSetupRosterStudentResponse[]> {
  return apiRequest<GradeSetupRosterStudentResponse[]>(`/grade-component-setups/${setupId}/roster`);
}

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

export interface CreateGradeEvaluationComponentRequest {
  subjectId?: number;
  skillId?: number;
  code: string;
  name: string;
  maxScore?: number;
  passThreshold?: number;
  scaleType?: GradeEvaluationComponentResponse["scaleType"];
  displayOrder?: number;
}

export function listGradeEvaluationComponents(setupId: number): Promise<GradeEvaluationComponentResponse[]> {
  return apiRequest<GradeEvaluationComponentResponse[]>(`/grade-component-setups/${setupId}/components`);
}

export function addGradeEvaluationComponent(setupId: number, request: CreateGradeEvaluationComponentRequest): Promise<GradeEvaluationComponentResponse> {
  return apiRequest<GradeEvaluationComponentResponse>(`/grade-component-setups/${setupId}/components`, { method: "POST", body: JSON.stringify(request) });
}

/** UC-19 (bổ sung): chỉ xoá được đầu điểm CHƯA có điểm nhập nào (BE tự chặn 422 nếu đã có điểm). */
export function deleteGradeEvaluationComponent(id: number): Promise<void> {
  return apiRequest<void>(`/grade-evaluation-components/${id}`, { method: "DELETE" });
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
export function listGradeEntries(classId: number, gradeEvaluationComponentId: number): Promise<GradeEntryResponse[]> {
  return apiRequest<GradeEntryResponse[]>(`/classes/${classId}/grades/components/${gradeEvaluationComponentId}`);
}

export function enterGrade(classId: number, gradeEvaluationComponentId: number, request: EnterGradeRequest): Promise<GradeEntryResponse> {
  return apiRequest<GradeEntryResponse>(`/classes/${classId}/grades/components/${gradeEvaluationComponentId}`, { method: "POST", body: JSON.stringify(request) });
}

// ===================== UC-53: Overall/Level + Nhận xét/Ghi chú (V94) theo (kỳ học, Giữa/Cuối kỳ) + Nhập điểm qua Excel =====================

/** V94 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): thay GradePeriodResultResponse, thêm comment/note ("Nhận xét"/"Ghi chú") tích hợp vào sổ điểm — hiển thị PH khi status=OFFICIAL. V100: thêm disclaimer ("Lưu ý") — thông tin bổ sung đặc thù cho kỳ, hiển thị ở header UI nhập điểm. */
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

export interface EnterGradeEvaluationResultRequest {
  overallScore?: number;
  scaleType: GradeEvaluationResultResponse["scaleType"];
  level?: string;
  comment?: string;
  note?: string;
  disclaimer?: string;
}

/** UC-53: Overall/Level/Nhận xét/Ghi chú GV đã tính sẵn (nhập tay hoặc từ Excel) — hệ thống chỉ lưu, không tự tính lại. */
export function enterEvaluationResult(classId: number, studentId: number, setupId: number, request: EnterGradeEvaluationResultRequest): Promise<GradeEvaluationResultResponse> {
  return apiRequest<GradeEvaluationResultResponse>(`/classes/${classId}/grades/students/${studentId}/setups/${setupId}/result`, {
    method: "POST",
    body: JSON.stringify(request)
  });
}

export function listEvaluationResults(classId: number, setupId: number): Promise<GradeEvaluationResultResponse[]> {
  return apiRequest<GradeEvaluationResultResponse[]>(`/classes/${classId}/grade-component-setups/${setupId}/results`);
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

/** UC-53 Main Flow: tải lên 1 file .xlsx đã hoàn thiện điểm cho đúng lớp + setup sổ điểm. */
export function importGrades(classId: number, setupId: number, file: File): Promise<GradeImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<GradeImportResponse>(`/classes/${classId}/grade-component-setups/${setupId}/grades/import`, { method: "POST", body: formData });
}

/** UC-53 (bổ sung): file mẫu điền sẵn theo đúng roster + đầu điểm của setup — gọi thẳng backend (GradeImportService#buildTemplate) thay vì tự dựng phía FE, tránh lệch header. */
export function downloadGradeImportTemplate(classId: number, setupId: number): Promise<Blob> {
  return apiRequestBlob(`/classes/${classId}/grade-component-setups/${setupId}/grades/import-template`);
}

/**
 * V44: X ngày này giờ CHỈ còn ý nghĩa THÔNG TIN — mốc "lần đầu nhập điểm" cho 1 lớp +
 * setup sổ điểm, không còn gắn job tự động nào (V43 dùng để tự động "công bố dự kiến",
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
export function submitGradesForApproval(request: { gradeEntryIds?: number[]; gradeEvaluationResultIds?: number[] }): Promise<GradeEntryResponse[]> {
  return apiRequest<GradeEntryResponse[]>("/grades/submit", { method: "POST", body: JSON.stringify(request) });
}

/** UC-20 Main Flow bước 1: Quản lý điểm trường xem điểm chờ duyệt (SUBMITTED) — của (các) site mình phụ trách. */
export function listUnpublishedGrades(): Promise<GradeEntryResponse[]> {
  return apiRequest<GradeEntryResponse[]>("/grades/pending");
}

/**
 * UC-20 Main Flow bước 2-5 (V44): Duyệt (SUBMITTED/REJECTED -> OFFICIAL, hiển thị ngay
 * cho Phụ huynh) hoặc Từ chối (SUBMITTED -> REJECTED, kèm lý do tuỳ chọn) — gradeEntryIds
 * và/hoặc gradeEvaluationResultIds, ít nhất 1 danh sách phải có phần tử.
 *
 * V94 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): khi APPROVE, Quản lý điểm
 * trường được sửa "Nhận xét"/"Ghi chú" NGAY TRƯỚC KHI công bố qua evaluationResultComments/
 * evaluationResultNotes (id -> nội dung mới) — KHÔNG sửa được điểm/score. Chỉ áp dụng
 * nhánh APPROVE, chỉ trên GradeEvaluationResult.
 */
export function publishGrades(request: {
  action: "APPROVE" | "REJECT";
  gradeEntryIds?: number[];
  gradeEvaluationResultIds?: number[];
  rejectReason?: string;
  evaluationResultComments?: Record<number, string>;
  evaluationResultNotes?: Record<number, string>;
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
  commentType: "DAILY";
  classSessionId: number;
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
  // Thang thái độ chốt lại 2026-08-12 (StudentComment.Attitude) — Yếu 20%/Trung bình 50%/Khá 70%/
  // Tốt 90%/Xuất sắc 100%, thay cho thang 6 mức POOR/WEAK/AVERAGE/ABOVE_AVERAGE/FAIR/GOOD cũ.
  attitude: "WEAK" | "AVERAGE" | "FAIR" | "GOOD" | "EXCELLENT" | null;
  homeworkPreviousScore: string | null;
  // BTVN Nghe-nói buổi trước (V56, nhập tay, đối xứng với homeworkPreviousScore ở trên) — độc lập với
  // videoPreviousProgress (tự tính) bên dưới.
  homeworkPreviousSpeakingScore: string | null;
  /**
   * V130 — "BTVN buổi trước - Offline" tách Reading/Writing, điểm % giáo viên tự chấm tay. CHỈ khác
   * null khi buổi classSession.teacherType=VIETNAMESE — buổi FOREIGN tiếp tục dùng homeworkPreviousScore
   * ở trên (không tách).
   */
  homeworkPreviousReadingScore: string | null;
  homeworkPreviousWritingScore: string | null;
  homeworkNext: string | null;
  /**
   * V130 — "BTVN - Offline" (giao buổi sau) tách Reading/Writing, mô tả bài + trang giao offline. CHỈ
   * khác null khi buổi teacherType=VIETNAMESE — buổi FOREIGN tiếp tục dùng homeworkNext ở trên.
   */
  homeworkNextReading: string | null;
  homeworkNextWriting: string | null;
  // BTVN online/offline theo từng học sinh (V55, PR UC-21-giao-btvn-online-offline, 2026-07-28) —
  // kênh ngữ pháp ONLINE (homeworkNextExerciseAssignmentId khác null) hoặc OFFLINE (dùng homeworkNext
  // ở trên); kênh Video Ôn tập luôn ONLINE. grammarPreviousProgress/videoPreviousProgress tự tính từ
  // exercise_attempts/review_video_progress|submissions của buổi TRƯỚC, không nhập tay được.
  // V65 (2026-07-30, bổ sung ngoài SDD gốc): 2 field *AssignmentId đều là id BẢN GIAO (ExerciseAssignment/
  // ReviewVideoAssignment) — không phải id nguồn (Exercise/ReviewVideoSet), dùng kèm *Title để hiện
  // tên nguồn. V127 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — CHỈ có giá trị SAU
  // KHI Gửi nhận xét (giao bài chuyển từ lúc Lưu nháp sang lúc Gửi) — với dòng còn DRAFT/REJECTED (kể
  // cả vừa sửa lại lựa chọn), 2 field này null, id NGUỒN nằm ở pendingHomeworkNextExerciseId/
  // pendingHomeworkNextReviewVideoSetId bên dưới (đọc thẳng, KHÔNG cần tra ngược qua
  // listAssignmentsForClass/listReviewVideoAssignmentsForClass nữa cho case này — chỉ còn cần tra ngược
  // khi hiện lại lựa chọn GIAO LẦN TRƯỚC của 1 dòng REJECTED chưa sửa gì).
  homeworkNextExerciseAssignmentId: number | null;
  homeworkNextExerciseTitle: string | null;
  homeworkNextReviewVideoAssignmentId: number | null;
  homeworkNextReviewVideoSetTitle: string | null;
  /** V137 — "BTVN - Online - Reading/Writing" (mirror homeworkNextExerciseAssignmentId/Title), chỉ khác null khi buổi teacherType=VIETNAMESE. Bản giao Exercise skillCategory=READING/WRITING tương ứng. */
  homeworkNextReadingExerciseAssignmentId: number | null;
  homeworkNextReadingExerciseTitle: string | null;
  homeworkNextWritingExerciseAssignmentId: number | null;
  homeworkNextWritingExerciseTitle: string | null;
  /** Hạn nộp BTVN buổi sau (lấy từ dueAt của bản giao) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05. */
  homeworkNextDueAt: string | null;
  /** V127: id/tên Exercise NGUỒN Giáo viên vừa chọn nhưng CHƯA Gửi nhận xét — null nếu chưa chọn gì hoặc đã Gửi. */
  pendingHomeworkNextExerciseId: number | null;
  pendingHomeworkNextExerciseTitle: string | null;
  /** V127: mirror pendingHomeworkNextExerciseId cho kênh Video Ôn tập. */
  pendingHomeworkNextReviewVideoSetId: number | null;
  pendingHomeworkNextReviewVideoSetTitle: string | null;
  /** V137: mirror pendingHomeworkNextExerciseId cho kênh Reading/Writing. */
  pendingHomeworkNextReadingExerciseId: number | null;
  pendingHomeworkNextReadingExerciseTitle: string | null;
  pendingHomeworkNextWritingExerciseId: number | null;
  pendingHomeworkNextWritingExerciseTitle: string | null;
  /**
   * V127: hạn nộp tự chọn đi kèm lựa chọn CHƯA giao — chuỗi "yyyy-MM-ddTHH:mm:ss" KHÔNG kèm offset
   * (LocalDateTime thô phía BE, khác homeworkNextDueAt ở trên là OffsetDateTime đã resolve) — cắt
   * thẳng 2 phần ngày/giờ được, không cần parse qua Date/quy đổi múi giờ gì cả. Null nếu chưa chọn gì
   * hoặc đã Gửi.
   */
  pendingHomeworkNextDueDate: string | null;
  grammarPreviousProgress: string | null;
  videoPreviousProgress: string | null;
  /** V137 — % tự động "BTVN buổi trước - Online - Reading/Writing" (mirror grammarPreviousProgress/videoPreviousProgress), chỉ khác null khi buổi teacherType=VIETNAMESE. */
  readingPreviousProgress: string | null;
  writingPreviousProgress: string | null;
  /** BTVN buổi trước từng giao Offline (chữ tự do) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06, phân biệt "BTVN buổi trước" có 3 loại (Offline/kênh Bài/kênh Video). Loại trừ với grammarPreviousProgress. */
  homeworkPreviousOfflineText: string | null;
  note: string | null;
  /** "Bài học hôm nay" của buổi (class_sessions.lesson_content) — null nếu không phải DAILY. Bổ sung ngoài SDD gốc, 2026-07-29 — chuyển từ Điểm danh sang Nhận xét. */
  lessonContent: string | null;
}

export interface CreateStudentCommentRequest {
  studentId: number;
  classSessionId: number;
  commentDate: string;
  content: string;
  structuredContent?: Record<string, unknown>;
  severity?: StudentCommentResponse["severity"];
  isWarning: boolean;
  // Chỉ áp dụng khi commentType=DAILY — bỏ qua với MID_TERM/END_TERM.
  attitude?: NonNullable<StudentCommentResponse["attitude"]>;
  homeworkPreviousScore?: string;
  homeworkPreviousSpeakingScore?: string;
  /** V130 — chỉ gửi khi buổi teacherType=VIETNAMESE, xem Javadoc StudentCommentResponse.homeworkPreviousReadingScore. */
  homeworkPreviousReadingScore?: string;
  homeworkPreviousWritingScore?: string;
  homeworkNext?: string;
  /** V130 — chỉ gửi khi buổi teacherType=VIETNAMESE, xem Javadoc StudentCommentResponse.homeworkNextReading. */
  homeworkNextReading?: string;
  homeworkNextWriting?: string;
  /**
   * V65 (2026-07-30, bổ sung ngoài SDD gốc): kênh ngữ pháp ONLINE — id của Exercise NGUỒN (đã
   * Publish), KHÔNG phải id bản giao như trước V65. Chọn khác null tự động giao đề cho CẢ LỚP ACTIVE,
   * hạn nộp = buổi học kế tiếp — để trống nếu dùng homeworkNext (OFFLINE) hoặc không giao gì (hủy bản
   * giao cũ nếu đang sửa 1 comment DRAFT đã chọn trước đó).
   */
  homeworkNextExerciseId?: number;
  /** Kênh Video Ôn tập (luôn ONLINE) — id của ReviewVideoSet NGUỒN (đã Publish), tự động giao cả lớp tương tự. Để trống nếu không giao. */
  homeworkNextReviewVideoSetId?: number;
  /** V137 — kênh Reading/Writing ONLINE (mirror homeworkNextExerciseId) — id của Exercise NGUỒN có skillCategory=READING/WRITING tương ứng. Chỉ gửi khi buổi teacherType=VIETNAMESE. */
  homeworkNextReadingExerciseId?: number;
  homeworkNextWritingExerciseId?: number;
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
  /** V130 — xem Javadoc CreateStudentCommentRequest.homeworkPreviousReadingScore. */
  homeworkPreviousReadingScore?: string;
  homeworkPreviousWritingScore?: string;
  homeworkNext?: string;
  /** V130 — xem Javadoc CreateStudentCommentRequest.homeworkNextReading. */
  homeworkNextReading?: string;
  homeworkNextWriting?: string;
  /** V65 — xem Javadoc CreateStudentCommentRequest.homeworkNextExerciseId. */
  homeworkNextExerciseId?: number;
  homeworkNextReviewVideoSetId?: number;
  /** V137 — xem Javadoc CreateStudentCommentRequest.homeworkNextReadingExerciseId. */
  homeworkNextReadingExerciseId?: number;
  homeworkNextWritingExerciseId?: number;
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05 — xem Javadoc CreateStudentCommentRequest.homeworkNextDueDate. */
  homeworkNextDueDate?: string;
  note?: string;
}

export function listComments(classId: number, studentId: number): Promise<StudentCommentResponse[]> {
  return apiRequest<StudentCommentResponse[]>(`/classes/${classId}/comments?studentId=${studentId}`);
}

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — TOÀN BỘ nhận xét của cả lớp trong 1
 * lần gọi, thay N request/học sinh (StudentCommentResponse đã có studentId, tự gom theo học sinh ở FE).
 */
export function listCommentsForClass(classId: number): Promise<StudentCommentResponse[]> {
  return apiRequest<StudentCommentResponse[]>(`/classes/${classId}/comments`);
}

/**
 * V146 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — % TỰ ĐỘNG "BTVN buổi trước"
 * cho cả lớp, tính ngay cả khi buổi đang xem CHƯA có StudentComment nào (kể cả nháp) — trước đây các %
 * này chỉ có trong StudentCommentResponse SAU KHI đã Lưu nháp/Gửi ít nhất 1 lần, khiến mở 1 buổi mới
 * hoàn toàn không thấy % tự động của buổi trước dù backend đã tính đúng. Field null = không tự tính
 * được (VD buổi trước giao Offline).
 */
export interface AutoProgressPreviewResponse {
  studentId: number;
  grammarPreviousProgress: string | null;
  videoPreviousProgress: string | null;
  readingPreviousProgress: string | null;
  writingPreviousProgress: string | null;
}

export function previewAutoProgress(classSessionId: number): Promise<AutoProgressPreviewResponse[]> {
  return apiRequest<AutoProgressPreviewResponse[]>(`/class-sessions/${classSessionId}/comments/auto-progress-preview`);
}

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — 1 mốc "phiên bản" trong lịch sử
 * chỉnh sửa (Lưu nháp/Gửi/Duyệt/Từ chối) — kiểu version history Google Sheets, xem lại được TOÀN BỘ
 * nội dung tại đúng thời điểm đó (không chỉ biết "đã sửa"). `details` khớp đúng key BE
 * StudentCommentService#buildHistorySnapshot ghi ra — đọc trực tiếp bằng string key, không đoán field.
 */
export interface StudentCommentHistoryResponse {
  id: number;
  studentCommentId: number;
  studentId: number;
  studentFullName: string;
  changedByUserId: number;
  changedByName: string;
  /** CREATED (lần lưu đầu) / UPDATED (mọi lần lưu sau — Lưu nháp, Gửi, Duyệt/Từ chối, sửa PENDING). */
  action: "CREATED" | "UPDATED";
  details: {
    status: StudentCommentResponse["status"];
    content: string;
    severity: StudentCommentResponse["severity"];
    isWarning: boolean;
    attitude: StudentCommentResponse["attitude"];
    homeworkPreviousScore: string | null;
    homeworkPreviousSpeakingScore: string | null;
    /** V130 — chỉ khác null khi buổi teacherType=VIETNAMESE. */
    homeworkPreviousReadingScore: string | null;
    homeworkPreviousWritingScore: string | null;
    /** % tự động (kênh Ngữ pháp/Video) của "BTVN buổi trước" TẠI thời điểm lưu — xem PreviousProgressCell ở DailyCommentPanel.tsx cho ý nghĩa 2 field này. */
    grammarPreviousProgress: string | null;
    videoPreviousProgress: string | null;
    /** V137 — mirror grammarPreviousProgress/videoPreviousProgress cho kênh Reading/Writing online. */
    readingPreviousProgress: string | null;
    writingPreviousProgress: string | null;
    homeworkNext: string | null;
    homeworkNextReading: string | null;
    homeworkNextWriting: string | null;
    note: string | null;
    rejectionReason: string | null;
    homeworkNextExerciseTitle: string | null;
    homeworkNextReviewVideoSetTitle: string | null;
    /** V137 — mirror homeworkNextExerciseTitle/homeworkNextReviewVideoSetTitle cho kênh Reading/Writing online. */
    homeworkNextReadingExerciseTitle: string | null;
    homeworkNextWritingExerciseTitle: string | null;
    homeworkNextDueAt: string | null;
    pendingHomeworkNextExerciseTitle: string | null;
    pendingHomeworkNextReviewVideoSetTitle: string | null;
    /** V137 — mirror pendingHomeworkNextExerciseTitle/pendingHomeworkNextReviewVideoSetTitle cho kênh Reading/Writing online. */
    pendingHomeworkNextReadingExerciseTitle: string | null;
    pendingHomeworkNextWritingExerciseTitle: string | null;
    pendingHomeworkNextDueDate: string | null;
  };
  createdAt: string;
}

export function listStudentCommentHistory(commentId: number): Promise<StudentCommentHistoryResponse[]> {
  return apiRequest<StudentCommentHistoryResponse[]>(`/student-comments/${commentId}/history`);
}

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — mirror listStudentCommentHistory
 * nhưng gộp CẢ BUỔI (mọi học sinh), dùng cho nút "Lịch sử phiên bản" ở đầu bảng Nhận xét hàng ngày
 * (kiểu Google Sheets: 1 nút xem lại TOÀN BỘ bảng tại 1 mốc thời gian, không phải xem riêng từng dòng).
 */
export function listStudentCommentHistoryForSession(classSessionId: number): Promise<StudentCommentHistoryResponse[]> {
  return apiRequest<StudentCommentHistoryResponse[]>(`/class-sessions/${classSessionId}/comments/history`);
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

/**
 * Bổ sung ngoài SDD gốc (2026-08-24, xác nhận với người dùng) — đổi Hạn nộp BTVN buổi sau cho TOÀN
 * BỘ nhận xét NHÁP/Bị từ chối của 1 buổi trong 1 lần gọi, thay vì N request updateComment() song
 * song (luôn thất bại khi N nhận xét đang cùng giữ 1 hạn nộp cũ — xem Javadoc BE
 * StudentCommentService#bulkUpdatePendingDueDate).
 */
export function bulkUpdatePendingDueDate(classSessionId: number, dueDate: string): Promise<StudentCommentResponse[]> {
  return apiRequest<StudentCommentResponse[]>(`/class-sessions/${classSessionId}/comments/due-date`, {
    method: "PUT",
    body: JSON.stringify({ dueDate })
  });
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

/** UC-21 (bổ sung): nhập lại file đã sửa — cập nhật cả điểm danh lẫn nhận xét Hàng ngày trong 1 lần. Không còn dùng ở UI (xem previewImportDailyComments), giữ lại cho tương thích ngược. */
export function importDailyComments(classSessionId: number, file: File): Promise<DailyCommentImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<DailyCommentImportResponse>(`/class-sessions/${classSessionId}/comments/import`, { method: "POST", body: formData });
}

export interface DailyCommentImportPreviewRow {
  studentId: number;
  attitude: StudentCommentResponse["attitude"];
  homeworkPreviousScore: string | null;
  homeworkPreviousSpeakingScore: string | null;
  /** V130 — chỉ khác null khi buổi teacherType=VIETNAMESE. */
  homeworkPreviousReadingScore: string | null;
  homeworkPreviousWritingScore: string | null;
  content: string | null;
  homeworkNext: string | null;
  homeworkNextReading: string | null;
  homeworkNextWriting: string | null;
  homeworkNextExerciseId: number | null;
  homeworkNextReviewVideoSetId: number | null;
  /** V137 — chỉ khác null khi buổi teacherType=VIETNAMESE. */
  homeworkNextReadingExerciseId: number | null;
  homeworkNextWritingExerciseId: number | null;
  note: string | null;
}

export interface DailyCommentImportPreviewResponse {
  totalRows: number;
  successRows: number;
  failedRows: number;
  errorSummary: { row: number; reason: string }[];
  lessonContent: string | null;
  teacherName: string | null;
  /** "yyyy-MM-ddTHH:mm:ss" (LocalDateTime BE) — cắt về "yyyy-MM-ddTHH:mm" khi tách ngày/giờ ở FE. */
  dueDate: string | null;
  rows: DailyCommentImportPreviewRow[];
}

/**
 * UC-21 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14): xem trước file Excel BTVN —
 * CHỈ parse & trả về dữ liệu để fill vào bảng nhận xét trên UI, KHÔNG ghi StudentComment/Bài học hôm
 * nay/Tên GV giảng dạy/Hạn nộp vào DB (điểm danh vẫn ghi ngay — nghiệp vụ độc lập). Giáo viên phải tự
 * bấm "Lưu" (hoặc chờ autosave) để thật sự ghi DRAFT — khác importDailyComments cũ (ghi thẳng DB).
 */
export function previewImportDailyComments(classSessionId: number, file: File): Promise<DailyCommentImportPreviewResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<DailyCommentImportPreviewResponse>(`/class-sessions/${classSessionId}/comments/import-preview`, { method: "POST", body: formData });
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
  /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — lấy qua Đề cha (exam.teacherType). */
  teacherType: "VIETNAMESE" | "FOREIGN";
  availableFrom: string;
  dueAt: string | null;
  status: "ACTIVE" | "COMPLETED";
  totalStudents: number;
  completedCount: number;
  completionPercent: number;
  passedCount: number;
  passRatePercent: number;
  /** Bổ sung 2026-08-08: số học sinh có lần làm bị dừng do vi phạm giám sát. */
  violatedStudentCount?: number;
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
  numberOfAttempts: number;
  /** Bổ sung 2026-08-08: số lần thoát được ghi nhận của lượt làm mới nhất. */
  latestAttemptViolationCount?: number;
  /** Bổ sung 2026-08-08: lượt làm chính thức (mới nhất hoặc chọn bởi GV) có bị dừng do vi phạm không. */
  selectedAttemptStoppedByViolation?: boolean;
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

// ===================== Mẫu Báo Cáo (UC-67) & Xuất Báo Cáo (UC-68) =====================

export interface ReportTemplateFieldMappingResponse {
  id: number;
  placeholderKey: string;
  dataPath: string | null;
  fieldType: "FIELD" | "FORMULA" | "TABLE";
  description: string | null;
}

export type ReportTemplateType = "TRANSCRIPT" | "DAILY_REPORT" | "STUDENT_PROFILE" | "GRADE_REPORT" | "STUDENT_COMMENT";

export const REPORT_TEMPLATE_TYPE_LABELS: Record<ReportTemplateType, string> = {
  TRANSCRIPT: "Học bạ / Bảng điểm",
  DAILY_REPORT: "Báo cáo ngày lớp học",
  STUDENT_COMMENT: "Nhận xét học sinh",
  STUDENT_PROFILE: "Hồ sơ học sinh",
  GRADE_REPORT: "Báo cáo điểm học kỳ",
};

export interface ReportTemplateResponse {
  id: number;
  name: string;
  templateType: ReportTemplateType;
  fileFormat: "DOCX" | "PDF" | "HTML";
  fileUrl: string;
  originalFilename: string;
  fileSizeBytes: number;
  description: string | null;
  active: boolean;
  placeholderKeys: string[];
  createdBy: number | null;
  fieldMappings: ReportTemplateFieldMappingResponse[];
}

export interface CreateReportTemplateRequest {
  name: string;
  templateType: ReportTemplateType;
  description?: string;
  file: File;
}

export interface UpdateReportTemplateRequest {
  name: string;
  description?: string;
  status: "ACTIVE" | "ARCHIVED";
}

export interface FieldMappingItemRequest {
  placeholderKey: string;
  dataPath: string | null;
  fieldType: "FIELD" | "FORMULA" | "TABLE";
  description?: string;
}

export interface UpdateFieldMappingsRequest {
  mappings: FieldMappingItemRequest[];
}

export interface ReportPeriodSelector {
  label: string;
  academicTermId: number;
  evaluationType: "MID_TERM" | "END_TERM";
}

export interface GenerateReportRequest {
  templateId: number;
  scope: "SINGLE" | "CLASS_SESSION" | "BULK_CLASS";
  studentId?: number;
  classId?: number;
  classSessionId?: number;
  periods?: ReportPeriodSelector[];
  outputFormat?: "DOCX" | "PDF";
}

export interface GeneratedReportResponse {
  id: number;
  templateId: number;
  reportType: string;
  scope: string;
  targetId: number;
  fileUrl: string;
  generatedBy: number;
  createdAt: string;
}

export function listReportTemplates(templateType?: string): Promise<ReportTemplateResponse[]> {
  const url = templateType ? `/report-templates?templateType=${templateType}` : `/report-templates`;
  return apiRequest<ReportTemplateResponse[]>(url);
}

export function getReportTemplate(id: number): Promise<ReportTemplateResponse> {
  return apiRequest<ReportTemplateResponse>(`/report-templates/${id}`);
}

export function createReportTemplate(data: CreateReportTemplateRequest): Promise<ReportTemplateResponse> {
  const formData = new FormData();
  formData.append("name", data.name);
  formData.append("templateType", data.templateType);
  if (data.description) formData.append("description", data.description);
  formData.append("file", data.file);
  
  return apiRequest<ReportTemplateResponse>(`/report-templates`, {
    method: "POST",
    body: formData as any, // apiRequest allows FormData if Content-Type is auto-managed
  });
}

export function updateReportTemplate(id: number, data: UpdateReportTemplateRequest): Promise<ReportTemplateResponse> {
  return apiRequest<ReportTemplateResponse>(`/report-templates/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
    headers: { "Content-Type": "application/json" }
  });
}

export function updateFieldMappings(id: number, data: UpdateFieldMappingsRequest): Promise<ReportTemplateResponse> {
  return apiRequest<ReportTemplateResponse>(`/report-templates/${id}/field-mappings`, {
    method: "PUT",
    body: JSON.stringify(data),
    headers: { "Content-Type": "application/json" }
  });
}

export function deleteReportTemplate(id: number): Promise<void> {
  return apiRequest<void>(`/report-templates/${id}`, { method: "DELETE" });
}

export interface AvailableReportFieldResponse {
  key: string;
  label: string;
  description: string;
  fieldType: string;
}

export function getAvailableReportFields(): Promise<Record<string, AvailableReportFieldResponse[]>> {
  return apiRequest<Record<string, AvailableReportFieldResponse[]>>(`/report-templates/available-fields`);
}

export function generateReport(data: GenerateReportRequest): Promise<GeneratedReportResponse> {
  return apiRequest<GeneratedReportResponse>(`/reports/generate`, {
    method: "POST",
    body: JSON.stringify(data),
    headers: { "Content-Type": "application/json" }
  });
}
