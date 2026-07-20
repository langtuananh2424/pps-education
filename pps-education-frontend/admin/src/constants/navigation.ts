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

export interface NavItem {
  id: string;
  label: string;
  path: string;
  icon: ComponentType<{ className?: string }>;
  requiredPermission?: string;
}

export interface NavSection {
  id: string;
  title: string;
  items: NavItem[];
}

/** Tra `requiredPermission` của 1 route theo path — dùng để chặn truy cập trực tiếp qua URL (không qua click Sidebar). */
export function findRequiredPermissionForPath(pathname: string): string | undefined {
  for (const section of navSections) {
    const item = section.items.find((i) => i.path === pathname);
    if (item) return item.requiredPermission;
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
  {
    id: "task",
    title: "ĐIỀU HÀNH & GIAO VIỆC",
    // Không gate requiredPermission: mọi nhân sự có thể là người NHẬN việc, không riêng người có task.create — xem "Việc tôi được giao".
    items: [{ id: "task-workflow", label: "Giao việc & Kanban (UC-06/07)", path: "/task-workflow", icon: CheckSquare }]
  },
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
      { id: "stu-profile", label: "Hồ sơ học sinh (UC-13)", path: "/student/profile", icon: Users, requiredPermission: "student.manage" },
      { id: "stu-parents", label: "Quản lý phụ huynh (UC-13)", path: "/student/parents", icon: Users, requiredPermission: "student.manage" },
      { id: "stu-attendance", label: "Điểm danh nhanh (UC-15)", path: "/student/attendance", icon: Users, requiredPermission: "student.manage" }
    ]
  },
  {
    id: "academic",
    title: "QUẢN LÝ HỌC THUẬT",
    items: [
      // Không gate requiredPermission: Giáo viên (không có academic.class.manage/academic.grade.manage) vẫn cần vào đây để điểm danh/nhập điểm/viết nhận xét cho lớp mình dạy.
      { id: "acad-classes", label: "Quản lý lớp học (UC-18)", path: "/academic/classes", icon: GraduationCap },
      // Self-service (UC-58) — không gate quyền, ai đăng nhập cũng vào được, chỉ có dữ liệu thật với Giáo viên.
      { id: "acad-my-schedule", label: "Lịch của tôi (UC-58)", path: "/schedule/my-timetable", icon: CalendarDays },
      { id: "acad-syllabus", label: "Khung chương trình (UC-16/17)", path: "/academic/syllabus", icon: GraduationCap, requiredPermission: "academic.curriculum.manage" },
      { id: "acad-grades", label: "Sổ điểm hệ thống (UC-19/20)", path: "/academic/grades", icon: Award },
      { id: "acad-comments", label: "Nhận xét học viên (UC-21/22)", path: "/academic/comments", icon: Award }
    ]
  },
  {
    id: "lms",
    title: "TÀI LIỆU & KHẢO THÍ LMS",
    items: [
      { id: "lms-question-banks", label: "Ngân hàng câu hỏi (UC-40)", path: "/lms/question-banks", icon: BookOpen, requiredPermission: "lms.exercise.manage" },
      { id: "lms-exercises", label: "Soạn & giao đề (UC-40)", path: "/lms/exercises", icon: BookOpen, requiredPermission: "lms.exercise.manage" },
      { id: "lms-lectures", label: "Kho bài giảng (UC-23)", path: "/lms/lectures", icon: BookOpen, requiredPermission: "lms.exercise.manage" },
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
      { id: "fac-feedback", label: "Ý kiến phản hồi (UC-38/39)", path: "/facility/feedback", icon: Building2 }
    ]
  },
  {
    id: "partner",
    title: "PORTAL TRƯỜNG LIÊN KẾT",
    items: [
      { id: "part-syllabus", label: "Kế hoạch giảng dạy (UC-28)", path: "/partner/syllabus", icon: ExternalLink },
      { id: "part-portal", label: "Báo cáo liên kết (UC-29)", path: "/partner/portal", icon: ExternalLink }
    ]
  }
];
