package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.com.pps.education.domain.Shift;

import java.time.LocalTime;

/** Bổ sung 2026-08-13 — xem docs/uc/phan-he-04-nhan-su.md (khối bổ sung dưới UC-09). */
public record CreateShiftRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull LocalTime checkInTime,
        @NotNull LocalTime checkOutTime,
        Integer checkInWindowBeforeMinutes,
        Integer checkInWindowAfterMinutes,
        Integer checkOutWindowBeforeMinutes,
        Integer checkOutWindowAfterMinutes,
        String appliesToWeekdays,
        Shift.WeekParity weekParity
) {}
