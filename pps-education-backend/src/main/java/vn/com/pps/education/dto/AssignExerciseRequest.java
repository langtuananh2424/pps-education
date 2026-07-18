package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** UC-40 Main Flow bước 3: giao đề ASSIGNED cho 1 lớp kèm deadline. */
public record AssignExerciseRequest(
        @NotNull Long classId,
        OffsetDateTime availableFrom,
        OffsetDateTime dueAt,
        boolean lateSubmissionAllowed,
        BigDecimal latePenaltyPercent,
        List<Long> targetStudentIds
) {}
