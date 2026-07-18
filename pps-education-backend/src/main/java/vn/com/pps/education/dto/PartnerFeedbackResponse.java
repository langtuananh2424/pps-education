package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

public record PartnerFeedbackResponse(
        Long id,
        Long siteId,
        Long submittedBy,
        String content,
        String feedbackType,
        String priority,
        String status,
        Long assignedTo,
        String resolutionNotes,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt
) {}
