package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Gán 1 ca cho 1 nhân sự — bổ sung 2026-08-13, xem docs/uc/phan-he-04-nhan-su.md. */
public record AssignShiftRequest(
        @NotNull Long employeeId,
        @NotNull Long shiftId,
        @NotNull LocalDate effectiveFrom
) {}
