package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Ghi log kết quả 1 lần chạy setupPushNotifications() phía client — bổ
 * sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07), xem
 * PushSetupLog. errorMessage/platform optional.
 */
public record PushSetupLogRequest(
        @NotBlank String status,
        String errorMessage,
        String platform
) {}
