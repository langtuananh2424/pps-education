package vn.com.pps.education.academic.dto;

/**
 * UC-76 bước 2 — trợ lý xử lý bất đồng bộ (như UC-74), FE hỏi lại qua {@code GET /api/term-comment-ai-drafts/{jobId}}.
 *
 * @param status {@code RUNNING}, {@code DONE} ({@code result} có dữ liệu) hoặc {@code FAILED} ({@code errorMessage}, A4).
 */
public record TermCommentAiDraftJobResponse(String jobId, String status, String errorMessage, TermCommentAiDraftResult result) {
}
