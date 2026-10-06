package vn.com.pps.education.academic.controller;

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
import vn.com.pps.education.academic.dto.ApplyTermCommentAiDraftRequest;
import vn.com.pps.education.academic.dto.ReviseTermCommentAiDraftRequest;
import vn.com.pps.education.academic.dto.TermCommentAiDraftJobResponse;
import vn.com.pps.education.academic.service.TermCommentAiDraftService;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.student.dto.GradeEvaluationResultResponse;

import java.util.List;

/**
 * UC-76: Trợ lý AI soạn nháp Nhận xét Giữa kỳ/Cuối kỳ — xem Javadoc TermCommentAiDraftService. Cùng quyền với nhập điểm
 * của UC-19 ({@code academic.grade.entry} hoặc {@code academic.grade.edit.override}); soạn/sửa nháp không ghi DB, chỉ
 * endpoint "Áp dụng" ghi Nhận xét vào sổ điểm do chính giáo viên bấm. Soạn/sửa nhận multipart để giáo viên gửi kèm audio
 * (bổ sung 2026-10-06 — sidebar trò chuyện như trợ lý UC-74).
 */
@RestController
public class TermCommentAiDraftController {

    private final TermCommentAiDraftService termCommentAiDraftService;

    public TermCommentAiDraftController(TermCommentAiDraftService termCommentAiDraftService) {
        this.termCommentAiDraftService = termCommentAiDraftService;
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.entry') or hasPermission(null, 'academic.grade.edit.override')")
    @PostMapping(value = "/api/classes/{classId}/grade-component-setups/{setupId}/comments/ai-draft", consumes = "multipart/form-data")
    public ResponseEntity<TermCommentAiDraftJobResponse> startDraft(@PathVariable Long classId, @PathVariable Long setupId,
                                                                    @RequestParam(value = "audio", required = false) MultipartFile audio,
                                                                    @RequestParam(value = "instruction", required = false) String instruction,
                                                                    @RequestParam(value = "studentIds", required = false) List<Long> studentIds,
                                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(termCommentAiDraftService.startDraft(classId, setupId, audio, instruction, studentIds, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.entry') or hasPermission(null, 'academic.grade.edit.override')")
    @PostMapping(value = "/api/classes/{classId}/grade-component-setups/{setupId}/comments/ai-draft/revise", consumes = "multipart/form-data")
    public ResponseEntity<TermCommentAiDraftJobResponse> revise(@PathVariable Long classId, @PathVariable Long setupId,
                                                                @RequestParam(value = "audio", required = false) MultipartFile audio,
                                                                @RequestParam(value = "instruction", required = false) String instruction,
                                                                @Valid @RequestPart("request") ReviseTermCommentAiDraftRequest request,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(termCommentAiDraftService.startRevise(classId, setupId, audio, instruction, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.entry') or hasPermission(null, 'academic.grade.edit.override')")
    @GetMapping("/api/term-comment-ai-drafts/{jobId}")
    public ResponseEntity<TermCommentAiDraftJobResponse> getJob(@PathVariable String jobId,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(termCommentAiDraftService.getJob(jobId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.entry') or hasPermission(null, 'academic.grade.edit.override')")
    @PostMapping("/api/classes/{classId}/grade-component-setups/{setupId}/comments/ai-draft/apply")
    public ResponseEntity<List<GradeEvaluationResultResponse>> apply(@PathVariable Long classId, @PathVariable Long setupId,
                                                                     @Valid @RequestBody ApplyTermCommentAiDraftRequest request,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(termCommentAiDraftService.apply(classId, setupId, request, actor.userId()));
    }
}
