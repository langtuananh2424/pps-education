package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TuitionPlanAssignmentResponse(
        Long id,
        Long classId,
        Long tuitionPlanId,
        BigDecimal priceOverride,
        String overrideReason,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        // Bổ sung 2026-10-03 cho màn Gói học phí phía Kế toán.
        String className,
        String tuitionPlanCode,
        String tuitionPlanName
) {}
