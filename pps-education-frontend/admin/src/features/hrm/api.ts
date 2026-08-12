import { apiRequest, apiRequestBlob } from "@/lib/apiClient";
import type { CreateUserRequest } from "@/features/system-admin/api";
import type { ClassSessionResponse } from "@/features/academic/api";

/** Khớp EmployeeResponse thật của backend — xem UC-08 (Quản lý hồ sơ nhân sự). */
export interface EmployeeResponse {
  id: number;
  userId: number;
  fullName: string;
  employeeCode: string;
  dateOfBirth: string;
  idCardNumber: string | null;
  idCardIssuedDate: string | null;
  idCardIssuedPlace: string | null;
  permanentAddress: string | null;
  currentAddress: string | null;
  bankAccountNumber: string | null;
  bankName: string | null;
  taxCode: string | null;
  socialInsuranceNumber: string | null;
  employeeType: "TEACHER" | "STAFF" | "MANAGER";
  positionId: number | null;
  positionName: string | null;
  departmentId: number | null;
  isManagement: boolean;
  isDefaultShiftRequired: boolean;
  hireDate: string;
  terminationDate: string | null;
  status: "ACTIVE" | "ON_LEAVE" | "TERMINATED";
  /** V48: ảnh đại diện nhân sự, upload qua POST /api/media/upload (module EMPLOYEE). */
  portraitUrl: string | null;
}

/**
 * Khớp CreateEmployeeRequest thật — cung cấp ĐÚNG 1 trong 2: `userId` (gắn
 * tài khoản có sẵn) hoặc `newAccount` (tạo tài khoản mới kèm hồ sơ trong
 * cùng transaction, UC-43 lồng trong UC-08).
 */
export interface CreateEmployeeRequest {
  userId?: number;
  newAccount?: CreateUserRequest;
  employeeCode: string;
  dateOfBirth: string;
  idCardNumber?: string;
  idCardIssuedDate?: string;
  idCardIssuedPlace?: string;
  permanentAddress?: string;
  currentAddress?: string;
  bankAccountNumber?: string;
  bankName?: string;
  taxCode?: string;
  socialInsuranceNumber?: string;
  employeeType: "TEACHER" | "STAFF" | "MANAGER";
  positionId?: number | null;
  departmentId?: number;
  isManagement?: boolean;
  isDefaultShiftRequired?: boolean;
  hireDate: string;
  portraitUrl?: string;
}

/** Khớp UpdateEmployeeRequest thật — employeeCode/userId bất biến, không sửa qua đây. */
export interface UpdateEmployeeRequest {
  dateOfBirth: string;
  idCardNumber?: string;
  idCardIssuedDate?: string;
  idCardIssuedPlace?: string;
  permanentAddress?: string;
  currentAddress?: string;
  bankAccountNumber?: string;
  bankName?: string;
  taxCode?: string;
  socialInsuranceNumber?: string;
  employeeType: "TEACHER" | "STAFF" | "MANAGER";
  positionId?: number | null;
  departmentId?: number;
  isManagement: boolean;
  isDefaultShiftRequired?: boolean;
  status: "ACTIVE" | "ON_LEAVE" | "TERMINATED";
  terminationDate?: string;
  portraitUrl?: string;
}

export interface QualificationResponse {
  id: number;
  employeeId: number;
  qualificationType: "DEGREE" | "PEDAGOGY_CERT" | "LANGUAGE_CERT" | "OTHER";
  title: string;
  issuer: string | null;
  issuedDate: string | null;
  expiryDate: string | null;
  fileUrl: string | null;
}

export interface CreateQualificationRequest {
  qualificationType: "DEGREE" | "PEDAGOGY_CERT" | "LANGUAGE_CERT" | "OTHER";
  title: string;
  issuer?: string;
  issuedDate?: string;
  expiryDate?: string;
  fileUrl?: string;
}

