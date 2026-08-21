package vn.com.pps.education.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.common.ExcelHttpResponses;
import vn.com.pps.education.dto.EnrollmentMovementGridResponse;
import vn.com.pps.education.dto.EnrollmentMovementStatsResponse;
import vn.com.pps.education.dto.EnrollmentMovementTrendResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.EnrollmentMovementReportService;

import java.time.LocalDate;

/**
 * UC-69: Thống kê biến động học sinh các lớp theo kỳ (FR-ACA-09) — xem
 * Javadoc EnrollmentMovementReportService. Mở rộng ngoài SDD gốc (xác nhận
 * với người dùng 2026-08-20): thêm nhóm endpoint theo điểm trường +
 * khoảng ngày tuỳ ý (fromDate/toDate) để hỗ trợ chế độ xem "theo
 * tháng"/"theo năm", song song với nhóm endpoint theo academicTermId gốc
 * ("theo kỳ") — không đổi/xoá nhóm cũ.
 */
@RestController
public class EnrollmentMovementReportController {

    private final EnrollmentMovementReportService enrollmentMovementReportService;

    public EnrollmentMovementReportController(EnrollmentMovementReportService enrollmentMovementReportService) {
        this.enrollmentMovementReportService = enrollmentMovementReportService;
    }

    @PreAuthorize("hasPermission(null, 'report.enrollment-stats.view')")
    @GetMapping("/api/academic-terms/{academicTermId}/enrollment-movement-stats")
    public ResponseEntity<EnrollmentMovementStatsResponse> getStats(
            @PathVariable Long academicTermId, @RequestParam(required = false) Long classId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(enrollmentMovementReportService.getStats(academicTermId, classId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'report.enrollment-stats.view')")
    @GetMapping("/api/academic-terms/{academicTermId}/enrollment-movement-trend")
    public ResponseEntity<EnrollmentMovementTrendResponse> getTrend(
            @PathVariable Long academicTermId, @RequestParam(required = false) Long classId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(enrollmentMovementReportService.getTrend(academicTermId, classId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'report.enrollment-stats.view')")
    @GetMapping("/api/academic-terms/{academicTermId}/enrollment-movement-stats/export")
    public ResponseEntity<byte[]> exportStats(
            @PathVariable Long academicTermId, @RequestParam(required = false) Long classId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        byte[] content = enrollmentMovementReportService.exportStatsExcel(academicTermId, classId, actor.userId());
        return ExcelHttpResponses.attachment(content, "bien-dong-hoc-sinh-ky-" + academicTermId + ".xlsx");
    }

    @PreAuthorize("hasPermission(null, 'report.enrollment-stats.view')")
    @GetMapping("/api/sites/{siteId}/enrollment-movement-stats")
    public ResponseEntity<EnrollmentMovementStatsResponse> getStatsForRange(
            @PathVariable Long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam String periodType,
            @RequestParam String periodLabel,
            @RequestParam(required = false) Long classId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(enrollmentMovementReportService.getStatsForRange(
                siteId, fromDate, toDate, classId, actor.userId(), periodType, null, periodLabel));
    }

    @PreAuthorize("hasPermission(null, 'report.enrollment-stats.view')")
    @GetMapping("/api/sites/{siteId}/enrollment-movement-trend")
    public ResponseEntity<EnrollmentMovementTrendResponse> getTrendForRange(
            @PathVariable Long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam String periodType,
            @RequestParam String periodLabel,
            @RequestParam(required = false) Long classId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(enrollmentMovementReportService.getTrendForRange(
                siteId, fromDate, toDate, classId, actor.userId(), periodType, null, periodLabel));
    }

    @PreAuthorize("hasPermission(null, 'report.enrollment-stats.view')")
    @GetMapping("/api/sites/{siteId}/enrollment-movement-grid")
    public ResponseEntity<EnrollmentMovementGridResponse> getGrid(
            @PathVariable Long siteId,
            @RequestParam String periodType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long classId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(enrollmentMovementReportService.getGrid(siteId, periodType, year, classId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'report.enrollment-stats.view')")
    @GetMapping("/api/sites/{siteId}/enrollment-movement-stats/export")
    public ResponseEntity<byte[]> exportStatsForRange(
            @PathVariable Long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam String periodType,
            @RequestParam String periodLabel,
            @RequestParam(required = false) Long classId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        byte[] content = enrollmentMovementReportService.exportStatsExcelForRange(
                siteId, fromDate, toDate, classId, actor.userId(), periodType, periodLabel);
        return ExcelHttpResponses.attachment(content, "bien-dong-hoc-sinh-" + fromDate + "-" + toDate + ".xlsx");
    }
}
