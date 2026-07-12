package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ExerciseAttemptResponse(
        Long id,
        Long exerciseId,
        Long exerciseAssignmentId,
        Long studentId,
        int attemptNumber,
        OffsetDateTime startedAt,
        OffsetDateTime submittedAt,
        BigDecimal autoGradeScore,
        BigDecimal manualGradeScore,
        BigDecimal totalScore,
        String status,
        boolean isLateSubmission
) {}
