package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;

import java.util.List;

/**
 * Gửi thông báo thủ công tới danh sách user được chọn — công cụ test/gửi
 * tay của Quản trị viên.
 *
 * @param channels bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 *                 2026-09-12: khi truyền (không rỗng), ÉP gửi đúng các kênh
 *                 này bất kể notification_preferences của user nhận — phục
 *                 vụ test cô lập 1 kênh (VD chỉ PUSH). Để trống/null = giữ
 *                 hành vi cũ, gửi theo preference thật của từng user.
 */
public record SendNotificationRequest(
        @NotEmpty List<Long> recipientUserIds,
        @NotNull Notification.NotificationType notificationType,
        @NotNull @Size(min = 1, max = 500) String title,
        @NotNull @Size(min = 1) String content,
        List<NotificationDelivery.Channel> channels
) {}
