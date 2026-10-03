package vn.com.pps.education.lms.dto;

/** UC-75 — job soạn lý do từ chối chạy nền; FE hỏi lại qua {@code GET /api/comment-ai-rejection-reasons/{jobId}}. */
public record CommentAiRejectionReasonJobResponse(String jobId, String status, String errorMessage,
                                                  CommentAiRejectionReasonResult result) {
}
