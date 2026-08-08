package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record GradeEvaluationResultResponse(
        Long id,
        Long classId,
        Long studentId,
        String studentFullName,
        String studentCode,
        Long academicTermId,
        String evaluationType,
        BigDecimal overallScore,
        String scaleType,
        String level,
        String comment,
        String note,
        String disclaimer,
        String source,
        Long importJobId,
        String status,
        Long enteredBy,
        Long publishedBy,
        OffsetDateTime publishedAt,
        OffsetDateTime finalizedAt
) {}
