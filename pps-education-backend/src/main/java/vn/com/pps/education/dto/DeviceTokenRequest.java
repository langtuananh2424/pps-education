package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Đăng ký/hủy device token cho kênh PUSH — xem PushNotificationSender. */
public record DeviceTokenRequest(
        @NotBlank String token,
        @NotBlank @Pattern(regexp = "ANDROID|IOS|WEB") String platform
) {}
