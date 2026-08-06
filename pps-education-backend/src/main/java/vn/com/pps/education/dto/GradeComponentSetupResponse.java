package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GradeComponentSetupResponse(
        Long id,
        Long classId,
        Long academicTermId,
        String academicTermName,
        String evaluationType,
        BigDecimal weightInFinal,
        LocalDate rosterAsOfDate,
        boolean commentRequired
) {}
