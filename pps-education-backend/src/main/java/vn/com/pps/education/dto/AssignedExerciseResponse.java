package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Học sinh tự xem đề đã được giao cho lớp mình đang học — xem Javadoc ExerciseAttemptService.listMyAssignedExercises. */
public record AssignedExerciseResponse(
        Long exerciseId,
        String exerciseCode,
        String title,
        String exerciseType,
        Long assignmentId,
        Long classId,
        String className,
        OffsetDateTime availableFrom,
        OffsetDateTime dueAt,
        boolean lateSubmissionAllowed,
        Long myLatestAttemptId,
        String myLatestAttemptStatus,
        BigDecimal myLatestTotalScore
) {}
