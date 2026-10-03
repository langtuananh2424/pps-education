package vn.com.pps.education.academic.dto;

import java.time.LocalDate;

public record AcademicYearResponse(
        Long id,
        String code,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String status
) {}
