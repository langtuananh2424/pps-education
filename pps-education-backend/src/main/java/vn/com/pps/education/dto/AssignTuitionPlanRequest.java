package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssignTuitionPlanRequest(
        @NotNull Long classId,
        @NotNull Long tuitionPlanId,
        BigDecimal priceOverride,
        String overrideReason,
        LocalDate effectiveFrom
) {}
