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
import vn.com.pps.education.dto.CreateListeningPracticeItemRequest;
import vn.com.pps.education.dto.ListeningPracticeAttemptResponse;
import vn.com.pps.education.dto.ListeningPracticeItemResponse;
import vn.com.pps.education.dto.PauseListeningPracticeAttemptRequest;
import vn.com.pps.education.dto.SubmitListeningPracticeAttemptRequest;
import vn.com.pps.education.dto.UpdateListeningPracticeItemRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ListeningPracticeService;

import java.util.List;

/** UC-26: Luyện Nghe – Nói (FR-LMS-04) — xem Javadoc ListeningPracticeService. */
@RestController
public class ListeningPracticeController {

    private final ListeningPracticeService listeningPracticeService;

    public ListeningPracticeController(ListeningPracticeService listeningPracticeService) {
        this.listeningPracticeService = listeningPracticeService;
    }

    @PreAuthorize("hasPermission(null, 'lms.listening-practice.manage')")
    @PostMapping("/api/listening-practice-items")
    public ResponseEntity<ListeningPracticeItemResponse> createItem(@Valid @RequestBody CreateListeningPracticeItemRequest request,
                                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(listeningPracticeService.createItem(request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.listening-practice.manage')")
    @PutMapping("/api/listening-practice-items/{id}")
    public ResponseEntity<ListeningPracticeItemResponse> updateItem(@PathVariable Long id,
                                                                       @Valid @RequestBody UpdateListeningPracticeItemRequest request,
                                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(listeningPracticeService.updateItem(id, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.listening-practice.manage')")
    @GetMapping("/api/curriculums/{curriculumId}/listening-practice-items")
    public ResponseEntity<List<ListeningPracticeItemResponse>> listByCurriculum(@PathVariable Long curriculumId) {
        return ResponseEntity.ok(listeningPracticeService.listByCurriculum(curriculumId));
    }

    @PostMapping("/api/listening-practice-items/{id}/attempts")
    public ResponseEntity<ListeningPracticeAttemptResponse> startAttempt(@PathVariable Long id,
                                                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(listeningPracticeService.startAttempt(id, actor.userId()));
    }

    @PostMapping("/api/listening-practice-attempts/{id}/pause")
    public ResponseEntity<ListeningPracticeAttemptResponse> pauseAttempt(@PathVariable Long id,
                                                                           @Valid @RequestBody PauseListeningPracticeAttemptRequest request,
                                                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(listeningPracticeService.pauseAttempt(id, request.positionSeconds(), actor.userId()));
    }

    @PostMapping("/api/listening-practice-attempts/{id}/submit")
    public ResponseEntity<ListeningPracticeAttemptResponse> submitAttempt(@PathVariable Long id,
                                                                            @RequestBody SubmitListeningPracticeAttemptRequest request,
                                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(listeningPracticeService.submitAttempt(id, request, actor.userId()));
    }

    @GetMapping("/api/listening-practice-attempts/{id}")
    public ResponseEntity<ListeningPracticeAttemptResponse> getAttempt(@PathVariable Long id,
                                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(listeningPracticeService.getAttempt(id, actor.userId()));
    }
}
