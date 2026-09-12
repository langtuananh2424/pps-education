package vn.com.pps.education.dto;

import java.util.List;

public record SendNotificationResponse(
        int totalRecipients,
        int succeeded,
        List<SendNotificationFailure> failures,
        List<ChannelResult> channelResults
) {
    public record SendNotificationFailure(Long recipientUserId, String reason) {}

    /**
     * Kết quả gửi theo TỪNG kênh của TỪNG user — bổ sung ngoài SDD gốc, đã
     * xác nhận với người dùng 2026-09-12: trả về ngay (dispatch đồng bộ tại
     * chỗ, không chờ job quét mỗi phút) để dùng làm công cụ test kênh
     * push/email/sms mà không phải tra DB.
     */
    public record ChannelResult(Long recipientUserId, String channel, String status, String errorMessage) {}
}
