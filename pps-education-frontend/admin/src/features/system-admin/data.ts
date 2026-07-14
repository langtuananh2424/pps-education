import { PermissionGroup, BusinessModule } from "./types";

export const BUSINESS_MODULES: BusinessModule[] = [
  { id: "user_mgmt", name: "Quản trị Hệ thống & Người dùng (System Admin & Users)", permissions: ["system.admin", "user.manage", "permission.edit"] },
  { id: "student_mgmt", name: "Quản lý Hồ sơ Học viên (Student Profiles)", permissions: ["student.manage", "student.attendance"] },
  { id: "teacher_mgmt", name: "Quản trị Nhân sự & Hợp đồng (HRM & Payroll)", permissions: ["hrm.manage", "hrm.payroll", "hrm.approve_leave"] },
  { id: "crm_mgmt", name: "Quản lý Leads Tuyển sinh & CSKH (CRM & Sales)", permissions: ["crm.manage", "crm.convert_student"] },
  { id: "academic_mgmt", name: "Cấu hình Khung chương trình (Curriculum & Syllabus)", permissions: ["academic.curriculum"] },
  { id: "grades_mgmt", name: "Quản lý Điểm số & Phê duyệt (Grade Management)", permissions: ["academic.grade_entry", "academic.grade_approve"] },
  { id: "comments_mgmt", name: "Nhận xét Định kỳ Học viên (Student Comments)", permissions: ["academic.comment_write", "academic.comment_approve"] },
  { id: "lms_mgmt", name: "Học liệu LMS & Chấm thi Tự luận (LMS & Lectures)", permissions: ["lms.manage", "lms.exam_grade"] },
  { id: "finance_mgmt", name: "Kế toán Học phí & Chi phí (Finance & Billing)", permissions: ["finance.billing", "finance.expenses", "finance.report_all"] },
  { id: "facility_mgmt", name: "Cơ sở vật chất & Đối tác (Facilities & Campuses)", permissions: ["facility.manage", "facility.feedback_resolve"] },
  { id: "task_mgmt", name: "Quản lý Nhiệm vụ & Công việc (Task Management)", permissions: ["task.create", "task.view_all"] }
];

export const permissionModuleFilterOptions = [
  { value: "ALL", label: "Tất cả phân hệ nghiệp vụ" },
  { value: "user_mgmt", label: "Hệ thống & Tài khoản" },
  { value: "student_mgmt", label: "Lý lịch Học viên" },
  { value: "teacher_mgmt", label: "Nhân sự & Bảng lương" },
  { value: "crm_mgmt", label: "CRM & Leads tuyển sinh" },
  { value: "academic_mgmt", label: "Khung giáo trình" },
  { value: "grades_mgmt", label: "Nhập & duyệt sổ điểm" },
  { value: "comments_mgmt", label: "Nhận xét định kỳ" },
  { value: "lms_mgmt", label: "CDN bài giảng LMS" },
  { value: "finance_mgmt", label: "Học phí & Chi hoạt động" },
  { value: "facility_mgmt", label: "Thiết bị phòng học" },
  { value: "task_mgmt", label: "Nhiệm vụ & Giao việc" }
];

