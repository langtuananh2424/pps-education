package vn.com.pps.education.dto;

/**
 * Số device token đang active của 1 user — bổ sung ngoài SDD gốc, đã xác
 * nhận với người dùng 2026-09-12: dùng ở trang "Gửi thông báo" để biết
 * trước user nào test PUSH được, thay vì phải tự tra DB.
 */
public record DeviceTokenCountResponse(Long userId, int activeTokenCount) {}
