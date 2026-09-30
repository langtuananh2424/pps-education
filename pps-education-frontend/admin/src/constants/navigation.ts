import {
  Coins,
  AlertTriangle,
  ArrowLeftRight,
  Award,
  BarChart3,
  Building2,
  BookMarked,
  BookOpen,
  BookOpenCheck,
  BookUser,
  Calculator,
  CalendarDays,
  CalendarRange,
  CalendarX,
  ClipboardCheck,
  ClipboardList,
  Clock,
  Contact,
  CreditCard,
  DoorOpen,
  ExternalLink,
  FileCheck2,
  FileEdit,
  FileSpreadsheet,
  GraduationCap,
  History,
  IdCard,
  LayoutDashboard,
  Library,
  Mail,
  MapPin,
  Megaphone,
  MessageSquare,
  Network,
  PhoneCall,
  PieChart,
  Receipt,
  Send,
  Settings2,
  ShieldCheck,
  UserCog,
  UserRound,
  Users,
  Video,
  Wallet
} from "lucide-react";
import type { ComponentType } from "react";
import { UserRole } from "@/types";
import { PAGE_PERMISSIONS } from "./pagePermissions";

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

/**
 * true nếu 1 NavItem cho phép hiển thị/truy cập. Mục có trong PAGE_PERMISSIONS (V202) hiện khi tài khoản có
 * đủ các mã "xem trang" của mục — tick mục trên màn "Nhóm vai trò" chính là cấp các mã này. Dùng "đủ" thay vì
 * "có 1 mã" vì 1 mã có thể dùng chung cho nhiều trang (VD academic.grade.view ở tab Sổ điểm của trang lớp học). Các mục còn lại (Portal
 * trường liên kết) giữ cách cũ: OR giữa requiredPermission và requiredRoleAny.
 */
