package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** UC-70 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13). Không có "code" -- bất biến sau khi tạo. */
public record UpdateShiftRequest(
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
