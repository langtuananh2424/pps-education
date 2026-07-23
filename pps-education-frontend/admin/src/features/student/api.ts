import { apiRequest } from "@/lib/apiClient";
import type { CreateUserRequest } from "@/features/system-admin/api";

/** Khớp StudentResponse thật của backend — xem UC-13 (Quản lý hồ sơ học sinh). */
export interface StudentResponse {
  id: number;
  userId: number;
  fullName: string;
  studentCode: string;
  dateOfBirth: string;
  gender: "MALE" | "FEMALE" | "OTHER" | null;
  portraitUrl: string | null;
  primarySiteId: number | null;
  primarySiteName: string | null;
  originalSchool: string | null;
  originalClass: string | null;
  status: "ACTIVE" | "SUSPENDED" | "EXPELLED" | "GRADUATED" | "WITHDRAWN" | "DEFERRAL";
  enrollmentDate: string;
  graduationDate: string | null;
  notes: string | null;
}

/**
 * Khớp CreateStudentRequest thật — cung cấp ĐÚNG 1 trong 2: `userId` (gắn
 * tài khoản có sẵn) hoặc `newAccount` (tạo tài khoản mới, tự gán role
 * STUDENT, trong cùng transaction). studentCode nhập tay, phải duy nhất.
 */
export interface CreateStudentRequest {
  userId?: number;
  newAccount?: CreateUserRequest;
  studentCode: string;
  dateOfBirth: string;
  gender?: "MALE" | "FEMALE" | "OTHER";
  portraitUrl?: string;
  primarySiteId?: number;
  originalSchool?: string;
  originalClass?: string;
  enrollmentDate: string;
  notes?: string;
}

/** Khớp UpdateStudentRequest thật — không có status (UC-14 riêng) hay primarySiteId (chỉ đổi qua recordTransfer). */
export interface UpdateStudentRequest {
  dateOfBirth: string;
  gender?: "MALE" | "FEMALE" | "OTHER";
  portraitUrl?: string;
  originalSchool?: string;
  originalClass?: string;
  notes?: string;
}

export interface ParentResponse {
  id: number;
  userId: number;
  fullName: string;
  occupation: string | null;
  workplace: string | null;
  address: string | null;
  notes: string | null;
  /** V48: ảnh đại diện phụ huynh, upload qua POST /api/media/upload (module PARENT). */
  portraitUrl: string | null;
}

/** Khớp CreateParentRequest thật — cung cấp ĐÚNG 1 trong 2: userId hoặc newAccount (tự gán role PARENT). */
export interface CreateParentRequest {
  userId?: number;
  newAccount?: CreateUserRequest;
  occupation?: string;
  workplace?: string;
  address?: string;
  notes?: string;
  portraitUrl?: string;
}

export interface UpdateParentRequest {
  occupation?: string;
  workplace?: string;
  address?: string;
  notes?: string;
  portraitUrl?: string;
}

export interface ParentStudentResponse {
  id: number;
  parentId: number;
  parentFullName: string;
  studentId: number;
  relationship: "FATHER" | "MOTHER" | "GUARDIAN" | "OTHER";
  isPrimaryContact: boolean;
  isFinancialResponsible: boolean;
  notes: string | null;
}

/** Khớp LinkParentRequest thật — liên kết 1 phụ huynh ĐÃ CÓ hồ sơ với học sinh. */
export interface LinkParentRequest {
  parentId: number;
  relationship: "FATHER" | "MOTHER" | "GUARDIAN" | "OTHER";
  isPrimaryContact: boolean;
  isFinancialResponsible: boolean;
  notes?: string;
}

export interface StudentTransferHistoryResponse {
  id: number;
  studentId: number;
  transferType: "CLASS_CHANGE" | "SITE_CHANGE" | "BOTH";
  fromClassId: number | null;
  toClassId: number | null;
  fromSiteId: number | null;
  toSiteId: number | null;
  effectiveDate: string;
  reason: string | null;
  approvedBy: number;
}

export interface RecordTransferRequest {
  transferType: "CLASS_CHANGE" | "SITE_CHANGE" | "BOTH";
  fromClassId?: number;
  toClassId?: number;
  toSiteId?: number;
  effectiveDate: string;
  reason?: string;
}

export interface StudentStatusHistoryResponse {
  id: number;
  studentId: number;
  oldStatus: string;
  newStatus: string;
  reason: string | null;
  effectiveDate: string;
  changedBy: number;
  changedAt: string;
}

/** UC-14: chọn trạng thái học tập mới. */
export interface UpdateStudentStatusRequest {
  newStatus: StudentResponse["status"];
  reason?: string;
  effectiveDate: string;
}

/** Khớp SiteResponse thật (chỉ lấy field cần cho dropdown chọn điểm trường). */
export interface SiteOption {
  id: number;
  code: string;
  name: string;
}

/** UC-13 Main Flow bước 1: danh sách/tìm kiếm học sinh — lọc thêm theo điểm trường (siteId) nếu có. */
export function listStudents(query?: string, siteId?: number): Promise<StudentResponse[]> {
  const params = new URLSearchParams();
  if (query?.trim()) params.set("query", query.trim());
  if (siteId) params.set("siteId", String(siteId));
  const qs = params.toString();
  return apiRequest<StudentResponse[]>(`/students${qs ? `?${qs}` : ""}`);
}

