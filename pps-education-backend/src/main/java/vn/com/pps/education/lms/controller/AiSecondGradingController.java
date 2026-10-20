package vn.com.pps.education.lms.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.lms.dto.AiGradedClassSummaryResponse;
import vn.com.pps.education.lms.dto.AiGradedItemResponse;
import vn.com.pps.education.lms.dto.ReviewReflexStepRequest;
import vn.com.pps.education.lms.service.AiSecondGradingService;
import vn.com.pps.education.security.AuthenticatedUser;

import java.util.List;

/**
 * Chấm lần 2 các bài do AI chấm (bổ sung ngoài SDD gốc, xác nhận 2026-10-19) — xem Javadoc
 * {@link AiSecondGradingService}. Chấm lại câu ESSAY dùng endpoint sẵn có {@code POST /api/answers/{id}/grade}
 * (UC-41), không có endpoint riêng.
 */
@RestController
@PreAuthorize("hasPermission(null, 'lms.grading.manage')")
public class AiSecondGradingController {

    private final AiSecondGradingService aiSecondGradingService;

    public AiSecondGradingController(AiSecondGradingService aiSecondGradingService) {
        this.aiSecondGradingService = aiSecondGradingService;
    }

    @GetMapping("/api/grading/ai-graded/classes")
    public ResponseEntity<List<AiGradedClassSummaryResponse>> listClasses(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(aiSecondGradingService.listClasses(actor.userId()));
    }

    @GetMapping("/api/classes/{classId}/ai-graded-items")
    public ResponseEntity<List<AiGradedItemResponse>> listItems(@PathVariable Long classId,
                                                                @RequestParam(defaultValue = "false") boolean includeReviewed,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(aiSecondGradingService.listItems(classId, includeReviewed, actor.userId()));
    }

    @PostMapping("/api/reflex-progress/{progressId}/review/{step}")
    public ResponseEntity<AiGradedItemResponse> reviewReflexStep(@PathVariable Long progressId,
                                                                 @PathVariable AiSecondGradingService.ReflexStep step,
                                                                 @Valid @RequestBody ReviewReflexStepRequest request,
                                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(aiSecondGradingService.reviewReflexStep(progressId, step, request, actor.userId()));
    }
}
