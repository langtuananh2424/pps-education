package vn.com.pps.education.service.notification;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.domain.User;

/**
 * Kênh SMS — gửi qua Twilio. Bổ sung ngoài SDD gốc (SDD chỉ để chỗ cho SMS
 * trong enum notification_deliveries.channel, chưa có sender cụ thể), đã
 * xác nhận với người dùng 2026-08-08: dùng riêng cho Phụ huynh/Học sinh
 * (notification_preferences.sms_enabled mặc định bật cho 2 nhóm này khi
 * chưa có bản ghi — xem NotificationService#isParentOrStudent).
 *
 * Nếu Twilio chưa cấu hình (ACCOUNT_SID/AUTH_TOKEN/FROM_NUMBER rỗng — mặc
 * định ở máy dev/CI) hoặc user chưa có SĐT, send() throw/false —
 * NotificationDispatchService tự đánh dấu FAILED + lên lịch retry, không
 * crash app (cùng convention với EmailNotificationSender).
 */
@Component
public class SmsNotificationSender implements NotificationChannelSender {

    private final String fromNumber;
    private final boolean configured;

    public SmsNotificationSender(@Value("${app.notification.twilio.account-sid}") String accountSid,
                                  @Value("${app.notification.twilio.auth-token}") String authToken,
                                  @Value("${app.notification.twilio.from-number}") String fromNumber) {
        this.fromNumber = fromNumber;
        this.configured = !accountSid.isBlank() && !authToken.isBlank() && !fromNumber.isBlank();
        if (configured) {
            Twilio.init(accountSid, authToken);
        }
    }

    @Override
    public NotificationDelivery.Channel channel() {
        return NotificationDelivery.Channel.SMS;
    }

    @Override
    public boolean send(NotificationDelivery delivery, Notification notification, User recipient) throws Exception {
        if (!configured) {
            throw new IllegalStateException(
                    "Twilio chưa cấu hình (TWILIO_ACCOUNT_SID/TWILIO_AUTH_TOKEN/TWILIO_FROM_NUMBER)");
        }
        if (recipient.getPhone() == null || recipient.getPhone().isBlank()) {
            return false;
        }

        Message message = Message.creator(
                new PhoneNumber(recipient.getPhone()),
                new PhoneNumber(fromNumber),
                smsBody(notification)
        ).create();

        delivery.setRecipientAddress(recipient.getPhone());
        delivery.setProvider("Twilio");
        delivery.setProviderMessageId(message.getSid());
        return true;
    }

    private String smsBody(Notification notification) {
        String body = notification.getTitle() + ": " + notification.getContent();
        return body.length() > 320 ? body.substring(0, 317) + "..." : body;
    }
}
