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
import java.util.Map;
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
    void notify_ATTENDANCE_PRESENT_suppressesEmail_evenWhenPreferenceExplicitlyEnabled() {
        notificationService.upsertPreference(recipient.getId(), Notification.NotificationType.ATTENDANCE_PRESENT,
                new NotificationPreferenceRequest(true, true, false, false, false));

        Notification notification = notificationService.notify(recipient.getId(),
                Notification.NotificationType.ATTENDANCE_PRESENT, "Có mặt đầy đủ", "Con bạn có mặt đầy đủ hôm nay");

        List<NotificationDelivery> deliveries = notificationDeliveryRepository.findAll().stream()
                .filter(d -> d.getNotification().getId().equals(notification.getId()))
                .toList();

        assertThat(deliveries).hasSize(1);
        assertThat(deliveries.get(0).getChannel()).isEqualTo(NotificationDelivery.Channel.IN_APP);
        assertThat(deliveries.get(0).getDeliveryStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.SENT);
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

    // ===== Toạ độ điều hướng trên NotificationResponse (Plan link hoá thông báo, 2026-09-22) =====
    // Mỗi entityType/notificationType có 1 test riêng (testing.md: 1 luồng = 1 test) — kiểm tra đúng
    // field nào được promote từ metadata, field không liên quan phải null.

    @Test
    void listMine_attendanceMark_exposesStudentIdAndClassId() {
        notificationService.notify(recipient.getId(), Notification.NotificationType.ATTENDANCE_ABSENT, "Vắng", "x",
                Map.of("studentId", 11, "classId", 22, "classSessionId", 33), "ATTENDANCE_MARK", 44L,
                Notification.Priority.HIGH, null);

        NotificationResponse r = firstMine();

        assertThat(r.studentId()).isEqualTo(11L);
        assertThat(r.classId()).isEqualTo(22L);
        assertThat(r.exerciseAssignmentId()).isNull();
        assertThat(r.reviewVideoAssignmentId()).isNull();
    }

    @Test
    void listMine_gradeEntry_exposesStudentIdAndClassId() {
        notificationService.notify(recipient.getId(), Notification.NotificationType.GRADE_PUBLISHED, "Điểm", "x",
                Map.of("studentId", 11, "classId", 22, "gradeEvaluationComponentId", 5), "GRADE_ENTRY", 44L,
                Notification.Priority.NORMAL, null);

        NotificationResponse r = firstMine();

        assertThat(r.studentId()).isEqualTo(11L);
        assertThat(r.classId()).isEqualTo(22L);
        assertThat(r.exerciseAssignmentId()).isNull();
    }

    @Test
    void listMine_gradePeriodResult_exposesStudentIdAndClassId() {
        notificationService.notify(recipient.getId(), Notification.NotificationType.GRADE_PUBLISHED, "Tổng kết", "x",
                Map.of("studentId", 11, "classId", 22, "academicTermId", 7), "GRADE_PERIOD_RESULT", 44L,
                Notification.Priority.NORMAL, null);

        NotificationResponse r = firstMine();

        assertThat(r.studentId()).isEqualTo(11L);
        assertThat(r.classId()).isEqualTo(22L);
    }

    @Test
    void listMine_homeworkDueSoonReminder_exposesStudentClassAndExerciseAssignmentId() {
        notificationService.notify(recipient.getId(), Notification.NotificationType.HOMEWORK_DUE_SOON_REMINDER, "Sắp hạn", "x",
                Map.of("studentId", 11, "classId", 22, "exerciseAssignmentId", 55), "STUDENT", 11L,
                Notification.Priority.NORMAL, null);

        NotificationResponse r = firstMine();

        assertThat(r.studentId()).isEqualTo(11L);
        assertThat(r.classId()).isEqualTo(22L);
        assertThat(r.exerciseAssignmentId()).isEqualTo(55L);
        assertThat(r.reviewVideoAssignmentId()).isNull();
    }

    @Test
    void listMine_homeworkMissReminder_exposesReviewVideoAssignmentId() {
        notificationService.notify(recipient.getId(), Notification.NotificationType.HOMEWORK_MISS_REMINDER, "Thiếu BTVN", "x",
                Map.of("studentId", 11, "classId", 22, "reviewVideoAssignmentId", 66), "STUDENT", 11L,
                Notification.Priority.NORMAL, null);

        NotificationResponse r = firstMine();

        assertThat(r.studentId()).isEqualTo(11L);
        assertThat(r.classId()).isEqualTo(22L);
        assertThat(r.exerciseAssignmentId()).isNull();
        assertThat(r.reviewVideoAssignmentId()).isEqualTo(66L);
    }

    @Test
    void listMine_homeworkMissWarningWithBothChannels_exposesBothAssignmentIds() {
        notificationService.notify(recipient.getId(), Notification.NotificationType.HOMEWORK_MISS_WARNING, "Cảnh báo", "x",
                Map.of("studentId", 11, "classId", 22, "exerciseAssignmentId", 55, "reviewVideoAssignmentId", 66), "STUDENT", 11L,
                Notification.Priority.HIGH, null);

        NotificationResponse r = firstMine();

        assertThat(r.exerciseAssignmentId()).isEqualTo(55L);
        assertThat(r.reviewVideoAssignmentId()).isEqualTo(66L);
    }

    @Test
    void listMine_homeworkMissParentMeetingInvite_exposesStudentAndClassOnly() {
        notificationService.notify(recipient.getId(), Notification.NotificationType.HOMEWORK_MISS_PARENT_MEETING_INVITE, "Thư mời", "x",
                Map.of("studentId", 11, "classId", 22), "STUDENT", 11L, Notification.Priority.URGENT, null);

        NotificationResponse r = firstMine();

        assertThat(r.studentId()).isEqualTo(11L);
        assertThat(r.classId()).isEqualTo(22L);
        assertThat(r.exerciseAssignmentId()).isNull();
        assertThat(r.reviewVideoAssignmentId()).isNull();
    }

    @Test
    void listMine_homeworkMissReminderNonConsecutive_exposesStudentClassAndAssignment() {
        notificationService.notify(recipient.getId(), Notification.NotificationType.HOMEWORK_MISS_REMINDER_NON_CONSECUTIVE, "Nhắc", "x",
                Map.of("studentId", 11, "classId", 22, "exerciseAssignmentId", 55), "STUDENT", 11L,
                Notification.Priority.NORMAL, null);

        NotificationResponse r = firstMine();

        assertThat(r.studentId()).isEqualTo(11L);
        assertThat(r.classId()).isEqualTo(22L);
        assertThat(r.exerciseAssignmentId()).isEqualTo(55L);
    }

    @Test
    void listMine_exerciseAssignment_exposesEntityIdAsExerciseAssignmentId() {
        notificationService.notify(recipient.getId(), Notification.NotificationType.OTHER, "Bài mới", "x",
                Map.of("classId", 22), "EXERCISE_ASSIGNMENT", 55L, Notification.Priority.NORMAL, null);

        NotificationResponse r = firstMine();

        assertThat(r.exerciseAssignmentId()).isEqualTo(55L);
        assertThat(r.classId()).isEqualTo(22L);
        assertThat(r.studentId()).isNull();
        assertThat(r.reviewVideoAssignmentId()).isNull();
    }

    @Test
    void listMine_reviewVideoAssignment_exposesEntityIdAsReviewVideoAssignmentId() {
        notificationService.notify(recipient.getId(), Notification.NotificationType.OTHER, "Video mới", "x",
                Map.of("classId", 22), "REVIEW_VIDEO_ASSIGNMENT", 66L, Notification.Priority.NORMAL, null);

        NotificationResponse r = firstMine();

        assertThat(r.reviewVideoAssignmentId()).isEqualTo(66L);
        assertThat(r.classId()).isEqualTo(22L);
        assertThat(r.exerciseAssignmentId()).isNull();
    }

    @Test
    void listMine_studentEntityWithNonHomeworkType_leavesNavigationFieldsNull() {
        // EXAM_INTEGRITY_VIOLATION cũng gắn entityType=STUDENT nhưng không thuộc nhóm nhắc BTVN — không promote gì.
        notificationService.notify(recipient.getId(), Notification.NotificationType.EXAM_INTEGRITY_VIOLATION, "Vi phạm", "x",
                Map.of("studentId", 11, "classId", 22), "STUDENT", 11L, Notification.Priority.HIGH, null);

        NotificationResponse r = firstMine();

        assertThat(r.studentId()).isNull();
        assertThat(r.classId()).isNull();
    }

    @Test
    void listMine_notificationWithoutEntityType_leavesNavigationFieldsNull() {
        notificationService.notify(recipient.getId(), Notification.NotificationType.SYSTEM_ANNOUNCEMENT, "Hệ thống", "x");

        NotificationResponse r = firstMine();

        assertThat(r.studentId()).isNull();
        assertThat(r.classId()).isNull();
        assertThat(r.exerciseAssignmentId()).isNull();
        assertThat(r.reviewVideoAssignmentId()).isNull();
    }

    @Test
    void listMine_attendanceMarkWithoutClassIdInMetadata_leavesClassIdNull() {
        // Thông báo tạo trước 2026-09-22 chưa có classId trong metadata — không được vỡ, chỉ thiếu classId.
        notificationService.notify(recipient.getId(), Notification.NotificationType.ATTENDANCE_LATE, "Muộn", "x",
                Map.of("studentId", 11), "ATTENDANCE_MARK", 44L, Notification.Priority.NORMAL, null);

        NotificationResponse r = firstMine();

        assertThat(r.studentId()).isEqualTo(11L);
        assertThat(r.classId()).isNull();
    }

    private NotificationResponse firstMine() {
        return notificationService.listMine(recipient.getId(), PageRequest.of(0, 10)).getContent().get(0);
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
