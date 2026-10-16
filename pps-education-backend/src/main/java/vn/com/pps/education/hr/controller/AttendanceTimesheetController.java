package vn.com.pps.education.hr.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.common.ExcelHttpResponses;
import vn.com.pps.education.hr.dto.AttendanceTimesheetResponse;
import vn.com.pps.education.hr.service.AttendanceTimesheetService;

/**
 * Bảng chấm công tháng tự động + xuất Excel (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-10-16),
 * dùng chung quyền hrm.attendance.view-all với màn "Dữ liệu chấm công". Xem AttendanceTimesheetService.
 */
@RestController
public class AttendanceTimesheetController {

    private final AttendanceTimesheetService timesheetService;

    public AttendanceTimesheetController(AttendanceTimesheetService timesheetService) {
        this.timesheetService = timesheetService;
    }

    @PreAuthorize("hasPermission(null, 'hrm.attendance.view-all')")
    @GetMapping("/api/attendance/timesheet")
    public ResponseEntity<AttendanceTimesheetResponse> timesheet(
            @RequestParam int year, @RequestParam int month,
            @RequestParam(required = false) Long siteId, @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(timesheetService.generate(year, month, siteId, departmentId));
    }

    @PreAuthorize("hasPermission(null, 'hrm.attendance.view-all')")
    @GetMapping("/api/attendance/timesheet/export")
    public ResponseEntity<byte[]> export(
            @RequestParam int year, @RequestParam int month,
            @RequestParam(required = false) Long siteId, @RequestParam(required = false) Long departmentId) {
        return ExcelHttpResponses.attachment(timesheetService.exportExcel(year, month, siteId, departmentId),
                String.format("bang-cham-cong-%d-%02d.xlsx", year, month));
    }
}
