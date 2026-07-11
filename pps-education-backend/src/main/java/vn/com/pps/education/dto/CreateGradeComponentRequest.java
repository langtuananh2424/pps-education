package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateGradeComponentRequest(
        Long subjectId,
        @NotBlank String code,
        @NotBlank String name,
        @NotNull BigDecimal weightInPeriod,
        BigDecimal maxScore,
        BigDecimal passThreshold,
        Integer displayOrder
) {}
