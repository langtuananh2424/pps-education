package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ListeningPracticeGradingResponse(
        Long id,
        Long practiceAttemptId,
        Long graderUserId,
        BigDecimal score,
        BigDecimal maxScore,
        String feedback,
        OffsetDateTime gradedAt
) {}
