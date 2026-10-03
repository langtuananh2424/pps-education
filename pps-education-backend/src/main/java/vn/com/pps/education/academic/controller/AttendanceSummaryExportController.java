package vn.com.pps.education.academic.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.common.ExcelHttpResponses;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.academic.service.AttendanceSummaryExportService;

import java.time.LocalDate;

/**
 * Xuất Excel "Tổng hợp chuyên cần" của 1 lớp (V203 — bổ sung ngoài SDD gốc, xác nhận với người dùng
 * 2026-09-30) — xem Javadoc AttendanceSummaryExportService.
 */
@RestController
public class AttendanceSummaryExportController {

    private final AttendanceSummaryExportService attendanceSummaryExportService;

    public AttendanceSummaryExportController(AttendanceSummaryExportService attendanceSummaryExportService) {
        this.attendanceSummaryExportService = attendanceSummaryExportService;
    }

    @PreAuthorize("hasPermission(null, 'academic.attendance.view')")
    @GetMapping("/api/classes/{classId}/attendance-summary/export")
    public ResponseEntity<byte[]> export(
            @PathVariable Long classId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        byte[] content = attendanceSummaryExportService.exportClassSummary(classId, fromDate, toDate, actor.userId());
        return ExcelHttpResponses.attachment(content, "tong-hop-chuyen-can-lop-" + classId + ".xlsx");
    }
}
