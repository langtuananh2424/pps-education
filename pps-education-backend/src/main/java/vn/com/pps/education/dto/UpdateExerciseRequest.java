package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04 — sửa lại
 * thông tin 1 "Bài" đã soạn (trước đây chỉ tạo được, không sửa được).
 * Không sửa được code/examId/exerciseType (cố định từ lúc tạo, giống quy
 * ước UpdateExamRequest).
 */
public record UpdateExerciseRequest(
        @NotBlank String title,
        Long subjectId,
        @NotNull BigDecimal totalPoints,
        boolean allowRetake,
        Integer maxAttempts,
        boolean showCorrectAnswers,
        /** V89, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05: NULL = giữ nguyên ngưỡng đã cấu hình. */
        BigDecimal passThresholdPercent
) {
    public UpdateExerciseRequest(String title, Long subjectId, BigDecimal totalPoints, boolean allowRetake,
                                  Integer maxAttempts, boolean showCorrectAnswers) {
        this(title, subjectId, totalPoints, allowRetake, maxAttempts, showCorrectAnswers, null);
    }
}