export function isNavItemAllowed(item: NavItem, roleCodes: string[], hasPermission: (permission?: string) => boolean): boolean {
  const page = PAGE_PERMISSIONS[item.id];
  if (page) return page.viewCodes.every((code) => hasPermission(code));
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

// Mục nào hiện với tài khoản nào do PAGE_PERMISSIONS (pagePermissions.ts) quyết định, xem isNavItemAllowed —
// không gate theo vai trò ở đây nữa (V202). "Giao việc & Kanban" (/task-workflow) vẫn ẩn vì chưa hoàn thiện.
export const navSections: NavSection[] = [
  {
    id: "dashboard",
    title: "BẢNG ĐIỀU KHIỂN",
    items: [{ id: "dash-all", label: "Dashboard Tổng hợp", path: "/dashboard", icon: LayoutDashboard }]
  },
  {
    id: "system",
    title: "QUẢN TRỊ HỆ THỐNG",
    items: [
      { id: "sys-users", label: "Quản lý người dùng", path: "/system-admin/users", icon: Users },
      { id: "sys-roles", label: "Nhóm vai trò", path: "/system-admin/roles", icon: ShieldCheck },
      { id: "sys-override", label: "Tùy chỉnh tài khoản", path: "/system-admin/overrides", icon: UserCog },
      { id: "sys-audit", label: "Nhật ký thay đổi", path: "/system-admin/audit-log", icon: History },
      { id: "sys-settings", label: "Cài đặt hệ thống", path: "/system-admin/settings", icon: Settings2 },
      { id: "sys-send-notification", label: "Gửi thông báo", path: "/system-admin/send-notification", icon: Send },
      { id: "sys-ai-token-usage", label: "Sử dụng token AI", path: "/system-admin/ai-token-usage", icon: Coins }
    ]
  },
  {
    id: "hrm",
    title: "QUẢN LÝ NHÂN SỰ (HRM)",
    items: [
      { id: "hrm-profile", label: "Hồ sơ cán bộ", path: "/hrm/profile", icon: IdCard },
      { id: "hrm-departments-positions", label: "Phòng ban & Chức vụ", path: "/hrm/departments-positions", icon: Network },
      { id: "hrm-attendance", label: "Dữ liệu chấm công", path: "/hrm/attendance", icon: Clock },
      { id: "hrm-attendance-sites", label: "Địa điểm chấm công", path: "/hrm/attendance-sites", icon: MapPin },
      { id: "hrm-shifts", label: "Ca làm việc", path: "/hrm/shifts", icon: Clock },
      { id: "hrm-work-calendar", label: "Lịch làm việc/Nghỉ lễ", path: "/hrm/work-calendar", icon: CalendarDays },
      { id: "hrm-employee-schedule", label: "Lịch làm việc", path: "/hrm/employee-schedule", icon: CalendarRange },
      { id: "hrm-leaves", label: "Nghỉ phép & duyệt đơn", path: "/hrm/leaves", icon: CalendarX },
      { id: "hrm-payroll", label: "Bảng lương", path: "/hrm/payroll", icon: Wallet }
    ]
  },
  {
    id: "crm",
    title: "TUYỂN SINH & CRM",
    items: [{ id: "crm-leads", label: "Khách hàng tiềm năng", path: "/crm/leads", icon: PhoneCall }]
  },
  {
    id: "student",
    title: "QUẢN LÝ HỌC SINH",
    items: [
      { id: "stu-profile", label: "Hồ sơ học sinh", path: "/student/profile", icon: UserRound },
      { id: "stu-parents", label: "Quản lý phụ huynh", path: "/student/parents", icon: Contact }
    ]
  },
  {
    id: "academic",
    title: "QUẢN LÝ HỌC THUẬT",
    items: [
      { id: "acad-my-schedule", label: "Lịch dạy", path: "/schedule/my-timetable", icon: CalendarDays },
      { id: "acad-classes", label: "Quản lý lớp học", path: "/academic/classes", icon: GraduationCap },
      { id: "acad-attendance", label: "Điểm danh", path: "/student/attendance", icon: ClipboardCheck },
      { id: "acad-syllabus", label: "Khung chương trình", path: "/academic/syllabus", icon: BookMarked },
      { id: "acad-entrance", label: "Đánh giá đầu vào", path: "/academic/entrance-assessment", icon: ClipboardCheck },
      { id: "acad-grades", label: "Sổ điểm hệ thống", path: "/academic/grades", icon: ClipboardList },
      { id: "acad-comments", label: "Nhận xét học viên", path: "/academic/comments", icon: MessageSquare },
      { id: "acad-homework-stats", label: "Thống kê BTVN theo lớp", path: "/academic/homework-stats", icon: BarChart3 },
      { id: "acad-teachers", label: "Hồ sơ giáo viên", path: "/academic/teachers", icon: IdCard },
      { id: "acad-change-history", label: "Lịch sử thay đổi dữ liệu", path: "/academic/change-history", icon: History }
    ]
  },
  {
    id: "notifications",
    title: "PHỤ HUYNH & PHẢN HỒI",
    items: [
      { id: "noti-meeting-invites", label: "Duyệt thư mời phụ huynh", path: "/notifications/meeting-invites", icon: Mail },
      { id: "noti-attitude-escalations", label: "Duyệt cảnh báo thái độ học tập", path: "/notifications/attitude-escalations", icon: AlertTriangle },
      { id: "fac-feedback", label: "Ý kiến phản hồi", path: "/facility/feedback", icon: Megaphone }
    ]
  },
  {
    id: "lms",
    title: "TÀI LIỆU & KHẢO THÍ LMS",
    items: [
      { id: "lms-book-catalog", label: "Danh mục sách", path: "/lms/book-catalog", icon: Library },
      { id: "lms-exercises", label: "Soạn & giao đề", path: "/lms/exercises", icon: FileEdit },
      { id: "lms-lectures", label: "Kho Video Ôn tập", path: "/lms/lectures", icon: Video },
      { id: "lms-documents", label: "Kho tài liệu tham khảo", path: "/lms/documents", icon: BookOpen },
      { id: "lms-exams", label: "Hàng chờ chấm bài", path: "/lms/exams", icon: FileCheck2 }
    ]
  },
  {
    id: "reports",
    title: "BÁO CÁO & THỐNG KÊ",
    items: [
      { id: "rep-templates", label: "Mẫu báo cáo tự động", path: "/reports/templates", icon: FileSpreadsheet },
      { id: "rep-daily", label: "Thống kê nhận xét", path: "/reports/daily-comments", icon: PieChart },
      { id: "rep-grades", label: "Thống kê điểm", path: "/reports/grades", icon: Award },
      { id: "rep-student", label: "Hồ sơ học tập", path: "/reports/student-progress", icon: BookUser },
      { id: "rep-enrollment-movement", label: "Thống kê biến động học sinh", path: "/reports/enrollment-movement", icon: ArrowLeftRight },
      { id: "rep-actual-periods", label: "Số tiết thực tế theo lớp", path: "/reports/actual-periods", icon: BookOpenCheck },
      { id: "rep-teaching-stats", label: "Thống kê giảng dạy theo GV", path: "/reports/teaching-stats", icon: BarChart3 }
    ]
  },
  {
    id: "finance",
    title: "QUẢN LÝ TÀI CHÍNH",
    items: [
      { id: "fin-billing", label: "Thu phí & hóa đơn", path: "/finance/billing", icon: Receipt },
      { id: "fin-expenses", label: "Chi phí vận hành", path: "/finance/expenses", icon: CreditCard },
      { id: "fin-reports", label: "Báo cáo kế toán", path: "/finance/reports", icon: Calculator }
    ]
  },
  {
    id: "facility",
    title: "CƠ SỞ VẬT CHẤT & ĐỐI TÁC",
    items: [
      { id: "fac-campuses", label: "Điểm trường & HĐ", path: "/facility/campuses", icon: Building2 },
      { id: "fac-rooms", label: "Phòng học & thiết bị", path: "/facility/rooms", icon: DoorOpen }
    ]
  },
  {
    id: "partner",
    title: "PORTAL TRƯỜNG LIÊN KẾT",
    items: [
      // PARTNER_REP không có quyền riêng — backend tính quyền xem qua site_managers (role_type=PARTNER_REP).
      { id: "part-syllabus", label: "Kế hoạch giảng dạy", path: "/partner/syllabus", icon: BookOpenCheck, requiredRoleAny: [UserRole.PARTNER_REP] },
      { id: "part-portal", label: "Báo cáo liên kết", path: "/partner/portal", icon: ExternalLink, requiredRoleAny: [UserRole.PARTNER_REP] }
    ]
  }
];
