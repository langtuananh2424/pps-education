package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * UC-70 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13). appliesToWeekdays:
 * CSV các số 1-7 (1=T2...7=CN, khớp quy ước Shift.java/AttendanceService),
 * để trống dùng mặc định "1,2,3,4,5,6". weekParity: ALL/ODD/EVEN.
 */
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
        String weekParity
) {}
