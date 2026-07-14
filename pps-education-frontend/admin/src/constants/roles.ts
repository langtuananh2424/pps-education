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
