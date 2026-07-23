package vn.com.pps.education.dto;

/**
 * UC-63: Phụ huynh tự cập nhật ảnh đại diện + thông tin liên hệ cá nhân
 * (nghề nghiệp, nơi làm việc, địa chỉ) của chính mình (FR-USR-07) — bổ
 * sung ngoài SDD gốc, đã xác nhận với người dùng. Tách riêng khỏi
 * UpdateParentRequest (dùng cho UC-13, Nhân viên/Admin sửa) — không nhận
 * notes (ghi chú nội bộ, VD "chỉ liên hệ khi khẩn cấp", vẫn do Nhân viên
 * Giáo vụ quản lý).
 */
public record UpdateOwnParentProfileRequest(
        String portraitUrl,
        String occupation,
        String workplace,
        String address
) {}
