package vn.com.pps.education.dto;

/**
 * UC-75 (bổ sung 2026-09-29, đã xác nhận với người dùng) — lý do từ chối AI soạn sẵn gửi giáo viên. CHƯA từ
 * chối gì: FE điền sẵn vào hộp thoại lý do, Quản lý sửa rồi tự bấm Từ chối qua đúng chức năng của UC-22.
 */
public record CommentAiRejectionReasonResult(Long commentId, String reason) {
}
