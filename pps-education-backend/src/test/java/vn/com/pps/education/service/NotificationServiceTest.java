package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.NotificationPreferenceRequest;
import vn.com.pps.education.dto.NotificationPreferenceResponse;
import vn.com.pps.education.dto.NotificationResponse;
import vn.com.pps.education.repository.NotificationDeliveryRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module Notification — tạo thông báo + fan-out theo notification_preferences
 * (mặc định in-app+email = enabled khi chưa có preference, xem SDD > Task
 * Management & Thông báo > Notifications > "Logic gửi thông báo").
 */
@Transactional
class NotificationServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    private User recipient;

    @BeforeEach
    void setUp() {
        recipient = newUser("notif.recipient");
    }

    @Test
    void notify_MainFlow_createsInAppSentImmediatelyAndEmailPending_whenNoPreferenceRecord() {
        Notification notification = notificationService.notify(recipient.getId(),
                Notification.NotificationType.SYSTEM_ANNOUNCEMENT, "Tiêu đề", "Nội dung");

        List<NotificationDelivery> deliveries = notificationDeliveryRepository.findAll().stream()
                .filter(d -> d.getNotification().getId().equals(notification.getId()))
                .toList();

        assertThat(deliveries).hasSize(2);
        assertThat(deliveries).anySatisfy(d -> {
            assertThat(d.getChannel()).isEqualTo(NotificationDelivery.Channel.IN_APP);
            assertThat(d.getDeliveryStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.SENT);
            assertThat(d.getSentAt()).isNotNull();
        });
        assertThat(deliveries).anySatisfy(d -> {
            assertThat(d.getChannel()).isEqualTo(NotificationDelivery.Channel.EMAIL);
            assertThat(d.getDeliveryStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.PENDING);
        });
    }

    @Test
    void notify_respectsPreference_disablesEmailWhenPreferenceSaysFalse() {
        notificationService.upsertPreference(recipient.getId(), Notification.NotificationType.TASK_ASSIGNED,
                new NotificationPreferenceRequest(true, false, false, false, false));

        Notification notification = notificationService.notify(recipient.getId(),
                Notification.NotificationType.TASK_ASSIGNED, "Việc mới", "Bạn có 1 việc mới");

        List<NotificationDelivery> deliveries = notificationDeliveryRepository.findAll().stream()
                .filter(d -> d.getNotification().getId().equals(notification.getId()))
                .toList();

        assertThat(deliveries).hasSize(1);
        assertThat(deliveries.get(0).getChannel()).isEqualTo(NotificationDelivery.Channel.IN_APP);
    }

    @Test
    void listMine_returnsOwnNotificationsOnly() {
        User other = newUser("notif.other");
        notificationService.notify(recipient.getId(), Notification.NotificationType.OTHER, "A", "a");
        notificationService.notify(other.getId(), Notification.NotificationType.OTHER, "B", "b");

        var page = notificationService.listMine(recipient.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).title()).isEqualTo("A");
    }

    @Test
    void markRead_setsReadAtOnce() {
        Notification notification = notificationService.notify(recipient.getId(),
                Notification.NotificationType.OTHER, "X", "y");
        assertThat(notification.getReadAt()).isNull();

        NotificationResponse response = notificationService.markRead(recipient.getId(), notification.getId());

        assertThat(response.readAt()).isNotNull();
    }

    @Test
    void getPreference_returnsDefaultWhenNoRecordExists() {
        NotificationPreferenceResponse pref = notificationService.getPreference(
                recipient.getId(), Notification.NotificationType.GRADE_PUBLISHED);

        assertThat(pref.inAppEnabled()).isTrue();
        assertThat(pref.emailEnabled()).isTrue();
        assertThat(pref.smsEnabled()).isFalse();
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + SEQ.incrementAndGet());
        user.setEmail(prefix + "." + SEQ.incrementAndGet() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
