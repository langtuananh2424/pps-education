package vn.com.pps.education.notification.dto;

public record NotificationPreferenceResponse(
        String notificationType,
        boolean inAppEnabled,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean zaloEnabled,
        boolean pushEnabled
) {}
