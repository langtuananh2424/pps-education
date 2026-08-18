package vn.com.pps.education.dto;

import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, xác nhận 2026-08-17: response cho trang roster
 * "Lịch làm việc" toàn công ty (EmployeeScheduleService) — gộp nhiều
 * nguồn dữ liệu, tái dùng nguyên vẹn các DTO đã có (không phát minh field
 * mới). FE tự join employeeShifts với shiftDefinitions theo shiftId, và tự
 * tính ca nào áp dụng ngày nào (giống EmployeeScheduleTab hiện có).
 *
 * classSessionCheckIns (UC-71, bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-08-18): trạng thái nhận lớp TÍNH RA của mỗi session
 * trong {@link #sessions}, FE tự join theo classSessionId — xem
 * ClassSessionCheckInService#listEffectiveStatus.
 */
public record EmployeeScheduleOverviewResponse(
        List<EmployeeResponse> employees,
        List<ShiftResponse> shiftDefinitions,
        List<EmployeeShiftResponse> employeeShifts,
        List<ClassSessionResponse> sessions,
        List<WorkCalendarResponse> workCalendarOverrides,
        List<ClassSessionCheckInStatusResponse> classSessionCheckIns
) {}
