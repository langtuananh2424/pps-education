package vn.com.pps.education.dto;

/** UC-75 — job đề xuất bản sửa chạy nền; FE hỏi lại qua {@code GET /api/comment-ai-suggestions/{jobId}}. */
public record CommentAiSuggestionJobResponse(String jobId, String status, String errorMessage, CommentAiSuggestionResult result) {
}
