package vn.com.pps.education.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.EmployeeScheduleOverviewResponse;
import vn.com.pps.education.service.EmployeeScheduleService;

import java.time.LocalDate;

/**
 * Bổ sung ngoài SDD gốc, xác nhận 2026-08-17: trang "Lịch làm việc" (roster
 * toàn công ty) trong menu Quản lý nhân sự -- tách riêng khỏi
 * EmployeeController/ShiftController vì đây là concern cross-cutting xuyên
 * nhiều domain (nhân sự + ca làm + lịch dạy + lịch nghỉ lễ), đúng tinh
 * thần SRP như AttendanceController tách riêng khỏi EmployeeController.
 * Xem docs/uc/phan-he-04-nhan-su.md (UC-70).
 */
@RestController
@RequestMapping("/api/hrm/employee-schedules")
public class EmployeeScheduleController {

    private final EmployeeScheduleService employeeScheduleService;

    public EmployeeScheduleController(EmployeeScheduleService employeeScheduleService) {
        this.employeeScheduleService = employeeScheduleService;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'hrm.employee-schedule.view')")
    public ResponseEntity<EmployeeScheduleOverviewResponse> getOverview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long employeeId) {
        return ResponseEntity.ok(employeeScheduleService.getOverview(from, to, departmentId, siteId, classId, employeeId));
    }
}
