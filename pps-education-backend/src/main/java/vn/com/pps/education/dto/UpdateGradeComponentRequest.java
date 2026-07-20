package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * SDD: nếu đã có grade_entries cho component này, weightInPeriod/maxScore
 * không được đổi (GradeComponentLockedException) — chỉ name/displayOrder
 * được sửa. Vẫn truyền đủ field để Service so sánh có thay đổi hay không.
 */
public record UpdateGradeComponentRequest(
        @NotBlank String name,
        @NotNull BigDecimal weightInPeriod,
        BigDecimal maxScore,
        BigDecimal passThreshold,
        Integer displayOrder
) {}
