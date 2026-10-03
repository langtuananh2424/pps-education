package vn.com.pps.education.academic.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record WithdrawEnrollmentRequest(
        @NotNull LocalDate withdrawnDate,
        String reason
) {}
