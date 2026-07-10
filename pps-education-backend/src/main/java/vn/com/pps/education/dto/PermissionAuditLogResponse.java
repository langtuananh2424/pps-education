package vn.com.pps.education.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/** UC-05: Xem nhật ký thay đổi quyền. */
public record PermissionAuditLogResponse(
        Long id,
        Long actorUserId,
        Long targetUserId,
        String action,
        Long targetRoleId,
        Long targetPermissionId,
        Map<String, Object> details,
        String ipAddress,
        OffsetDateTime createdAt
) {}
