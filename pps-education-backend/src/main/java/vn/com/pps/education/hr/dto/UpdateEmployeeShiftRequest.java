package vn.com.pps.education.hr.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * UC-70 (bổ sung): sửa 1 bản ghi employee_shifts -- đổi ca, ngày bắt đầu, ngày kết thúc
 * (effectiveTo null = đang áp dụng). Xem EmployeeShiftService.updateAssignment.
 */
public record UpdateEmployeeShiftRequest(
        @NotNull Long shiftId,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
