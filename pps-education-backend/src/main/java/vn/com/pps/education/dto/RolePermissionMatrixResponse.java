package vn.com.pps.education.dto;

import java.util.List;

/** UC-03 bước 2: ma trận quyền hiện tại của 1 role. */
public record RolePermissionMatrixResponse(
        Long roleId,
        String roleCode,
        List<PermissionMatrixItem> permissions
) {}
