package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.ReflexQuestionProgressHistoryResponse;
import vn.com.pps.education.dto.ReviewVideoAssignmentQuestionStatsResponse;
import vn.com.pps.education.dto.ReviewVideoAssignmentStudentStatsResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ReviewVideoReportService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    /** V191 — nghe lại audio + xem kết quả AI chấm theo TỪNG lần làm của 1 học sinh (Video phản xạ). */
    @PreAuthorize("hasPermission(null, 'lms.review-video.view')")
    @GetMapping("/api/review-video-assignments/{assignmentId}/stats/students/{studentId}/reflex-history")
    public ResponseEntity<List<ReflexQuestionProgressHistoryResponse>> getStudentReflexHistory(
            @PathVariable Long assignmentId, @PathVariable Long studentId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(reviewVideoReportService.getStudentReflexHistory(assignmentId, studentId, actor.userId()));
    }

    /** V191 — xuất toàn bộ audio (mọi lần ghi âm, mọi học sinh) + kết quả AI chấm thành 1 file ZIP. */
    @PreAuthorize("hasPermission(null, 'lms.review-video.view')")
    @GetMapping("/api/review-video-assignments/{assignmentId}/export-reflex-data")
    public ResponseEntity<byte[]> exportReflexData(@PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUser actor) {
        byte[] content = reviewVideoReportService.exportReflexData(assignmentId, actor.userId());
        String filename = "video-phan-xa-" + assignmentId + ".zip";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header("Content-Type", "application/zip")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encodedFilename)
                .body(content);
    }
}
