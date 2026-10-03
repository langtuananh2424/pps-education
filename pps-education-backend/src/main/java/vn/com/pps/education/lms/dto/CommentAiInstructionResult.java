package vn.com.pps.education.lms.dto;

import java.util.List;

/**
 * UC-75 bước 9 (bổ sung 2026-09-29, đã xác nhận với người dùng) — Quản lý điểm trường ra yêu cầu sửa bằng
 * giọng nói hoặc chữ trong sidebar trợ lý; kết quả là các bản sửa ĐỀ XUẤT, chưa ghi DB. "Áp dụng" dùng đúng
 * endpoint sửa nội dung PENDING của UC-22.
 */
public record CommentAiInstructionResult(String transcript, String assistantMessage, List<Change> changes) {

    public record Change(Long commentId, String studentFullName, String originalContent, String suggestedContent,
                         List<String> warnings) {
    }
}
