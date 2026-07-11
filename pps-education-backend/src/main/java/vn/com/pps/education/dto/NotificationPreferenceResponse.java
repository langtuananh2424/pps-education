package vn.com.pps.education.dto;

public record NotificationPreferenceResponse(
        String notificationType,
        boolean inAppEnabled,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean zaloEnabled,
        boolean pushEnabled
) {}
