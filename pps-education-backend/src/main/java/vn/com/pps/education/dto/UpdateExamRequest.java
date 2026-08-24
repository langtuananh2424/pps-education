package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Kho đề — sửa được tiêu đề + teacherType/examType (V74, đã xác nhận với
 * người dùng 2026-08-04); khung chương trình bất biến sau khi tạo (mirror
 * ReviewVideoSet không đổi scope).
 *
 * V144 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) —
 * sửa được thêm skillCategory/allowRetake/maxAttempts/passThresholdPercent
 * (chuyển từ UpdateExerciseRequest cũ) — mirror cách teacherType/examType
 * sửa được cùng title.
 */
public record UpdateExamRequest(
        @NotBlank String title,
        @NotBlank String teacherType,
        @NotBlank String examType,
        @NotBlank String skillCategory,
        boolean allowRetake,
        Integer maxAttempts,
        BigDecimal passThresholdPercent
) {
}
