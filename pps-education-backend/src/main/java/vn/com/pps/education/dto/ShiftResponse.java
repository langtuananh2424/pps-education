package vn.com.pps.education.dto;

import vn.com.pps.education.domain.Shift;

import java.time.LocalTime;

/** Bổ sung 2026-08-13 — xem docs/uc/phan-he-04-nhan-su.md (khối bổ sung dưới UC-09). */
public record ShiftResponse(
        Long id,
        String code,
        String name,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        int checkInWindowBeforeMinutes,
        int checkInWindowAfterMinutes,
        int checkOutWindowBeforeMinutes,
        int checkOutWindowAfterMinutes,
        String appliesToWeekdays,
        Shift.WeekParity weekParity,
        boolean active
) {}
