package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AttendanceCheckRequest;
import vn.com.pps.education.dto.AttendanceRecordAdminResponse;
import vn.com.pps.education.dto.AttendanceRecordResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.AttendanceService;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-09: Chấm công (FR-HRM-02). Tự phục vụ -- nhân sự chấm công cho chính
 * mình (employeeId suy ra từ principal), không cần permission riêng ngoài
 * đăng nhập (Precondition chỉ yêu cầu is_management=FALSE).
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/check-in")
    public ResponseEntity<AttendanceRecordResponse> checkIn(@Valid @RequestBody AttendanceCheckRequest request,
                                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(attendanceService.checkIn(actor.userId(), request));
    }

    @PostMapping("/check-out")
    public ResponseEntity<AttendanceRecordResponse> checkOut(@Valid @RequestBody AttendanceCheckRequest request,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(attendanceService.checkOut(actor.userId(), request));
    }

    /**
     * UC-09 — bổ sung ngoài Main Flow gốc, xác nhận với người dùng 2026-08-12.
     * Xem AttendanceService.listRecords.
     */
    @GetMapping("/records")
    @PreAuthorize("hasPermission(null, 'hrm.attendance.view-all')")
    public ResponseEntity<List<AttendanceRecordAdminResponse>> listRecords(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(attendanceService.listRecords(employeeId, siteId, from, to));
    }
}
