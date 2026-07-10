package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

/** UC-05 bước 2: bộ lọc nhật ký thay đổi quyền — tất cả field đều tùy chọn. */
public record PermissionAuditLogSearchRequest(
        Long actorUserId,
        Long targetUserId,
        String action,
        OffsetDateTime fromDate,
        OffsetDateTime toDate
) {}
