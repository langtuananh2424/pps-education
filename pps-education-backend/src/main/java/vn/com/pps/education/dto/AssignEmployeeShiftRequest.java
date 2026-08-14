package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * UC-70 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13). Đóng bản ghi
 * employee_shifts đang active (nếu có) tại effectiveFrom.minusDays(1), tạo
 * bản ghi mới -- xem EmployeeShiftService.assignShift.
 */
public record AssignEmployeeShiftRequest(
        @NotNull Long employeeId,
        @NotNull Long shiftId,
        @NotNull LocalDate effectiveFrom
) {}
