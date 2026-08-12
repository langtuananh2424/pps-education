package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ExerciseAssignmentStatsResponse(
        Long assignmentId,
        Long exerciseId,
        String exerciseCode,
        String exerciseTitle,
        String exerciseType,
        /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — lấy qua Đề cha (exercise.exam.teacherType), Exercise không tự có field này. Dùng lọc GV Việt Nam/nước ngoài ở UC-66. */
        String teacherType,
        OffsetDateTime availableFrom,
        OffsetDateTime dueAt,
        String status,
        int totalStudents,
        int completedCount,
        BigDecimal completionPercent,
        int passedCount,
        BigDecimal passRatePercent
) {}
