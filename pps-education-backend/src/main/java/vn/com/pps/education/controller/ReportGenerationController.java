package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.GenerateReportRequest;
import vn.com.pps.education.dto.GeneratedReportResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ReportGenerationService;

/** UC-68: Xuất báo cáo từ mẫu (FR-ACA-08) — xem Javadoc ReportGenerationService. */
@RestController
public class ReportGenerationController {

    private final ReportGenerationService reportGenerationService;

    public ReportGenerationController(ReportGenerationService reportGenerationService) {
        this.reportGenerationService = reportGenerationService;
    }

    @PreAuthorize("hasPermission(null, 'report.generate')")
    @PostMapping("/api/reports/generate")
    public ResponseEntity<GeneratedReportResponse> generate(@RequestBody GenerateReportRequest request,
                                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        GeneratedReportResponse response = switch (request.scope()) {
            case "SINGLE" -> reportGenerationService.generateSingle(
                    request.templateId(), request.studentId(), request.classId(), request.periods(),
                    request.classSessionId(), request.outputFormat(), actor.userId());
            case "BULK_CLASS" -> reportGenerationService.generateBulkByClass(
                    request.templateId(), request.classId(), request.periods(),
                    request.classSessionId(), request.outputFormat(), actor.userId());
            case "CLASS_SESSION" -> reportGenerationService.generateClassSession(
                    request.templateId(), request.classSessionId(), request.outputFormat(), actor.userId());
            default -> throw new IllegalArgumentException(
                    "scope không hợp lệ: " + request.scope() + ". Giá trị hợp lệ: SINGLE, BULK_CLASS, CLASS_SESSION");
        };
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasPermission(null, 'report.generate')")
    @org.springframework.web.bind.annotation.GetMapping("/api/reports/download/{id}")
    public ResponseEntity<byte[]> download(@org.springframework.web.bind.annotation.PathVariable Long id) {
        return reportGenerationService.downloadReportFile(id);
    }
}
