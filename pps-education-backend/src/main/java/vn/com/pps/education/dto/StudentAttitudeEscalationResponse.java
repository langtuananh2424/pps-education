package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

public record StudentAttitudeEscalationResponse(
        Long id,
        Long studentId,
        String studentName,
        Long schoolClassId,
        String className,
        int streakCount,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime decidedAt,
        String rejectionReason
) {}
