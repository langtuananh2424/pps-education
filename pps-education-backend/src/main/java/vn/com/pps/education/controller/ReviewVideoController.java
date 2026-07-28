package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.GradeReviewVideoSubmissionRequest;
import vn.com.pps.education.dto.ReportVideoProgressRequest;
import vn.com.pps.education.dto.ReviewVideoProgressResponse;
import vn.com.pps.education.dto.ReviewVideoResponse;
import vn.com.pps.education.dto.ReviewVideoSetResponse;
import vn.com.pps.education.dto.ReviewVideoSetStatsResponse;
import vn.com.pps.education.dto.ReviewVideoSubmissionResponse;
import vn.com.pps.education.dto.SubmitReviewVideoAudioRequest;
import vn.com.pps.education.dto.UpdateReviewVideoSetRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ReviewVideoService;

import java.util.List;

/** UC-23/UC-23a: Kho Video Ôn tập (FR-LMS-01) — xem Javadoc ReviewVideoService. */
@RestController
public class ReviewVideoController {

    private final ReviewVideoService reviewVideoService;

    public ReviewVideoController(ReviewVideoService reviewVideoService) {
        this.reviewVideoService = reviewVideoService;
    }

    @PostMapping("/api/review-video-sets")
    public ResponseEntity<ReviewVideoSetResponse> createSet(@Valid @RequestBody CreateReviewVideoSetRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.createSet(request, actor.userId()));
    }

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

    @PutMapping("/api/review-videos/{videoId}/progress")
    public ResponseEntity<ReviewVideoProgressResponse> reportProgress(@PathVariable Long videoId,
                                                                        @Valid @RequestBody ReportVideoProgressRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.reportProgress(videoId, request, actor.userId()));
    }

    @GetMapping("/api/review-video-sets/{setId}/stats")
    public ResponseEntity<ReviewVideoSetStatsResponse> getStats(@PathVariable Long setId,
                                                                  @RequestParam(required = false) Long classId,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.getStats(setId, classId, actor.userId()));
    }

    @PutMapping("/api/review-videos/{videoId}/submission")
    public ResponseEntity<ReviewVideoSubmissionResponse> submitAudio(@PathVariable Long videoId,
                                                                        @Valid @RequestBody SubmitReviewVideoAudioRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.submitAudio(videoId, request, actor.userId()));
    }

    @GetMapping("/api/review-videos/{videoId}/submission")
    public ResponseEntity<ReviewVideoSubmissionResponse> getMySubmission(@PathVariable Long videoId,
                                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        ReviewVideoSubmissionResponse response = reviewVideoService.getMySubmission(videoId, actor.userId());
        return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
    }

    @GetMapping("/api/review-video-sets/{setId}/submissions")
    public ResponseEntity<List<ReviewVideoSubmissionResponse>> listSubmissions(@PathVariable Long setId,
                                                                                  @RequestParam(required = false) Long classId,
                                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.listSubmissionsForTeacher(setId, classId, actor.userId()));
    }

    @PostMapping("/api/review-video-submissions/{submissionId}/grade")
    public ResponseEntity<ReviewVideoSubmissionResponse> gradeSubmission(@PathVariable Long submissionId,
                                                                            @Valid @RequestBody GradeReviewVideoSubmissionRequest request,
                                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoService.gradeSubmission(submissionId, request, actor.userId()));
    }
}