export interface CommendationResponse {
  id: number;
  employeeId: number;
  recordType: "COMMENDATION" | "DISCIPLINE";
  recordDate: string;
  title: string;
  amount: number | null;
  decidedBy: number | null;
}

export interface CreateCommendationRequest {
  recordType: "COMMENDATION" | "DISCIPLINE";
  recordDate: string;
  title: string;
  amount?: number;
}

export interface EmploymentContractResponse {
  id: number;
  employeeId: number;
  contractNumber: string;
  contractType: "PROBATION" | "FIXED_TERM" | "INDEFINITE" | "SEASONAL";
  startDate: string;
  endDate: string | null;
  baseSalary: number;
  salaryType: "MONTHLY" | "HOURLY";
  status: "DRAFT" | "ACTIVE" | "EXPIRED" | "TERMINATED";
  fileUrl: string | null;
}

export interface CreateEmploymentContractRequest {
  contractNumber: string;
  contractType: "PROBATION" | "FIXED_TERM" | "INDEFINITE" | "SEASONAL";
  startDate: string;
  endDate?: string;
  baseSalary: number;
  salaryType: "MONTHLY" | "HOURLY";
  status: "DRAFT" | "ACTIVE" | "EXPIRED" | "TERMINATED";
  fileUrl?: string;
}

/** Khớp UpdateEmploymentContractRequest thật — KHÔNG có contractNumber (bất biến sau khi tạo). */
export interface UpdateEmploymentContractRequest {
  contractType: "PROBATION" | "FIXED_TERM" | "INDEFINITE" | "SEASONAL";
  startDate: string;
  endDate?: string;
  baseSalary: number;
  salaryType: "MONTHLY" | "HOURLY";
  status: "DRAFT" | "ACTIVE" | "EXPIRED" | "TERMINATED";
  fileUrl?: string;
}

export interface ExpiringContractResponse {
  contractId: number;
  employeeId: number;
  employeeCode: string;
  employeeFullName: string;
  contractNumber: string;
  endDate: string;
}

/** UC-08 Main Flow bước 1: danh sách/tìm kiếm nhân sự. */
export function listEmployees(query?: string): Promise<EmployeeResponse[]> {
  const params = query?.trim() ? `?query=${encodeURIComponent(query.trim())}` : "";
  return apiRequest<EmployeeResponse[]>(`/employees${params}`);
}

export function getEmployee(id: number): Promise<EmployeeResponse> {
  return apiRequest<EmployeeResponse>(`/employees/${id}`);
}

/** UC-08 Main Flow bước 1-2: khởi tạo hồ sơ nhân sự (kèm tài khoản mới hoặc gắn tài khoản có sẵn). */
export function createEmployee(request: CreateEmployeeRequest): Promise<EmployeeResponse> {
  return apiRequest<EmployeeResponse>("/employees", { method: "POST", body: JSON.stringify(request) });
}

