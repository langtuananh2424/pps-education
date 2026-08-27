package vn.com.pps.education.dto;

/**
 * V146 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — % TỰ ĐỘNG "BTVN buổi trước"
 * (Ngữ pháp/Reading/Writing Online, Video ôn tập) tính SẴN cho 1 buổi học, dùng để hiện ngay trên bảng
 * Nhận xét hàng ngày CHO DÙ buổi đó CHƯA có bản ghi StudentComment nào (kể cả nháp) — trước V146 các %
 * này chỉ "ngấm" vào StudentCommentResponse SAU KHI đã có ít nhất 1 lần Lưu nháp/Gửi cho buổi, khiến
 * giáo viên mở 1 buổi hoàn toàn mới không thấy % tự động của buổi trước dù đã tính đúng ở backend (xem
 * StudentCommentService#previewAutoProgress). Field null nghĩa là không tự tính được (VD buổi trước
 * giao Offline, hoặc học sinh chưa từng có buổi trước).
 */
public record AutoProgressPreviewResponse(
        Long studentId,
        String grammarPreviousProgress,
        String videoPreviousProgress,
        String readingPreviousProgress,
        String writingPreviousProgress) {
}
