package vn.com.pps.education.service.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.com.pps.education.domain.DeviceToken;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.repository.DeviceTokenRepository;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Kênh PUSH (FCM) — bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-08). Unit test thuần
 * (mock FirebaseMessaging + DeviceTokenRepository), không chạm DB.
 *
 * Trọng tâm: payload phải là DATA-ONLY — bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-09-07 sau sự cố THẬT (tài khoản phangiabao04 nhận đúp 2 thông báo). Có block "notification"
 * trong payload khiến FCM tự hiện popup ở nền SONG SONG với showNotification() của Service Worker.
 */
class PushNotificationSenderTest {

    private FirebaseMessaging firebaseMessaging;
    private DeviceTokenRepository deviceTokenRepository;
    private PushNotificationSender sender;

    private final User recipient = user(7L);
    private final Notification notification = notification(42L, "Tiêu đề", "Nội dung");
    private NotificationDelivery delivery;

    @BeforeEach
    void setUp() {
        firebaseMessaging = mock(FirebaseMessaging.class);
        deviceTokenRepository = mock(DeviceTokenRepository.class);
        sender = new PushNotificationSender(Optional.of(firebaseMessaging), deviceTokenRepository, new NotificationPushTemplateService());
        delivery = new NotificationDelivery();
    }

    @Test
    void send_sendsDataOnlyPayload_withoutNotificationBlock() throws Exception {
        when(deviceTokenRepository.findByUserIdAndActiveTrue(7L)).thenReturn(List.of(token("tok-A")));

        boolean sent = sender.send(delivery, notification, recipient);

        assertThat(sent).isTrue();
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging).send(captor.capture());
        Message message = captor.getValue();

