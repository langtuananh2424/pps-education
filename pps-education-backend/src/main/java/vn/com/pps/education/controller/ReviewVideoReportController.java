package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.ReviewVideoAssignmentQuestionStatsResponse;
import vn.com.pps.education.dto.ReviewVideoAssignmentStudentStatsResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ReviewVideoReportService;

/** UC-66: "Xem chi tiết" BTVN Video Ôn tập (REFLEX/CONNECTION) — xem Javadoc ReviewVideoReportService. */
@RestController
public class ReviewVideoReportController {

    private final ReviewVideoReportService reviewVideoReportService;

    public ReviewVideoReportController(ReviewVideoReportService reviewVideoReportService) {
        this.reviewVideoReportService = reviewVideoReportService;
    }

    @PreAuthorize("hasPermission(null, 'lms.review-video.view')")
    @GetMapping("/api/review-video-assignments/{assignmentId}/stats/students")
    public ResponseEntity<ReviewVideoAssignmentStudentStatsResponse> getStudentStats(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoReportService.getStudentStats(assignmentId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.review-video.view')")
    @GetMapping("/api/review-video-assignments/{assignmentId}/stats/questions")
    public ResponseEntity<ReviewVideoAssignmentQuestionStatsResponse> getQuestionStats(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoReportService.getQuestionStats(assignmentId, actor.userId()));
    }
}
