package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.ClassSessionCheckInRequest;
import vn.com.pps.education.dto.ClassSessionCheckInResponse;
import vn.com.pps.education.dto.ClassSessionCheckInStatusResponse;
import vn.com.pps.education.exception.AlreadyCheckedInException;
import vn.com.pps.education.exception.ClassSessionNotCheckableException;
import vn.com.pps.education.exception.NotAssignedTeacherForSessionException;
import vn.com.pps.education.exception.OutsideAttendanceWindowException;
import vn.com.pps.education.exception.OutsideGpsRadiusException;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * UC-71: Nhận lớp — Main Flow, A1 (quá sớm), A2 (hết cửa sổ), A3 (đã
 * nhận), A4 (GPS ngoài bán kính), sai GV được phân công, buổi
 * CANCELLED/RESCHEDULED, và listEffectiveStatus (NOT_YET_OPEN/PENDING/
 * ABSENT tính khi đọc). Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Transactional
class ClassSessionCheckInServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();
    // Hà Nội (toạ độ điểm trường trong test)
    private static final double SITE_LAT = 21.0285;
    private static final double SITE_LNG = 105.8542;
    // TP.HCM -- cách xa hơn nhiều lần bán kính cho phép (200m mặc định)
    private static final double FAR_LAT = 10.8231;
    private static final double FAR_LNG = 106.6297;

    @Autowired
    private ClassSessionCheckInService classSessionCheckInService;

    @Autowired
    private ClassSessionService classSessionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private CurriculumRepository curriculumRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Autowired
    private ClassSessionRepository classSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User teacher;
    private Site site;
    private SchoolClass schoolClass;

    @BeforeEach
    void setUp() {
        teacher = newUser();
        site = newSite();
        schoolClass = newSchoolClass(site, teacher);
    }

    @Test
    void checkIn_UC71_MainFlow_onTimeWhenBeforeStartWithinWindow() {
        LocalTime now = LocalTime.now();
        ClassSession session = newSession(plusMinutesClamped(now, 5), plusHoursClamped(now, 2), ClassSession.Status.SCHEDULED);

        ClassSessionCheckInResponse response = classSessionCheckInService.checkIn(
                session.getId(), teacher.getId(), new ClassSessionCheckInRequest(SITE_LAT, SITE_LNG));

        assertThat(response.status()).isEqualTo("ON_TIME");
        assertThat(response.classSessionId()).isEqualTo(session.getId());
        assertThat(response.teacherId()).isEqualTo(teacher.getId());
    }

    @Test
    void checkIn_UC71_MainFlow_lateWhenCheckingInDuringClass() {
        LocalTime now = LocalTime.now();
        ClassSession session = newSession(minusMinutesClamped(now, 10), plusHoursClamped(now, 1), ClassSession.Status.SCHEDULED);

        ClassSessionCheckInResponse response = classSessionCheckInService.checkIn(
                session.getId(), teacher.getId(), new ClassSessionCheckInRequest(SITE_LAT, SITE_LNG));

        assertThat(response.status()).isEqualTo("LATE");
    }

    @Test
    void checkIn_UC71_A1_rejectsTooEarlyBeforeWindowOpens() {
        LocalTime now = LocalTime.now();
        ClassSession session = newSession(plusHoursClamped(now, 1), plusHoursClamped(now, 2), ClassSession.Status.SCHEDULED);

        assertThatThrownBy(() -> classSessionCheckInService.checkIn(
                session.getId(), teacher.getId(), new ClassSessionCheckInRequest(SITE_LAT, SITE_LNG)))
                .isInstanceOf(OutsideAttendanceWindowException.class);
    }

    @Test
    void checkIn_UC71_A2_rejectsAfterWindowClosed() {
        LocalTime now = LocalTime.now();
        ClassSession session = newSession(minusHoursClamped(now, 2), minusMinutesClamped(now, 10), ClassSession.Status.SCHEDULED);

        assertThatThrownBy(() -> classSessionCheckInService.checkIn(
                session.getId(), teacher.getId(), new ClassSessionCheckInRequest(SITE_LAT, SITE_LNG)))
                .isInstanceOf(OutsideAttendanceWindowException.class);
    }

    @Test
    void checkIn_UC71_A3_rejectsDuplicateCheckInForSameSession() {
        LocalTime now = LocalTime.now();
        ClassSession session = newSession(plusMinutesClamped(now, 5), plusHoursClamped(now, 2), ClassSession.Status.SCHEDULED);
        classSessionCheckInService.checkIn(session.getId(), teacher.getId(), new ClassSessionCheckInRequest(SITE_LAT, SITE_LNG));

        assertThatThrownBy(() -> classSessionCheckInService.checkIn(
                session.getId(), teacher.getId(), new ClassSessionCheckInRequest(SITE_LAT, SITE_LNG)))
                .isInstanceOf(AlreadyCheckedInException.class);
    }

    @Test
    void checkIn_UC71_A4_rejectsWhenOutsideGpsRadius() {
        LocalTime now = LocalTime.now();
        ClassSession session = newSession(plusMinutesClamped(now, 5), plusHoursClamped(now, 2), ClassSession.Status.SCHEDULED);

        assertThatThrownBy(() -> classSessionCheckInService.checkIn(
                session.getId(), teacher.getId(), new ClassSessionCheckInRequest(FAR_LAT, FAR_LNG)))
                .isInstanceOf(OutsideGpsRadiusException.class);
    }

    @Test
    void checkIn_UC71_rejectsWhenActorNotAssignedTeacherForSession() {
        LocalTime now = LocalTime.now();
        ClassSession session = newSession(plusMinutesClamped(now, 5), plusHoursClamped(now, 2), ClassSession.Status.SCHEDULED);
        User otherTeacher = newUser();

        assertThatThrownBy(() -> classSessionCheckInService.checkIn(
                session.getId(), otherTeacher.getId(), new ClassSessionCheckInRequest(SITE_LAT, SITE_LNG)))
                .isInstanceOf(NotAssignedTeacherForSessionException.class);
    }

    @Test
    void checkIn_UC71_rejectsWhenSessionCancelled() {
        LocalTime now = LocalTime.now();
        ClassSession session = newSession(plusMinutesClamped(now, 5), plusHoursClamped(now, 2), ClassSession.Status.CANCELLED);

        assertThatThrownBy(() -> classSessionCheckInService.checkIn(
                session.getId(), teacher.getId(), new ClassSessionCheckInRequest(SITE_LAT, SITE_LNG)))
                .isInstanceOf(ClassSessionNotCheckableException.class);
    }

    @Test
    void listEffectiveStatus_UC71_computesNotYetOpenPendingAbsentAndCheckedInPerSession() {
        LocalTime now = LocalTime.now();
        ClassSession notYetOpenSession = newSession(plusHoursClamped(now, 1), plusHoursClamped(now, 2), ClassSession.Status.SCHEDULED);
        ClassSession pendingSession = newSession(minusMinutesClamped(now, 5), plusHoursClamped(now, 1), ClassSession.Status.SCHEDULED);
        ClassSession absentSession = newSession(minusHoursClamped(now, 2), minusMinutesClamped(now, 10), ClassSession.Status.SCHEDULED);
        ClassSession checkedInSession = newSession(minusMinutesClamped(now, 20), plusHoursClamped(now, 1), ClassSession.Status.SCHEDULED);
        classSessionCheckInService.checkIn(checkedInSession.getId(), teacher.getId(), new ClassSessionCheckInRequest(SITE_LAT, SITE_LNG));
        ClassSession cancelledSession = newSession(minusMinutesClamped(now, 5), plusMinutesClamped(now, 55), ClassSession.Status.CANCELLED);

        var sessions = classSessionService.listMySessions(teacher.getId(), LocalDate.now(), LocalDate.now());
        List<ClassSessionCheckInStatusResponse> statuses = classSessionCheckInService.listEffectiveStatus(sessions);

        assertThat(statuses).extracting("classSessionId", "effectiveStatus")
                .contains(
                        tuple(notYetOpenSession.getId(), "NOT_YET_OPEN"),
                        tuple(pendingSession.getId(), "PENDING"),
                        tuple(absentSession.getId(), "ABSENT"),
                        tuple(checkedInSession.getId(), "LATE"));
        assertThat(statuses).extracting("classSessionId").doesNotContain(cancelledSession.getId());
    }

    private User newUser() {
        User user = new User();
        user.setUsername("checkin.user." + SEQ.incrementAndGet());
        user.setEmail("checkin.user." + SEQ.incrementAndGet() + "@pps.edu.vn");
        user.setFullName("CheckIn Test User");
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }

    private Site newSite() {
        Site site = new Site();
        site.setCode("SITE-CHECKIN-" + SEQ.incrementAndGet());
        site.setName("Test Site");
        site.setSiteType(Site.SiteType.OWNED);
        site = siteRepository.save(site);
        jdbcTemplate.update(
                "UPDATE sites SET geo_location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography WHERE id = ?",
                SITE_LNG, SITE_LAT, site.getId());
        return site;
    }

    private SchoolClass newSchoolClass(Site site, User creator) {
        Curriculum curriculum = new Curriculum();
        curriculum.setCode("CUR-CHECKIN-" + SEQ.incrementAndGet());
        curriculum.setName("Test curriculum");
        curriculum.setClassCategory(Curriculum.ClassCategory.MAIN);
        curriculum.setCreatedBy(creator);
        curriculum = curriculumRepository.save(curriculum);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setClassCode("CLS-CHECKIN-" + SEQ.incrementAndGet());
        schoolClass.setName("Test class");
        schoolClass.setSite(site);
        schoolClass.setCurriculum(curriculum);
        schoolClass.setClassType(SchoolClass.ClassType.OPEN);
        schoolClass.setMaxStudents(20);
        schoolClass.setStartDate(LocalDate.now());
        schoolClass.setColor("#F97316");
        schoolClass.setCreatedBy(creator);
        return schoolClassRepository.save(schoolClass);
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

    /**
     * Trừ N giờ nhưng giữ trong cùng ngày -- class_sessions có
     * chk_session_time CHECK (end_time > start_time) (V14); trừ giờ thẳng từ
     * LocalTime.now() có thể wrap qua ngày. Clamp về LocalTime.MIN nếu bị
     * wrap. Cùng idiom AttendanceServiceTest.
     */
    private static LocalTime minusHoursClamped(LocalTime base, long hours) {
        LocalTime candidate = base.minusHours(hours);
        return candidate.isAfter(base) ? LocalTime.MIN : candidate;
    }

    private static LocalTime plusHoursClamped(LocalTime base, long hours) {
        LocalTime candidate = base.plusHours(hours);
        return candidate.isBefore(base) ? LocalTime.of(23, 59, 59) : candidate;
    }

    private static LocalTime minusMinutesClamped(LocalTime base, long minutes) {
        LocalTime candidate = base.minusMinutes(minutes);
        return candidate.isAfter(base) ? LocalTime.MIN : candidate;
    }

    private static LocalTime plusMinutesClamped(LocalTime base, long minutes) {
        LocalTime candidate = base.plusMinutes(minutes);
        return candidate.isBefore(base) ? LocalTime.of(23, 59, 59) : candidate;
    }
}
