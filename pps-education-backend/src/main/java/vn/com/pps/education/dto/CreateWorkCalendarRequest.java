package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * UC-70 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13). shiftId bắt buộc khi
 * appliesToScope=SHIFT, employeeId bắt buộc khi appliesToScope=EMPLOYEE --
 * xem WorkCalendarService.createOverride (validate, DB đã có unique index
 * V120 chặn trùng calendar_date/scope).
 */
public record CreateWorkCalendarRequest(
        @NotNull LocalDate calendarDate,
        @NotBlank String dayType,
        @NotBlank String appliesToScope,
        Long shiftId,
        Long employeeId,
        String description
) {}
