package vn.com.pps.education.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.ActualPeriodsGridResponse;
import vn.com.pps.education.dto.ActualPeriodsStatsResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ActualPeriodsReportService;

import java.time.LocalDate;

/**
 * Báo cáo "Số tiết thực tế theo lớp" (bổ sung ngoài SDD gốc, xác nhận với
 * người dùng 2026-08-20) — xem Javadoc ActualPeriodsReportService.
 */
@RestController
public class ActualPeriodsReportController {

    private final ActualPeriodsReportService actualPeriodsReportService;

    public ActualPeriodsReportController(ActualPeriodsReportService actualPeriodsReportService) {
        this.actualPeriodsReportService = actualPeriodsReportService;
    }

    @PreAuthorize("hasPermission(null, 'report.actual-periods.view')")
    @GetMapping("/api/sites/{siteId}/actual-periods-stats")
    public ResponseEntity<ActualPeriodsStatsResponse> getStats(
            @PathVariable Long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam String periodType,
            @RequestParam String periodLabel,
            @RequestParam(required = false) Long classId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(actualPeriodsReportService.getStats(
                siteId, fromDate, toDate, classId, actor.userId(), periodType, periodLabel));
    }

    @PreAuthorize("hasPermission(null, 'report.actual-periods.view')")
    @GetMapping("/api/sites/{siteId}/actual-periods-grid")
    public ResponseEntity<ActualPeriodsGridResponse> getGrid(
            @PathVariable Long siteId,
            @RequestParam String periodType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long classId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(actualPeriodsReportService.getGrid(siteId, periodType, year, classId, actor.userId()));
    }
}
