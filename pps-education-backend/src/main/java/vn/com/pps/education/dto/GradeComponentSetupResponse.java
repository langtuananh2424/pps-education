package vn.com.pps.education.dto;

import java.time.LocalDate;

public record GradeComponentSetupResponse(
        Long id,
        Long classId,
        Long academicTermId,
        String academicTermName,
        String evaluationType,
        String scaleType,
        LocalDate rosterAsOfDate,
        boolean commentRequired
) {}
