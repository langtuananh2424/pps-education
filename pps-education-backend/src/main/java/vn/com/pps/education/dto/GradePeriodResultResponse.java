package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record GradePeriodResultResponse(
        Long id,
        Long classId,
        Long studentId,
        String studentFullName,
        String studentCode,
        Long gradePeriodId,
        BigDecimal overallScore,
        String scaleType,
        String level,
        String source,
        Long importJobId,
        String status,
        Long enteredBy,
        Long publishedBy,
        OffsetDateTime publishedAt
) {}
