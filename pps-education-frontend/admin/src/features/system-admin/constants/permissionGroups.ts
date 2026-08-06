export interface PermissionGroupDef {
  /** Duy nhất trong 1 module — dùng để lưu trạng thái mở/đóng riêng từng nhóm con. */
  key: string;
  label: string;
  codes: string[];
}

/**
 * Nhóm quyền hạt nhân theo tính năng con trong từng module (VD "Điểm danh" gồm
 * tạo/sửa/xoá/điểm danh) — chỉ để HIỂN THỊ cây cha-con cho dễ hình dung khi cấu
 * hình vai trò (UC-03), không đổi gì ở dữ liệu quyền thật. Module/quyền nào
 * không liệt kê ở đây (VD LMS/TASK/AUTH — mỗi tính năng con chỉ có đúng 1
 * quyền) vẫn hiện dạng phẳng như cũ, không cần nhóm.
 *
 * ⚠️ Mọi `codes` ở đây PHẢI khớp đúng cột `permissions.code` thật trong DB —
 * 1 mã sai/không tồn tại sẽ khiến các quyền thật CÒN LẠI trong nhóm đó rớt
 * xuống hiện phẳng ở cuối danh sách (silent fallback trong PermissionChecklist,
 * không báo lỗi), trông lộn xộn dù dữ liệu quyền thật không hề sai (đã phát
 * hiện + sửa 2026-07-29: `student.parent.manage` và `user.role.manage` không
 * tồn tại — đúng phải là `student.parent.update`/`user.role.view`).
 */
export const permissionGroupsByModule: Record<string, PermissionGroupDef[]> = {
  ACADEMIC: [
    {
      key: "attendance",
      label: "Điểm danh",
      codes: ["academic.attendance.mark", "academic.attendance.create", "academic.attendance.update", "academic.attendance.delete"]
    },
    { key: "comment", label: "Nhận xét học viên", codes: ["academic.comment.write", "academic.comment.approve"] },
    { key: "grade", label: "Sổ điểm", codes: ["academic.grade.manage", "academic.grade.approve", "academic.grade.edit.override"] },
    {
      key: "grade-setup",
      label: "Sổ điểm — Setup (lớp + kỳ học + Giữa/Cuối kỳ)",
      codes: ["academic.grade.setup.create", "academic.grade.setup.update", "academic.grade.setup.delete"]
    },
    {
      key: "grade-component",
      label: "Sổ điểm — Thành phần điểm",
      codes: ["academic.grade.component.create", "academic.grade.component.update", "academic.grade.component.delete"]
    },
    { key: "skill", label: "Kỹ năng", codes: ["academic.skill.create", "academic.skill.update"] }
  ],
  STUDENT: [
    {
      key: "profile",
      label: "Hồ sơ học sinh",
      codes: ["student.profile.view", "student.profile.create", "student.profile.update", "student.profile.import"]
    },
    {
      key: "parent",
      label: "Phụ huynh",
      codes: ["student.parent.view", "student.parent.create", "student.parent.update", "student.parent.import"]
    },
    {
      key: "parent-link",
      label: "Phụ huynh — Liên kết với học sinh",
      codes: ["student.parent.link.create", "student.parent.link.delete"]
    }
  ],
  CRM: [
    {
      key: "lead",
      label: "Khách hàng tiềm năng (Lead)",
      codes: ["crm.lead.create", "crm.lead.update", "crm.lead.convert", "crm.lead.assign"]
    }
  ],
  USER: [
    {
      key: "permission-catalog",
      label: "Danh mục quyền",
      codes: ["permission.catalog.view", "permission.catalog.create", "permission.catalog.update", "permission.catalog.delete"]
    },
    {
      key: "permission-role",
      label: "Vai trò tuỳ biến",
      codes: ["permission.role.view", "permission.role.create", "permission.role.update", "permission.role.delete"]
    },
    {
      key: "permission-override",
      label: "Tuỳ chỉnh quyền riêng theo tài khoản",
      codes: ["permission.override.view", "permission.override.set", "permission.override.delete"]
    },
    {
      key: "user-role",
      label: "Gán vai trò cho tài khoản",
      codes: ["user.role.view", "user.role.assign", "user.role.revoke"]
    }
  ],
  HRM: [
    { key: "department", label: "Phòng ban", codes: ["hrm.department.create", "hrm.department.update", "hrm.department.delete"] },
    {
      key: "position",
      label: "Chức vụ",
      codes: ["hrm.position.view", "hrm.position.create", "hrm.position.update", "hrm.position.delete"]
    },
    {
      key: "employee",
      label: "Hồ sơ nhân sự",
      codes: ["hrm.employee.view", "hrm.employee.create", "hrm.employee.update", "hrm.employee.import"]
    }
  ],
  FACILITY: [
    {
      key: "partner-contract",
      label: "Hợp đồng đối tác",
      codes: [
        "facility.partner-contract.view",
        "facility.partner-contract.create",
        "facility.partner-contract.update",
        "facility.partner-contract.delete"
      ]
    },
    { key: "site", label: "Điểm trường", codes: ["facility.site.create", "facility.site.update"] },
    {
      key: "site-teacher",
      label: "Gán giáo viên vào điểm trường",
      codes: ["facility.site-teacher.assign", "facility.site-teacher.remove"]
    }
  ],
  FINANCE: [{ key: "expense", label: "Chi vận hành", codes: ["finance.expense.create", "finance.expense.approve"] }]
};
