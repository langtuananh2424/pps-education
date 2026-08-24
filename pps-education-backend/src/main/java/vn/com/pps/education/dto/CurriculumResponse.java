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
        /** V140 — NULL = chưa phân loại. */
        String gradeLevel,
        String track,
        Integer totalPeriods,
        BigDecimal defaultGradePassThreshold,
        String status,
        Long createdBy,
        Long approvedBy
) {}
