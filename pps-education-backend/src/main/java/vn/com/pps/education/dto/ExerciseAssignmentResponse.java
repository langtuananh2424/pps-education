package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ExerciseAssignmentResponse(
        Long id,
        Long exerciseId,
        Long classId,
        Long assignedBy,
        OffsetDateTime availableFrom,
        OffsetDateTime dueAt,
        boolean lateSubmissionAllowed,
        BigDecimal latePenaltyPercent,
        List<Long> targetStudentIds,
        String status
) {}
