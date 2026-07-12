package vn.com.pps.education.dto;

import java.math.BigDecimal;

public record GradeComponentResponse(
        Long id,
        Long gradePeriodId,
        Long subjectId,
        String code,
        String name,
        BigDecimal weightInPeriod,
        BigDecimal maxScore,
        BigDecimal passThreshold,
        int displayOrder
) {}
