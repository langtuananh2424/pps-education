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
/** Nhãn `label` dịch qua i18next namespace "system-admin-roles" (key `permissionGroups.<MODULE>.<groupKey>`) — xem src/i18n/locales/{vi,en}/system-admin-roles.json. */
export function getPermissionGroupsByModule(t: (key: string) => string): Record<string, PermissionGroupDef[]> {
  return {
    ACADEMIC: [
      {
        key: "attendance",
        label: t("permissionGroups.ACADEMIC.attendance"),
        codes: ["academic.attendance.mark", "academic.attendance.create", "academic.attendance.update", "academic.attendance.delete"]
      },
      { key: "comment", label: t("permissionGroups.ACADEMIC.comment"), codes: ["academic.comment.write", "academic.comment.approve"] },
      {
        key: "grade",
        label: t("permissionGroups.ACADEMIC.grade"),
        codes: ["academic.grade.manage", "academic.grade.approve", "academic.grade.edit.override"]
      },
      {
        key: "grade-setup",
        label: t("permissionGroups.ACADEMIC.gradeSetup"),
        codes: ["academic.grade.setup.create", "academic.grade.setup.update", "academic.grade.setup.delete"]
      },
      {
        key: "grade-component",
        label: t("permissionGroups.ACADEMIC.gradeComponent"),
        codes: ["academic.grade.component.create", "academic.grade.component.update", "academic.grade.component.delete"]
      },
      { key: "skill", label: t("permissionGroups.ACADEMIC.skill"), codes: ["academic.skill.create", "academic.skill.update"] }
    ],
    STUDENT: [
      {
        key: "profile",
        label: t("permissionGroups.STUDENT.profile"),
        codes: ["student.profile.view", "student.profile.create", "student.profile.update", "student.profile.import"]
      },
      {
        key: "parent",
        label: t("permissionGroups.STUDENT.parent"),
        codes: ["student.parent.view", "student.parent.create", "student.parent.update", "student.parent.import"]
      },
      {
        key: "parent-link",
        label: t("permissionGroups.STUDENT.parentLink"),
        codes: ["student.parent.link.create", "student.parent.link.delete"]
      }
    ],
    CRM: [
      {
        key: "lead",
        label: t("permissionGroups.CRM.lead"),
        codes: ["crm.lead.create", "crm.lead.update", "crm.lead.convert", "crm.lead.assign"]
      }
    ],
    USER: [
      {
        key: "permission-catalog",
        label: t("permissionGroups.USER.permissionCatalog"),
        codes: ["permission.catalog.view", "permission.catalog.create", "permission.catalog.update", "permission.catalog.delete"]
      },
      {
        key: "permission-role",
        label: t("permissionGroups.USER.permissionRole"),
        codes: ["permission.role.view", "permission.role.create", "permission.role.update", "permission.role.delete"]
      },
      {
        key: "permission-override",
        label: t("permissionGroups.USER.permissionOverride"),
        codes: ["permission.override.view", "permission.override.set", "permission.override.delete"]
      },
      {
        key: "user-role",
        label: t("permissionGroups.USER.userRole"),
        codes: ["user.role.view", "user.role.assign", "user.role.revoke"]
      }
    ],
    HRM: [
      {
        key: "department",
        label: t("permissionGroups.HRM.department"),
        codes: ["hrm.department.create", "hrm.department.update", "hrm.department.delete"]
      },
      {
        key: "position",
        label: t("permissionGroups.HRM.position"),
        codes: ["hrm.position.view", "hrm.position.create", "hrm.position.update", "hrm.position.delete"]
      },
      {
        key: "employee",
        label: t("permissionGroups.HRM.employee"),
        codes: ["hrm.employee.view", "hrm.employee.create", "hrm.employee.update", "hrm.employee.import"]
      }
    ],
    FACILITY: [
      {
        key: "partner-contract",
        label: t("permissionGroups.FACILITY.partnerContract"),
        codes: [
          "facility.partner-contract.view",
          "facility.partner-contract.create",
          "facility.partner-contract.update",
          "facility.partner-contract.delete"
        ]
      },
      { key: "site", label: t("permissionGroups.FACILITY.site"), codes: ["facility.site.create", "facility.site.update"] },
      {
        key: "site-teacher",
        label: t("permissionGroups.FACILITY.siteTeacher"),
        codes: ["facility.site-teacher.assign", "facility.site-teacher.remove"]
      }
    ],
    FINANCE: [
      { key: "expense", label: t("permissionGroups.FINANCE.expense"), codes: ["finance.expense.create", "finance.expense.approve"] }
    ]
  };
}
