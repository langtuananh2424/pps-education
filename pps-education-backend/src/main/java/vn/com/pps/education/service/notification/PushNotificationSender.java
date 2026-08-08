package vn.com.pps.education.service.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.DeviceToken;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.repository.DeviceTokenRepository;

import java.util.List;
import java.util.Optional;

/**
 * Kênh PUSH — gửi qua Firebase Cloud Messaging (FCM). Bổ sung ngoài SDD gốc
 * (SDD chỉ để chỗ cho PUSH trong enum notification_deliveries.channel,
 * chưa có sender cụ thể), đã xác nhận với người dùng 2026-08-08: dùng cho
 * thông báo hàng ngày (notification_preferences.push_enabled mặc định bật
 * cho mọi user khi chưa có bản ghi — xem NotificationService).
 *
 * Device token lấy từ bảng device_tokens (mới, xem
 * V104__notification_sms_push_channels.sql — SDD chỉ nói "snapshot device
 * token tại thời điểm gửi" vào notification_deliveries.recipient_address,
 * không chỉ rõ nguồn token hiện tại). 1 user có thể có nhiều thiết bị —
 * gửi tới tất cả token active, coi là thành công nếu >= 1 token nhận được;
 * token báo lỗi UNREGISTERED (app đã gỡ/token hết hạn) tự động bị vô hiệu
 * hoá để lần gửi sau không thử lại vô ích.
 */
@Component
public class PushNotificationSender implements NotificationChannelSender {

    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * FirebaseMessaging bọc trong Optional vì FirebaseConfig#firebaseMessaging
     * trả về null khi FIREBASE_CREDENTIALS_BASE64 chưa cấu hình — Spring chỉ
     * coi injection point là "có thể vắng mặt" (Optional.empty()) khi khai
     * báo kiểu Optional&lt;T&gt;, injection kiểu T thô sẽ throw
     * UnsatisfiedDependencyException lúc khởi động dù bean method có tồn tại.
     */
    public PushNotificationSender(Optional<FirebaseMessaging> firebaseMessaging,
                                   DeviceTokenRepository deviceTokenRepository) {
        this.firebaseMessaging = firebaseMessaging.orElse(null);
        this.deviceTokenRepository = deviceTokenRepository;
    }

    @Override
    public NotificationDelivery.Channel channel() {
        return NotificationDelivery.Channel.PUSH;
    }

    @Override
    public boolean send(NotificationDelivery delivery, Notification notification, User recipient) throws Exception {
        if (firebaseMessaging == null) {
            throw new IllegalStateException(
                    "Firebase chưa cấu hình (FIREBASE_CREDENTIALS_BASE64 rỗng)");
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndActiveTrue(recipient.getId());
        if (tokens.isEmpty()) {
            return false;
        }

        StringBuilder sentTokens = new StringBuilder();
        for (DeviceToken deviceToken : tokens) {
            Message message = Message.builder()
                    .setToken(deviceToken.getToken())
                    .setNotification(com.google.firebase.messaging.Notification.builder()
                            .setTitle(notification.getTitle())
                            .setBody(notification.getContent())
                            .build())
                    .build();
            try {
                firebaseMessaging.send(message);
                if (sentTokens.length() > 0) {
                    sentTokens.append(",");
                }
                sentTokens.append(deviceToken.getToken());
            } catch (FirebaseMessagingException ex) {
                if (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    deviceToken.setActive(false);
                    deviceTokenRepository.save(deviceToken);
                }
            }
        }

        if (sentTokens.length() == 0) {
            return false;
        }
        delivery.setRecipientAddress(sentTokens.toString());
        delivery.setProvider("FCM");
        return true;
    }
}
