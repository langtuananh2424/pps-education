package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

/** UC-44 bước 3: 1 dòng override quyền riêng hiện có của tài khoản (UC-04). */
public record UserPermissionOverrideSummary(
        Long permissionId,
        String permissionCode,
        String overrideType,
        String reason,
        OffsetDateTime expiresAt
) {}
