package vn.com.pps.education.lms.controller;

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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.lms.dto.CommentAiDraftJobResponse;
import vn.com.pps.education.lms.dto.HomeworkScoreInput;
import vn.com.pps.education.lms.dto.ReviseCommentAiDraftRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.lms.service.CommentAiDraftService;

import java.util.List;

/**
 * UC-74: Trợ lý AI soạn nháp nhận xét hàng ngày từ audio — xem Javadoc CommentAiDraftService. Cùng quyền
 * với Lưu nháp của UC-21 ({@code academic.comment.write}); không có endpoint nào để trợ lý tự ghi DB.
 */
@RestController
public class CommentAiDraftController {

    private final CommentAiDraftService commentAiDraftService;

    public CommentAiDraftController(CommentAiDraftService commentAiDraftService) {
        this.commentAiDraftService = commentAiDraftService;
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.write')")
    @PostMapping(value = "/api/class-sessions/{classSessionId}/comments/ai-draft", consumes = "multipart/form-data")
    public ResponseEntity<CommentAiDraftJobResponse> startDraft(@PathVariable Long classSessionId,
                                                                @RequestParam(value = "audio", required = false) MultipartFile audio,
                                                                @RequestParam(value = "note", required = false) String note,
                                                                @RequestPart(value = "homeworkScores", required = false) List<HomeworkScoreInput> homeworkScores,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(commentAiDraftService.startDraft(classSessionId, audio, note, homeworkScores, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.write')")
    @PostMapping("/api/class-sessions/{classSessionId}/comments/ai-draft/revise")
    public ResponseEntity<CommentAiDraftJobResponse> revise(@PathVariable Long classSessionId,
                                                            @Valid @RequestBody ReviseCommentAiDraftRequest request,
                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(commentAiDraftService.startRevise(classSessionId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.comment.write')")
    @GetMapping("/api/comment-ai-drafts/{jobId}")
    public ResponseEntity<CommentAiDraftJobResponse> getJob(@PathVariable String jobId,
                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(commentAiDraftService.getJob(jobId, actor.userId()));
    }
}
