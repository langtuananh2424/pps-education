package vn.com.pps.education.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.common.ExcelHttpResponses;
import vn.com.pps.education.dto.TeachingStatsResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.TeachingStatsService;

import java.time.LocalDate;

/**
 * "Thống kê giảng dạy theo giáo viên" (V203 — bổ sung ngoài SDD gốc, xác nhận với người dùng
 * 2026-09-30) — xem Javadoc TeachingStatsService.
 */
@RestController
public class TeachingStatsController {

    private final TeachingStatsService teachingStatsService;

    public TeachingStatsController(TeachingStatsService teachingStatsService) {
        this.teachingStatsService = teachingStatsService;
    }

    @PreAuthorize("hasPermission(null, 'report.teacher-stats.view')")
    @GetMapping("/api/reports/teaching-stats")
    public ResponseEntity<TeachingStatsResponse> getStats(
            @RequestParam(required = false) Long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(teachingStatsService.getStats(siteId, fromDate, toDate, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'report.teacher-stats.view')")
    @GetMapping("/api/reports/teaching-stats/export")
    public ResponseEntity<byte[]> exportStats(
            @RequestParam(required = false) Long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        byte[] content = teachingStatsService.exportStatsExcel(siteId, fromDate, toDate, actor.userId());
        return ExcelHttpResponses.attachment(content, "giang-day-theo-giao-vien-" + fromDate + "-" + toDate + ".xlsx");
    }
}
