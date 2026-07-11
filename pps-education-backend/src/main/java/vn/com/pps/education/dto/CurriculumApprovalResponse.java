package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

public record CurriculumApprovalResponse(
        Long id,
        Long curriculumId,
        String curriculumCode,
        String curriculumName,
        String status,
        Long submittedBy,
        OffsetDateTime submittedAt,
        Long approverId,
        String decision,
        String comment,
        OffsetDateTime decidedAt
) {}
