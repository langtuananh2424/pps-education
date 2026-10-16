package vn.com.pps.education.hr.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Bảng chấm công tháng (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-10-16): mỗi nhân sự 1 dòng,
 * mỗi ngày trong tháng 1 ô. Dùng chung cho màn hình (GET /api/attendance/timesheet) và file Excel.
 *
 * Mã ô (code): X = đủ công, M = đi muộn, S = về sớm, V = thiếu chấm công (MISSING), P = nghỉ phép có lương
 * (ANNUAL/SICK/PERSONAL đã duyệt), KP = nghỉ không lương đã duyệt. Ngày không có dữ liệu → không có ô.
 */
public record AttendanceTimesheetResponse(
        int year,
        int month,
        LocalDate fromDate,
        LocalDate toDate,
        List<Row> rows
) {

    public record Row(
            Long employeeId,
            String employeeCode,
            String employeeFullName,
            String departmentName,
            List<Cell> cells,
            Totals totals
    ) {}

    public record Cell(
            LocalDate date,
            String code,
            OffsetDateTime checkInAt,
            OffsetDateTime checkOutAt,
            Double hours
    ) {}

    public record Totals(
            int workDays,
            int lateDays,
            int earlyLeaveDays,
            int missingDays,
            int paidLeaveDays,
            int unpaidLeaveDays,
            double totalHours
    ) {}
}
