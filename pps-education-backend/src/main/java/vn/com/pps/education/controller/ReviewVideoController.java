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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AddReviewVideoConnectionQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.GradeReviewVideoSubmissionRequest;
import vn.com.pps.education.dto.ReportVideoProgressRequest;
import vn.com.pps.education.dto.ReviewVideoAssignmentResponse;
import vn.com.pps.education.dto.ReviewVideoConnectionQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoConnectionQuizResultResponse;
import vn.com.pps.education.dto.ReviewVideoProgressResponse;
import vn.com.pps.education.dto.ReviewVideoQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoResponse;
import vn.com.pps.education.dto.ReviewVideoSetResponse;
import vn.com.pps.education.dto.ReviewVideoSetStatsResponse;
import vn.com.pps.education.dto.ReviewVideoSubmissionResponse;
import vn.com.pps.education.dto.StartWatchSessionResponse;
import vn.com.pps.education.dto.SubmitConnectionAnswersRequest;
import vn.com.pps.education.dto.SubmitReviewVideoAudioRequest;
import vn.com.pps.education.dto.UpdateReviewVideoSetRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ReviewVideoService;

import java.util.List;

/**
 * UC-23/UC-23a: Kho Video Ôn tập (FR-LMS-01) — xem Javadoc ReviewVideoService.
 *
 * Permission (V63, bổ sung ngoài SDD gốc — đã xác nhận với người dùng
 * 2026-07-30): chỉ gate thao tác quản lý của Giáo viên
 * (lms.review-video.create/update/view + lms.grading.manage cho chấm
 * điểm). Các endpoint GET dùng chung Học sinh không gate permission.
 * requireAssignedTeacher trong Service vẫn là lớp kiểm soát chính theo
 * Precondition UC-23 — permission chỉ là lớp bổ sung, không thay thế.
 */
@RestController
public class ReviewVideoController {

    private final ReviewVideoService reviewVideoService;

    public ReviewVideoController(ReviewVideoService reviewVideoService) {
        this.reviewVideoService = reviewVideoService;
    }

    @PreAuthorize("hasPermission(null, 'lms.review-video.create')")
    @PostMapping("/api/review-video-sets")
    public ResponseEntity<ReviewVideoSetResponse> createSet(@Valid @RequestBody CreateReviewVideoSetRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.createSet(request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.review-video.update')")
    @PutMapping("/api/review-video-sets/{id}")
    public ResponseEntity<ReviewVideoSetResponse> updateSet(@PathVariable Long id,
                                                              @Valid @RequestBody UpdateReviewVideoSetRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.updateSet(id, request, actor.userId()));
    }

    @GetMapping("/api/classes/{classId}/review-video-sets")
    public ResponseEntity<List<ReviewVideoSetResponse>> listByClass(@PathVariable Long classId,
                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listByClass(classId, actor.userId()));
    }