export function updateEmployee(id: number, request: UpdateEmployeeRequest): Promise<EmployeeResponse> {
  return apiRequest<EmployeeResponse>(`/employees/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

/** UC-63: nhân sự tự sửa hồ sơ CHÍNH MÌNH — whitelist field hẹp hơn hẳn UpdateEmployeeRequest (chỉ ảnh + 2 địa chỉ). */
export interface UpdateOwnEmployeeProfileRequest {
  portraitUrl?: string;
  permanentAddress?: string;
  currentAddress?: string;
}

/** Trả 404 nếu tài khoản đang đăng nhập chưa có hồ sơ Employee (VD SUPER_ADMIN thuần) — gọi có try/catch ở nơi dùng. */
export function getMyEmployeeProfile(): Promise<EmployeeResponse> {
  return apiRequest<EmployeeResponse>("/employees/me");
}

export function updateMyEmployeeProfile(request: UpdateOwnEmployeeProfileRequest): Promise<EmployeeResponse> {
  return apiRequest<EmployeeResponse>("/employees/me", { method: "PUT", body: JSON.stringify(request) });
}

export function listQualifications(employeeId: number): Promise<QualificationResponse[]> {
  return apiRequest<QualificationResponse[]>(`/employees/${employeeId}/qualifications`);
}

export function addQualification(employeeId: number, request: CreateQualificationRequest): Promise<QualificationResponse> {
  return apiRequest<QualificationResponse>(`/employees/${employeeId}/qualifications`, { method: "POST", body: JSON.stringify(request) });
}

export function listCommendations(employeeId: number): Promise<CommendationResponse[]> {
  return apiRequest<CommendationResponse[]>(`/employees/${employeeId}/commendations`);
}

export function addCommendation(employeeId: number, request: CreateCommendationRequest): Promise<CommendationResponse> {
  return apiRequest<CommendationResponse>(`/employees/${employeeId}/commendations`, { method: "POST", body: JSON.stringify(request) });
}

export function listContracts(employeeId: number): Promise<EmploymentContractResponse[]> {
  return apiRequest<EmploymentContractResponse[]>(`/employees/${employeeId}/contracts`);
}

export function addContract(employeeId: number, request: CreateEmploymentContractRequest): Promise<EmploymentContractResponse> {
  return apiRequest<EmploymentContractResponse>(`/employees/${employeeId}/contracts`, { method: "POST", body: JSON.stringify(request) });
}

export function updateContract(employeeId: number, contractId: number, request: UpdateEmploymentContractRequest): Promise<EmploymentContractResponse> {
  return apiRequest<EmploymentContractResponse>(`/employees/${employeeId}/contracts/${contractId}`, { method: "PUT", body: JSON.stringify(request) });
}

/** UC-08 A2: hợp đồng ACTIVE sắp/đã hết hạn trong `withinDays` ngày. */
export function listExpiringContracts(withinDays: number): Promise<ExpiringContractResponse[]> {
  return apiRequest<ExpiringContractResponse[]>(`/employees/contracts/expiring?withinDays=${withinDays}`);
}

// ===================== Phòng ban (Department) — đổ dropdown, UC-08 =====================

export interface DepartmentResponse {
  id: number;
  code: string;
  name: string;
  headUserId: number | null;
  headUserFullName: string | null;
  parentDepartmentId: number | null;
  parentDepartmentName: string | null;
}

export interface CreateDepartmentRequest {
  code: string;
  name: string;
  headUserId?: number;
  parentDepartmentId?: number;
}

/** Khớp UpdateDepartmentRequest thật — code bất biến, không sửa qua đây. */
export interface UpdateDepartmentRequest {
  name: string;
  headUserId?: number;
  parentDepartmentId?: number;
}

export function listDepartments(): Promise<DepartmentResponse[]> {
  return apiRequest<DepartmentResponse[]>("/departments");
}

export function getDepartment(id: number): Promise<DepartmentResponse> {
  return apiRequest<DepartmentResponse>(`/departments/${id}`);
}

export function createDepartment(request: CreateDepartmentRequest): Promise<DepartmentResponse> {
  return apiRequest<DepartmentResponse>("/departments", { method: "POST", body: JSON.stringify(request) });
}

export function updateDepartment(id: number, request: UpdateDepartmentRequest): Promise<DepartmentResponse> {
  return apiRequest<DepartmentResponse>(`/departments/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export function deleteDepartment(id: number): Promise<void> {
  return apiRequest<void>(`/departments/${id}`, { method: "DELETE" });
}

// ===================== Chức vụ (Position) — đổ dropdown + gán role mặc định, bổ sung ngoài SDD gốc (V36) =====================

export interface PositionResponse {
  id: number;
  code: string;
  name: string;
}

export interface CreatePositionRequest {
  code: string;
  name: string;
}

/** Khớp UpdatePositionRequest thật — code bất biến, không sửa qua đây. */
export interface UpdatePositionRequest {
  name: string;
}

export function listPositions(): Promise<PositionResponse[]> {
  return apiRequest<PositionResponse[]>("/positions");
}

export function getPosition(id: number): Promise<PositionResponse> {
  return apiRequest<PositionResponse>(`/positions/${id}`);
}

export function createPosition(request: CreatePositionRequest): Promise<PositionResponse> {
  return apiRequest<PositionResponse>("/positions", { method: "POST", body: JSON.stringify(request) });
}

export function updatePosition(id: number, request: UpdatePositionRequest): Promise<PositionResponse> {
  return apiRequest<PositionResponse>(`/positions/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export function deletePosition(id: number): Promise<void> {
  return apiRequest<void>(`/positions/${id}`, { method: "DELETE" });
}

export interface PositionDefaultRoleResponse {
  id: number;
  code: string;
  name: string;
  description: string | null;
  isSystem: boolean;
}

export interface PositionDefaultRolesResponse {
  positionId: number;
  positionCode: string;
  defaultRoles: PositionDefaultRoleResponse[];
}

export function getPositionDefaultRoles(positionId: number): Promise<PositionDefaultRolesResponse> {
  return apiRequest<PositionDefaultRolesResponse>(`/positions/${positionId}/default-roles`);
}

/** Thay thế TOÀN BỘ danh sách role mặc định của chức vụ — roleIds=[] nghĩa là bỏ hết, không tự gán role nào khi chọn chức vụ này. */
export function updatePositionDefaultRoles(positionId: number, roleIds: number[]): Promise<void> {
  return apiRequest<void>(`/positions/${positionId}/default-roles`, { method: "PUT", body: JSON.stringify({ roleIds }) });
}

/**
 * UC-51: Nhập nhân sự theo lô (FR-HRM-05) — khớp EmployeeBatchImportResponse
 * thật. generatedCredentials chỉ có ở lần gọi import này, gọi lại
 * GET /api/employee-imports/{id} sau đó sẽ không còn thấy mật khẩu tạm.
 */
export interface EmployeeBatchImportResponse {
  id: number;
  sourceFileName: string;
  totalRows: number | null;
  successRows: number;
  failedRows: number;
  status: string;
  errorSummary: { row: number; reason: string }[];
  generatedCredentials: { row: number; username: string; temporaryPassword: string }[];
}

export function importEmployees(file: File): Promise<EmployeeBatchImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<EmployeeBatchImportResponse>("/employee-imports", { method: "POST", body: formData });
}

/** Xuất Excel danh sách tài khoản nhân sự vừa tạo qua import — khớp AccountExportRequest thật, cùng cơ chế Student/Parent. */
export function exportEmployeeAccounts(
  accounts: { username: string; temporaryPassword: string; fullName?: string }[]
): Promise<Blob> {
  return apiRequestBlob("/employee-imports/accounts-export", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ accounts })
  });
}

// ===================== Đơn từ (UC-10/UC-11, FR-HRM-03) =====================

/** Khớp SubstituteAssignmentRequest thật — 1 buổi học + giáo viên dạy thay được chọn (UC-10 bước 3). */
export interface SubstituteAssignmentRequest {
  classSessionId: number;
  substituteTeacherId: number;
}

/** Khớp CreateLeaveRequestRequest thật. substitutes: chỉ Giáo viên có buổi dạy trong khoảng nghỉ mới điền (UC-10 A3/A4). */
export interface CreateLeaveRequestRequest {
  leaveType: "ANNUAL" | "SICK" | "UNPAID" | "LATE" | "EARLY_LEAVE" | "PERSONAL";
  startDate: string;
  endDate: string;
  startTime?: string;
  endTime?: string;
  reason: string;
  attachmentUrl?: string;
  substitutes?: SubstituteAssignmentRequest[] | null;
}

export interface DecideLeaveRequestRequest {
  decision: "APPROVED" | "REJECTED";
  comment?: string;
}

/** Khớp LeaveRequestResponse thật của backend. */
export interface LeaveRequestResponse {
  id: number;
  employeeId: number;
  employeeFullName: string;
  employeeCode: string;
  departmentName: string | null;
  leaveType: "ANNUAL" | "SICK" | "UNPAID" | "LATE" | "EARLY_LEAVE" | "PERSONAL";
  startDate: string;
  endDate: string;
  startTime: string | null;
  endTime: string | null;
  totalDays: number;
  reason: string;
  attachmentUrl: string | null;
  status: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
  currentStep: number;
  currentApproverUserId: number | null;
  submittedAt: string | null;
  finalizedAt: string | null;
}

/** Khớp LeaveRequestApprovalResponse thật — 1 bước duyệt (UC-11). */
export interface LeaveRequestApprovalResponse {
  id: number;
  stepOrder: number;
  approverRole: "DEPARTMENT_HEAD" | "OPERATIONS_MANAGER" | "EXECUTIVE";
  approverUserId: number | null;
  approverName: string | null;
  decision: "APPROVED" | "REJECTED" | null;
  comment: string | null;
  decidedAt: string | null;
}

/** UC-10 Main Flow bước 5: nộp đơn từ — nếu có substitutes, hệ thống gán dạy thay NGAY, không đợi duyệt. */
export function submitLeaveRequest(request: CreateLeaveRequestRequest): Promise<LeaveRequestResponse> {
  return apiRequest<LeaveRequestResponse>("/leave-requests", { method: "POST", body: JSON.stringify(request) });
}

/** UC-10 bước 3: buổi dạy của người gọi trong khoảng nghỉ dự kiến, để chọn giáo viên dạy thay trước khi nộp đơn. */
export function listTeachingSessionsForSubstitution(startDate: string, endDate: string): Promise<ClassSessionResponse[]> {
  return apiRequest<ClassSessionResponse[]>(`/leave-requests/teaching-sessions?startDate=${startDate}&endDate=${endDate}`);
}

/** UC-10 bước 3: gợi ý giáo viên dạy thay — self-service, KHÔNG dùng /api/users (đòi quyền user.view mà Giáo viên không có). */
export interface TeacherLookupResponse {
  id: number;
  username: string;
  email: string;
  fullName: string;
}

export function searchSubstituteTeacherCandidates(keyword?: string): Promise<TeacherLookupResponse[]> {
  const params = keyword?.trim() ? `?keyword=${encodeURIComponent(keyword.trim())}` : "";
  return apiRequest<TeacherLookupResponse[]>(`/leave-requests/substitute-teacher-candidates${params}`);
}

/** Self-service: đơn từ đã nộp của chính người gọi. */
export function listMyLeaveRequests(): Promise<LeaveRequestResponse[]> {
  return apiRequest<LeaveRequestResponse[]>("/leave-requests/mine");
}

/** UC-11 Main Flow bước 1: danh sách đơn chờ duyệt thuộc thẩm quyền người gọi. */
export function listPendingLeaveRequestsForApprover(): Promise<LeaveRequestResponse[]> {
  return apiRequest<LeaveRequestResponse[]>("/leave-requests/pending-for-me");
}

export function getLeaveRequest(id: number): Promise<LeaveRequestResponse> {
  return apiRequest<LeaveRequestResponse>(`/leave-requests/${id}`);
}

export function listLeaveRequestApprovals(id: number): Promise<LeaveRequestApprovalResponse[]> {
  return apiRequest<LeaveRequestApprovalResponse[]>(`/leave-requests/${id}/approvals`);
}

/** UC-11 Main Flow bước 3-6: duyệt/từ chối 1 bước — nếu REJECTED và đơn có dạy thay, hệ thống thu hồi ngay (A2). */
export function decideLeaveRequest(id: number, request: DecideLeaveRequestRequest): Promise<LeaveRequestResponse> {
  return apiRequest<LeaveRequestResponse>(`/leave-requests/${id}/decision`, { method: "POST", body: JSON.stringify(request) });
}

/** UC-11 Mở rộng: trang lịch sử dạy thay (GV dạy thay theo đơn nghỉ, tự thu hồi sau end_date + 2 ngày). */
export interface LeaveSubstitutionResponse {
  id: number;
  leaveRequestId: number;
  classSessionId: number;
  sessionDate: string;
  classId: number;
  className: string;
  originalTeacherId: number;
  originalTeacherName: string;
  substituteTeacherId: number;
  substituteTeacherName: string;
  revokedAt: string | null;
  createdAt: string;
}

export function listLeaveSubstitutionHistory(): Promise<LeaveSubstitutionResponse[]> {
  return apiRequest<LeaveSubstitutionResponse[]>("/leave-substitutions");
}

/** Loại nghỉ phép — lấy từ backend (tránh hardcode). */
export interface LeaveTypeResponse {
  code: string;
  label: string;
  sortOrder: number;
}

export function listLeaveTypes(): Promise<LeaveTypeResponse[]> {
  return apiRequest<LeaveTypeResponse[]>("/leave-types");
}

// ===================== UC-09: Chấm công (FR-HRM-02) =====================

/** Khớp AttendanceRecordResponse thật của backend — chấm công của chính mình (tự phục vụ). */
export interface AttendanceRecordResponse {
  id: number;
  employeeId: number;
  workDate: string;
  checkInAt: string | null;
  checkOutAt: string | null;
  checkInMethod: "FINGERPRINT" | "FACE" | "GPS" | "MANUAL" | null;
  checkOutMethod: "FINGERPRINT" | "FACE" | "GPS" | "MANUAL" | null;
  siteId: number | null;
  status: "NORMAL" | "LATE" | "EARLY_LEAVE" | "MISSING";
}

/** Khớp AttendanceCheckRequest thật — siteId/latitude/longitude bắt buộc với method=GPS. */
export interface AttendanceCheckRequest {
  method: "FINGERPRINT" | "FACE" | "GPS" | "MANUAL";
  siteId?: number;
  latitude?: number;
  longitude?: number;
  biometricVerified?: boolean;
}

export function checkIn(request: AttendanceCheckRequest): Promise<AttendanceRecordResponse> {
  return apiRequest<AttendanceRecordResponse>("/attendance/check-in", { method: "POST", body: JSON.stringify(request) });
}

export function checkOut(request: AttendanceCheckRequest): Promise<AttendanceRecordResponse> {
  return apiRequest<AttendanceRecordResponse>("/attendance/check-out", { method: "POST", body: JSON.stringify(request) });
}

/**
 * Khớp AttendanceRecordAdminResponse thật — bổ sung ngoài UC-09 gốc, xác nhận
 * với người dùng 2026-08-12. Xem GET /api/attendance/records (AttendanceController.java),
 * gate quyền hrm.attendance.view-all.
 */
export interface AttendanceRecordAdminResponse {
  id: number;
  employeeId: number;
  employeeFullName: string;
  employeeCode: string;
  workDate: string;
  checkInAt: string | null;
  checkOutAt: string | null;
  checkInMethod: "FINGERPRINT" | "FACE" | "GPS" | "MANUAL" | null;
  checkOutMethod: "FINGERPRINT" | "FACE" | "GPS" | "MANUAL" | null;
  siteId: number | null;
  siteName: string | null;
  status: "NORMAL" | "LATE" | "EARLY_LEAVE" | "MISSING";
}

export interface ListAttendanceRecordsParams {
  employeeId?: number;
  siteId?: number;
  from: string;
  to: string;
}

export function listAttendanceRecords(params: ListAttendanceRecordsParams): Promise<AttendanceRecordAdminResponse[]> {
  const query = new URLSearchParams();
  query.set("from", params.from);
  query.set("to", params.to);
  if (params.employeeId != null) query.set("employeeId", String(params.employeeId));
  if (params.siteId != null) query.set("siteId", String(params.siteId));
  return apiRequest<AttendanceRecordAdminResponse[]>(`/attendance/records?${query.toString()}`);
}
