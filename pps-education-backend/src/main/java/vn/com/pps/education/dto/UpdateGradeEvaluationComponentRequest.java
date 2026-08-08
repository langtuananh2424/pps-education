package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * SDD: nếu đã có grade_entries cho component này, maxScore không được
 * đổi (GradeComponentLockedException) — chỉ name/displayOrder được sửa.
 * Vẫn truyền đủ field để Service so sánh có thay đổi hay không.
 */
public record UpdateGradeEvaluationComponentRequest(
        @NotBlank String name,
        BigDecimal maxScore,
        BigDecimal passThreshold,
        Integer displayOrder
) {}
