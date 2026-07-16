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
  positionTitle: string | null;
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
  positionTitle?: string;
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
  positionTitle?: string;
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
