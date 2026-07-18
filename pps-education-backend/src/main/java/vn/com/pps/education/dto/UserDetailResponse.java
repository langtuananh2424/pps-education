package vn.com.pps.education.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** UC-44 Main Flow bước 3: chi tiết đầy đủ 1 tài khoản — không bao giờ chứa password_hash. */
public record UserDetailResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phone,
        Long departmentId,
        String status,
        boolean isManagement,
        boolean passwordSet,
        boolean googleLinked,
        OffsetDateTime lastLoginAt,
        int failedLoginCount,
        OffsetDateTime lockedUntil,
        List<RoleResponse> roles,
        List<UserPermissionOverrideSummary> permissionOverrides
) {}
