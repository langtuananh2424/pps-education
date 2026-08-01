package vn.com.pps.education.dto;

import java.time.LocalDate;

public record AcademicTermResponse(
        Long id,
        Long siteId,
        String siteName,
        String code,
        String name,
        LocalDate startDate,
        LocalDate endDate
) {}
