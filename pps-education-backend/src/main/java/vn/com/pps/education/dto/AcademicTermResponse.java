package vn.com.pps.education.dto;

import java.time.LocalDate;

public record AcademicTermResponse(
        Long id,
        Long siteId,
        String siteName,
        Long academicYearId,
        String academicYearCode,
        String academicYearName,
        String code,
        String name,
        LocalDate startDate,
        LocalDate endDate
) {}
