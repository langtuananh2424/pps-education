import { Employee, UserRole } from "@/types";

export function getEmployeeOrganization(emp: Employee): string {
  switch (emp.role) {
    case UserRole.EXECUTIVE:
      return "Ban Giám Đốc";
    case UserRole.HEAD_ACADEMIC:
      return "Phòng Quản lý Đào tạo";
    case UserRole.SITE_MANAGER:
      return "Ban Giám đốc Điểm trường";
    case UserRole.TEACHER:
      return "Khối Giáo viên Bản ngữ/Việt Nam";
    case UserRole.HR_MANAGER:
      return "Phòng Hành chính - Nhân sự";
    case UserRole.STAFF:
      return "Tổ Giáo vụ / Tài vụ - Kế toán";
    case UserRole.OPS_MANAGER:
      return "Tổ Vận hành Điểm trường";
    default:
      return "Khối Đối tác - Chăm sóc";
  }
}

export function nowTimestamp(): string {
  return new Date().toISOString().replace("T", " ").substring(0, 16);
}
