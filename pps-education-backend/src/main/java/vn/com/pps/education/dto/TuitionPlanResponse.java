package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TuitionPlanResponse(
        Long id,
        String code,
        String name,
        Long curriculumId,
        String pricingModel,
        String classTypeFilter,
        BigDecimal basePrice,
        BigDecimal pricePerUnit,
        Integer unitCount,
        String currency,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status
) {}
