import { UserRole } from "@/types";

export const roleLabels: Record<UserRole, string> = {
  [UserRole.SYS_ADMIN]: "Quản trị viên (Admin)",
  [UserRole.EXECUTIVE]: "Ban Giám Đốc",
  [UserRole.HEAD_ACADEMIC]: "Trưởng Phòng Đào Tạo",
  [UserRole.SITE_MANAGER]: "Quản Lý Điểm Trường",
  [UserRole.TEACHER]: "Giáo Viên Giảng Dạy",
  [UserRole.HR_MANAGER]: "Quản Lý Nhân Sự (HR)",
  [UserRole.STAFF]: "Nhân Viên",
  [UserRole.OPS_MANAGER]: "Quản Lý Vận Hành (Ops)",
  [UserRole.PARTNER_REP]: "Đại Diện Trường Liên Kết",
  [UserRole.PARENT]: "Phụ Huynh",
  [UserRole.STUDENT]: "Học Sinh"
};

/**
 * 8 role thuộc app Admin (`admin/`) theo kiến trúc A1/A2 đã định nghĩa ở
 * docs/sdd-groups/00-intro-va-kien-truc.md — 3 role còn lại (STUDENT/PARENT/
 * PARTNER_REP) thuộc app Portal (`user/`).
 *
 * KHÔNG dùng list này để CHẶN truy cập Admin (xem ProtectedRoute.tsx) — chỉ
 * dùng để hiển thị/tham chiếu. Lý do: "Nhóm vai trò (UC-03)" cho phép Admin
 * tự tạo vai trò TÙY BIẾN (role code bất kỳ, VD "TRUONG_PHONG_DAO_TAO") —
 * những role này không nằm trong enum UserRole cố định, nên nếu chặn theo
 * kiểu allowlist (chỉ cho vào nếu role nằm trong danh sách này) thì tài
 * khoản chỉ có role tùy biến sẽ bị chặn hẳn khỏi Admin dù được tạo ra để
 * dùng ở Admin. Xem portalOnlyRoles để chặn đúng chiều (blocklist).
 */
export const adminAppRoles: UserRole[] = [
  UserRole.SYS_ADMIN,
  UserRole.TEACHER,
  UserRole.HEAD_ACADEMIC,
  UserRole.SITE_MANAGER,
  UserRole.HR_MANAGER,
  UserRole.STAFF,
  UserRole.OPS_MANAGER,
  UserRole.EXECUTIVE
];

/**
 * 3 role CHỈ thuộc Portal (`user/`) — Admin dùng blocklist này (không phải
 * allowlist adminAppRoles) để quyết định 1 tài khoản có vào được Admin hay
 * không, vì vai trò tùy biến (tạo qua UC-03) không nằm trong enum UserRole
 * cố định nhưng vẫn phải vào được Admin (đó là nơi duy nhất tạo ra chúng).
 */
export const portalOnlyRoles: UserRole[] = [UserRole.STUDENT, UserRole.PARENT, UserRole.PARTNER_REP];

/**
 * Nhãn vai trò hiển thị Header/Sidebar — ưu tiên tên tiếng Việt nếu roleCodes có ít nhất 1 mã hệ
 * thống (theo rolePriorityOrder); nếu KHÔNG (tài khoản chỉ có vai trò tùy biến tạo qua UC-03, không
 * nằm trong enum UserRole cố định) thì hiện thẳng mã vai trò gốc — không suy đoán/mặc định về
 * "Học Sinh" (sai và gây hiểu nhầm nghiêm trọng, xem sự cố tài khoản Trưởng phòng đào tạo 2026-07-23).
 * FE hiện KHÔNG có cách lấy tên tiếng Việt thật của vai trò tùy biến cho chính tài khoản đang đăng
 * nhập (GET /api/auth/me chỉ trả roleCodes, không kèm tên; GET /api/roles trả tên nhưng yêu cầu
 * permission.role.manage mà tài khoản vai trò tùy biến thường không có) — cần BE bổ sung tên vai
 * trò vào CurrentUserResponse mới hiện đúng tên tiếng Việt được, đã ghi lại thành yêu cầu BE riêng.
 */
export function deriveCurrentRoleLabel(roleCodes: string[]): string {
  const matched = rolePriorityOrder.find((role) => roleCodes.includes(role));
  if (matched) return roleLabels[matched];
  return roleCodes[0] ?? "—";
}

/** Thứ tự ưu tiên khi 1 user có nhiều role — role cao nhất đứng trước, dùng để chọn currentRole hiển thị chính. */
export const rolePriorityOrder: UserRole[] = [
  UserRole.SYS_ADMIN,
  UserRole.EXECUTIVE,
  UserRole.HEAD_ACADEMIC,
  UserRole.SITE_MANAGER,
  UserRole.HR_MANAGER,
  UserRole.OPS_MANAGER,
  UserRole.TEACHER,
  UserRole.STAFF,
  UserRole.PARTNER_REP,
  UserRole.PARENT,
  UserRole.STUDENT
];
