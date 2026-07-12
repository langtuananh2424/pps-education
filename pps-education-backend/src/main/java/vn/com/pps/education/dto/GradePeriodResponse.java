package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GradePeriodResponse(
        Long id,
        Long curriculumId,
        String code,
        String name,
        int displayOrder,
        BigDecimal weightInFinal,
        LocalDate startDate,
        LocalDate endDate,
        String status
) {}
