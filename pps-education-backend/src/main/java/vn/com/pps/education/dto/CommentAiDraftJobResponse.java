package vn.com.pps.education.dto;

/**
 * UC-74 bước 2 — trợ lý AI xử lý bất đồng bộ (STT + nhiều lượt gọi AI có thể vượt timeout 60s của
 * reverse proxy), FE hỏi lại trạng thái qua {@code GET /api/comment-ai-drafts/{jobId}}.
 *
 * @param status {@code RUNNING}, {@code DONE} ({@code result} có dữ liệu) hoặc {@code FAILED}
 *               ({@code errorMessage} có lý do, UC-74 A5).
 */
public record CommentAiDraftJobResponse(String jobId, String status, String errorMessage, CommentAiDraftResult result) {
}
