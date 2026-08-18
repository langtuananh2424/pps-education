package vn.com.pps.education.dto;

import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, xác nhận 2026-08-17: response cho trang roster
 * "Lịch làm việc" toàn công ty (EmployeeScheduleService) — gộp 4 nguồn
 * dữ liệu, tái dùng nguyên vẹn các DTO đã có (không phát minh field mới).
 * FE tự join employeeShifts với shiftDefinitions theo shiftId, và tự tính
 * ca nào áp dụng ngày nào (giống EmployeeScheduleTab hiện có).
 */
public record EmployeeScheduleOverviewResponse(
        List<EmployeeResponse> employees,
        List<ShiftResponse> shiftDefinitions,
        List<EmployeeShiftResponse> employeeShifts,
        List<ClassSessionResponse> sessions,
        List<WorkCalendarResponse> workCalendarOverrides
) {}
