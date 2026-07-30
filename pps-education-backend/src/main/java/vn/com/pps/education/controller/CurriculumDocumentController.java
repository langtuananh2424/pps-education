package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateCurriculumDocumentRequest;
import vn.com.pps.education.dto.CurriculumDocumentResponse;
import vn.com.pps.education.dto.UpdateCurriculumDocumentRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.CurriculumDocumentService;

import java.util.List;

/** UC-60: Kho tài liệu tham khảo (FR-LMS-13) — xem Javadoc CurriculumDocumentService. */
@RestController
public class CurriculumDocumentController {

    private final CurriculumDocumentService curriculumDocumentService;

    public CurriculumDocumentController(CurriculumDocumentService curriculumDocumentService) {
        this.curriculumDocumentService = curriculumDocumentService;
    }

    @PreAuthorize("hasPermission(null, 'lms.document.create')")
    @PostMapping("/api/curriculums/{curriculumId}/documents")
    public ResponseEntity<CurriculumDocumentResponse> createDocument(@PathVariable Long curriculumId,
                                                                        @Valid @RequestBody CreateCurriculumDocumentRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        CreateCurriculumDocumentRequest merged = new CreateCurriculumDocumentRequest(
                curriculumId, request.title(), request.description(), request.documentType(),
                request.fileUrl(), request.displayOrder(), request.coverImageUrl());
        return ResponseEntity.ok(curriculumDocumentService.createDocument(merged, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.document.update')")
    @PutMapping("/api/documents/{id}")
    public ResponseEntity<CurriculumDocumentResponse> updateDocument(@PathVariable Long id,
                                                                        @Valid @RequestBody UpdateCurriculumDocumentRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(curriculumDocumentService.updateDocument(id, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.document.view')")
    @GetMapping("/api/curriculums/{curriculumId}/documents")
    public ResponseEntity<List<CurriculumDocumentResponse>> listByCurriculum(@PathVariable Long curriculumId) {
        return ResponseEntity.ok(curriculumDocumentService.listByCurriculum(curriculumId));
    }
}
