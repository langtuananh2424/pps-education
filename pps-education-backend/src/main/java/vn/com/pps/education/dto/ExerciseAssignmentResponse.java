package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ExerciseAssignmentResponse(
        Long id,
        UUID uuid,
        Long exerciseId,
        String exerciseTitle,
        String exerciseCode,
        Long classId,
        Long assignedBy,
        OffsetDateTime availableFrom,
        OffsetDateTime dueAt,
        boolean lateSubmissionAllowed,
        /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — NULL = nộp muộn không giới hạn. */
        OffsetDateTime lateSubmissionDeadline,
        BigDecimal latePenaltyPercent,
        List<Long> targetStudentIds,
        String status
) {}
