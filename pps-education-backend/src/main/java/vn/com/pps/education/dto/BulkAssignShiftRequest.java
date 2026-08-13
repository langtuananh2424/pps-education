package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** Gán 1 ca cho nhiều nhân sự cùng lúc — bổ sung 2026-08-13, xem docs/uc/phan-he-04-nhan-su.md. */
public record BulkAssignShiftRequest(
        @NotEmpty List<Long> employeeIds,
        @NotNull Long shiftId,
        @NotNull LocalDate effectiveFrom
) {}