    @GetMapping("/api/curriculums/{curriculumId}/review-video-sets")
    public ResponseEntity<List<ReviewVideoSetResponse>> listByCurriculum(@PathVariable Long curriculumId,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listByCurriculum(curriculumId, actor.userId()));
    }

    /** V65 — GV xem bản giao ACTIVE của lớp (tra ngược id bản giao → ReviewVideoSet cho FE Nhận xét học viên). */
    @GetMapping("/api/classes/{classId}/review-video-assignments")
    public ResponseEntity<List<ReviewVideoAssignmentResponse>> listAssignmentsForClass(@PathVariable Long classId,
                                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listAssignmentsForClass(classId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.review-video.update')")
    @PostMapping("/api/review-video-sets/{setId}/videos")
    public ResponseEntity<ReviewVideoResponse> addVideo(@PathVariable Long setId,
                                                          @Valid @RequestBody AddReviewVideoRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.addVideo(setId, request, actor.userId()));
    }

    @GetMapping("/api/review-video-sets/{setId}/videos")
    public ResponseEntity<List<ReviewVideoResponse>> listVideos(@PathVariable Long setId,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listVideos(setId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.review-video.update')")
    @PostMapping("/api/review-videos/{videoId}/questions")
    public ResponseEntity<ReviewVideoQuestionResponse> addQuestion(@PathVariable Long videoId,
                                                                      @Valid @RequestBody AddReviewVideoQuestionRequest request,
                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.addQuestion(videoId, request, actor.userId()));
    }

    @GetMapping("/api/review-videos/{videoId}/questions")
    public ResponseEntity<List<ReviewVideoQuestionResponse>> listQuestions(@PathVariable Long videoId,
                                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listQuestions(videoId, actor.userId()));
    }

    /** V76 — mirror addQuestion/listQuestions của REFLEX, dành cho câu hỏi trắc nghiệm của video CONNECTION. */
    @PreAuthorize("hasPermission(null, 'lms.review-video.update')")
    @PostMapping("/api/review-videos/{videoId}/connection-questions")
    public ResponseEntity<ReviewVideoConnectionQuestionResponse> addConnectionQuestion(
            @PathVariable Long videoId, @Valid @RequestBody AddReviewVideoConnectionQuestionRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.addConnectionQuestion(videoId, request, actor.userId()));
    }

    @GetMapping("/api/review-videos/{videoId}/connection-questions")
    public ResponseEntity<List<ReviewVideoConnectionQuestionResponse>> listConnectionQuestions(
            @PathVariable Long videoId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listConnectionQuestions(videoId, actor.userId()));
    }

    @PostMapping("/api/review-videos/{videoId}/watch-sessions")
    public ResponseEntity<StartWatchSessionResponse> startWatchSession(@PathVariable Long videoId,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.startWatchSession(videoId, actor.userId()));
    }

    @PutMapping("/api/review-videos/{videoId}/progress")
    public ResponseEntity<ReviewVideoProgressResponse> reportProgress(@PathVariable Long videoId,
                                                                        @Valid @RequestBody ReportVideoProgressRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.reportProgress(videoId, request, actor.userId()));
    }

    /** V76 — học sinh nộp toàn bộ câu trả lời trắc nghiệm cho 1 lượt xem cụ thể (khớp cặp 1-1 qua watchSessionId). */
    @PutMapping("/api/review-video-watch-sessions/{watchSessionId}/connection-answers")
    public ResponseEntity<ReviewVideoConnectionQuizResultResponse> submitConnectionAnswers(
            @PathVariable Long watchSessionId, @Valid @RequestBody SubmitConnectionAnswersRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.submitConnectionAnswers(watchSessionId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.review-video.view')")
    @GetMapping("/api/review-video-sets/{setId}/stats")
    public ResponseEntity<ReviewVideoSetStatsResponse> getStats(@PathVariable Long setId,
                                                                  @RequestParam(required = false) Long classId,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.getStats(setId, classId, actor.userId()));
    }

    @PutMapping("/api/review-video-questions/{questionId}/submissions")
    public ResponseEntity<ReviewVideoSubmissionResponse> submitQuestionAudio(@PathVariable Long questionId,
                                                                        @Valid @RequestBody SubmitReviewVideoAudioRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.submitQuestionAudio(questionId, request, actor.userId()));
    }

    @GetMapping("/api/review-video-questions/{questionId}/submissions/latest")
    public ResponseEntity<ReviewVideoSubmissionResponse> getMyLatestSubmission(@PathVariable Long questionId,
                                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        ReviewVideoSubmissionResponse response = reviewVideoService.getMyLatestSubmission(questionId, actor.userId());
        return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
    }

    @GetMapping("/api/review-video-questions/{questionId}/submissions/history")
    public ResponseEntity<List<ReviewVideoSubmissionResponse>> listMySubmissionHistory(@PathVariable Long questionId,
                                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listMySubmissionHistory(questionId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.grading.manage')")
    @GetMapping("/api/review-video-sets/{setId}/submissions")
    public ResponseEntity<List<ReviewVideoSubmissionResponse>> listSubmissions(@PathVariable Long setId,
                                                                                  @RequestParam(required = false) Long classId,
                                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listSubmissionsForTeacher(setId, classId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.grading.manage')")
    @PostMapping("/api/review-video-submissions/{submissionId}/grade")
    public ResponseEntity<ReviewVideoSubmissionResponse> gradeSubmission(@PathVariable Long submissionId,
                                                                            @Valid @RequestBody GradeReviewVideoSubmissionRequest request,
                                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.gradeSubmission(submissionId, request, actor.userId()));
    }
}
