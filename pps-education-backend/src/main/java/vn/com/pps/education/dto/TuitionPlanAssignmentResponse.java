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
        LocalDate effectiveTo
) {}
