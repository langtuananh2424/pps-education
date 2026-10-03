package vn.com.pps.education.lms.dto;

/** UC-75 — job xử lý yêu cầu sửa của Quản lý chạy nền; FE hỏi lại qua {@code GET /api/comment-ai-instructions/{jobId}}. */
public record CommentAiInstructionJobResponse(String jobId, String status, String errorMessage, CommentAiInstructionResult result) {
}
