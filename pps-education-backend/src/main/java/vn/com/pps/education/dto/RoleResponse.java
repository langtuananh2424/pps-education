package vn.com.pps.education.dto;

/** UC-03: Cấu hình nhóm quyền mặc định. */
public record RoleResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean isSystem
) {}
