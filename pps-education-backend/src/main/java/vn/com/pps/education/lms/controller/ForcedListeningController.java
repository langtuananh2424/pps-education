package vn.com.pps.education.lms.controller;

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
import vn.com.pps.education.lms.dto.ExerciseSectionRequest;
import vn.com.pps.education.lms.dto.ExerciseSectionResponse;
import vn.com.pps.education.lms.dto.ForcedListeningImportResponse;
import vn.com.pps.education.lms.dto.SectionResultsResponse;
import vn.com.pps.education.lms.dto.TapescriptViewResponse;
import vn.com.pps.education.lms.service.ForcedListeningImportService;
import vn.com.pps.education.lms.service.ForcedListeningService;
import vn.com.pps.education.security.AuthenticatedUser;

import java.util.List;

/** V226 (Nghe ép, đã xác nhận với người dùng 2026-11-09) — bài tập con (UC-40) và bảng điểm/tapescript (UC-24/UC-27), xem Javadoc ForcedListeningService. */
@RestController
public class ForcedListeningController {

    private final ForcedListeningService forcedListeningService;
    private final ForcedListeningImportService forcedListeningImportService;

    public ForcedListeningController(ForcedListeningService forcedListeningService,
                                     ForcedListeningImportService forcedListeningImportService) {
        this.forcedListeningService = forcedListeningService;
        this.forcedListeningImportService = forcedListeningImportService;
    }

    /** Import Excel riêng cho Bài Nghe ép — xem Javadoc ForcedListeningImportService. */
    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @PostMapping(value = "/api/exercises/{id}/forced-listening/import", consumes = "multipart/form-data")
    public ResponseEntity<ForcedListeningImportResponse> importExcel(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(forcedListeningImportService.importExcel(id, file, actor.userId()));
    }

    @GetMapping("/api/exercises/{id}/sections")
    public ResponseEntity<List<ExerciseSectionResponse>> listSections(@PathVariable Long id) {
        return ResponseEntity.ok(forcedListeningService.listSections(id));
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @PostMapping("/api/exercises/{id}/sections")
    public ResponseEntity<ExerciseSectionResponse> createSection(@PathVariable Long id,
                                                                  @Valid @RequestBody ExerciseSectionRequest request) {
        return ResponseEntity.ok(forcedListeningService.createSection(id, request));
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @PutMapping("/api/exercises/{id}/sections/{sectionId}")
    public ResponseEntity<ExerciseSectionResponse> updateSection(@PathVariable Long id, @PathVariable Long sectionId,
                                                                  @Valid @RequestBody ExerciseSectionRequest request) {
        return ResponseEntity.ok(forcedListeningService.updateSection(id, sectionId, request));
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @DeleteMapping("/api/exercises/{id}/sections/{sectionId}")
    public ResponseEntity<Void> deleteSection(@PathVariable Long id, @PathVariable Long sectionId) {
        forcedListeningService.deleteSection(id, sectionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/attempts/{id}/section-results")
    public ResponseEntity<SectionResultsResponse> getSectionResults(@PathVariable Long id,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(forcedListeningService.getSectionResults(id, actor.userId()));
    }

    @PostMapping("/api/attempts/{id}/tapescripts/{questionId}/views")
    public ResponseEntity<TapescriptViewResponse> viewTapescript(@PathVariable Long id, @PathVariable Long questionId,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(forcedListeningService.viewTapescript(id, questionId, actor.userId()));
    }
}
