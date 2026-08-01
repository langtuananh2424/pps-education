package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Room;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.BulkCreateClassSessionRequest;
import vn.com.pps.education.dto.BulkCreateClassSessionResponse;
import vn.com.pps.education.dto.CancelClassSessionRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateClassSessionRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.RescheduleClassSessionRequest;
import vn.com.pps.education.dto.SessionPeriodResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.InvalidClassSessionStatusTransitionException;
import vn.com.pps.education.exception.MakeupSessionAlreadyLinkedException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RoomConflictException;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.RoomRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-48: Xếp lịch buổi học (FR-ACA-05) — Main Flow (tạo buổi + tự sinh
 * session_periods, FR-FAC-03 trùng phòng), A2 (hủy buổi), A3 (dời lịch).
 */
@Transactional
class ClassSessionServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ClassSessionService classSessionService;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private SiteManagerRepository siteManagerRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ClassSessionRepository classSessionRepository;

    private User headAcademic;
    private User teacher;
    private ClassResponse schoolClass;
    private Room room;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());
        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        room = newRoom(site, false);
    }

    @Test
    void createSession_MainFlow_autoGeneratesDefaultPeriods() {
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        assertThat(session.status()).isEqualTo("SCHEDULED");
        List<SessionPeriodResponse> periods = classSessionService.listPeriods(session.id(), headAcademic.getId());
        assertThat(periods).hasSize(2); // academic.default_periods_per_session = 2
        assertThat(periods.get(0).periodNumber()).isEqualTo(1);
        assertThat(periods.get(0).startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(periods.get(1).endTime()).isEqualTo(LocalTime.of(9, 40));
    }

    /** Bổ sung (đã xác nhận với người dùng 2026-07-29): loại giáo viên (Việt Nam/nước ngoài) của buổi học. */
    @Test
    void createSession_boSung_setsTeacherTypeWhenProvided() {
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR", "FOREIGN", null),
                headAcademic.getId());

        assertThat(session.teacherType()).isEqualTo("FOREIGN");
    }

    @Test
    void createSession_boSung_teacherTypeNullWhenNotProvided() {
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        assertThat(session.teacherType()).isNull();
    }

    @Test
    void createSession_FRFAC03_rejectsOverlappingRoomBooking() {
        LocalDate date = LocalDate.now().plusDays(2);
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        assertThatThrownBy(() -> classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, LocalTime.of(9, 0), LocalTime.of(10, 30), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId()))
                .isInstanceOf(RoomConflictException.class);
    }

    @Test
    void createSession_FRFAC03_allowsOverlapWhenRoomIsFlexible() {
        Site site = newSite();
        Room flexibleRoom = newRoom(site, true);
        LocalDate date = LocalDate.now().plusDays(3);
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, LocalTime.of(8, 0), LocalTime.of(9, 40), flexibleRoom.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        // GV và lớp khác buổi đầu — cô lập đúng hành vi "phòng flexible bỏ qua room-conflict" đang
        // test, không lẫn với chặn trùng giờ GV/Lớp (bổ sung ngoài SDD gốc, đã xác nhận với người
        // dùng 2026-07-30): cùng GV/lớp chồng giờ phải bị chặn dù phòng flexible.
        User otherTeacher = newUser("teacher2");
        assignRole(otherTeacher, "TEACHER");
        CurriculumResponse otherCurriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse otherActiveCurriculum = curriculumService.update(otherCurriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());
        ClassResponse otherClass = classService.create(
                new CreateClassRequest(classCode(), "8A3", site.getId(), otherActiveCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        ClassSessionResponse second = classSessionService.createSession(otherClass.id(),
                new CreateClassSessionRequest(date, LocalTime.of(9, 0), LocalTime.of(10, 30), flexibleRoom.getId(), otherTeacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        assertThat(second.id()).isNotNull();
    }

    @Test
    void cancelSession_UC48_A2_cancelsScheduledSessionAndFreesRoom() {
        LocalDate date = LocalDate.now().plusDays(4);
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        ClassSessionResponse cancelled = classSessionService.cancelSession(schoolClass.id(), session.id(),
                new CancelClassSessionRequest("Giáo viên nghỉ đột xuất"), headAcademic.getId());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancellationReason()).isEqualTo("Giáo viên nghỉ đột xuất");

        ClassSessionResponse another = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        assertThat(another.id()).isNotNull();
    }

    @Test
    void cancelSession_UC48_rejectsWhenSessionNotScheduled() {
        LocalDate date = LocalDate.now().plusDays(5);
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        classSessionService.cancelSession(schoolClass.id(), session.id(), new CancelClassSessionRequest(null), headAcademic.getId());

        assertThatThrownBy(() -> classSessionService.cancelSession(schoolClass.id(), session.id(),
                new CancelClassSessionRequest("Hủy lần 2"), headAcademic.getId()))
                .isInstanceOf(InvalidClassSessionStatusTransitionException.class);
    }

    @Test
    void rescheduleSession_UC48_A3_createsNewSessionAndMarksOldAsRescheduled() {
        LocalDate oldDate = LocalDate.now().plusDays(6);
        ClassSessionResponse oldSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(oldDate, LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        LocalDate newDate = oldDate.plusDays(1);
        ClassSessionResponse newSession = classSessionService.rescheduleSession(schoolClass.id(), oldSession.id(),
                new RescheduleClassSessionRequest(newDate, LocalTime.of(10, 0), LocalTime.of(11, 40), room.getId(), teacher.getId(),
                        "Phòng bảo trì"),
                headAcademic.getId());

        assertThat(newSession.status()).isEqualTo("SCHEDULED");
        assertThat(newSession.sessionDate()).isEqualTo(newDate);
        List<SessionPeriodResponse> newPeriods = classSessionService.listPeriods(newSession.id(), headAcademic.getId());
        assertThat(newPeriods).hasSize(2);

        List<ClassSessionResponse> sessions = classSessionService.listSessions(schoolClass.id(), headAcademic.getId());
        ClassSessionResponse reloadedOld = sessions.stream().filter(s -> s.id().equals(oldSession.id())).findFirst().orElseThrow();
        assertThat(reloadedOld.status()).isEqualTo("RESCHEDULED");
        assertThat(reloadedOld.cancellationReason()).isEqualTo("Phòng bảo trì");
        assertThat(reloadedOld.rescheduledToSessionId()).isEqualTo(newSession.id());
    }

    /** Bổ sung (đã xác nhận với người dùng 2026-07-29): dời lịch không đổi loại giáo viên — copy nguyên từ buổi cũ. */
    @Test
    void rescheduleSession_boSung_copiesTeacherTypeFromOldSession() {
        LocalDate oldDate = LocalDate.now().plusDays(7);
        ClassSessionResponse oldSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(oldDate, LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", "FOREIGN", null),
                headAcademic.getId());

        ClassSessionResponse newSession = classSessionService.rescheduleSession(schoolClass.id(), oldSession.id(),
                new RescheduleClassSessionRequest(oldDate.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 40), room.getId(), teacher.getId(),
                        "Phòng bảo trì"),
                headAcademic.getId());

        assertThat(newSession.teacherType()).isEqualTo("FOREIGN");
    }

    @Test
    void listSessions_teacherWithoutSiteAssignment_seesNoSessions() {
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(10), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        User outsider = newUser("teacher.outsider.session");
        assignRole(outsider, "TEACHER");

        assertThat(classSessionService.listSessions(schoolClass.id(), outsider.getId())).isEmpty();
        assertThat(classSessionService.listPeriods(session.id(), outsider.getId())).isEmpty();
    }

    @Test
    void listSessions_teacherWithSiteAssignment_seesOwnSiteSessions() {
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(11), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());

        assertThat(classSessionService.listSessions(schoolClass.id(), teacher.getId()))
                .extracting(ClassSessionResponse::id).contains(session.id());
        assertThat(classSessionService.listPeriods(session.id(), teacher.getId())).isNotEmpty();
    }

    /**
     * Bổ sung (audit FE 2026-07-20): resolveAllowedSiteIds trước đây chỉ
     * cộng site theo site_teachers, bỏ sót site_managers -- Quản lý điểm
     * trường không kiêm giáo viên gọi GET /api/classes/{id}/sessions luôn
     * ra rỗng dù phụ trách đúng site của lớp đó.
     */
    @Test
    void listSessions_siteManagerForSite_seesOwnSiteSessions() {
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(12), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        Site managedSite = siteRepository.findById(schoolClass.siteId()).orElseThrow();
        User siteManagerUser = newUser("site.manager.sessions");
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(managedSite);
        siteManager.setUser(siteManagerUser);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(siteManagerUser);
        siteManagerRepository.save(siteManager);

        assertThat(classSessionService.listSessions(schoolClass.id(), siteManagerUser.getId()))
                .extracting(ClassSessionResponse::id).contains(session.id());
        assertThat(classSessionService.listPeriods(session.id(), siteManagerUser.getId())).isNotEmpty();
    }

    @Test
    void rescheduleSession_UC48_FRFAC03_rejectsOverlappingRoomAtNewSlot() {
        LocalDate oldDate = LocalDate.now().plusDays(7);
        ClassSessionResponse oldSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(oldDate, LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        LocalDate blockedDate = oldDate.plusDays(1);
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(blockedDate, LocalTime.of(10, 0), LocalTime.of(11, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        assertThatThrownBy(() -> classSessionService.rescheduleSession(schoolClass.id(), oldSession.id(),
                new RescheduleClassSessionRequest(blockedDate, LocalTime.of(10, 30), LocalTime.of(12, 0), room.getId(), teacher.getId(), null),
                headAcademic.getId()))
                .isInstanceOf(RoomConflictException.class);
    }

    @Test
    void rescheduleSession_UC48_rejectsWhenOldSessionNotScheduled() {
        LocalDate date = LocalDate.now().plusDays(8);
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        classSessionService.cancelSession(schoolClass.id(), session.id(), new CancelClassSessionRequest(null), headAcademic.getId());

        assertThatThrownBy(() -> classSessionService.rescheduleSession(schoolClass.id(), session.id(),
                new RescheduleClassSessionRequest(date.plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), null),
                headAcademic.getId()))
                .isInstanceOf(InvalidClassSessionStatusTransitionException.class);
    }

    @Test
    void bulkCreateSessions_UC56_MainFlow_generatesSessionsOnMatchingWeekdays() {
        LocalDate startDate = nextWeekday(LocalDate.now().plusDays(20), DayOfWeek.MONDAY);
        LocalDate endDate = startDate.plusDays(13); // 2 tuần trọn vẹn -> đúng 2 Monday + 2 Wednesday

        BulkCreateClassSessionResponse response = classSessionService.bulkCreateSessions(schoolClass.id(),
                new BulkCreateClassSessionRequest(startDate, endDate, List.of("MONDAY", "WEDNESDAY"),
                        LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null),
                headAcademic.getId());

        assertThat(response.totalDates()).isEqualTo(4);
        assertThat(response.createdCount()).isEqualTo(4);
        assertThat(response.skippedCount()).isEqualTo(0);
        assertThat(response.created()).hasSize(4)
                .allSatisfy(s -> assertThat(s.sessionDate().getDayOfWeek()).isIn(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
    }

    @Test
    void bulkCreateSessions_UC56_A1_skipsDateWithRoomConflictButContinuesOtherDates() {
        LocalDate startDate = nextWeekday(LocalDate.now().plusDays(40), DayOfWeek.MONDAY);
        LocalDate endDate = startDate.plusDays(13);
        LocalDate conflictDate = startDate.plusDays(7); // Monday thứ 2 trong khoảng 14 ngày

        // Đã có sẵn 1 buổi khác trùng phòng đúng khung giờ vào conflictDate.
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(conflictDate, LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        BulkCreateClassSessionResponse response = classSessionService.bulkCreateSessions(schoolClass.id(),
                new BulkCreateClassSessionRequest(startDate, endDate, List.of("MONDAY"),
                        LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null),
                headAcademic.getId());

        assertThat(response.totalDates()).isEqualTo(2); // 2 Monday trong khoảng 14 ngày
        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(response.skipped().get(0).get("date")).isEqualTo(conflictDate.toString());
    }

    /** Bổ sung (đã xác nhận với người dùng 2026-07-29): 1 giá trị teacherType dùng chung cho cả lô sinh lịch. */
    @Test
    void bulkCreateSessions_boSung_appliesTeacherTypeToAllCreatedSessions() {
        LocalDate startDate = nextWeekday(LocalDate.now().plusDays(100), DayOfWeek.MONDAY);
        LocalDate endDate = startDate.plusDays(13);

        BulkCreateClassSessionResponse response = classSessionService.bulkCreateSessions(schoolClass.id(),
                new BulkCreateClassSessionRequest(startDate, endDate, List.of("MONDAY"),
                        LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", "VIETNAMESE"),
                headAcademic.getId());

        assertThat(response.created()).hasSize(2)
                .allSatisfy(s -> assertThat(s.teacherType()).isEqualTo("VIETNAMESE"));
    }

    /**
     * Bổ sung (đã xác nhận với người dùng 2026-07-29): sessionNumber đếm
     * mọi buổi theo thứ tự session_date, kể cả CANCELLED — buổi bị hủy
     * vẫn giữ đúng số của nó, không làm dồn số các buổi sau.
     */
    @Test
    void listSessions_boSung_sessionNumberCountsAllSessionsInDateOrderIncludingCancelled() {
        LocalDate base = LocalDate.now().plusDays(200);
        ClassSessionResponse session1 = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(base, LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        ClassSessionResponse session2 = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(base.plusDays(2), LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        ClassSessionResponse session3 = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(base.plusDays(4), LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        classSessionService.cancelSession(schoolClass.id(), session2.id(), new CancelClassSessionRequest(null), headAcademic.getId());

        List<ClassSessionResponse> sessions = classSessionService.listSessions(schoolClass.id(), headAcademic.getId());
        assertThat(sessions).filteredOn(s -> s.id().equals(session1.id())).extracting(ClassSessionResponse::sessionNumber).containsExactly(1);
        assertThat(sessions).filteredOn(s -> s.id().equals(session2.id())).extracting(ClassSessionResponse::sessionNumber).containsExactly(2);
        assertThat(sessions).filteredOn(s -> s.id().equals(session3.id())).extracting(ClassSessionResponse::sessionNumber).containsExactly(3);
    }

    /** Bổ sung (đã xác nhận với người dùng 2026-07-29): tab Nhận xét tự chọn buổi hôm nay. */
    @Test
    void listTodaySessions_boSung_returnsSessionScheduledToday() {
        ClassSessionResponse today = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        assertThat(classSessionService.listTodaySessions(schoolClass.id(), headAcademic.getId()))
                .extracting(ClassSessionResponse::id).containsExactly(today.id());
    }

    @Test
    void listTodaySessions_boSung_returnsEmptyWhenNoSessionToday() {
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        assertThat(classSessionService.listTodaySessions(schoolClass.id(), headAcademic.getId())).isEmpty();
    }

    @Test
    void listTodaySessions_boSung_excludesCancelledSessionToday() {
        ClassSessionResponse today = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        classSessionService.cancelSession(schoolClass.id(), today.id(), new CancelClassSessionRequest(null), headAcademic.getId());

        assertThat(classSessionService.listTodaySessions(schoolClass.id(), headAcademic.getId())).isEmpty();
    }

    // ===================== Case 1: liên kết buổi hủy↔bù (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29) =====================

    private ClassSession cancelledSession(LocalDate date) {
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, LocalTime.of(8, 0), LocalTime.of(9, 40), room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        classSessionService.cancelSession(schoolClass.id(), session.id(), new CancelClassSessionRequest(null), headAcademic.getId());
        return classSessionRepository.findById(session.id()).orElseThrow();
    }

    @Test
    void createSession_boSung_linksMakeupSessionToCancelledSession() {
        ClassSession cancelled = cancelledSession(LocalDate.now().plusDays(120));

        ClassSessionResponse makeup = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(121), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "MAKEUP", null, cancelled.getId()),
                headAcademic.getId());

        assertThat(makeup.makeupForSessionId()).isEqualTo(cancelled.getId());
    }

    @Test
    void createSession_boSung_rejectsMakeupWithoutMakeupForSessionId() {
        assertThatThrownBy(() -> classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(122), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "MAKEUP", null, null),
                headAcademic.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSession_boSung_rejectsMakeupForSessionIdWhenNotMakeupType() {
        ClassSession cancelled = cancelledSession(LocalDate.now().plusDays(123));

        assertThatThrownBy(() -> classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(124), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR", null, cancelled.getId()),
                headAcademic.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSession_boSung_rejectsMakeupForSessionNotCancelled() {
        ClassSessionResponse scheduled = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(125), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        assertThatThrownBy(() -> classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(126), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "MAKEUP", null, scheduled.id()),
                headAcademic.getId()))
                .isInstanceOf(InvalidClassSessionStatusTransitionException.class);
    }

    @Test
    void createSession_boSung_rejectsMakeupForSessionAlreadyLinked() {
        ClassSession cancelled = cancelledSession(LocalDate.now().plusDays(127));
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(128), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "MAKEUP", null, cancelled.getId()),
                headAcademic.getId());

        assertThatThrownBy(() -> classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(129), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "MAKEUP", null, cancelled.getId()),
                headAcademic.getId()))
                .isInstanceOf(MakeupSessionAlreadyLinkedException.class);
    }

    @Test
    void rescheduleSession_boSung_copiesMakeupForSessionFromOldSession() {
        ClassSession cancelled = cancelledSession(LocalDate.now().plusDays(130));
        ClassSessionResponse makeup = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(131), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "MAKEUP", null, cancelled.getId()),
                headAcademic.getId());

        ClassSessionResponse rescheduled = classSessionService.rescheduleSession(schoolClass.id(), makeup.id(),
                new RescheduleClassSessionRequest(LocalDate.now().plusDays(132), LocalTime.of(10, 0), LocalTime.of(11, 40),
                        room.getId(), teacher.getId(), "Đổi giờ"),
                headAcademic.getId());

        assertThat(rescheduled.makeupForSessionId()).isEqualTo(cancelled.getId());
    }

    @Test
    void listCancelledSessionsPendingMakeup_boSung_excludesSessionsAlreadyLinked() {
        ClassSession pending = cancelledSession(LocalDate.now().plusDays(133));
        ClassSession alreadyLinked = cancelledSession(LocalDate.now().plusDays(134));
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(135), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "MAKEUP", null, alreadyLinked.getId()),
                headAcademic.getId());

        List<ClassSessionResponse> result = classSessionService.listCancelledSessionsPendingMakeup(schoolClass.id(), headAcademic.getId());

        assertThat(result).extracting(ClassSessionResponse::id)
                .contains(pending.getId())
                .doesNotContain(alreadyLinked.getId());
    }

    @Test
    void listMySessions_UC58_MainFlow_returnsSessionsAcrossAllClassesForActorOnly() {
        ClassSessionResponse session1 = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(60), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        Site site2 = newSite();
        ClassResponse class2 = classService.create(new CreateClassRequest(classCode(), "9A1", site2.getId(),
                schoolClass.curriculumId(), "OPEN", 20, null, LocalDate.now(), null, null), headAcademic.getId());
        Room room2 = newRoom(site2, false);
        ClassSessionResponse session2 = classSessionService.createSession(class2.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(61), LocalTime.of(10, 0), LocalTime.of(11, 40),
                        room2.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        User otherTeacher = newUser("teacher.other.myschedule");
        assignRole(otherTeacher, "TEACHER");
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(62), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), otherTeacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        List<ClassSessionResponse> mySessions = classSessionService.listMySessions(teacher.getId(), null, null);

        assertThat(mySessions).extracting(ClassSessionResponse::id).contains(session1.id(), session2.id());
        assertThat(mySessions).extracting(ClassSessionResponse::primaryTeacherId).containsOnly(teacher.getId());
    }

    @Test
    void listMySessions_UC58_filtersByFromDateToDate() {
        ClassSessionResponse early = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(70), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        ClassSessionResponse late = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(80), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        List<ClassSessionResponse> filtered = classSessionService.listMySessions(teacher.getId(),
                LocalDate.now().plusDays(75), LocalDate.now().plusDays(85));

        assertThat(filtered).extracting(ClassSessionResponse::id).contains(late.id()).doesNotContain(early.id());
    }

    @Test
    void listMySessionsForStudent_UC59_MainFlow_returnsSessionsAcrossAllEnrolledClasses() {
        ClassSessionResponse session1 = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(90), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        Site site2 = newSite();
        ClassResponse class2 = classService.create(new CreateClassRequest(classCode(), "9A1", site2.getId(),
                schoolClass.curriculumId(), "OPEN", 20, null, LocalDate.now(), null, null), headAcademic.getId());
        Room room2 = newRoom(site2, false);
        ClassSessionResponse session2 = classSessionService.createSession(class2.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(91), LocalTime.of(10, 0), LocalTime.of(11, 40),
                        room2.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        // Buổi của 1 lớp khác mà học sinh KHÔNG ghi danh -- không được xuất hiện.
        Site site3 = newSite();
        ClassResponse class3 = classService.create(new CreateClassRequest(classCode(), "10A1", site3.getId(),
                schoolClass.curriculumId(), "OPEN", 20, null, LocalDate.now(), null, null), headAcademic.getId());
        Room room3 = newRoom(site3, false);
        ClassSessionResponse otherClassSession = classSessionService.createSession(class3.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(92), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room3.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        Student student = enrollStudentIn(schoolClass.id());
        classService.enroll(class2.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        List<ClassSessionResponse> mySessions = classSessionService.listMySessionsForStudent(
                student.getUser().getId(), null, null, null);

        assertThat(mySessions).extracting(ClassSessionResponse::id)
                .contains(session1.id(), session2.id())
                .doesNotContain(otherClassSession.id());
    }

    @Test
    void listMySessionsForStudent_UC59_A1_filtersToSelectedClassWhenClassIdProvided() {
        ClassSessionResponse session1 = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(93), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        Site site2 = newSite();
        ClassResponse class2 = classService.create(new CreateClassRequest(classCode(), "9A1", site2.getId(),
                schoolClass.curriculumId(), "OPEN", 20, null, LocalDate.now(), null, null), headAcademic.getId());
        Room room2 = newRoom(site2, false);
        ClassSessionResponse session2 = classSessionService.createSession(class2.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(94), LocalTime.of(10, 0), LocalTime.of(11, 40),
                        room2.getId(), teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        Student student = enrollStudentIn(schoolClass.id());
        classService.enroll(class2.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        List<ClassSessionResponse> filtered = classSessionService.listMySessionsForStudent(
                student.getUser().getId(), null, null, schoolClass.id());

        assertThat(filtered).extracting(ClassSessionResponse::id).contains(session1.id()).doesNotContain(session2.id());
    }

    @Test
    void listMySessionsForStudent_UC59_rejectsWhenActorHasNoStudentProfile() {
        assertThatThrownBy(() -> classSessionService.listMySessionsForStudent(teacher.getId(), null, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Student enrollStudentIn(Long classId) {
        User studentUser = newUser("student.schedule");
        Student student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-SCH-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        student = studentRepository.save(student);
        classService.enroll(classId, new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        return student;
    }

    /** Ngày đầu tiên >= from khớp đúng dayOfWeek yêu cầu — dùng để dựng test case UC-56 không phụ thuộc ngày chạy test. */
    private LocalDate nextWeekday(LocalDate from, DayOfWeek dayOfWeek) {
        LocalDate date = from;
        while (date.getDayOfWeek() != dayOfWeek) {
            date = date.plusDays(1);
        }
        return date;
    }

    private String curriculumCode() {
        return "CUR-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-" + SEQ.incrementAndGet();
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private Site newSite() {
        Site s = new Site();
        s.setCode("SITE-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private Room newRoom(Site site, boolean flexible) {
        Room r = new Room();
        r.setSite(site);
        r.setCode("ROOM-" + SEQ.incrementAndGet());
        r.setName("Test Room");
        r.setRoomType(Room.RoomType.THEORY);
        r.setCapacity(30);
        r.setFlexible(flexible);
        return roomRepository.save(r);
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
