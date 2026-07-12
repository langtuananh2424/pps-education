package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateGradePeriodRequest(
        @NotBlank String name,
        Integer displayOrder,
        @NotNull BigDecimal weightInFinal,
        LocalDate startDate,
        LocalDate endDate,
        @NotBlank String status
) {}
