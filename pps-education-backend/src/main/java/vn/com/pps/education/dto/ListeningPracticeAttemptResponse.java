package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ListeningPracticeAttemptResponse(
        Long id,
        Long practiceItemId,
        Long studentId,
        int attemptNumber,
        OffsetDateTime startedAt,
        OffsetDateTime submittedAt,
        Integer pausedPositionSeconds,
        String dictationAnswerText,
        BigDecimal dictationScore,
        String audioAnswerUrl,
        String status
) {}
