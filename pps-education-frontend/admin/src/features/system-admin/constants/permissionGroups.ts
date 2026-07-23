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
 * không liệt kê ở đây (VD FINANCE/FACILITY/LMS/TASK — mỗi tính năng con chỉ có
 * đúng 1 quyền) vẫn hiện dạng phẳng như cũ, không cần nhóm.
 */
export const permissionGroupsByModule: Record<string, PermissionGroupDef[]> = {
  ACADEMIC: [
    {
      key: "attendance",
      label: "Điểm danh",
      codes: ["academic.attendance.mark", "academic.attendance.create", "academic.attendance.update", "academic.attendance.delete"]
    },
    { key: "comment", label: "Nhận xét học viên", codes: ["academic.comment.write", "academic.comment.approve"] },
    { key: "grade", label: "Sổ điểm", codes: ["academic.grade.manage", "academic.grade.publish", "academic.grade.edit.override"] },
    {
      key: "grade-period",
      label: "Sổ điểm — Kỳ đánh giá",
      codes: ["academic.grade.period.create", "academic.grade.period.update", "academic.grade.period.delete"]
    },
    {
      key: "grade-component",
      label: "Sổ điểm — Thành phần điểm",
      codes: ["academic.grade.component.create", "academic.grade.component.update", "academic.grade.component.delete"]
    }
  ],
  STUDENT: [
    {
      key: "profile",
      label: "Hồ sơ học sinh",
      codes: ["student.profile.view", "student.profile.create", "student.profile.update", "student.profile.import"]
    },
    { key: "parent", label: "Phụ huynh", codes: ["student.parent.view", "student.parent.manage"] }
  ],
  CRM: [{ key: "lead", label: "Khách hàng tiềm năng (Lead)", codes: ["crm.lead.manage", "crm.lead.assign"] }],
  USER: [
    {
      key: "permission",
      label: "Phân quyền hệ thống",
      codes: ["permission.role.manage", "permission.override.manage", "permission.catalog.manage", "permission.audit.view"]
    }
  ]
};
