package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * UC-70 (V124, 2026-08-14) -- chủ động kết thúc 1 bản ghi employee_shifts
 * đang active, để có thể gán ca khác không chồng chéo lịch. Xem
 * EmployeeShiftService.endShift.
 */
public record EndEmployeeShiftRequest(@NotNull LocalDate effectiveTo) {}
