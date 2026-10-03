package vn.com.pps.education.permission.dto;

/** UC-03: Cấu hình nhóm quyền mặc định. */
public record RoleResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean isSystem,
        /** V202 — ALL / SITE / CLASS / SELF, xem Role.DataScope. */
        String dataScope
) {}
