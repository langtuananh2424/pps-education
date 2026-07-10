package vn.com.pps.education.dto;

/** UC-02: Quản lý danh mục quyền. */
public record PermissionResponse(
        Long id,
        String code,
        String name,
        String module,
        String description
) {}
