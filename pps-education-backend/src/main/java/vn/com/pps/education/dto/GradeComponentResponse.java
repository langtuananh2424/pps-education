package vn.com.pps.education.dto;

import java.math.BigDecimal;

public record GradeComponentResponse(
        Long id,
        Long gradePeriodId,
        Long subjectId,
        Long skillId,
        String code,
        String name,
        BigDecimal maxScore,
        BigDecimal passThreshold,
        String scaleType,
        int displayOrder
) {}
