package vn.com.pps.education.dto;

import java.math.BigDecimal;

public record GradeEvaluationComponentResponse(
        Long id,
        Long gradeComponentSetupId,
        Long subjectId,
        Long skillId,
        String code,
        String name,
        BigDecimal maxScore,
        BigDecimal passThreshold,
        String scaleType,
        int displayOrder
) {}
