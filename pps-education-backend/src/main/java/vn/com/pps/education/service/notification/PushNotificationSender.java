package vn.com.pps.education.service.notification;

import com.google.firebase.messaging.AndroidConfig;
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
 *
 * Payload là DATA-ONLY (không có block "notification") — bổ sung ngoài SDD
 * gốc, đã xác nhận với người dùng 2026-09-07: có block "notification" khiến
 * FCM tự hiện popup ở nền song song với showNotification() của Service
 * Worker → hiện đúp thông báo. FE (firebase-messaging-sw.js + onMessage
 * trong pushNotifications.ts) tự đọc data.title/body/notificationId và là
 * đường DUY NHẤT hiển thị; data.notificationId dùng làm `tag` để gộp nếu
 * cả 2 handler cùng chạy.
 */
@Component
public class PushNotificationSender implements NotificationChannelSender {

    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationPushTemplateService templateService;

    /**
     * FirebaseMessaging bọc trong Optional vì FirebaseConfig#firebaseMessaging
     * trả về null khi FIREBASE_CREDENTIALS_BASE64 chưa cấu hình — Spring chỉ
     * coi injection point là "có thể vắng mặt" (Optional.empty()) khi khai
     * báo kiểu Optional&lt;T&gt;, injection kiểu T thô sẽ throw
     * UnsatisfiedDependencyException lúc khởi động dù bean method có tồn tại.
     */
    public PushNotificationSender(Optional<FirebaseMessaging> firebaseMessaging,
                                   DeviceTokenRepository deviceTokenRepository,
                                   NotificationPushTemplateService templateService) {
        this.firebaseMessaging = firebaseMessaging.orElse(null);
        this.deviceTokenRepository = deviceTokenRepository;
        this.templateService = templateService;
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

        // Mẫu push riêng theo NotificationType (xem NotificationPushTemplateService) — bổ sung
        // ngoài SDD gốc, đã xác nhận với người dùng 2026-09-12: nội dung notifications.content
        // dùng chung cho in-app khá kỹ thuật khi đọc trên điện thoại. Type chưa có mẫu -> fallback
        // dùng title/content thô (hành vi cũ).
        var pushTemplate = templateService.renderFor(notification.getNotificationType(), notification.getMetadata());
        String pushTitle = pushTemplate.map(NotificationPushTemplateService.PushTemplate::title)
                .orElseGet(notification::getTitle);
        String pushBody = pushTemplate.map(NotificationPushTemplateService.PushTemplate::body)
                .orElseGet(notification::getContent);

        StringBuilder sentTokens = new StringBuilder();
        for (DeviceToken deviceToken : tokens) {
            Message message = Message.builder()
                    .setToken(deviceToken.getToken())
                    // Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07): gửi DATA-ONLY,
                    // KHÔNG set .setNotification(). Khi payload có block "notification", trên nền
                    // Chrome/Android FCM TỰ hiển thị popup từ payload TRONG KHI Service Worker
                    // (onBackgroundMessage) cũng gọi showNotification() cho cùng push đó → người dùng
                    // nhận 2 thông báo (đã xảy ra thật với tài khoản phangiabao04). Data-only để chỉ
                    // còn đúng 1 đường hiển thị do FE kiểm soát (SW + onMessage). Kèm notificationId
                    // để FE đặt làm `tag` — nếu cả 2 handler cùng chạy thì trình duyệt gộp theo tag.
                    .putData("title", nullToEmpty(pushTitle))
                    .putData("body", nullToEmpty(pushBody))
                    .putData("notificationId", String.valueOf(notification.getId()))
                    // Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-05): ép priority HIGH để
                    // FCM đánh thức thiết bị ngay (kể cả doze mode) thay vì trì hoãn theo lô — KHÔNG tự
                    // quyết định được việc có hiện popup/heads-up hay không, việc đó do Android
                    // Notification Channel importance (người dùng tự cấu hình cho website này) quyết định.
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
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
        delivery.setRecipientAddress(truncateToColumnLimit(sentTokens.toString()));
        delivery.setProvider("FCM");
        return true;
    }

    /**
     * notification_deliveries.recipient_address là VARCHAR(500) — bổ sung ngoài SDD gốc, đã xác nhận
     * với người dùng 2026-09-07 sau sự cố THẬT trên staging: 1 user tích luỹ 7 device token, chuỗi
     * token nối bằng dấu phẩy dài ~1.100 ký tự nên lệnh save ném lỗi "value too long" → CẢ transaction
     * rollback → delivery vĩnh viễn kẹt ở PENDING → job nền quét lại mỗi phút → GỬI LẠI push mãi mãi
     * (push đã bay ra FCM rồi thì không rollback được). Cắt bớt tại đây để 1 dòng audit dài bất
     * thường không bao giờ có thể gây lặp thông báo cho người dùng nữa.
     */
    private String truncateToColumnLimit(String value) {
        final int maxLength = 500;
        if (value.length() <= maxLength) {
            return value;
        }
        final String suffix = "...(cat bot)";
        return value.substring(0, maxLength - suffix.length()) + suffix;
    }

    /** FCM Message.putData() ném NPE nếu value null — title/content vốn NOT NULL nhưng vẫn phòng thủ. */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
