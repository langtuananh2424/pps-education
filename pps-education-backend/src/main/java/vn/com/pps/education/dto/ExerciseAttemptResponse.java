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
        boolean isLateSubmission,
        /** V89, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05: NULL khi totalScore chưa có (chưa chấm xong). */
        BigDecimal percentage,
        Boolean passed,
        /** V92, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: lượt bị hệ thống dừng ép do vi phạm giám sát. */
        boolean stoppedByIntegrityViolation,
        /** V93, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: Giáo viên đã chọn lượt này làm kết quả chính thức chưa. */
        boolean selectedForGrading
) {}
