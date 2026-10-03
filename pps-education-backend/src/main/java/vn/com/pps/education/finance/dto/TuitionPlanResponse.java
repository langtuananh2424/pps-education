package vn.com.pps.education.finance.dto;

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
        String status,
        // Bổ sung 2026-10-03 cho màn Gói học phí phía Kế toán.
        String curriculumName
) {}