export const initialGroups: PermissionGroup[] = [
  { id: "GRP-01", code: "GRP-SYS-ADMIN", name: "Nhóm Toàn quyền Quản trị", module: "SYS", description: "Bao gồm tất cả quyền quản trị hệ thống, tài khoản và kiểm soát phân quyền hệ thống.", permissions: ["system.admin", "user.manage", "permission.edit"], status: "active", createdBy: "Hệ thống", updatedAt: "2026-07-10" },
  { id: "GRP-02", code: "GRP-SYS-EXEC", name: "Nhóm Ban Giám đốc & Tổng hợp", module: "SYS", description: "Nhóm quyền cao cấp cho phép xem toàn bộ báo cáo tài chính, giao việc toàn hệ thống và bao quát tiến độ.", permissions: ["task.view_all", "task.create", "finance.report_all", "facility.manage"], status: "active", createdBy: "Hệ thống", updatedAt: "2026-07-10" },
  { id: "GRP-03", code: "GRP-ACAD-CURRICULUM", name: "Nhóm Học thuật & Khung chương trình", module: "ACAD", description: "Xây dựng khung chương trình đào tạo, giáo án học viên và học liệu bài giảng.", permissions: ["academic.curriculum", "lms.manage"], status: "active", createdBy: "Hệ thống", updatedAt: "2026-07-10" },
  { id: "GRP-04", code: "GRP-ACAD-TEACHING", name: "Nhóm Nghiệp vụ Giảng dạy & Chuyên môn", module: "ACAD", description: "Nhập điểm thành phần học viên, viết nhận xét daily/mid/final và chấm điểm bài thi nói.", permissions: ["academic.grade_entry", "academic.comment_write", "lms.manage", "lms.exam_grade"], status: "active", createdBy: "Hệ thống", updatedAt: "2026-07-11" },
  { id: "GRP-05", code: "GRP-ACAD-APPROVAL", name: "Nhóm Phê duyệt Đào tạo & Khảo thí", module: "ACAD", description: "Phê duyệt sổ điểm học kỳ, duyệt nhận xét định kỳ để hiển thị lên Portal Phụ huynh.", permissions: ["academic.grade_approve", "academic.comment_approve"], status: "active", createdBy: "Hệ thống", updatedAt: "2026-07-11" },
  { id: "GRP-06", code: "GRP-CRM-SALES", name: "Nhóm Tuyển sinh & CSKH (CRM)", module: "CRM", description: "Chăm sóc và chia Leads tuyển sinh, gọi điện tư vấn phụ huynh và chuyển đổi lead thành học viên.", permissions: ["crm.manage", "crm.convert_student", "student.manage"], status: "active", createdBy: "Hệ thống", updatedAt: "2026-07-10" },
  { id: "GRP-07", code: "GRP-STUDENT-REGISTRAR", name: "Nhóm Giáo vụ & Vận hành lớp học", module: "STUDENT", description: "Điểm danh học viên chuyên cần, quản lý hồ sơ lý lịch học sinh và quản lý phòng học.", permissions: ["student.manage", "student.attendance", "facility.manage"], status: "active", createdBy: "Hệ thống", updatedAt: "2026-07-10" },
  { id: "GRP-08", code: "GRP-FIN-ACCOUNTING", name: "Nhóm Nghiệp vụ Kế toán & Đối soát", module: "FIN", description: "Quản lý học phí học sinh, xuất hóa đơn đóng phí, quét mã QR thanh toán và ghi nhận chi phí.", permissions: ["finance.billing", "finance.expenses"], status: "active", createdBy: "Hệ thống", updatedAt: "2026-07-11" },
  { id: "GRP-09", code: "GRP-HRM-STAFF", name: "Nhóm Nhân sự & Tiền lương (HRM)", module: "HRM", description: "Quản lý hồ sơ cán bộ giáo viên, lập hợp đồng lao động và tính toán bảng lương tự động.", permissions: ["hrm.manage", "hrm.payroll"], status: "active", createdBy: "Hệ thống", updatedAt: "2026-07-11" },
  { id: "GRP-10", code: "GRP-HRM-LEAVE", name: "Nhóm Phê duyệt Nhân sự & Đơn từ", module: "HRM", description: "Duyệt đơn xin nghỉ phép, xin đi muộn/về sớm của cán bộ nhân viên và giáo viên.", permissions: ["hrm.approve_leave"], status: "active", createdBy: "Hệ thống", updatedAt: "2026-07-11" },
  { id: "GRP-11", code: "GRP-FACILITY-PARTNER", name: "Nhóm Cơ sở vật chất & Đối tác", module: "FACILITY", description: "Xếp phòng ca học, quản lý điểm trường liên kết và giải quyết phản hồi từ đối tác.", permissions: ["facility.manage", "facility.feedback_resolve"], status: "active", createdBy: "Hệ thống", updatedAt: "2026-07-12" }
];

export const initialRoleGroups: Record<string, string[]> = {
  SYS_ADMIN: ["GRP-01"],
  EXECUTIVE: ["GRP-02", "GRP-05", "GRP-10", "GRP-11"],
  HEAD_ACADEMIC: ["GRP-03", "GRP-04", "GRP-05"],
  SITE_MANAGER: ["GRP-05", "GRP-06", "GRP-07", "GRP-08", "GRP-10", "GRP-11"],
  TEACHER: ["GRP-04"],
  HR_MANAGER: ["GRP-09", "GRP-10"],
  STAFF: ["GRP-06", "GRP-07", "GRP-08"],
  OPS_MANAGER: ["GRP-02", "GRP-10", "GRP-11"],
  PARTNER_REP: []
};

export const initialGroupMembers: Record<string, string[]> = {
  "GRP-01": ["EMP-001"],
  "GRP-02": ["EMP-001"],
  "GRP-03": ["EMP-002"],
  "GRP-04": ["EMP-005", "EMP-006"],
  "GRP-05": ["EMP-002", "EMP-003", "EMP-004"],
  "GRP-06": ["EMP-008"],
  "GRP-07": ["EMP-003", "EMP-004"],
  "GRP-08": ["EMP-009"],
  "GRP-09": ["EMP-007"],
  "GRP-10": ["EMP-001", "EMP-007"],
  "GRP-11": ["EMP-003", "EMP-004"]
};
