package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** V226 (Nghe ép) — tạo/sửa 1 bài tập con. {@code displayOrder} NULL = xếp cuối. {@code passThresholdPercent} NULL = dùng mức của Bài. */
public record ExerciseSectionRequest(
        @NotBlank String title,
        Integer displayOrder,
        @DecimalMin("0.01") @DecimalMax("100") BigDecimal passThresholdPercent
) {}
