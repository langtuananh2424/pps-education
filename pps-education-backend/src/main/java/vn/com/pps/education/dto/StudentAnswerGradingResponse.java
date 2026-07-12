package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StudentAnswerGradingResponse(
        Long id,
        Long studentAnswerId,
        Long graderUserId,
        BigDecimal score,
        BigDecimal maxScore,
        String feedback,
        OffsetDateTime gradedAt
) {}