        assertThat(readField(message, "notification"))
                .as("payload phải data-only để FCM không tự hiện popup song song với Service Worker")
                .isNull();
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) readField(message, "data");
        assertThat(data)
                .containsEntry("title", "Tiêu đề")
                .containsEntry("body", "Nội dung")
                .containsEntry("notificationId", "42");
    }

    @Test
    void send_fansOutToEveryActiveToken_andSnapshotsThemInRecipientAddress() throws Exception {
        when(deviceTokenRepository.findByUserIdAndActiveTrue(7L))
                .thenReturn(List.of(token("tok-A"), token("tok-B")));

        boolean sent = sender.send(delivery, notification, recipient);

        assertThat(sent).isTrue();
        verify(firebaseMessaging, times(2)).send(any(Message.class));
        assertThat(delivery.getRecipientAddress()).isEqualTo("tok-A,tok-B");
        assertThat(delivery.getProvider()).isEqualTo("FCM");
    }

    @Test
    void send_deactivatesToken_whenFcmReportsUnregistered() throws Exception {
        DeviceToken dead = token("tok-dead");
        DeviceToken alive = token("tok-alive");
        when(deviceTokenRepository.findByUserIdAndActiveTrue(7L)).thenReturn(List.of(dead, alive));
        FirebaseMessagingException unregistered = mock(FirebaseMessagingException.class);
        when(unregistered.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.send(any(Message.class)))
                .thenThrow(unregistered)
                .thenReturn("ok");

        boolean sent = sender.send(delivery, notification, recipient);

        assertThat(sent).isTrue();
        assertThat(dead.isActive()).isFalse();
        assertThat(alive.isActive()).isTrue();
        verify(deviceTokenRepository).save(dead);
        assertThat(delivery.getRecipientAddress()).isEqualTo("tok-alive");
    }

    @Test
    void send_dedupesSameDeviceByUserAgent_keepsNewestAndDeactivatesOlder() throws Exception {
        // Sự cố THẬT (2026-09-13): user xoá rồi tạo lại shortcut trên iOS -> deviceId đổi mới,
        // dedupe lúc ĐĂNG KÝ (NotificationService) trượt -> 2 token cùng active cho CÙNG 1 máy
        // vật lý (nhận diện qua user_agent giống hệt nhau) -> mỗi lần gửi bắn đúp. Chỉ giữ token
        // updated_at MỚI NHẤT trong nhóm cùng user_agent, các token cũ hơn tự bị vô hiệu hoá NGAY.
        String sameDeviceUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X)";
        DeviceToken older = tokenWithUserAgent("tok-old", sameDeviceUserAgent, OffsetDateTime.parse("2026-09-12T07:52:11Z"));
        DeviceToken newer = tokenWithUserAgent("tok-new", sameDeviceUserAgent, OffsetDateTime.parse("2026-09-12T08:16:06Z"));
        DeviceToken otherDevice = tokenWithUserAgent("tok-other", "Mozilla/5.0 (Linux; Android 14)", OffsetDateTime.parse("2026-09-12T08:00:00Z"));
        when(deviceTokenRepository.findByUserIdAndActiveTrue(7L)).thenReturn(List.of(older, newer, otherDevice));

        boolean sent = sender.send(delivery, notification, recipient);

        assertThat(sent).isTrue();
        verify(firebaseMessaging, times(2)).send(any(Message.class));
        assertThat(delivery.getRecipientAddress()).isEqualTo("tok-new,tok-other");
        assertThat(older.isActive()).as("token cũ hơn cùng máy phải tự bị vô hiệu hoá").isFalse();
        assertThat(newer.isActive()).isTrue();
        assertThat(otherDevice.isActive()).as("máy khác (user_agent khác) không bị đụng tới").isTrue();
        verify(deviceTokenRepository).saveAll(List.of(older));
    }

    @Test
    void send_returnsFalse_whenUserHasNoActiveTokens() throws Exception {
        when(deviceTokenRepository.findByUserIdAndActiveTrue(7L)).thenReturn(List.of());

        assertThat(sender.send(delivery, notification, recipient)).isFalse();
        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void send_throws_whenFirebaseNotConfigured() {
        PushNotificationSender noFirebase =
                new PushNotificationSender(Optional.empty(), deviceTokenRepository, new NotificationPushTemplateService());

        assertThatThrownBy(() -> noFirebase.send(delivery, notification, recipient))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void send_truncatesRecipientAddress_toColumnLimit() throws Exception {
        // 30 token nối bằng dấu phẩy > 500 ký tự — phải bị cắt để không làm vỡ cột VARCHAR(500)
        // (sự cố THẬT: chuỗi tràn → rollback → delivery kẹt PENDING → job gửi lại push mỗi phút).
        List<DeviceToken> many = IntStream.range(0, 30)
                .mapToObj(i -> token("token-with-a-fairly-long-value-" + i))
                .toList();
        when(deviceTokenRepository.findByUserIdAndActiveTrue(7L)).thenReturn(many);

        boolean sent = sender.send(delivery, notification, recipient);

        assertThat(sent).isTrue();
        assertThat(delivery.getRecipientAddress().length()).isLessThanOrEqualTo(500);
        assertThat(delivery.getRecipientAddress()).endsWith("...(cat bot)");
    }

    // --- helpers ---

    /** Message của firebase-admin không có getter public — đọc field qua reflection cho test này. */
    private static Object readField(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                // thử lớp cha
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        throw new AssertionError("Message không có field '" + name + "' (firebase-admin đổi API?)");
    }

    private static DeviceToken token(String value) {
        DeviceToken t = new DeviceToken();
        t.setToken(value);
        t.setActive(true);
        return t;
    }

    private static DeviceToken tokenWithUserAgent(String value, String userAgent, OffsetDateTime updatedAt) {
        DeviceToken t = token(value);
        t.setUserAgent(userAgent);
        t.setUpdatedAt(updatedAt);
        return t;
    }

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private static Notification notification(long id, String title, String content) {
        Notification n = new Notification();
        n.setId(id);
        n.setTitle(title);
        n.setContent(content);
        return n;
    }
}
