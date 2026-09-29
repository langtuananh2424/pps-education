package vn.com.pps.education.dto;

import java.util.List;

/**
 * UC-75 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — nhắc Quản lý điểm trường, NGAY trên
 * bảng chờ duyệt (không cần soát bằng AI), dòng nào khi duyệt sẽ gửi cảnh báo thái độ cho phụ huynh. Chỉ đọc.
 *
 * @param items chỉ gồm dòng Thái độ Yếu/Trung bình.
 */
public record CommentAttitudeAlertPreviewResponse(List<Item> items) {

    /**
     * @param consecutiveLowCount số buổi Yếu/Trung bình liên tiếp tính cả dòng này, nếu duyệt theo thứ tự ngày.
     * @param escalation          {@code true} khi duyệt dòng này chạm mốc cảnh báo 3 buổi liên tiếp.
     */
    public record Item(Long commentId, int consecutiveLowCount, boolean escalation, String message) {
    }
}
