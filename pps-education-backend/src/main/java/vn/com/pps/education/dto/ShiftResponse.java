package vn.com.pps.education.dto;

import java.time.LocalTime;

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
        String weekParity,
        boolean active
) {}
