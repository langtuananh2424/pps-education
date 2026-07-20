package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.GradeListeningAttemptRequest;
import vn.com.pps.education.dto.ListeningPracticeGradingResponse;
import vn.com.pps.education.dto.PendingListeningGradingResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ListeningPracticeGradingService;

import java.util.List;

/**
 * UC-26: Chấm thủ công lượt luyện Nói — hàng chờ TÁCH RIÊNG khỏi
 * ManualGradingController (UC-41), xem Javadoc ListeningPracticeGradingService.
 */
@RestController
@PreAuthorize("hasPermission(null, 'lms.grading.manage')")
public class ListeningPracticeGradingController {

    private final ListeningPracticeGradingService listeningPracticeGradingService;

    public ListeningPracticeGradingController(ListeningPracticeGradingService listeningPracticeGradingService) {
        this.listeningPracticeGradingService = listeningPracticeGradingService;
    }

    @GetMapping("/api/listening-practice/grading/pending")
    public ResponseEntity<List<PendingListeningGradingResponse>> listPendingGrading() {
        return ResponseEntity.ok(listeningPracticeGradingService.listPendingGrading());
    }

    @PostMapping("/api/listening-practice-attempts/{id}/grade")
    public ResponseEntity<ListeningPracticeGradingResponse> gradeAttempt(@PathVariable Long id,
                                                                           @Valid @RequestBody GradeListeningAttemptRequest request,
                                                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(listeningPracticeGradingService.gradeAttempt(id, request, actor.userId()));
    }
}
