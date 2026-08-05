package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ExerciseAssignmentStatsResponse(
        Long assignmentId,
        Long exerciseId,
        String exerciseCode,
        String exerciseTitle,
        String exerciseType,
        OffsetDateTime availableFrom,
        OffsetDateTime dueAt,
        String status,
        int totalStudents,
        int completedCount,
        BigDecimal completionPercent,
        int passedCount,
        BigDecimal passRatePercent
) {}