export function getStudent(id: number): Promise<StudentResponse> {
  return apiRequest<StudentResponse>(`/students/${id}`);
}

/** UC-13 Main Flow bước 1-3: khởi tạo hồ sơ học sinh (kèm tài khoản mới hoặc gắn tài khoản có sẵn). */
export function createStudent(request: CreateStudentRequest): Promise<StudentResponse> {
  return apiRequest<StudentResponse>("/students", { method: "POST", body: JSON.stringify(request) });
}

export function updateStudent(id: number, request: UpdateStudentRequest): Promise<StudentResponse> {
  return apiRequest<StudentResponse>(`/students/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

/** UC-13 Main Flow bước 2: khởi tạo hồ sơ phụ huynh (kèm tài khoản mới hoặc gắn tài khoản có sẵn). Resource /api/parents (tách khỏi /api/students từ PR#39). */
export function createParent(request: CreateParentRequest): Promise<ParentResponse> {
  return apiRequest<ParentResponse>("/parents", { method: "POST", body: JSON.stringify(request) });
}

export function updateParent(parentId: number, request: UpdateParentRequest): Promise<ParentResponse> {
  return apiRequest<ParentResponse>(`/parents/${parentId}`, { method: "PUT", body: JSON.stringify(request) });
}

/** Danh sách/tìm kiếm toàn bộ phụ huynh — có từ PR#39, thay cho cách tổng hợp thủ công qua từng học sinh. */
export function searchParents(query?: string): Promise<ParentResponse[]> {
  const params = query?.trim() ? `?query=${encodeURIComponent(query.trim())}` : "";
  return apiRequest<ParentResponse[]>(`/parents${params}`);
}

export function getParentById(id: number): Promise<ParentResponse> {
  return apiRequest<ParentResponse>(`/parents/${id}`);
}

export function listStudentParents(studentId: number): Promise<ParentStudentResponse[]> {
  return apiRequest<ParentStudentResponse[]>(`/students/${studentId}/parents`);
}

/** UC-13 Main Flow bước 2: liên kết 1 phụ huynh đã có hồ sơ với học sinh. */
export function linkParent(studentId: number, request: LinkParentRequest): Promise<ParentStudentResponse> {
  return apiRequest<ParentStudentResponse>(`/students/${studentId}/parents`, { method: "POST", body: JSON.stringify(request) });
}

export function unlinkParent(studentId: number, parentStudentId: number): Promise<void> {
  return apiRequest<void>(`/students/${studentId}/parents/${parentStudentId}`, { method: "DELETE" });
}

export function listTransferHistory(studentId: number): Promise<StudentTransferHistoryResponse[]> {
  return apiRequest<StudentTransferHistoryResponse[]>(`/students/${studentId}/transfers`);
}

/** UC-13 Main Flow bước 4, A1: ghi nhận chuyển lớp/chuyển điểm trường. */
export function recordTransfer(studentId: number, request: RecordTransferRequest): Promise<StudentTransferHistoryResponse> {
  return apiRequest<StudentTransferHistoryResponse>(`/students/${studentId}/transfers`, { method: "POST", body: JSON.stringify(request) });
}

export function listStatusHistory(studentId: number): Promise<StudentStatusHistoryResponse[]> {
  return apiRequest<StudentStatusHistoryResponse[]>(`/students/${studentId}/status-history`);
}

/** UC-14: đổi trạng thái học tập — quyền riêng student.status.manage, tách khỏi nhóm student.profile.xxx / student.parent.xxx (V44). */
export function updateStudentStatus(studentId: number, request: UpdateStudentStatusRequest): Promise<StudentStatusHistoryResponse> {
  return apiRequest<StudentStatusHistoryResponse>(`/students/${studentId}/status`, { method: "POST", body: JSON.stringify(request) });
}

/** Danh sách điểm trường — dùng cho dropdown chọn primarySiteId (GET /api/sites không yêu cầu permission riêng). */
export function listSites(): Promise<SiteOption[]> {
  return apiRequest<SiteOption[]>("/sites");
}

/** UC-35: Nhập học sinh theo lô (FR-CRM-04) — khớp StudentBatchImportResponse thật. */
export interface StudentBatchImportResponse {
  id: number;
  sourceFileName: string;
  totalRows: number | null;
  successRows: number;
  failedRows: number;
  status: string;
  errorSummary: { row: number; reason: string }[];
}

export function importStudents(file: File): Promise<StudentBatchImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<StudentBatchImportResponse>("/student-imports", { method: "POST", body: formData });
}

/** UC-50: Nhập phụ huynh theo lô (FR-STU-04) — khớp ParentBatchImportResponse thật. */
export interface ParentBatchImportResponse {
  id: number;
  sourceFileName: string;
  totalRows: number | null;
  successRows: number;
  failedRows: number;
  status: string;
  errorSummary: { row: number; reason: string }[];
}

export function importParents(file: File): Promise<ParentBatchImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<ParentBatchImportResponse>("/parent-imports", { method: "POST", body: formData });
}
