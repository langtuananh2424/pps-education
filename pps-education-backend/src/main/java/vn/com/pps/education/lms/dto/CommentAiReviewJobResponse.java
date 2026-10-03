package vn.com.pps.education.lms.dto;

/** UC-75 — job soát nhận xét chạy nền; FE hỏi lại qua {@code GET /api/comment-ai-reviews/{jobId}}. */
public record CommentAiReviewJobResponse(String jobId, String status, String errorMessage, CommentAiReviewResult result) {
}
