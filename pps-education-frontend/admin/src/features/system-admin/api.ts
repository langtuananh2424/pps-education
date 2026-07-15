import { apiRequest } from "@/lib/apiClient";
import type { Page } from "@/types";

/** Khớp RoleResponse thật của backend — xem API.md > Nhóm quyền mặc định (UC-03). */
export interface RoleResponse {
  id: number;
  code: string;
  name: string;
  description: string | null;
  isSystem: boolean;
}

/** Khớp UserPermissionOverrideSummary thật — xem UserDetailResponse (UC-44 bước 3). */
export interface UserPermissionOverrideSummary {
  permissionId: number;
  permissionCode: string;
  overrideType: string;
  reason: string;
  expiresAt: string | null;
}

/** Khớp UserListItemResponse thật của backend (UC-44 bước 1). */
export interface UserListItemResponse {
  id: number;
  username: string;
  email: string;
  fullName: string;
  phone: string | null;
  departmentId: number | null;
  status: "ACTIVE" | "INACTIVE" | "SUSPENDED";
  isManagement: boolean;
  roles: RoleResponse[];
}

/** Khớp UserDetailResponse thật của backend (UC-44 bước 3). */
export interface UserDetailResponse extends UserListItemResponse {
  passwordSet: boolean;
  googleLinked: boolean;
  lastLoginAt: string | null;
  failedLoginCount: number;
  lockedUntil: string | null;
  permissionOverrides: UserPermissionOverrideSummary[];
}

/** Khớp UserResponse thật của backend — trả về sau tạo/sửa/khóa-mở khóa (UC-43/UC-47/UC-49). */
export interface UserResponse {
  id: number;
  username: string;
  email: string;
  fullName: string;
  phone: string | null;
  departmentId: number | null;
  status: "ACTIVE" | "INACTIVE" | "SUSPENDED";
  isManagement: boolean;
  passwordSet: boolean;
  googleLinked: boolean;
}

export interface UserSearchFilter {
  keyword?: string;
  departmentId?: number;
  status?: "ACTIVE" | "INACTIVE" | "SUSPENDED";
}

/** Khớp CreateUserRequest thật (UC-43). */
export interface CreateUserRequest {
  username: string;
  email: string;
  fullName: string;
  phone?: string;
  departmentId?: number;
  isManagement?: boolean;
  password?: string;
}

/** Khớp UpdateUserRequest thật (UC-49) — không có username/email/password/status. */
export interface UpdateUserRequest {
  fullName: string;
  phone?: string;
  departmentId?: number;
  isManagement: boolean;
}

/** UC-44 Main Flow bước 1-2: danh sách + tìm kiếm/lọc tài khoản. */
export function searchUsers(filter: UserSearchFilter, page: number, size: number): Promise<Page<UserListItemResponse>> {
  const params = new URLSearchParams();
  if (filter.keyword) params.set("keyword", filter.keyword);
  if (filter.departmentId) params.set("departmentId", String(filter.departmentId));
  if (filter.status) params.set("status", filter.status);
  params.set("page", String(page));
  params.set("size", String(size));
  return apiRequest<Page<UserListItemResponse>>(`/users?${params.toString()}`);
}

/** UC-44 Main Flow bước 3: chi tiết đầy đủ 1 tài khoản. */
export function getUserDetail(userId: number): Promise<UserDetailResponse> {
  return apiRequest<UserDetailResponse>(`/users/${userId}`);
}

/** UC-43: tạo tài khoản mới. */
export function createUser(request: CreateUserRequest): Promise<UserResponse> {
  return apiRequest<UserResponse>("/users", { method: "POST", body: JSON.stringify(request) });
}

/** UC-49: cập nhật hồ sơ tài khoản (họ tên/SĐT/phòng ban/is_management). */
export function updateUser(userId: number, request: UpdateUserRequest): Promise<UserResponse> {
  return apiRequest<UserResponse>(`/users/${userId}`, { method: "PUT", body: JSON.stringify(request) });
}

/** UC-47: khóa/mở khóa tài khoản (chuyển status). */
export function updateUserStatus(userId: number, status: "ACTIVE" | "INACTIVE" | "SUSPENDED"): Promise<UserResponse> {
  return apiRequest<UserResponse>(`/users/${userId}/status`, { method: "PUT", body: JSON.stringify({ status }) });
}

/** UC-45 A4: Quản trị viên đặt lại mật khẩu cho một tài khoản khác. */
export function changeUserPassword(userId: number, newPassword: string): Promise<void> {
  return apiRequest<void>(`/users/${userId}/password`, { method: "PUT", body: JSON.stringify({ newPassword }) });
}

/** Khớp PermissionMatrixItem thật — 1 dòng trong ma trận quyền của 1 role (UC-03 bước 2). */
export interface PermissionMatrixItem {
  permissionId: number;
  code: string;
  name: string;
  module: string;
  granted: boolean;
}

/** Khớp RolePermissionMatrixResponse thật (UC-03 bước 2). */
export interface RolePermissionMatrixResponse {
  roleId: number;
  roleCode: string;
  permissions: PermissionMatrixItem[];
}

/** Khớp CreateRoleRequest thật — tạo vai trò tùy chỉnh (luôn isSystem=false). */
export interface CreateRoleRequest {
  code: string;
  name: string;
  description?: string;
}

