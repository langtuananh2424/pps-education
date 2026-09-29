package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.dto.CommentAiInstructionJobResponse;
import vn.com.pps.education.dto.CommentAiRejectionReasonJobResponse;
import vn.com.pps.education.dto.CommentAiReviewJobResponse;
import vn.com.pps.education.dto.CommentAiReviewRequest;
import vn.com.pps.education.dto.CommentAiSuggestionJobResponse;
import vn.com.pps.education.dto.CommentAiSuggestionRequest;
import vn.com.pps.education.dto.CommentAttitudeAlertPreviewResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.CommentAiReviewService;

import java.util.List;

/**
 * UC-75: Trợ lý AI soát nhận xét chờ duyệt — xem Javadoc CommentAiReviewService. Cùng quyền với duyệt nhận xét
 * của UC-22 ({@code academic.comment.approve}); không có endpoint nào để trợ lý tự duyệt/từ chối/sửa.
 */
@RestController
public class CommentAiReviewController {

    private final CommentAiReviewService commentAiReviewService;

    public CommentAiReviewController(CommentAiReviewService commentAiReviewService) {
        this.commentAiReviewService = commentAiReviewService;
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.approve')")
    @PostMapping("/api/comments/ai-review")
    public ResponseEntity<CommentAiReviewJobResponse> startReview(@Valid @RequestBody CommentAiReviewRequest request,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(commentAiReviewService.startReview(request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.approve')")
    @GetMapping("/api/comment-ai-reviews/{jobId}")
    public ResponseEntity<CommentAiReviewJobResponse> getReview(@PathVariable String jobId,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(commentAiReviewService.getReview(jobId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.approve')")
    @PostMapping("/api/comments/{id}/ai-suggestion")
    public ResponseEntity<CommentAiSuggestionJobResponse> startSuggestion(@PathVariable Long id,
                                                                          @Valid @RequestBody(required = false) CommentAiSuggestionRequest request,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(commentAiReviewService.startSuggestion(id, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.approve')")
    @GetMapping("/api/comment-ai-suggestions/{jobId}")
    public ResponseEntity<CommentAiSuggestionJobResponse> getSuggestion(@PathVariable String jobId,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(commentAiReviewService.getSuggestion(jobId, actor.userId()));
    }

    /** UC-75 bước 9 — yêu cầu sửa bằng audio (multipart "audio") và/hoặc chữ ("note") cho các nhận xét chờ duyệt. */
    @PreAuthorize("hasPermission(null, 'academic.comment.approve')")
    @PostMapping(value = "/api/comments/ai-instruction", consumes = "multipart/form-data")
    public ResponseEntity<CommentAiInstructionJobResponse> startInstruction(@RequestParam("commentIds") List<Long> commentIds,
                                                                            @RequestParam(value = "audio", required = false) MultipartFile audio,
                                                                            @RequestParam(value = "note", required = false) String note,
                                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(commentAiReviewService.startInstruction(commentIds, audio, note, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.approve')")
    @GetMapping("/api/comment-ai-instructions/{jobId}")
    public ResponseEntity<CommentAiInstructionJobResponse> getInstruction(@PathVariable String jobId,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(commentAiReviewService.getInstruction(jobId, actor.userId()));
    }

    /** UC-75 (bổ sung 2026-09-29) — dòng nào duyệt sẽ gửi cảnh báo thái độ cho phụ huynh; chỉ đọc, không gọi AI. */
    @PreAuthorize("hasPermission(null, 'academic.comment.approve')")
    @PostMapping("/api/comments/attitude-alert-preview")
    public ResponseEntity<CommentAttitudeAlertPreviewResponse> previewAttitudeAlerts(@Valid @RequestBody CommentAiReviewRequest request,
                                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(commentAiReviewService.previewAttitudeAlerts(request, actor.userId()));
    }

    /** UC-75 (bổ sung 2026-09-29) — AI soạn sẵn lý do từ chối; Quản lý sửa rồi tự bấm Từ chối (UC-22). */
    @PreAuthorize("hasPermission(null, 'academic.comment.approve')")
    @PostMapping("/api/comments/{id}/ai-rejection-reason")
    public ResponseEntity<CommentAiRejectionReasonJobResponse> startRejectionReason(@PathVariable Long id,
                                                                                    @Valid @RequestBody(required = false) CommentAiSuggestionRequest request,
                                                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(commentAiReviewService.startRejectionReason(id, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.approve')")
    @GetMapping("/api/comment-ai-rejection-reasons/{jobId}")
    public ResponseEntity<CommentAiRejectionReasonJobResponse> getRejectionReason(@PathVariable String jobId,
                                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(commentAiReviewService.getRejectionReason(jobId, actor.userId()));
    }
}
