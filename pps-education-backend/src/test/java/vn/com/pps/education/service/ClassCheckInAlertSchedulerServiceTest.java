package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.ClassSessionCheckIn;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.repository.ClassSessionCheckInRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.NotificationDeliveryRepository;
import vn.com.pps.education.repository.NotificationRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-71 mở rộng (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21): cảnh báo giáo viên
 * chưa nhận lớp (tới giờ) / không nhận lớp (hết giờ) — PUSH tới Quản lý điểm trường, EMAIL tới giáo
 * viên; idempotent theo buổi; tôn trọng cờ bật/tắt; bỏ qua buổi đã nhận lớp/CANCELLED. Mốc thời gian
 * "now" được truyền cố định vào job để không phụ thuộc giờ chạy test.
 */
@Transactional
class ClassCheckInAlertSchedulerServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();
    /** Mốc giả lập 10:00 hôm nay (giờ hệ thống) — mọi buổi học trong test đặt quanh mốc này. */
    private static final LocalTime NOW = LocalTime.of(10, 0);

    @Autowired
    private ClassCheckInAlertSchedulerService scheduler;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private SiteManagerRepository siteManagerRepository;
    @Autowired
    private CurriculumRepository curriculumRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    @Autowired
    private ClassSessionRepository classSessionRepository;
    @Autowired
    private ClassSessionCheckInRepository classSessionCheckInRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User teacher;
    private User manager;
    private Site site;
    private SchoolClass schoolClass;

    @BeforeEach
    void setUp() {
        teacher = newUser("teacher");
        manager = newUser("manager");
        site = newSite();
        schoolClass = newSchoolClass(site, teacher);

        SiteManager sm = new SiteManager();
        sm.setSite(site);
        sm.setUser(manager);
        sm.setRoleType(SiteManager.RoleType.SITE_MANAGER);
        sm.setAssignedFrom(LocalDate.now().minusMonths(1));
        sm.setAssignedBy(manager);
        siteManagerRepository.save(sm);

        setEnabled(true);
        jdbcTemplate.update("UPDATE system_settings SET setting_value = '5'::jsonb WHERE setting_key = 'class_checkin_alert.late_after_minutes'");
    }

    @Test
    void lateAlert_MainFlow_pushToSiteManagerAndEmailToTeacherWhenStartPassedWithoutCheckIn() {
        // Bắt đầu 09:50, chưa nhận lớp, now = 10:00 > 09:50 + 5 phút trễ cho phép
        ClassSession session = newSession(LocalTime.of(9, 50), LocalTime.of(11, 30), ClassSession.Status.SCHEDULED);

        scheduler.runAlertScan(at(NOW));

        Notification managerNotif = single(manager, Notification.NotificationType.CLASS_CHECKIN_LATE_ALERT);
        assertThat(managerNotif.getTitle()).contains(schoolClass.getName()).contains(teacher.getFullName()).contains("chưa nhận lớp");
        assertThat(channelsOf(managerNotif)).containsExactlyInAnyOrder(NotificationDelivery.Channel.IN_APP, NotificationDelivery.Channel.PUSH);

        Notification teacherNotif = single(teacher, Notification.NotificationType.CLASS_CHECKIN_LATE_ALERT);
        assertThat(channelsOf(teacherNotif)).containsExactlyInAnyOrder(NotificationDelivery.Channel.IN_APP, NotificationDelivery.Channel.EMAIL);

        ClassSession reloaded = classSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getCheckinLateAlertSentAt()).isNotNull();
        assertThat(reloaded.getCheckinAbsentAlertSentAt()).isNull();
        // Chưa hết giờ -> chưa có cảnh báo "không nhận lớp"
        assertThat(count(manager, Notification.NotificationType.CLASS_CHECKIN_ABSENT_ALERT)).isZero();
    }

    @Test
    void lateAlert_A1_notSentWhileStillWithinGraceMinutes() {
        // Bắt đầu 09:57, trễ cho phép 5 phút -> 10:00 vẫn trong khoảng ân hạn
        newSession(LocalTime.of(9, 57), LocalTime.of(11, 30), ClassSession.Status.SCHEDULED);

        scheduler.runAlertScan(at(NOW));

        assertThat(count(manager, Notification.NotificationType.CLASS_CHECKIN_LATE_ALERT)).isZero();
        assertThat(count(teacher, Notification.NotificationType.CLASS_CHECKIN_LATE_ALERT)).isZero();
    }

    @Test
    void lateAlert_A2_idempotentAcrossRepeatedScans() {
        newSession(LocalTime.of(9, 30), LocalTime.of(11, 30), ClassSession.Status.SCHEDULED);

        scheduler.runAlertScan(at(NOW));
        scheduler.runAlertScan(at(NOW.plusMinutes(1)));
        scheduler.runAlertScan(at(NOW.plusMinutes(2)));

        assertThat(count(manager, Notification.NotificationType.CLASS_CHECKIN_LATE_ALERT)).isEqualTo(1);
        assertThat(count(teacher, Notification.NotificationType.CLASS_CHECKIN_LATE_ALERT)).isEqualTo(1);
    }

    @Test
    void lateAlert_A3_skippedWhenSessionAlreadyCheckedIn() {
        ClassSession session = newSession(LocalTime.of(9, 30), LocalTime.of(11, 30), ClassSession.Status.SCHEDULED);
        checkIn(session, ClassSessionCheckIn.Status.ON_TIME);

        scheduler.runAlertScan(at(NOW));

        assertThat(count(manager, Notification.NotificationType.CLASS_CHECKIN_LATE_ALERT)).isZero();
        assertThat(classSessionRepository.findById(session.getId()).orElseThrow().getCheckinLateAlertSentAt()).isNull();
    }

    @Test
    void lateAlert_A4_skippedForCancelledSession() {
        newSession(LocalTime.of(9, 30), LocalTime.of(11, 30), ClassSession.Status.CANCELLED);

        scheduler.runAlertScan(at(NOW));

        assertThat(count(manager, Notification.NotificationType.CLASS_CHECKIN_LATE_ALERT)).isZero();
    }

    @Test
    void absentAlert_MainFlow_sentAfterSessionEndWithoutCheckIn() {
        // 07:00–08:30, đã kết thúc trước now = 10:00, không có nhận lớp
        ClassSession session = newSession(LocalTime.of(7, 0), LocalTime.of(8, 30), ClassSession.Status.SCHEDULED);

        scheduler.runAlertScan(at(NOW));

        Notification managerNotif = single(manager, Notification.NotificationType.CLASS_CHECKIN_ABSENT_ALERT);
        assertThat(managerNotif.getTitle()).contains(teacher.getFullName()).contains("không nhận lớp");
        assertThat(channelsOf(managerNotif)).containsExactlyInAnyOrder(NotificationDelivery.Channel.IN_APP, NotificationDelivery.Channel.PUSH);

        Notification teacherNotif = single(teacher, Notification.NotificationType.CLASS_CHECKIN_ABSENT_ALERT);
        assertThat(channelsOf(teacherNotif)).containsExactlyInAnyOrder(NotificationDelivery.Channel.IN_APP, NotificationDelivery.Channel.EMAIL);

        ClassSession reloaded = classSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getCheckinAbsentAlertSentAt()).isNotNull();
        // Cùng lần quét, buổi này cũng thoả "chưa nhận lớp" (đã qua giờ bắt đầu) -> 2 cảnh báo cùng lúc, mỗi loại 1 lần
        assertThat(reloaded.getCheckinLateAlertSentAt()).isNotNull();
    }

    @Test
    void absentAlert_A1_notSentWhenTeacherCheckedInLate() {
        ClassSession session = newSession(LocalTime.of(7, 0), LocalTime.of(8, 30), ClassSession.Status.SCHEDULED);
        checkIn(session, ClassSessionCheckIn.Status.LATE);

        scheduler.runAlertScan(at(NOW));

        assertThat(count(manager, Notification.NotificationType.CLASS_CHECKIN_ABSENT_ALERT)).isZero();
        assertThat(count(teacher, Notification.NotificationType.CLASS_CHECKIN_ABSENT_ALERT)).isZero();
    }

    @Test
    void alerts_A5_notSentWhenSettingDisabled() {
        newSession(LocalTime.of(7, 0), LocalTime.of(8, 30), ClassSession.Status.SCHEDULED);
        setEnabled(false);

        scheduler.runAlertScan(); // entry cron -- đọc cờ bật/tắt

        assertThat(count(manager, Notification.NotificationType.CLASS_CHECKIN_LATE_ALERT)).isZero();
        assertThat(count(manager, Notification.NotificationType.CLASS_CHECKIN_ABSENT_ALERT)).isZero();
    }

    @Test
    void alerts_A6_cmTeacherAlsoEmailedWhenAssigned() {
        User cm = newUser("cm");
        ClassSession session = newSession(LocalTime.of(9, 30), LocalTime.of(11, 30), ClassSession.Status.SCHEDULED);
        session.setCmTeacher(cm);
        classSessionRepository.save(session);

        scheduler.runAlertScan(at(NOW));

        assertThat(count(cm, Notification.NotificationType.CLASS_CHECKIN_LATE_ALERT)).isEqualTo(1);
        assertThat(count(teacher, Notification.NotificationType.CLASS_CHECKIN_LATE_ALERT)).isEqualTo(1);
    }

    // ----- helpers -----

    private static OffsetDateTime at(LocalTime time) {
        return LocalDate.now().atTime(time).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private void setEnabled(boolean enabled) {
        jdbcTemplate.update("UPDATE system_settings SET setting_value = ?::jsonb WHERE setting_key = 'class_checkin_alert.enabled'",
                Boolean.toString(enabled));
    }

    private List<Notification> of(User user, Notification.NotificationType type) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 50))
                .getContent().stream().filter(n -> n.getNotificationType() == type).toList();
    }

    private long count(User user, Notification.NotificationType type) {
        return of(user, type).size();
    }

    private Notification single(User user, Notification.NotificationType type) {
        List<Notification> list = of(user, type);
        assertThat(list).hasSize(1);
        return list.get(0);
    }

    private List<NotificationDelivery.Channel> channelsOf(Notification n) {
        return notificationDeliveryRepository.findByNotificationId(n.getId()).stream().map(NotificationDelivery::getChannel).toList();
    }

    private void checkIn(ClassSession session, ClassSessionCheckIn.Status status) {
        ClassSessionCheckIn c = new ClassSessionCheckIn();
        c.setClassSession(session);
        c.setTeacher(teacher);
        c.setCheckInTime(OffsetDateTime.now());
        c.setStatus(status);
        c.setLatitude(21.0285);
        c.setLongitude(105.8542);
        c.setSite(site);
        classSessionCheckInRepository.save(c);
    }

    private User newUser(String prefix) {
        long n = SEQ.incrementAndGet();
        User user = new User();
        user.setUsername("checkin.alert." + prefix + "." + n);
        user.setEmail("checkin.alert." + prefix + "." + n + "@pps.edu.vn");
        user.setFullName("CheckIn Alert " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }

    private Site newSite() {
        Site s = new Site();
        s.setCode("SITE-CIA-" + SEQ.incrementAndGet());
        s.setName("Alert Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private SchoolClass newSchoolClass(Site site, User creator) {
        Curriculum curriculum = new Curriculum();
        curriculum.setCode("CUR-CIA-" + SEQ.incrementAndGet());
        curriculum.setName("Test curriculum");
        curriculum.setClassCategory(Curriculum.ClassCategory.MAIN);
        curriculum.setCreatedBy(creator);
        curriculum = curriculumRepository.save(curriculum);

        SchoolClass sc = new SchoolClass();
        sc.setClassCode("CLS-CIA-" + SEQ.incrementAndGet());
        sc.setName("Alert class " + SEQ.get());
        sc.setSite(site);
        sc.setCurriculum(curriculum);
        sc.setClassType(SchoolClass.ClassType.OPEN);
        sc.setMaxStudents(20);
        sc.setStartDate(LocalDate.now());
        sc.setColor("#F97316");
        sc.setCreatedBy(creator);
        return schoolClassRepository.save(sc);
    }

    private ClassSession newSession(LocalTime start, LocalTime end, ClassSession.Status status) {
        ClassSession session = new ClassSession();
        session.setSchoolClass(schoolClass);
        session.setSessionDate(LocalDate.now());
        session.setStartTime(start);
        session.setEndTime(end);
        session.setPrimaryTeacher(teacher);
        session.setCreatedBy(teacher);
        session.setStatus(status);
        return classSessionRepository.save(session);
    }
}
