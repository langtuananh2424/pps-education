package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        BigDecimal myLatestTotalScore,
        /** V89, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05: BTVN <ngưỡng đạt phải làm lại — xem ExerciseAttemptService#applyPassOutcome. */
        BigDecimal myLatestPercentage,
        Boolean myLatestPassed,
        /** V123, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14: giáo viên Việt Nam/nước ngoài phụ trách Đề chứa Bài này (Exam.teacherType), không phải NULL trừ dữ liệu cũ trước khi teacher_type bắt buộc. */
        String teacherType,
        /** V123: ngày buổi học mà Giáo viên đã giao BTVN này (xem ExerciseAssignment#getSourceClassSession()) — NULL với bản giao tạo TRƯỚC V123. */
        LocalDate sessionDate
) {}
