package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.GradeAnswerRequest;
import vn.com.pps.education.dto.PendingGradingResponse;
import vn.com.pps.education.dto.StudentAnswerGradingResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ManualGradingService;

import java.util.List;

/** UC-41: Chấm bài thủ công (FR-LMS-11) — xem Javadoc ManualGradingService. */
@RestController
public class ManualGradingController {

    private final ManualGradingService manualGradingService;

    public ManualGradingController(ManualGradingService manualGradingService) {
        this.manualGradingService = manualGradingService;
    }

    @GetMapping("/api/grading/pending")
    public ResponseEntity<List<PendingGradingResponse>> listPendingGrading(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(manualGradingService.listPendingGrading(actor.userId()));
    }

    @PostMapping("/api/answers/{studentAnswerId}/grade")
    public ResponseEntity<StudentAnswerGradingResponse> gradeAnswer(@PathVariable Long studentAnswerId,
                                                                      @Valid @RequestBody GradeAnswerRequest request,
                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(manualGradingService.gradeAnswer(studentAnswerId, request, actor.userId()));
    }
}
