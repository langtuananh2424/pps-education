package vn.com.pps.education.notification.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceRequest(
        @NotNull Boolean inAppEnabled,
        @NotNull Boolean emailEnabled,
        @NotNull Boolean smsEnabled,
        @NotNull Boolean zaloEnabled,
        @NotNull Boolean pushEnabled
) {}
