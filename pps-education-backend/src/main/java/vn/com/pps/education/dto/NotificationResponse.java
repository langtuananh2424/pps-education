package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

public record NotificationResponse(
        Long id,
        String notificationType,
        String title,
        String content,
        String entityType,
        Long entityId,
        String priority,
        OffsetDateTime createdAt,
        OffsetDateTime readAt
) {}
