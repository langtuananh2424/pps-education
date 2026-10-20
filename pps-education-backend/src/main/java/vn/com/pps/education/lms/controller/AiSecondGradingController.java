package vn.com.pps.education.lms.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.lms.dto.EssayGradingHistoryResponse;
import vn.com.pps.education.lms.dto.ReviewReflexStepRequest;
import vn.com.pps.education.lms.service.AiSecondGradingService;
import vn.com.pps.education.lms.service.EssayGradingHistoryService;
import vn.com.pps.education.security.AuthenticatedUser;

import java.util.List;

/** Chấm lần 2 bài Video phản xạ do AI chấm (bổ sung ngoài SDD gốc, xác nhận 2026-10-19) — xem Javadoc {@link AiSecondGradingService}. */
@RestController
@PreAuthorize("hasPermission(null, 'lms.grading.manage')")
public class AiSecondGradingController {

    private final AiSecondGradingService aiSecondGradingService;
    private final EssayGradingHistoryService essayGradingHistoryService;

    public AiSecondGradingController(AiSecondGradingService aiSecondGradingService,
                                     EssayGradingHistoryService essayGradingHistoryService) {
        this.aiSecondGradingService = aiSecondGradingService;
        this.essayGradingHistoryService = essayGradingHistoryService;
    }

    /** Lịch sử bản chấm (AI + giáo viên) các câu ESSAY của 1 học sinh trong 1 bản giao BTVN; chấm lại qua POST /api/answers/{id}/grade. */
    @GetMapping("/api/exercise-assignments/{assignmentId}/students/{studentId}/essay-grading-history")
    public ResponseEntity<List<EssayGradingHistoryResponse>> getEssayGradingHistory(@PathVariable Long assignmentId,
                                                                                    @PathVariable Long studentId,
                                                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(essayGradingHistoryService.getStudentEssayHistory(assignmentId, studentId, actor.userId()));
    }

    @PostMapping("/api/reflex-progress/{progressId}/review/{step}")
    public ResponseEntity<Void> reviewReflexStep(@PathVariable Long progressId,
                                                 @PathVariable AiSecondGradingService.ReflexStep step,
                                                 @Valid @RequestBody ReviewReflexStepRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        aiSecondGradingService.reviewReflexStep(progressId, step, request, actor.userId());
        return ResponseEntity.noContent().build();
    }
}
