package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.common.ExcelHttpResponses;
import vn.com.pps.education.dto.EnrollmentMovementStatsResponse;
import vn.com.pps.education.dto.EnrollmentMovementTrendResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.EnrollmentMovementReportService;

/** UC-69: Thống kê biến động học sinh các lớp theo kỳ (FR-ACA-09) — xem Javadoc EnrollmentMovementReportService. */
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
}
