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
import vn.com.pps.education.dto.SessionReportTimelineEvent;
import vn.com.pps.education.dto.SessionReportTrackingResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.SessionReportTrackingService;

import java.time.LocalDate;
import java.util.List;

/**
 * Trang "Tình hình nộp & duyệt báo cáo" (V207 — bổ sung ngoài SDD gốc, xác nhận với người dùng
 * 2026-10-01) — xem Javadoc SessionReportTrackingService.
 */
@RestController
public class SessionReportTrackingController {

    private final SessionReportTrackingService sessionReportTrackingService;

    public SessionReportTrackingController(SessionReportTrackingService sessionReportTrackingService) {
        this.sessionReportTrackingService = sessionReportTrackingService;
    }

    @PreAuthorize("hasPermission(null, 'report.session-report.view')")
    @GetMapping("/api/reports/session-reports")
    public ResponseEntity<SessionReportTrackingResponse> getTracking(
            @RequestParam(required = false) Long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(sessionReportTrackingService.getTracking(siteId, fromDate, toDate, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'report.session-report.view')")
    @GetMapping("/api/reports/session-reports/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        byte[] content = sessionReportTrackingService.exportExcel(siteId, fromDate, toDate, actor.userId());
        return ExcelHttpResponses.attachment(content, "nop-duyet-bao-cao-" + fromDate + "-" + toDate + ".xlsx");
    }

    @PreAuthorize("hasPermission(null, 'report.session-report.view')")
    @GetMapping("/api/reports/session-reports/sessions/{sessionId}/timeline")
    public ResponseEntity<List<SessionReportTimelineEvent>> getTimeline(@PathVariable Long sessionId,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(sessionReportTrackingService.getTimeline(sessionId, actor.userId()));
    }
}
