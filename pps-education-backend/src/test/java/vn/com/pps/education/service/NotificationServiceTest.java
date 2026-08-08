package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.NotificationPreferenceRequest;
import vn.com.pps.education.dto.NotificationPreferenceResponse;
import vn.com.pps.education.dto.NotificationResponse;
import vn.com.pps.education.repository.NotificationDeliveryRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module Notification — tạo thông báo + fan-out theo notification_preferences.
 * Mặc định khi chưa có preference (SDD > Task Management & Thông báo >
 * Notifications > "Logic gửi thông báo", mở rộng ngoài SDD gốc đã xác nhận
 * với người dùng 2026-08-08): in-app + email + push = enabled cho mọi user;
 * sms chỉ enabled mặc định cho Phụ huynh/Học sinh (xem
 * NotificationService#isParentOrStudent).
 */
@Transactional
class NotificationServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    private User recipient;

    @BeforeEach
    void setUp() {
        recipient = newUser("notif.recipient");
    }

    @Test
    void notify_MainFlow_createsInAppSentImmediatelyPlusEmailAndPushPending_whenNoPreferenceRecord() {
        Notification notification = notificationService.notify(recipient.getId(),
                Notification.NotificationType.SYSTEM_ANNOUNCEMENT, "Tiêu đề", "Nội dung");

        List<NotificationDelivery> deliveries = notificationDeliveryRepository.findAll().stream()
                .filter(d -> d.getNotification().getId().equals(notification.getId()))
                .toList();

        assertThat(deliveries).hasSize(3);
        assertThat(deliveries).anySatisfy(d -> {
            assertThat(d.getChannel()).isEqualTo(NotificationDelivery.Channel.IN_APP);
            assertThat(d.getDeliveryStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.SENT);
            assertThat(d.getSentAt()).isNotNull();
        });
        assertThat(deliveries).anySatisfy(d -> {
            assertThat(d.getChannel()).isEqualTo(NotificationDelivery.Channel.EMAIL);
            assertThat(d.getDeliveryStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.PENDING);
        });
        assertThat(deliveries).anySatisfy(d -> {
            assertThat(d.getChannel()).isEqualTo(NotificationDelivery.Channel.PUSH);
            assertThat(d.getDeliveryStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.PENDING);
        });
        assertThat(deliveries).noneMatch(d -> d.getChannel() == NotificationDelivery.Channel.SMS);
    }

    @Test
    void notify_defaultsSmsPendingForParentRecipient_whenNoPreferenceRecord() {
        User parentUser = newUser("notif.parent");
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parentRepository.save(parent);

        Notification notification = notificationService.notify(parentUser.getId(),
                Notification.NotificationType.ATTENDANCE_ABSENT, "Vắng học", "Con bạn vắng học hôm nay");

        List<NotificationDelivery> deliveries = notificationDeliveryRepository.findAll().stream()
                .filter(d -> d.getNotification().getId().equals(notification.getId()))
                .toList();

        assertThat(deliveries).anySatisfy(d -> {
            assertThat(d.getChannel()).isEqualTo(NotificationDelivery.Channel.SMS);
            assertThat(d.getDeliveryStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.PENDING);
        });
    }

    @Test
    void notify_defaultsSmsPendingAndNoEmailForStudentRecipient_whenNoPreferenceRecord() {
        User studentUser = newUser("notif.student");
        Student student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-NOTIF-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        studentRepository.save(student);

        Notification notification = notificationService.notify(studentUser.getId(),
                Notification.NotificationType.ATTENDANCE_ABSENT, "Vắng học", "Bạn vắng học hôm nay");

        List<NotificationDelivery> deliveries = notificationDeliveryRepository.findAll().stream()
                .filter(d -> d.getNotification().getId().equals(notification.getId()))
                .toList();

        assertThat(deliveries).anySatisfy(d -> {
            assertThat(d.getChannel()).isEqualTo(NotificationDelivery.Channel.SMS);
            assertThat(d.getDeliveryStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.PENDING);
        });
        assertThat(deliveries).noneMatch(d -> d.getChannel() == NotificationDelivery.Channel.EMAIL);
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
        assertThat(pref.pushEnabled()).isTrue();
        assertThat(pref.smsEnabled()).isFalse();
    }

    @Test
    void getPreference_defaultsEmailDisabledForStudent_whenNoRecordExists() {
        User studentUser = newUser("notif.student.pref");
        Student student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-PREF-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        studentRepository.save(student);

        NotificationPreferenceResponse pref = notificationService.getPreference(
                studentUser.getId(), Notification.NotificationType.GRADE_PUBLISHED);

        assertThat(pref.emailEnabled()).isFalse();
        assertThat(pref.smsEnabled()).isTrue();
    }

    @Test
    void getPreference_defaultsSmsEnabledForParent_whenNoRecordExists() {
        User parentUser = newUser("notif.parent.pref");
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parentRepository.save(parent);

        NotificationPreferenceResponse pref = notificationService.getPreference(
                parentUser.getId(), Notification.NotificationType.GRADE_PUBLISHED);

        assertThat(pref.smsEnabled()).isTrue();
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
