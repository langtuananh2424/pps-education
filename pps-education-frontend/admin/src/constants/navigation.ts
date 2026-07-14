import {
  Award,
  Building2,
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

export const navSections: NavSection[] = [
  {
    id: "dashboard",
    title: "BẢNG ĐIỀU KHIỂN",
    items: [
      { id: "dash-all", label: "Dashboard Tổng hợp", path: "/dashboard", icon: LayoutDashboard, requiredPermission: "task.view_all" },
      // { id: "dash-acad", label: "Dashboard Học thuật", path: "/dashboard", icon: GraduationCap, requiredPermission: "academic.curriculum" },
      // { id: "dash-campus", label: "Dashboard Điểm trường", path: "/dashboard", icon: School, requiredPermission: "facility.feedback_resolve" }
    ]
  },
  {
    id: "system",
    title: "QUẢN TRỊ HỆ THỐNG",
    items: [
      { id: "sys-roles", label: "Nhóm vai trò (UC-03)", path: "/system-admin/roles", icon: ShieldAlert, requiredPermission: "system.admin" },
      { id: "sys-override", label: "Tùy chỉnh tài khoản (UC-04)", path: "/system-admin/overrides", icon: ShieldAlert, requiredPermission: "system.admin" },
      { id: "sys-audit", label: "Nhật ký thay đổi (UC-05)", path: "/system-admin/audit-log", icon: ShieldAlert, requiredPermission: "system.admin" }
    ]
  },
  {
    id: "task",
    title: "ĐIỀU HÀNH & GIAO VIỆC",
    items: [{ id: "task-workflow", label: "Giao việc & Kanban (UC-06/07)", path: "/task-workflow", icon: CheckSquare, requiredPermission: "task.view_all" }]
  },
  {
    id: "hrm",
    title: "QUẢN LÝ NHÂN SỰ (HRM)",
    items: [
      { id: "hrm-profile", label: "Hồ sơ cán bộ (UC-08)", path: "/hrm/profile", icon: Users, requiredPermission: "hrm.manage" },
      { id: "hrm-attendance", label: "Dữ liệu chấm công (UC-09)", path: "/hrm/attendance", icon: Users, requiredPermission: "hrm.payroll" },
      { id: "hrm-leaves", label: "Phê duyệt đơn từ (UC-11)", path: "/hrm/leaves", icon: Users, requiredPermission: "hrm.approve_leave" },
      { id: "hrm-payroll", label: "Tính toán bảng lương (UC-12)", path: "/hrm/payroll", icon: Users, requiredPermission: "hrm.payroll" }
    ]
  },
  {
    id: "crm",
    title: "TUYỂN SINH & CRM",
    items: [{ id: "crm-leads", label: "Khách hàng tiềm năng (UC-33)", path: "/crm/leads", icon: PhoneCall, requiredPermission: "crm.manage" }]
  },
  {
    id: "student",
    title: "QUẢN LÝ HỌC SINH",
    items: [
      { id: "stu-profile", label: "Hồ sơ học sinh (UC-13)", path: "/student/profile", icon: Users, requiredPermission: "student.manage" },
      { id: "stu-attendance", label: "Điểm danh nhanh (UC-15)", path: "/student/attendance", icon: Users, requiredPermission: "student.attendance" }
    ]
  },
  {
    id: "academic",
    title: "QUẢN LÝ HỌC THUẬT",
    items: [
      { id: "acad-syllabus", label: "Khung chương trình (UC-16/17)", path: "/academic/syllabus", icon: GraduationCap, requiredPermission: "academic.curriculum" },
      { id: "acad-grades", label: "Sổ điểm hệ thống (UC-19/20)", path: "/academic/grades", icon: Award, requiredPermission: "academic.grade_entry" },
      { id: "acad-comments", label: "Nhận xét học viên (UC-21/22)", path: "/academic/comments", icon: Award, requiredPermission: "academic.comment_write" }
    ]
  },
  {
    id: "lms",
    title: "TÀI LIỆU & KHẢO THÍ LMS",
    items: [
      { id: "lms-lectures", label: "Kho bài giảng (UC-23)", path: "/lms/lectures", icon: BookOpen, requiredPermission: "lms.manage" },
      { id: "lms-exams", label: "Hàng chờ chấm bài (UC-41)", path: "/lms/exams", icon: BookOpen, requiredPermission: "lms.exam_grade" }
    ]
  },
  {
    id: "finance",
    title: "QUẢN LÝ TÀI CHÍNH",
    items: [
      { id: "fin-billing", label: "Thu phí & hóa đơn (UC-30)", path: "/finance/billing", icon: DollarSign, requiredPermission: "finance.billing" },
      { id: "fin-expenses", label: "Chi phí vận hành (UC-31)", path: "/finance/expenses", icon: DollarSign, requiredPermission: "finance.expenses" },
      { id: "fin-reports", label: "Báo cáo kế toán (UC-32)", path: "/finance/reports", icon: DollarSign, requiredPermission: "finance.report_all" }
    ]
  },
  {
    id: "facility",
    title: "CƠ SỞ VẬT CHẤT & ĐỐI TÁC",
    items: [
      { id: "fac-campuses", label: "Điểm trường & HĐ (UC-36/36b)", path: "/facility/campuses", icon: Building2, requiredPermission: "facility.manage" },
      { id: "fac-rooms", label: "Phòng học & thiết bị (UC-37)", path: "/facility/rooms", icon: Building2, requiredPermission: "facility.manage" },
      { id: "fac-feedback", label: "Ý kiến phản hồi (UC-38/39)", path: "/facility/feedback", icon: Building2, requiredPermission: "facility.feedback_resolve" }
    ]
  },
  {
    id: "partner",
    title: "PORTAL TRƯỜNG LIÊN KẾT",
    items: [
      { id: "part-syllabus", label: "Kế hoạch giảng dạy (UC-28)", path: "/partner/syllabus", icon: ExternalLink, requiredPermission: "student.manage" },
      { id: "part-portal", label: "Báo cáo liên kết (UC-29)", path: "/partner/portal", icon: ExternalLink, requiredPermission: "student.manage" }
    ]
  }
];
