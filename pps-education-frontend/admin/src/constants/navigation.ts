import {
  Award,
  Building2,
  CalendarDays,
  CheckSquare,
  DollarSign,
  ExternalLink,
  GraduationCap,
  LayoutDashboard,
  BookOpen,
  PhoneCall,
  School,
  ShieldAlert,
  Users
} from "lucide-react";
import type { ComponentType } from "react";
import { UserRole } from "@/types";

export interface NavItem {
  id: string;
  label: string;
  path: string;
  icon: ComponentType<{ className?: string }>;
  requiredPermission?: string;
  /**
   * Cho vào theo VAI TRÒ (OR với requiredPermission, không phải AND) — dùng cho vài màn mà quyền
   * truy cập được backend tính theo QUAN HỆ DỮ LIỆU (VD site_managers) chứ không qua 1 permission
   * cụ thể nào (SITE_MANAGER hiện KHÔNG có permission riêng cho "Ý kiến phản hồi"; PARTNER_REP
   * cũng vậy với "Kế hoạch giảng dạy/Báo cáo liên kết") — nếu chỉ gate bằng requiredPermission thì
   * 2 role này sẽ bị ẩn mất mục họ vẫn cần dùng thật. Đã xác nhận với người dùng 2026-07-23 (sự cố
   * vai trò tùy biến "Trưởng phòng đào tạo" thấy được các mục không liên quan vì mục đó KHÔNG gate
   * gì cả — nay gate lại bằng permission thật (nếu có) + role thật cần dùng (nếu quyền không tồn tại
   * ở tầng permission), không còn mục nào mở toang cho mọi vai trò như trước.
   */
  requiredRoleAny?: UserRole[];
}

export interface NavSection {
  id: string;
  title: string;
  items: NavItem[];
}

/** true nếu 1 NavItem cho phép hiển thị/truy cập với (roleCodes, hasPermission) đang có — OR giữa requiredPermission và requiredRoleAny. */
export function isNavItemAllowed(item: NavItem, roleCodes: string[], hasPermission: (permission?: string) => boolean): boolean {
  if (!item.requiredPermission && !item.requiredRoleAny) return true;
  if (item.requiredPermission && hasPermission(item.requiredPermission)) return true;
  if (item.requiredRoleAny && item.requiredRoleAny.some((role) => roleCodes.includes(role))) return true;
  return false;
}

/** Tra NavItem theo path — dùng để chặn truy cập trực tiếp qua URL (không qua click Sidebar). */
export function findNavItemForPath(pathname: string): NavItem | undefined {
  for (const section of navSections) {
    const item = section.items.find((i) => i.path === pathname);
    if (item) return item;
  }
  return undefined;
}

