package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Đăng ký/hủy device token cho kênh PUSH — xem PushNotificationSender.
 * deviceId: bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07) — UUID sinh + lưu
 * localStorage phía client, dùng để dedupe token cũ theo ĐÚNG thiết bị vật lý (xem
 * NotificationService.registerDeviceToken). Optional/nullable — client cũ (bundle cache trước khi
 * deploy đổi này) chưa gửi vẫn đăng ký token được bình thường, chỉ không dedupe được.
 */
public record DeviceTokenRequest(
        @NotBlank String token,
        @NotBlank @Pattern(regexp = "ANDROID|IOS|WEB") String platform,
        String deviceId
) {}
