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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.dto.AddReviewVideoConnectionQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.GradeReviewVideoSubmissionRequest;
import vn.com.pps.education.dto.PendingGradingClassSummaryResponse;
import vn.com.pps.education.dto.ReportVideoProgressRequest;
import vn.com.pps.education.dto.ReviewVideoAssignmentResponse;
import vn.com.pps.education.dto.ReviewVideoAssignmentStatsResponse;
import vn.com.pps.education.dto.ReviewVideoConnectionQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoConnectionQuizResultResponse;
import vn.com.pps.education.dto.ReviewVideoProgressResponse;
import vn.com.pps.education.dto.ReviewVideoQuestionImportResponse;
import vn.com.pps.education.dto.ReviewVideoQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoResponse;
import vn.com.pps.education.dto.ReviewVideoSetResponse;
import vn.com.pps.education.dto.ReviewVideoSetStatsResponse;
import vn.com.pps.education.dto.ReviewVideoSubmissionResponse;
import vn.com.pps.education.dto.StartWatchSessionResponse;
import vn.com.pps.education.dto.SubmitConnectionAnswersRequest;
import vn.com.pps.education.dto.SubmitReviewVideoAudioRequest;
import vn.com.pps.education.dto.UpdateReviewVideoConnectionQuestionRequest;
import vn.com.pps.education.dto.UpdateReviewVideoQuestionRequest;
import vn.com.pps.education.dto.UpdateReviewVideoSetRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ReviewVideoQuestionImportService;
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
    private final ReviewVideoQuestionImportService reviewVideoQuestionImportService;

    public ReviewVideoController(ReviewVideoService reviewVideoService,
                                  ReviewVideoQuestionImportService reviewVideoQuestionImportService) {
        this.reviewVideoService = reviewVideoService;
        this.reviewVideoQuestionImportService = reviewVideoQuestionImportService;
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

    /** V156 — "Xóa Bộ" (soft-delete), chỉ xóa được khi Bộ đã hết Video — xem Javadoc ReviewVideoService#deleteSet. */
    @PreAuthorize("hasPermission(null, 'lms.review-video.delete')")
    @DeleteMapping("/api/review-video-sets/{id}")
    public ResponseEntity<Void> deleteSet(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        reviewVideoService.deleteSet(id, actor.userId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/classes/{classId}/review-video-sets")
    public ResponseEntity<List<ReviewVideoSetResponse>> listByClass(@PathVariable Long classId,
                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listByClass(classId, actor.userId()));
    }

    /** Kho Video — lọc theo khung chương trình/loại giáo viên (V98, mirror ExamController#listExams). */
    @GetMapping("/api/review-video-sets")
    public ResponseEntity<List<ReviewVideoSetResponse>> listSets(@RequestParam(required = false) Long curriculumId,
                                                                   @RequestParam(required = false) String teacherType,
                                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listSets(curriculumId, teacherType, actor.userId()));
    }

    /** V98 (mirror ExamController#assignToClass) — gán Bộ cho 1 lớp, điều kiện hiển thị DUY NHẤT cho học sinh lớp đó. */
    @PreAuthorize("hasPermission(null, 'lms.review-video.assign')")
    @PostMapping("/api/review-video-sets/{id}/classes/{classId}")
    public ResponseEntity<Void> assignToClass(@PathVariable Long id, @PathVariable Long classId,
                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        reviewVideoService.assignToClass(id, classId, actor.userId());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasPermission(null, 'lms.review-video.assign')")
    @DeleteMapping("/api/review-video-sets/{id}/classes/{classId}")
    public ResponseEntity<Void> unassignFromClass(@PathVariable Long id, @PathVariable Long classId,
                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        reviewVideoService.unassignFromClass(id, classId, actor.userId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/review-video-sets/{id}/classes")
    public ResponseEntity<List<ClassResponse>> listAssignedClasses(@PathVariable Long id,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listAssignedClasses(id, actor.userId()));
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

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — xem Javadoc ReviewVideoService#deleteVideo. */
    @PreAuthorize("hasPermission(null, 'lms.review-video.update')")
    @DeleteMapping("/api/review-videos/{videoId}")
    public ResponseEntity<Void> deleteVideo(@PathVariable Long videoId,
                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        reviewVideoService.deleteVideo(videoId, actor.userId());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasPermission(null, 'lms.review-video.update')")
    @PostMapping("/api/review-videos/{videoId}/questions")
    public ResponseEntity<ReviewVideoQuestionResponse> addQuestion(@PathVariable Long videoId,
                                                                      @Valid @RequestBody AddReviewVideoQuestionRequest request,
                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.addQuestion(videoId, request, actor.userId()));
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — soạn hàng loạt câu hỏi REFLEX bằng file mẫu Excel (.xlsx), mirror QuestionImportController. */
    @PreAuthorize("hasPermission(null, 'lms.review-video.update')")
    @PostMapping(value = "/api/review-videos/{videoId}/questions/import", consumes = "multipart/form-data")
    public ResponseEntity<ReviewVideoQuestionImportResponse> importQuestions(@PathVariable Long videoId,
                                                                               @RequestParam("file") MultipartFile file,
                                                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoQuestionImportService.importReflexQuestions(videoId, file, actor.userId()));
    }

    @GetMapping("/api/review-videos/{videoId}/questions")
    public ResponseEntity<List<ReviewVideoQuestionResponse>> listQuestions(@PathVariable Long videoId,
                                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listQuestions(videoId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.review-video.update')")
    @PutMapping("/api/review-video-questions/{questionId}")
    public ResponseEntity<ReviewVideoQuestionResponse> updateQuestion(@PathVariable Long questionId,
                                                                         @Valid @RequestBody UpdateReviewVideoQuestionRequest request,
                                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.updateQuestion(questionId, request, actor.userId()));
    }

    /** V83 — mirror addQuestion/listQuestions của REFLEX, dành cho câu hỏi trắc nghiệm của video CONNECTION. */
    @PreAuthorize("hasPermission(null, 'lms.review-video.update')")
    @PostMapping("/api/review-videos/{videoId}/connection-questions")
    public ResponseEntity<ReviewVideoConnectionQuestionResponse> addConnectionQuestion(
            @PathVariable Long videoId, @Valid @RequestBody AddReviewVideoConnectionQuestionRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.addConnectionQuestion(videoId, request, actor.userId()));
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — soạn hàng loạt câu hỏi CONNECTION bằng file mẫu Excel (.xlsx), mirror importQuestions (REFLEX). */
    @PreAuthorize("hasPermission(null, 'lms.review-video.update')")
    @PostMapping(value = "/api/review-videos/{videoId}/connection-questions/import", consumes = "multipart/form-data")
    public ResponseEntity<ReviewVideoQuestionImportResponse> importConnectionQuestions(@PathVariable Long videoId,
                                                                                          @RequestParam("file") MultipartFile file,
                                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoQuestionImportService.importConnectionQuestions(videoId, file, actor.userId()));
    }

    @GetMapping("/api/review-videos/{videoId}/connection-questions")
    public ResponseEntity<List<ReviewVideoConnectionQuestionResponse>> listConnectionQuestions(
            @PathVariable Long videoId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listConnectionQuestions(videoId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.review-video.update')")
    @PutMapping("/api/review-video-connection-questions/{questionId}")
    public ResponseEntity<ReviewVideoConnectionQuestionResponse> updateConnectionQuestion(
            @PathVariable Long questionId, @Valid @RequestBody UpdateReviewVideoConnectionQuestionRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.updateConnectionQuestion(questionId, request, actor.userId()));
    }

    // V128/V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — assignmentId bắt
    // buộc: 1 bộ video có thể có nhiều lần giao ACTIVE song song, không còn suy được "lần giao nào"
    // chỉ từ videoId. FE (mỗi thẻ BTVN) đã có sẵn assignmentId, chỉ cần truyền kèm.
    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — xem Javadoc ReviewVideoService#getProgress. */
    @GetMapping("/api/review-videos/{videoId}/progress")
    public ResponseEntity<ReviewVideoProgressResponse> getProgress(@PathVariable Long videoId,
                                                                      @RequestParam Long assignmentId,
                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.getProgress(videoId, assignmentId, actor.userId()));
    }

    @PostMapping("/api/review-videos/{videoId}/watch-sessions")
    public ResponseEntity<StartWatchSessionResponse> startWatchSession(@PathVariable Long videoId,
                                                                          @RequestParam Long assignmentId,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.startWatchSession(videoId, assignmentId, actor.userId()));
    }

    @PutMapping("/api/review-videos/{videoId}/progress")
    public ResponseEntity<ReviewVideoProgressResponse> reportProgress(@PathVariable Long videoId,
                                                                        @Valid @RequestBody ReportVideoProgressRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.reportProgress(videoId, request, actor.userId()));
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11 — chỉ trả về NHÓM câu hỏi của đúng lượt xem này (khác endpoint listConnectionQuestions trả toàn bộ ngân hàng, dùng cho giáo viên soạn). */
    @GetMapping("/api/review-video-watch-sessions/{watchSessionId}/connection-questions")
    public ResponseEntity<List<ReviewVideoConnectionQuestionResponse>> listConnectionQuestionsForSession(
            @PathVariable Long watchSessionId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listConnectionQuestionsForSession(watchSessionId, actor.userId()));
    }

    /** V83 — học sinh nộp toàn bộ câu trả lời trắc nghiệm cho 1 lượt xem cụ thể (khớp cặp 1-1 qua watchSessionId). */
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

    /** UC-66 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — gộp BTVN Video Ôn tập vào trang "Thống kê BTVN theo lớp". */
    @PreAuthorize("hasPermission(null, 'lms.review-video.view')")
    @GetMapping("/api/classes/{classId}/review-video-assignments/stats")
    public ResponseEntity<List<ReviewVideoAssignmentStatsResponse>> listAssignmentStats(@PathVariable Long classId,
                                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listAssignmentStatsForClass(classId, actor.userId()));
    }

    @PutMapping("/api/review-video-questions/{questionId}/submissions")
    public ResponseEntity<ReviewVideoSubmissionResponse> submitQuestionAudio(@PathVariable Long questionId,
                                                                        @RequestParam Long assignmentId,
                                                                        @Valid @RequestBody SubmitReviewVideoAudioRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.submitQuestionAudio(questionId, assignmentId, request, actor.userId()));
    }

    @GetMapping("/api/review-video-questions/{questionId}/submissions/latest")
    public ResponseEntity<ReviewVideoSubmissionResponse> getMyLatestSubmission(@PathVariable Long questionId,
                                                                            @RequestParam Long assignmentId,
                                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        ReviewVideoSubmissionResponse response = reviewVideoService.getMyLatestSubmission(questionId, assignmentId, actor.userId());
        return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
    }

    @GetMapping("/api/review-video-questions/{questionId}/submissions/history")
    public ResponseEntity<List<ReviewVideoSubmissionResponse>> listMySubmissionHistory(@PathVariable Long questionId,
                                                                            @RequestParam Long assignmentId,
                                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listMySubmissionHistory(questionId, assignmentId, actor.userId()));
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

    /** Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — badge Sidebar + landing "Hàng chờ chấm bài". */
    @PreAuthorize("hasPermission(null, 'lms.grading.manage')")
    @GetMapping("/api/review-video-submissions/pending-grading")
    public ResponseEntity<List<PendingGradingClassSummaryResponse>> listPendingGradingClasses(
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.getPendingGradingSummaryForTeacher(actor.userId()));
    }

    /** Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — hàng chờ chấm gộp mọi Bộ REFLEX đã gán cho 1 lớp. */
    @PreAuthorize("hasPermission(null, 'lms.grading.manage')")
    @GetMapping("/api/classes/{classId}/review-video-submissions")
    public ResponseEntity<List<ReviewVideoSubmissionResponse>> listSubmissionsForClass(
            @PathVariable Long classId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listSubmissionsForTeacherByClass(classId, actor.userId()));
    }
}
