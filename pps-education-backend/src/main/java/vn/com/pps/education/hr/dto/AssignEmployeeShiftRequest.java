package vn.com.pps.education.hr.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * UC-70 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13). Tạo bản ghi
 * employee_shifts mới -- xem EmployeeShiftService.assignShift.
 *
 * replaceOverlapping (null = false): khi true, các ca đang active chồng chéo lịch
 * với ca mới được đóng tại effectiveFrom.minusDays(1) thay vì bị từ chối (đổi ca).
 */
public record AssignEmployeeShiftRequest(
        @NotNull Long employeeId,
        @NotNull Long shiftId,
        @NotNull LocalDate effectiveFrom,
        Boolean replaceOverlapping
) {

    public AssignEmployeeShiftRequest(Long employeeId, Long shiftId, LocalDate effectiveFrom) {
        this(employeeId, shiftId, effectiveFrom, null);
    }
}
