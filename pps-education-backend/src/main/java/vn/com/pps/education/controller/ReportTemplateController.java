package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.dto.AvailableReportFieldResponse;
import vn.com.pps.education.dto.CreateReportTemplateRequest;
import vn.com.pps.education.dto.ReportTemplateResponse;
import vn.com.pps.education.dto.UpdateFieldMappingsRequest;
import vn.com.pps.education.dto.UpdateReportTemplateRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ReportTemplateService;

import java.util.List;
import java.util.Map;

/** UC-67: Quản lý mẫu báo cáo (FR-ACA-08) — xem Javadoc ReportTemplateService. */
@RestController
public class ReportTemplateController {

    private final ReportTemplateService reportTemplateService;

    public ReportTemplateController(ReportTemplateService reportTemplateService) {
        this.reportTemplateService = reportTemplateService;
    }

    @PreAuthorize("hasPermission(null, 'report.template.create')")
    @PostMapping(value = "/api/report-templates", consumes = "multipart/form-data")
    public ResponseEntity<ReportTemplateResponse> create(@RequestParam("file") MultipartFile file,
                                                            @RequestParam String name,
                                                            @RequestParam String templateType,
                                                            @RequestParam(required = false) String description,
                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        CreateReportTemplateRequest request = new CreateReportTemplateRequest(name, templateType, description);
        return ResponseEntity.ok(reportTemplateService.createTemplate(file, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'report.template.update')")
    @PutMapping("/api/report-templates/{id}")
    public ResponseEntity<ReportTemplateResponse> update(@PathVariable Long id,
                                                            @Valid @RequestBody UpdateReportTemplateRequest request,
                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reportTemplateService.updateTemplate(id, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'report.template.update')")
    @PutMapping("/api/report-templates/{id}/field-mappings")
    public ResponseEntity<ReportTemplateResponse> updateFieldMappings(@PathVariable Long id,
                                                                         @Valid @RequestBody UpdateFieldMappingsRequest request,
                                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reportTemplateService.updateFieldMappings(id, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'report.template.delete')")
    @DeleteMapping("/api/report-templates/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        reportTemplateService.deleteTemplate(id, actor.userId());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasPermission(null, 'report.template.view')")
    @GetMapping("/api/report-templates/{id}")
    public ResponseEntity<ReportTemplateResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(reportTemplateService.getById(id));
    }

    @PreAuthorize("hasPermission(null, 'report.template.view')")
    @GetMapping("/api/report-templates")
    public ResponseEntity<List<ReportTemplateResponse>> list(@RequestParam(required = false) String templateType) {
        return ResponseEntity.ok(reportTemplateService.list(templateType));
    }

    @PreAuthorize("hasPermission(null, 'report.template.view')")
    @GetMapping("/api/report-templates/available-fields")
    public ResponseEntity<Map<String, List<vn.com.pps.education.dto.AvailableReportFieldResponse>>> getAvailableFields() {
        return ResponseEntity.ok(reportTemplateService.getAvailableFields());
    }
}
