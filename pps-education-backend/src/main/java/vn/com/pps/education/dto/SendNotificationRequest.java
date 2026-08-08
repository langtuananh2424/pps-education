package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.com.pps.education.domain.Notification;

import java.util.List;

/** Gửi thông báo thủ công tới danh sách user được chọn — công cụ test/gửi tay của Quản trị viên. */
public record SendNotificationRequest(
        @NotEmpty List<Long> recipientUserIds,
        @NotNull Notification.NotificationType notificationType,
        @NotNull @Size(min = 1, max = 500) String title,
        @NotNull @Size(min = 1) String content
) {}