/** UC-03: danh sách 11 role hệ thống + role tùy chỉnh. */
export function listRoles(): Promise<RoleResponse[]> {
  return apiRequest<RoleResponse[]>("/roles");
}

/** UC-03 bổ sung: tạo vai trò tùy chỉnh mới. */
export function createRole(request: CreateRoleRequest): Promise<RoleResponse> {
  return apiRequest<RoleResponse>("/roles", { method: "POST", body: JSON.stringify(request) });
}

/** UC-03 bổ sung: xóa vai trò tùy chỉnh (chặn nếu là role hệ thống / đang gán cho ai / đã có trong audit log). */
export function deleteRole(roleId: number): Promise<void> {
  return apiRequest<void>(`/roles/${roleId}`, { method: "DELETE" });
}

/** UC-03 bước 2: ma trận quyền hiện tại của 1 role. */
export function getRolePermissionMatrix(roleId: number): Promise<RolePermissionMatrixResponse> {
  return apiRequest<RolePermissionMatrixResponse>(`/roles/${roleId}/permissions`);
}

/** UC-03 bước 3-4: lưu lại toàn bộ danh sách permissionId cho role (không phải diff). confirm=true khi A1 (bỏ hết quyền role đang có tài khoản active). */
export function updateRolePermissions(roleId: number, permissionIds: number[], confirm = false): Promise<void> {
  return apiRequest<void>(`/roles/${roleId}/permissions`, {
    method: "PUT",
    body: JSON.stringify({ permissionIds, confirm })
  });
}

/** UC-46: gán 1 role cho 1 tài khoản. */
export function assignUserRole(userId: number, roleId: number): Promise<void> {
  return apiRequest<void>(`/users/${userId}/roles/${roleId}`, { method: "PUT" });
}

/** UC-46 A1: thu hồi 1 role khỏi 1 tài khoản. */
export function revokeUserRole(userId: number, roleId: number): Promise<void> {
  return apiRequest<void>(`/users/${userId}/roles/${roleId}`, { method: "DELETE" });
}

/** Khớp PermissionResponse thật — 1 quyền hạt nhân trong danh mục (UC-02). */
export interface PermissionCatalogItem {
  id: number;
  code: string;
  name: string;
  module: string;
  description: string | null;
}

/** Khớp EffectivePermissionsResponse thật (UC-04). */
export interface EffectivePermissionsResponse {
  userId: number;
  permissions: string[];
}

/** UC-02: toàn bộ danh mục quyền hạt nhân. */
export function listPermissions(): Promise<PermissionCatalogItem[]> {
  return apiRequest<PermissionCatalogItem[]>("/permissions");
}

/** UC-04 bước 2: quyền hiệu lực hiện tại của 1 tài khoản (hợp từ role + override đang hiệu lực). */
export function getEffectivePermissions(userId: number): Promise<EffectivePermissionsResponse> {
  return apiRequest<EffectivePermissionsResponse>(`/users/${userId}/effective-permissions`);
}

/** UC-04 bước 3-4: cấp/tước 1 quyền ngoại lệ cho tài khoản (upsert theo UNIQUE user+permission). */
export function upsertUserPermissionOverride(
  userId: number,
  permissionId: number,
  overrideType: "GRANT" | "REVOKE",
  reason: string,
  expiresAt?: string
): Promise<void> {
  return apiRequest<void>(`/users/${userId}/permission-overrides/${permissionId}`, {
    method: "PUT",
    body: JSON.stringify({ overrideType, reason, expiresAt: expiresAt || undefined })
  });
}

/** UC-04 A2: gỡ 1 quyền ngoại lệ (backend tự đánh dấu hết hiệu lực, không xóa cứng). */
export function removeUserPermissionOverride(userId: number, permissionId: number): Promise<void> {
  return apiRequest<void>(`/users/${userId}/permission-overrides/${permissionId}`, { method: "DELETE" });
}

/** Khớp PermissionAuditLogResponse thật — chỉ chứa ID thô (không kèm tên), FE tự resolve qua danh sách user/role/permission đã tải (UC-05). */
export interface PermissionAuditLogResponse {
  id: number;
  actorUserId: number;
  targetUserId: number;
  action: "ROLE_GRANTED" | "ROLE_REVOKED" | "PERM_OVERRIDE_ADDED" | "PERM_OVERRIDE_REMOVED";
  targetRoleId: number | null;
  targetPermissionId: number | null;
  details: Record<string, unknown> | null;
  ipAddress: string | null;
  createdAt: string;
}

export interface AuditLogSearchFilter {
  actorUserId?: number;
  targetUserId?: number;
  action?: string;
  fromDate?: string;
  toDate?: string;
}

/** UC-05: tra cứu nhật ký thay đổi quyền, có lọc + phân trang. */
export function searchAuditLogs(filter: AuditLogSearchFilter, page: number, size: number): Promise<Page<PermissionAuditLogResponse>> {
  const params = new URLSearchParams();
  if (filter.actorUserId) params.set("actorUserId", String(filter.actorUserId));
  if (filter.targetUserId) params.set("targetUserId", String(filter.targetUserId));
  if (filter.action) params.set("action", filter.action);
  if (filter.fromDate) params.set("fromDate", filter.fromDate);
  if (filter.toDate) params.set("toDate", filter.toDate);
  params.set("page", String(page));
  params.set("size", String(size));
  return apiRequest<Page<PermissionAuditLogResponse>>(`/permission-audit-logs?${params.toString()}`);
}
