package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * UC-03 bước 3-4: danh sách permissionId cuối cùng cho role (không phải diff).
 * confirm=true bắt buộc khi A1 (xóa hết quyền của role đang có tài khoản active).
 */
public record UpdateRolePermissionsRequest(
        @NotNull Set<Long> permissionIds,
        boolean confirm
) {}
