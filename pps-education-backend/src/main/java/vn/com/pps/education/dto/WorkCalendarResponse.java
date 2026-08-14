package vn.com.pps.education.dto;

import java.time.LocalDate;

public record WorkCalendarResponse(
        Long id,
        LocalDate calendarDate,
        String dayType,
        String appliesToScope,
        Long shiftId,
        String shiftName,
        Long employeeId,
        String employeeFullName,
        String description
) {}