export const navSections: NavSection[] = [
  {
    id: "dashboard",
    title: "BẢNG ĐIỀU KHIỂN",
    items: [
      { id: "dash-all", label: "Dashboard Tổng hợp", path: "/dashboard", icon: LayoutDashboard },
      // { id: "dash-acad", label: "Dashboard Học thuật", path: "/dashboard", icon: GraduationCap, requiredPermission: "academic.curriculum.manage" },
      // { id: "dash-campus", label: "Dashboard Điểm trường", path: "/dashboard", icon: School }
    ]
  },
  {
    id: "system",
    title: "QUẢN TRỊ HỆ THỐNG",
    items: [
      { id: "sys-users", label: "Quản lý người dùng", path: "/system-admin/users", icon: Users, requiredPermission: "user.manage" },
      { id: "sys-roles", label: "Nhóm vai trò (UC-03)", path: "/system-admin/roles", icon: ShieldAlert, requiredPermission: "permission.role.manage" },
      { id: "sys-override", label: "Tùy chỉnh tài khoản (UC-04)", path: "/system-admin/overrides", icon: ShieldAlert, requiredPermission: "permission.override.manage" },
      { id: "sys-audit", label: "Nhật ký thay đổi (UC-05)", path: "/system-admin/audit-log", icon: ShieldAlert, requiredPermission: "permission.audit.view" }
    ]
  },
  // Tạm ẩn cả mục "ĐIỀU HÀNH & GIAO VIỆC" theo yêu cầu người dùng (2026-07-23) — đang phát triển
  // tiếp, chưa muốn hiện lên sidebar. CHỈ ẩn khỏi menu, không xoá route (App.tsx vẫn giữ nguyên
  // /task-workflow và /schedule/my-timetable, vào thẳng URL vẫn dùng được bình thường).
  // {
  //   id: "task",
  //   title: "ĐIỀU HÀNH & GIAO VIỆC",
  //   items: [
  //     // Không gate requiredPermission: mọi nhân sự có thể là người NHẬN việc, không riêng người có task.create — xem "Việc tôi được giao".
  //     { id: "task-workflow", label: "Giao việc & Kanban (UC-06/07)", path: "/task-workflow", icon: CheckSquare },
  //     // Self-service (UC-58) — không gate quyền, ai đăng nhập cũng vào được, chỉ có dữ liệu thật với Giáo viên.
  //     { id: "acad-my-schedule", label: "Lịch của tôi (UC-58)", path: "/schedule/my-timetable", icon: CalendarDays }
  //   ]
  // },
  {
    id: "hrm",
    title: "QUẢN LÝ NHÂN SỰ (HRM)",
    items: [
      { id: "hrm-profile", label: "Hồ sơ cán bộ (UC-08)", path: "/hrm/profile", icon: Users, requiredPermission: "hrm.manage" },
      { id: "hrm-departments-positions", label: "Phòng ban & Chức vụ", path: "/hrm/departments-positions", icon: Users, requiredPermission: "hrm.manage" },
      { id: "hrm-attendance", label: "Dữ liệu chấm công (UC-09)", path: "/hrm/attendance", icon: Users, requiredPermission: "hrm.manage" },
      { id: "hrm-leaves", label: "Phê duyệt đơn từ (UC-11)", path: "/hrm/leaves", icon: Users, requiredPermission: "hrm.manage" },
      { id: "hrm-payroll", label: "Tính toán bảng lương (UC-12)", path: "/hrm/payroll", icon: Users, requiredPermission: "hrm.manage" }
    ]
  },
  {
    id: "crm",
    title: "TUYỂN SINH & CRM",
    items: [{ id: "crm-leads", label: "Khách hàng tiềm năng (UC-33)", path: "/crm/leads", icon: PhoneCall, requiredPermission: "crm.lead.manage" }]
  },
  {
    id: "student",
    title: "QUẢN LÝ HỌC SINH",
    items: [
      { id: "stu-profile", label: "Hồ sơ học sinh (UC-13)", path: "/student/profile", icon: Users, requiredPermission: "student.profile.view" },
      { id: "stu-parents", label: "Quản lý phụ huynh (UC-13)", path: "/student/parents", icon: Users, requiredPermission: "student.parent.view" },
      { id: "stu-attendance", label: "Điểm danh nhanh (UC-15)", path: "/student/attendance", icon: Users, requiredPermission: "academic.attendance.mark" }
    ]
  },
  {
    id: "academic",
    title: "QUẢN LÝ HỌC THUẬT",
    items: [
      // academic.class.manage: TEACHER/HEAD_ACADEMIC đều có sẵn permission này (xem UC-18) — không cần gate thêm role.
      { id: "acad-classes", label: "Quản lý lớp học (UC-18)", path: "/academic/classes", icon: GraduationCap, requiredPermission: "academic.class.manage" },
      { id: "acad-syllabus", label: "Khung chương trình (UC-16/17)", path: "/academic/syllabus", icon: GraduationCap, requiredPermission: "academic.curriculum.manage" },
      // academic.grade.manage: TEACHER/HEAD_ACADEMIC có sẵn. SITE_MANAGER KHÔNG có permission điểm nào (công bố/xem lại điểm ở GradesPage tự nhận diện qua role, không qua permission) — phải gate thêm requiredRoleAny mới không mất quyền vào của Site Manager.
      { id: "acad-grades", label: "Sổ điểm hệ thống (UC-19/20)", path: "/academic/grades", icon: Award, requiredPermission: "academic.grade.manage", requiredRoleAny: [UserRole.SITE_MANAGER] },
      // academic.comment.write: TEACHER có sẵn (viết nhận xét). SITE_MANAGER duyệt nhận xét qua role check nội bộ trang, không có permission riêng.
      { id: "acad-comments", label: "Nhận xét học viên (UC-21/22)", path: "/academic/comments", icon: Award, requiredPermission: "academic.comment.write", requiredRoleAny: [UserRole.SITE_MANAGER] }
    ]
  },
  {
    id: "lms",
    title: "TÀI LIỆU & KHẢO THÍ LMS",
    items: [
      { id: "lms-question-banks", label: "Ngân hàng câu hỏi (UC-40)", path: "/lms/question-banks", icon: BookOpen, requiredPermission: "lms.exercise.manage" },
      { id: "lms-exercises", label: "Soạn & giao đề (UC-40)", path: "/lms/exercises", icon: BookOpen, requiredPermission: "lms.exercise.manage" },
      { id: "lms-lectures", label: "Kho bài giảng (UC-23)", path: "/lms/lectures", icon: BookOpen, requiredPermission: "lms.exercise.manage" },
      { id: "lms-documents", label: "Kho tài liệu tham khảo (UC-60)", path: "/lms/documents", icon: BookOpen, requiredPermission: "lms.document.manage" },
      { id: "lms-exams", label: "Hàng chờ chấm bài (UC-41)", path: "/lms/exams", icon: BookOpen, requiredPermission: "lms.grading.manage" }
    ]
  },
  {
    id: "finance",
    title: "QUẢN LÝ TÀI CHÍNH",
    items: [
      { id: "fin-billing", label: "Thu phí & hóa đơn (UC-30)", path: "/finance/billing", icon: DollarSign, requiredPermission: "finance.manage" },
      { id: "fin-expenses", label: "Chi phí vận hành (UC-31)", path: "/finance/expenses", icon: DollarSign, requiredPermission: "finance.manage" },
      { id: "fin-reports", label: "Báo cáo kế toán (UC-32)", path: "/finance/reports", icon: DollarSign, requiredPermission: "finance.report.view" }
    ]
  },
  {
    id: "facility",
    title: "CƠ SỞ VẬT CHẤT & ĐỐI TÁC",
    items: [
      { id: "fac-campuses", label: "Điểm trường & HĐ (UC-36/36b)", path: "/facility/campuses", icon: Building2, requiredPermission: "facility.manage" },
      { id: "fac-rooms", label: "Phòng học & thiết bị (UC-37)", path: "/facility/rooms", icon: Building2, requiredPermission: "facility.room.manage" },
      // Không có permission riêng — quyền xem/xử lý được backend tính qua bảng site_managers (đúng site đang phụ trách), không qua permission nào.
      { id: "fac-feedback", label: "Ý kiến phản hồi (UC-38/39)", path: "/facility/feedback", icon: Building2, requiredRoleAny: [UserRole.SITE_MANAGER] }
    ]
  },
  {
    id: "partner",
    title: "PORTAL TRƯỜNG LIÊN KẾT",
    items: [
      // PARTNER_REP không có permission riêng — quyền xem được backend tính qua bảng site_managers (role_type=PARTNER_REP).
      { id: "part-syllabus", label: "Kế hoạch giảng dạy (UC-28)", path: "/partner/syllabus", icon: ExternalLink, requiredRoleAny: [UserRole.PARTNER_REP] },
      { id: "part-portal", label: "Báo cáo liên kết (UC-29)", path: "/partner/portal", icon: ExternalLink, requiredRoleAny: [UserRole.PARTNER_REP] }
    ]
  }
];
