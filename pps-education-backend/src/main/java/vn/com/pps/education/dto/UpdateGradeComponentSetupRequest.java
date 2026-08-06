package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateGradeComponentSetupRequest(
        BigDecimal weightInFinal,
        @NotNull LocalDate rosterAsOfDate,
        boolean commentRequired
) {}
