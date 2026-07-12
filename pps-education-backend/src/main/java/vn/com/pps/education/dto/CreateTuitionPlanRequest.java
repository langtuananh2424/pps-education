package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Hạ tầng cho UC-30 Main Flow bước 1 (không có UC riêng mô tả tạo định mức phí — xem TuitionPlanService). */
public record CreateTuitionPlanRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull Long curriculumId,
        @NotBlank String pricingModel,
        String classTypeFilter,
        @NotNull BigDecimal basePrice,
        BigDecimal pricePerUnit,
        Integer unitCount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
