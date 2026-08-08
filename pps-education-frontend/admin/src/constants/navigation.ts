import {
  Award,
  BarChart3,
  Building2,
  CalendarDays,
  CheckSquare,
  ClipboardCheck,
  DollarSign,
  ExternalLink,
  GraduationCap,
  LayoutDashboard,
  BookOpen,
  PhoneCall,
  School,
  Send,
  Settings2,
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
      // { id: "dash-acad", label: "Dashboard Học thuật", path: "/dashboard", icon: GraduationCap, requiredPermission: "academic.curriculum.create" },
      // { id: "dash-campus", label: "Dashboard Điểm trường", path: "/dashboard", icon: School }
    ]
  },
  {
    id: "system",
    title: "QUẢN TRỊ HỆ THỐNG",
    items: [
      // user.manage đã bị tách nhỏ (hạt nhân hóa V62) thành user.view/create/update — mã gộp cũ
      // không còn tồn tại. Gate theo .view (thấp nhất) để vào trang; nút Tạo/Sửa bên trong tự ẩn
      // theo đúng quyền con nếu thiếu.
      { id: "sys-users", label: "Quản lý người dùng", path: "/system-admin/users", icon: Users, requiredPermission: "user.view" },
      // permission.role.manage/permission.override.manage đã bị tách nhỏ (hạt nhân hóa PR #94/#98)
      // thành permission.role.view/create/update/delete và permission.override.view/set/delete —
      // 2 mã "manage" gộp cũ KHÔNG còn tồn tại trong bảng permissions nữa. Gate theo mã cũ khiến
      // KHÔNG tài khoản nào (kể cả SYS_ADMIN) vào được 2 trang này — đã xác nhận qua DB (0 role nào
      // có 2 mã cũ) và code backend RoleController/UserPermissionOverrideController (chỉ check các
      // mã .view/.create/.update/.delete/.set). Gate theo .view (quyền xem, thấp nhất) để vào được
      // trang; nút Tạo/Sửa/Xoá bên trong trang tự ẩn theo đúng quyền con nếu thiếu.
      { id: "sys-roles", label: "Nhóm vai trò", path: "/system-admin/roles", icon: ShieldAlert, requiredPermission: "permission.role.view" },
      { id: "sys-override", label: "Tùy chỉnh tài khoản", path: "/system-admin/overrides", icon: ShieldAlert, requiredPermission: "permission.override.view" },
      { id: "sys-audit", label: "Nhật ký thay đổi", path: "/system-admin/audit-log", icon: ShieldAlert, requiredPermission: "permission.audit.view" },
      // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-08 — chỉ Quản trị viên hệ thống (V105).
      { id: "sys-settings", label: "Cài đặt hệ thống", path: "/system-admin/settings", icon: Settings2, requiredPermission: "system.settings.manage" },
      { id: "sys-send-notification", label: "Gửi thông báo", path: "/system-admin/send-notification", icon: Send, requiredPermission: "notification.send.manual" }
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
      // hrm.manage đã bị tách nhỏ (hạt nhân hóa) thành hrm.employee.*/hrm.department.*/hrm.position.*
      // — mã gộp cũ không còn tồn tại, ẩn mất cả section này với mọi tài khoản. hrm.employee.view là
      // quyền gần nhất đại diện "thuộc nhóm quản trị nhân sự" cho 2 mục hồ sơ/danh mục dùng chung.
      { id: "hrm-profile", label: "Hồ sơ cán bộ", path: "/hrm/profile", icon: Users, requiredPermission: "hrm.employee.view" },
      { id: "hrm-departments-positions", label: "Phòng ban & Chức vụ", path: "/hrm/departments-positions", icon: Users, requiredPermission: "hrm.employee.view" },
      // UC-09/UC-11/UC-12: backend KHÔNG dùng permission code (tự phục vụ — ai cũng chấm công/nộp
      // đơn/xem lương của chính mình được; người duyệt/HR tự thấy thêm dữ liệu quản trị theo role
      // ngay trong trang) — đã xác nhận với người dùng 2026-07-27: không gate gì ở đây, ai đăng nhập
      // cũng vào được, khớp đúng Javadoc AttendanceController/LeaveRequestController/PayrollController.
      { id: "hrm-attendance", label: "Dữ liệu chấm công", path: "/hrm/attendance", icon: Users },
      { id: "hrm-leaves", label: "Xin nghỉ phép", path: "/hrm/leaves", icon: Users },
      { id: "hrm-payroll", label: "Tính toán bảng lương", path: "/hrm/payroll", icon: Users }
    ]
  },
  {
    id: "crm",
    title: "TUYỂN SINH & CRM",
    // crm.lead.manage đã bị tách nhỏ (hạt nhân hóa V62) thành crm.lead.create/update/convert — mã
    // gộp cũ không còn tồn tại. Gate theo .create (STAFF luôn có cả 3, không có mã .view riêng vì
    // GET /api/leads/* chưa từng gate quyền ở backend).
    items: [{ id: "crm-leads", label: "Khách hàng tiềm năng", path: "/crm/leads", icon: PhoneCall, requiredPermission: "crm.lead.create" }]
  },
  {
    id: "student",
    title: "QUẢN LÝ HỌC SINH",
    items: [
      { id: "stu-profile", label: "Hồ sơ học sinh", path: "/student/profile", icon: Users, requiredPermission: "student.profile.view" },
      { id: "stu-parents", label: "Quản lý phụ huynh", path: "/student/parents", icon: Users, requiredPermission: "student.parent.view" }
      // Mục "Điểm danh nhanh" đã chuyển sang section QUẢN LÝ HỌC THUẬT bên dưới (yêu cầu người dùng
      // 2026-07-31, đảo lại quyết định ẩn hẳn khỏi sidebar ngày 2026-07-24) — xem "acad-attendance".
      // Vẫn giữ nguyên nút "Điểm danh" deep-link kèm classId/sessionId ở tab "Buổi học & Điểm danh"
      // trong Quản lý lớp học (UC-18, ClassDetailPanel) — 2 lối vào cùng 1 route /student/attendance.
    ]
  },
  {
    id: "academic",
    title: "QUẢN LÝ HỌC THUẬT",
    items: [
      // academic.class.manage: TEACHER/HEAD_ACADEMIC đều có sẵn permission này (xem UC-18) — không cần gate thêm role.
      { id: "acad-classes", label: "Quản lý lớp học", path: "/academic/classes", icon: GraduationCap, requiredPermission: "academic.class.manage" },
      // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — trước đó chỉ vào được qua nút
      // deep-link trong Quản lý lớp học (xem ghi chú "stu-attendance" cũ ở section QUẢN LÝ HỌC SINH).
      // Lọc theo lớp lấy từ lớp đang chọn ở Header (đã có sẵn, không đổi gì ở AttendancePage.tsx).
      { id: "acad-attendance", label: "Điểm danh (UC-15)", path: "/student/attendance", icon: ClipboardCheck, requiredPermission: "academic.attendance.mark" },
      // academic.curriculum.manage đã bị tách nhỏ (hạt nhân hóa V62) thành
      // academic.curriculum.create/update/approve — mã gộp cũ không còn tồn tại. Gate theo
      // .create (thấp nhất) để vào trang; khối duyệt tùy biến (UC-17) bên trong tự gate riêng
      // theo .approve (xem SyllabusPage.tsx).
      { id: "acad-syllabus", label: "Khung chương trình", path: "/academic/syllabus", icon: GraduationCap, requiredPermission: "academic.curriculum.create" },
      // GV thuần giờ nhập điểm ngay ở tab "Sổ điểm" trong Quản lý lớp học (UC-18, ClassDetailPanel) nên
      // KHÔNG còn hiện mục này với TEACHER nữa — chỉ gate theo role quản trị (không dùng requiredPermission
      // academic.grade.manage vì TEACHER cũng có permission đó, sẽ lại lọt vào nếu gate theo permission).
      // Trang GradesPage vẫn giữ cho HEAD_ACADEMIC/SYS_ADMIN xem tổng quan + SITE_MANAGER công bố điểm (UC-20).
      { id: "acad-grades", label: "Sổ điểm hệ thống", path: "/academic/grades", icon: Award, requiredRoleAny: [UserRole.HEAD_ACADEMIC, UserRole.SYS_ADMIN, UserRole.SITE_MANAGER] },
      // academic.comment.write: TEACHER có sẵn (viết nhận xét). SITE_MANAGER duyệt nhận xét qua role check nội bộ trang, không có permission riêng.
      { id: "acad-comments", label: "Nhận xét học viên", path: "/academic/comments", icon: Award, requiredPermission: "academic.comment.write", requiredRoleAny: [UserRole.SITE_MANAGER] },
      // UC-66/FR-ACA-07: lms.exercise.report.view (V90) chỉ gán TEACHER + SITE_MANAGER — tự gate đúng audience, không cần requiredRoleAny.
      { id: "acad-homework-stats", label: "Thống kê BTVN theo lớp", path: "/academic/homework-stats", icon: BarChart3, requiredPermission: "lms.exercise.report.view" }
    ]
  },
  {
    id: "lms",
    title: "TÀI LIỆU & KHẢO THÍ LMS",
    items: [
      // lms.exercise.manage đã bị tách nhỏ (hạt nhân hóa V62) thành 2 nhóm KHÁC NHAU theo resource:
      // lms.question-bank.create/update/view (Ngân hàng câu hỏi) và lms.exercise.create/update/
      // publish (Soạn & giao đề, không có mã .view riêng vì GET /api/exercises/* chưa từng gate
      // quyền ở backend) — mã gộp cũ không còn tồn tại.
      // V82 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — ẩn hẳn "Ngân hàng câu
      // hỏi" khỏi sidebar cho MỌI role (kể cả HEAD_ACADEMIC/SYS_ADMIN/SUPER_ADMIN, trước đó vẫn còn
      // thấy) — câu hỏi giờ soạn thẳng theo Đề (examId), trang bank legacy không còn cần thiết. Route
      // /lms/question-banks vẫn còn (chưa xóa hẳn) để không phá link cũ, chỉ không còn hiện ở menu.
      { id: "lms-exercises", label: "Soạn & giao đề", path: "/lms/exercises", icon: BookOpen, requiredPermission: "lms.exercise.create" },
      // ReviewVideoController có permission từ V63 (bổ sung, trước đó không hề gate permission nào —
      // đã fix bug 2026-07-30: cấp "full quyền" cho sysadmin không có tác dụng vì quyền chưa tồn tại
      // trong catalog). Gate theo .create (thấp nhất trong 3 quyền TEACHER được gán mặc định), giống
      // cách "Soạn & giao đề" bên dưới. Lưu ý: có quyền chỉ vào được trang — thao tác tạo/sửa cho 1
      // lớp cụ thể vẫn cần tài khoản có mặt trong class_teachers của lớp đó (requireAssignedTeacher,
      // Precondition UC-23, backend tự chặn 403 nếu không đúng phân công).
      { id: "lms-lectures", label: "Kho Video Ôn tập", path: "/lms/lectures", icon: BookOpen, requiredPermission: "lms.review-video.create" },
      // lms.document.manage đã bị tách nhỏ (hạt nhân hóa V62) thành lms.document.create/update/
      // view — mã gộp cũ không còn tồn tại. Gate theo .view (thấp nhất) để vào trang.
      { id: "lms-documents", label: "Kho tài liệu tham khảo", path: "/lms/documents", icon: BookOpen, requiredPermission: "lms.document.view" },
      { id: "lms-exams", label: "Hàng chờ chấm bài", path: "/lms/exams", icon: BookOpen, requiredPermission: "lms.grading.manage" }
    ]
  },
  {
    id: "finance",
    title: "QUẢN LÝ TÀI CHÍNH",
    items: [
      // finance.manage đã bị tách nhỏ thành finance.invoice.*/finance.expense.*/finance.scholarship.*/
      // finance.tuition-plan.* — mã gộp cũ không còn tồn tại. finance.invoice.manage (V51) tự nó lại
      // bị tách tiếp (V62) thành finance.invoice.generate/payment.record — không có mã .view riêng
      // vì GET /api/finance/invoices/* chưa từng gate quyền ở backend.
      { id: "fin-billing", label: "Thu phí & hóa đơn", path: "/finance/billing", icon: DollarSign, requiredPermission: "finance.invoice.generate" },
      // finance.expense.create (STAFF, người tạo) và finance.expense.approve (EXECUTIVE, người duyệt)
      // là 2 role KHÁC nhau hoàn toàn (không có 1 permission chung nào) — OperatingExpenseController
      // GET dùng "finance.expense.create' or 'finance.expense.approve'". requiredPermission không hỗ
      // trợ OR 2 mã nên gate theo requiredRoleAny cả 2 role thay vì bỏ sót 1 bên.
      { id: "fin-expenses", label: "Chi phí vận hành", path: "/finance/expenses", icon: DollarSign, requiredRoleAny: [UserRole.STAFF, UserRole.EXECUTIVE] },
      { id: "fin-reports", label: "Báo cáo kế toán", path: "/finance/reports", icon: DollarSign, requiredPermission: "finance.report.view" }
    ]
  },
  {
    id: "facility",
    title: "CƠ SỞ VẬT CHẤT & ĐỐI TÁC",
    items: [
      // facility.manage đã bị tách nhỏ thành facility.site.create/facility.site.update (cùng gán cho
      // OPS_MANAGER + SYS_ADMIN nên không có vấn đề role-split như finance.expense ở trên).
      { id: "fac-campuses", label: "Điểm trường & HĐ", path: "/facility/campuses", icon: Building2, requiredPermission: "facility.site.update" },
      // facility.room.manage đã bị tách nhỏ (hạt nhân hóa V62) thành facility.room.create/update
      // (phòng học) và facility.equipment.create/update (thiết bị) — cùng gán cho STAFF+HEAD_ACADEMIC
      // nên gate theo 1 mã đại diện (facility.room.create) là đủ, không có vấn đề role-split.
      { id: "fac-rooms", label: "Phòng học & thiết bị", path: "/facility/rooms", icon: Building2, requiredPermission: "facility.room.create" },
      // Không có permission riêng — quyền xem/xử lý được backend tính qua bảng site_managers (đúng site đang phụ trách), không qua permission nào.
      { id: "fac-feedback", label: "Ý kiến phản hồi", path: "/facility/feedback", icon: Building2, requiredRoleAny: [UserRole.SITE_MANAGER] }
    ]
  },
  {
    id: "partner",
    title: "PORTAL TRƯỜNG LIÊN KẾT",
    items: [
      // PARTNER_REP không có permission riêng — quyền xem được backend tính qua bảng site_managers (role_type=PARTNER_REP).
      { id: "part-syllabus", label: "Kế hoạch giảng dạy", path: "/partner/syllabus", icon: ExternalLink, requiredRoleAny: [UserRole.PARTNER_REP] },
      { id: "part-portal", label: "Báo cáo liên kết", path: "/partner/portal", icon: ExternalLink, requiredRoleAny: [UserRole.PARTNER_REP] }
    ]
  }
];
