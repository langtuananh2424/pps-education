package vn.com.pps.education.dto;

import java.util.List;

public record SendNotificationResponse(
        int totalRecipients,
        int succeeded,
        List<SendNotificationFailure> failures
) {
    public record SendNotificationFailure(Long recipientUserId, String reason) {}
}
