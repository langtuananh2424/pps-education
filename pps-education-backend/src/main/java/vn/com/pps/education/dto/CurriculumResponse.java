package vn.com.pps.education.dto;

import java.math.BigDecimal;

public record CurriculumResponse(
        Long id,
        String code,
        String name,
        Long siteId,
        Long parentCurriculumId,
        String classCategory,
        String level,
        Integer totalPeriods,
        BigDecimal defaultGradePassThreshold,
        String status,
        Long createdBy,
        Long approvedBy
) {}
