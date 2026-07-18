import { apiRequest } from "@/lib/apiClient";
import type { CreateUserRequest } from "@/features/system-admin/api";

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
