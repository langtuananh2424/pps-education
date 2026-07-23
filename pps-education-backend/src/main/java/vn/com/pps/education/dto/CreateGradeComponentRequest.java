package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateGradeComponentRequest(
        Long subjectId,
        Long skillId,
        @NotBlank String code,
        @NotBlank String name,
        BigDecimal maxScore,
        BigDecimal passThreshold,
        String scaleType,
        Integer displayOrder
) {}
