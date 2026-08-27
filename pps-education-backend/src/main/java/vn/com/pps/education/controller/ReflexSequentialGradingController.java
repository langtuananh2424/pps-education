package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.ReflexQuestionProgressResponse;
import vn.com.pps.education.dto.SubmitReflexSpokenAnswerRequest;
import vn.com.pps.education.dto.SubmitReflexWrittenAnswerRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ReflexSequentialGradingService;

import java.util.List;

/**
 * UC-23b V2 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22) — Video phản xạ, luồng
 * tuần tự viết → AI chấm ngữ pháp → đạt → ghi âm → AI chấm nội dung → đạt → mở câu tiếp theo. Xem
 * Javadoc {@link ReflexSequentialGradingService}. Tách endpoint riêng khỏi
 * {@code /api/review-video-questions/{id}/submissions} (luồng cũ, {@link ReviewVideoController}) —
 * 2 cơ chế khác nhau hoàn toàn (nộp từng bước có chấm ngay vs nộp cả loạt cuối video).
 */
@RestController
public class ReflexSequentialGradingController {

    private final ReflexSequentialGradingService reflexSequentialGradingService;

    public ReflexSequentialGradingController(ReflexSequentialGradingService reflexSequentialGradingService) {
        this.reflexSequentialGradingService = reflexSequentialGradingService;
    }

    @PutMapping("/api/review-video-questions/{questionId}/reflex-progress/writing")
    public ResponseEntity<ReflexQuestionProgressResponse> submitWrittenAnswer(@PathVariable Long questionId,
                                                                                @RequestParam Long assignmentId,
                                                                                @Valid @RequestBody SubmitReflexWrittenAnswerRequest request,
                                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reflexSequentialGradingService.submitWrittenAnswer(questionId, assignmentId, request.answerText(), actor.userId()));
    }

    @PutMapping("/api/review-video-questions/{questionId}/reflex-progress/speaking")
    public ResponseEntity<ReflexQuestionProgressResponse> submitSpokenAnswer(@PathVariable Long questionId,
                                                                               @RequestParam Long assignmentId,
                                                                               @Valid @RequestBody SubmitReflexSpokenAnswerRequest request,
                                                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reflexSequentialGradingService.submitSpokenAnswer(questionId, assignmentId, request.audioUrl(), actor.userId()));
    }

    @GetMapping("/api/review-video-assignments/{assignmentId}/reflex-progress")
    public ResponseEntity<List<ReflexQuestionProgressResponse>> listMyProgress(@PathVariable Long assignmentId,
                                                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reflexSequentialGradingService.listMyProgress(assignmentId, actor.userId()));
    }
}
