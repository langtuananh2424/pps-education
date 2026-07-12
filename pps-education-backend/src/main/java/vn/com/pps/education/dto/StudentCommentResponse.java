package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

public record StudentCommentResponse(
        Long id,
        Long studentId,
        String studentFullName,
        Long classId,
        Long teacherId,
        String commentType,
        Long classSessionId,
        Long gradePeriodId,
        LocalDate commentDate,
        String content,
        Map<String, Object> structuredContent,
        String severity,
        boolean isWarning,
        String status,
        OffsetDateTime submittedAt,
        OffsetDateTime approvedAt,
        Long approvedBy,
        OffsetDateTime visibleToParentAt,
        String rejectionReason
) {}
