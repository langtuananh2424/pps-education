package vn.com.pps.education.dto;

import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — kết quả "Lưu nháp" cả lô. Tách riêng
 * {@code saved}/{@code skipped} thay vì all-or-nothing để giữ ĐÚNG hành vi cũ của FE (mỗi dòng trước
 * đây là 1 request Promise.allSettled ĐỘC LẬP — 1 dòng lỗi (VD học sinh vừa bị duyệt/khoá giữa chừng
 * bởi Quản lý điểm trường) không chặn các dòng khác lưu thành công). Xem Javadoc
 * StudentCommentService#saveDraftBatch.
 */
public record SaveDraftCommentsResponse(
        List<StudentCommentResponse> saved,
        List<SkippedRow> skipped
) {
    public record SkippedRow(Long studentId, String reason) {}
}
