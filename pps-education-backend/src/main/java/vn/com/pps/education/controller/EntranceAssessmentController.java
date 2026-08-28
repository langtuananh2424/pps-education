package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.com.pps.education.dto.*;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.EntranceAssessmentResultService;
import vn.com.pps.education.service.EntranceAssessmentSetupService;

import java.util.List;

/**
 * UC-18c: Đánh giá đầu vào & đề xuất xếp lớp (bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng 2026-08-28). Xem Javadoc
 * {@link EntranceAssessmentSetupService} / {@link EntranceAssessmentResultService}.
 */
@RestController
public class EntranceAssessmentController {

    private final EntranceAssessmentSetupService setupService;
    private final EntranceAssessmentResultService resultService;

    public EntranceAssessmentController(EntranceAssessmentSetupService setupService,
                                        EntranceAssessmentResultService resultService) {
        this.setupService = setupService;
        this.resultService = resultService;
    }

    // ===================== Bộ đề =====================

    @GetMapping("/api/entrance-assessment-setups")
    public ResponseEntity<List<EntranceAssessmentSetupResponse>> listSetups(@RequestParam Long siteId,
                                                                           @RequestParam(required = false) Long academicYearId) {
        return ResponseEntity.ok(setupService.listSetups(siteId, academicYearId));
    }

    @GetMapping("/api/entrance-assessment-setups/{id}")
    public ResponseEntity<EntranceAssessmentSetupResponse> getSetup(@PathVariable Long id) {
        return ResponseEntity.ok(setupService.getSetup(id));
    }

    @PreAuthorize("hasPermission(null, 'academic.entrance.setup.create')")
    @PostMapping("/api/entrance-assessment-setups")
    public ResponseEntity<EntranceAssessmentSetupResponse> createSetup(@Valid @RequestBody CreateEntranceAssessmentSetupRequest request,
                                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(setupService.createSetup(request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.entrance.setup.update')")
    @PutMapping("/api/entrance-assessment-setups/{id}")
    public ResponseEntity<EntranceAssessmentSetupResponse> updateSetup(@PathVariable Long id,
                                                                       @Valid @RequestBody UpdateEntranceAssessmentSetupRequest request) {
        return ResponseEntity.ok(setupService.updateSetup(id, request));
    }

    @PreAuthorize("hasPermission(null, 'academic.entrance.setup.delete')")
    @DeleteMapping("/api/entrance-assessment-setups/{id}")
    public ResponseEntity<Void> deleteSetup(@PathVariable Long id) {
        setupService.deleteSetup(id);
        return ResponseEntity.noContent().build();
    }

    // ===================== Đầu điểm =====================

    @PreAuthorize("hasPermission(null, 'academic.entrance.setup.update')")
    @PostMapping("/api/entrance-assessment-setups/{setupId}/components")
    public ResponseEntity<EntranceAssessmentComponentResponse> addComponent(@PathVariable Long setupId,
                                                                            @Valid @RequestBody CreateEntranceAssessmentComponentRequest request) {
        return ResponseEntity.ok(setupService.addComponent(setupId, request));
    }

    @PreAuthorize("hasPermission(null, 'academic.entrance.setup.update')")
    @PutMapping("/api/entrance-assessment-components/{id}")
    public ResponseEntity<EntranceAssessmentComponentResponse> updateComponent(@PathVariable Long id,
                                                                               @Valid @RequestBody UpdateEntranceAssessmentComponentRequest request) {
        return ResponseEntity.ok(setupService.updateComponent(id, request));
    }

    @PreAuthorize("hasPermission(null, 'academic.entrance.setup.delete')")
    @DeleteMapping("/api/entrance-assessment-components/{id}")
    public ResponseEntity<Void> deleteComponent(@PathVariable Long id) {
        setupService.deleteComponent(id);
        return ResponseEntity.noContent().build();
    }

    // ===================== Kết quả thí sinh =====================

    @GetMapping("/api/entrance-assessment-setups/{setupId}/results")
    public ResponseEntity<List<EntranceAssessmentResultResponse>> listResults(@PathVariable Long setupId) {
        return ResponseEntity.ok(resultService.listResults(setupId));
    }

    @PreAuthorize("hasPermission(null, 'academic.entrance.score.manage')")
    @PostMapping("/api/entrance-assessment-setups/{setupId}/results")
    public ResponseEntity<EntranceAssessmentResultResponse> upsertResult(@PathVariable Long setupId,
                                                                         @Valid @RequestBody UpsertEntranceAssessmentResultRequest request,
                                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(resultService.upsertResult(setupId, request, actor.userId()));
    }

    @GetMapping("/api/entrance-assessment-results/{id}")
    public ResponseEntity<EntranceAssessmentResultResponse> getResult(@PathVariable Long id) {
        return ResponseEntity.ok(resultService.getResult(id));
    }

    @PreAuthorize("hasPermission(null, 'academic.entrance.score.manage')")
    @DeleteMapping("/api/entrance-assessment-results/{id}")
    public ResponseEntity<Void> deleteResult(@PathVariable Long id) {
        resultService.deleteResult(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasPermission(null, 'academic.entrance.score.manage')")
    @PostMapping("/api/entrance-assessment-results/{id}/mark-placed")
    public ResponseEntity<EntranceAssessmentResultResponse> markPlaced(@PathVariable Long id) {
        return ResponseEntity.ok(resultService.markPlaced(id));
    }
}
