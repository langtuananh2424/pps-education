package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** UC-19 Precondition: cấu hình kỳ đánh giá cho khung chương trình (actor HEAD_ACADEMIC). */
public record CreateGradePeriodRequest(
        @NotBlank String code,
        @NotBlank String name,
        Integer displayOrder,
        @NotNull BigDecimal weightInFinal,
        LocalDate startDate,
        LocalDate endDate
) {}
