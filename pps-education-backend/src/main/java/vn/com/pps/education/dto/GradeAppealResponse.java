package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

public record GradeAppealResponse(
        Long id,
        String entityType,
        Long entityId,
        Long classId,
        Long studentId,
        String studentFullName,
        Long requestedByUserId,
        String reason,
        String status,
        Long acceptedByUserId,
        OffsetDateTime acceptedAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt
) {}
