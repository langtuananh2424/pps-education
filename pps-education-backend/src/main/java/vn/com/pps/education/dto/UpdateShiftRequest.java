package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.com.pps.education.domain.Shift;

import java.time.LocalTime;

/** Khớp CreateShiftRequest — code bất biến, không sửa qua đây (giống UpdatePositionRequest). */
public record UpdateShiftRequest(
        @NotBlank String name,
        @NotNull LocalTime checkInTime,
        @NotNull LocalTime checkOutTime,
        Integer checkInWindowBeforeMinutes,
        Integer checkInWindowAfterMinutes,
        Integer checkOutWindowBeforeMinutes,
        Integer checkOutWindowAfterMinutes,
        String appliesToWeekdays,
        Shift.WeekParity weekParity,
        boolean active
) {}
