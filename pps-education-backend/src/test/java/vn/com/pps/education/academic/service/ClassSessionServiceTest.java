package vn.com.pps.education.academic.service;

import vn.com.pps.education.hr.service.ScheduledTeacherAssignmentService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.config.MutableClock;
import vn.com.pps.education.academic.domain.ClassSession;
import vn.com.pps.education.academic.domain.ClassSessionHistory;
import vn.com.pps.education.academic.domain.ClassTeacher;
import vn.com.pps.education.hr.domain.Employee;
import vn.com.pps.education.hr.domain.LeaveRequest;
import vn.com.pps.education.hr.domain.LeaveSubstitution;
import vn.com.pps.education.permission.domain.Role;
import vn.com.pps.education.facility.domain.Room;
import vn.com.pps.education.facility.domain.Site;
import vn.com.pps.education.facility.domain.SiteManager;
import vn.com.pps.education.facility.domain.SitePeriodTemplate;
import vn.com.pps.education.student.domain.Student;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.permission.domain.UserRole;
import vn.com.pps.education.academic.dto.AssignTeacherRequest;
import vn.com.pps.education.academic.dto.BulkCreateClassSessionRequest;
import vn.com.pps.education.academic.dto.BulkCreateClassSessionResponse;
import vn.com.pps.education.academic.dto.CancelClassSessionRequest;
import vn.com.pps.education.academic.dto.ChangeTeacherRequest;
import vn.com.pps.education.lms.dto.ClassResponse;
import vn.com.pps.education.hr.dto.ClassSessionResponse;
import vn.com.pps.education.academic.dto.CreateClassRequest;
import vn.com.pps.education.academic.dto.CreateClassSessionRequest;
import vn.com.pps.education.academic.dto.CreateCurriculumRequest;
import vn.com.pps.education.academic.dto.CurriculumResponse;
import vn.com.pps.education.academic.dto.EnrollStudentRequest;
import vn.com.pps.education.academic.dto.RescheduleClassSessionRequest;
import vn.com.pps.education.academic.dto.SessionPeriodResponse;
import vn.com.pps.education.academic.dto.UpdateCurriculumRequest;
import vn.com.pps.education.academic.dto.UpdateSessionAssignmentRequest;
import vn.com.pps.education.exception.InvalidClassSessionStatusTransitionException;
import vn.com.pps.education.exception.MakeupSessionAlreadyLinkedException;
import vn.com.pps.education.exception.NotAllowedToCorrectPastSessionException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RoomConflictException;
import vn.com.pps.education.exception.TeacherScheduleConflictException;
import vn.com.pps.education.academic.repository.ClassSessionHistoryRepository;
import vn.com.pps.education.academic.repository.ClassSessionRepository;
import vn.com.pps.education.academic.repository.ClassTeacherRepository;
import vn.com.pps.education.hr.repository.EmployeeRepository;
import vn.com.pps.education.hr.repository.LeaveRequestRepository;
import vn.com.pps.education.hr.repository.LeaveSubstitutionRepository;
import vn.com.pps.education.permission.repository.RoleRepository;
import vn.com.pps.education.facility.repository.RoomRepository;
import vn.com.pps.education.academic.repository.SessionPeriodRepository;
import vn.com.pps.education.facility.repository.SiteManagerRepository;
import vn.com.pps.education.facility.repository.SitePeriodTemplateRepository;
import vn.com.pps.education.facility.repository.SiteRepository;
import vn.com.pps.education.facility.repository.SiteTeacherRepository;
import vn.com.pps.education.student.repository.StudentRepository;
import vn.com.pps.education.auth.repository.UserRepository;
import vn.com.pps.education.permission.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-48: Xếp lịch buổi học (FR-ACA-05) — Main Flow (tạo buổi + sinh
 * session_periods từ site_period_templates, FR-FAC-03 trùng phòng), A2
 * (hủy buổi), A3 (dời lịch).
 *
 * Mỗi site test dùng 8 "tiết" cố định (seedDefaultPeriods) phủ đúng 4
 * khung giờ dùng xuyên suốt file (8:00-9:40, 9:00-10:30, 10:00-11:40,
 * 10:30-12:00) — mỗi khung = 2 tiết liên tiếp (periodNumbers [1,2]/[3,4]/
 * [5,6]/[7,8]) để giữ nguyên các assertion cũ kiểu "2 session_periods".
 */
@Transactional
class ClassSessionServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();
    /** [1,2]=8:00-9:40, [3,4]=9:00-10:30, [5,6]=10:00-11:40, [7,8]=10:30-12:00 — xem seedDefaultPeriods. */
    private static final List<Integer> SLOT_A = List.of(1, 2);
    private static final List<Integer> SLOT_B = List.of(3, 4);
    private static final List<Integer> SLOT_C = List.of(5, 6);
    private static final List<Integer> SLOT_D = List.of(7, 8);

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
    private SitePeriodTemplateRepository sitePeriodTemplateRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ClassSessionRepository classSessionRepository;

    @Autowired
    private ClassTeacherRepository classTeacherRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveSubstitutionRepository leaveSubstitutionRepository;

    @Autowired
    private ClassSessionStatusSchedulerService classSessionStatusSchedulerService;

    @Autowired
    private ClassSessionHistoryRepository classSessionHistoryRepository;

    @Autowired
    private SessionPeriodRepository sessionPeriodRepository;

    @Autowired
    private MutableClock clock;

    @Autowired
    private ScheduledTeacherAssignmentService scheduledTeacherAssignmentService;

    @Autowired
    private SiteTeacherRepository siteTeacherRepository;

    private User headAcademic;
    private User teacher;
    private ClassResponse schoolClass;
    private Room room;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());
        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        room = newRoom(site, false);
        assignPrimaryTeacher(schoolClass, teacher, "VIETNAMESE");
        assignPrimaryTeacher(schoolClass, teacher, "FOREIGN");
    }

    /** clock là bean singleton dùng chung ApplicationContext — trả đồng hồ thật sau mỗi test (xem StudentAttendanceServiceTest). */
    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /** Gán teacher làm giáo viên chính (PRIMARY) loại teacherType cho 1 lớp — vẫn là điều kiện UC-18, KHÔNG còn là điều kiện bắt buộc để xếp lịch (xem test allowsManuallyChosenTeacher...). */
    private void assignPrimaryTeacher(ClassResponse cls, User teacherUser, String teacherType) {
        classService.assignTeacher(cls.id(),
                new AssignTeacherRequest(teacherUser.getId(), "PRIMARY", null, LocalDate.now(), teacherType), headAcademic.getId());
    }

    @Test
    void createSession_MainFlow_generatesPeriodsFromSitePeriodTemplates() {
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        assertThat(session.status()).isEqualTo("SCHEDULED");
        assertThat(session.primaryTeacherId()).isEqualTo(teacher.getId());
        List<SessionPeriodResponse> periods = classSessionService.listPeriods(session.id(), headAcademic.getId());
        assertThat(periods).hasSize(2);
        assertThat(periods.get(0).periodNumber()).isEqualTo(1);
        assertThat(periods.get(0).startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(periods.get(1).endTime()).isEqualTo(LocalTime.of(9, 40));
    }

    /** Bổ sung (đã xác nhận với người dùng 2026-07-29): loại giáo viên (Việt Nam/nước ngoài) của buổi học. */
    @Test
    void createSession_boSung_setsTeacherTypeWhenProvided() {
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), "MORNING", SLOT_A, room.getId(), "REGULAR", "FOREIGN",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        assertThat(session.teacherType()).isEqualTo("FOREIGN");
    }

    /**
     * Bổ sung ngoài SDD gốc, xác nhận 2026-08-19 — ĐẢO NGƯỢC quyết định
     * 2026-08-13: giáo viên chính chọn tay không còn bắt buộc phải là
     * PRIMARY đang active của lớp (khác hẳn hành vi cũ từng từ chối tạo
     * buổi khi lớp thiếu giáo viên chính đúng loại).
     */
    @Test
    void createSession_boSung_allowsManuallyChosenTeacherEvenWithoutClassPrimaryAssignment() {
        Site site = newSite();
        Room otherRoom = newRoom(site, false);
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());
        ClassResponse classWithoutTeacher = classService.create(
                new CreateClassRequest(classCode(), "8A9", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        User anyTeacher = newUser("teacher.manual.noassignment");
        assignRole(anyTeacher, "TEACHER");

        ClassSessionResponse session = classSessionService.createSession(classWithoutTeacher.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), "MORNING", SLOT_A, otherRoom.getId(), "REGULAR", "VIETNAMESE",
                        anyTeacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        assertThat(session.primaryTeacherId()).isEqualTo(anyTeacher.getId());
    }

    /** Bổ sung ngoài SDD gốc, xác nhận 2026-08-19: gán GV phụ/CM riêng theo buổi (khác class_teachers cấp lớp). */
    @Test
    void createSession_boSung_assignsAssistantAndCmTeacherToSession() {
        User assistant = newUser("teacher.assistant");
        assignRole(assistant, "TEACHER");
        User cm = newUser("teacher.cm");
        assignRole(cm, "TEACHER");

        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), assistant.getId(), cm.getId(), null, null, null),
                headAcademic.getId());

        assertThat(session.assistantTeacherId()).isEqualTo(assistant.getId());
        assertThat(session.cmTeacherId()).isEqualTo(cm.getId());
    }

    /** Bổ sung ngoài SDD gốc, xác nhận 2026-08-19: GV phụ/CM không trực tiếp đứng lớp nên KHÔNG bị chặn trùng giờ (khác primaryTeacher). */
    @Test
    void createSession_boSung_doesNotCheckTeacherConflictForAssistantOrCmTeacher() {
        User assistant = newUser("teacher.assistant.overlap");
        assignRole(assistant, "TEACHER");
        LocalDate date = LocalDate.now().plusDays(1);
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), assistant.getId(), null, null, null, null),
                headAcademic.getId());

        Site site2 = newSite();
        Room room2 = newRoom(site2, false);
        ClassResponse class2 = classService.create(new CreateClassRequest(classCode(), "8B1", site2.getId(),
                schoolClass.curriculumId(), "OPEN", 20, null, LocalDate.now(), null, null), headAcademic.getId());
        User teacher2 = newUser("teacher.overlap.primary");
        assignRole(teacher2, "TEACHER");

        ClassSessionResponse second = classSessionService.createSession(class2.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, room2.getId(), "REGULAR", "VIETNAMESE",
                        teacher2.getId(), assistant.getId(), null, null, null, null),
                headAcademic.getId());

        assertThat(second.assistantTeacherId()).isEqualTo(assistant.getId());
    }

    @Test
    void createSession_FRFAC03_rejectsOverlappingRoomBooking() {
        LocalDate date = LocalDate.now().plusDays(2);
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        assertThatThrownBy(() -> classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_B, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId()))
                .isInstanceOf(RoomConflictException.class);
    }

    @Test
    void createSession_FRFAC03_allowsOverlapWhenRoomIsFlexible() {
        Site site = newSite();
        Room flexibleRoom = newRoom(site, true);
        LocalDate date = LocalDate.now().plusDays(3);
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, flexibleRoom.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        // GV và lớp khác buổi đầu — cô lập đúng hành vi "phòng flexible bỏ qua room-conflict" đang
        // test, không lẫn với chặn trùng giờ GV/Lớp (bổ sung ngoài SDD gốc, đã xác nhận với người
        // dùng 2026-07-30): cùng GV/lớp chồng giờ phải bị chặn dù phòng flexible.
        User otherTeacher = newUser("teacher2");
        assignRole(otherTeacher, "TEACHER");
        CurriculumResponse otherCurriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        CurriculumResponse otherActiveCurriculum = curriculumService.update(otherCurriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());
        ClassResponse otherClass = classService.create(
                new CreateClassRequest(classCode(), "8A3", site.getId(), otherActiveCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        assignPrimaryTeacher(otherClass, otherTeacher, "VIETNAMESE");

        ClassSessionResponse second = classSessionService.createSession(otherClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_B, flexibleRoom.getId(), "REGULAR", "VIETNAMESE",
                        otherTeacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        assertThat(second.id()).isNotNull();
    }

    @Test
    void createSession_boSung_rejectsTeacherOverlapAcrossClassesByDefault() {
        // 1 GV không thể bị xếp 2 buổi chồng giờ ở 2 lớp khác nhau (bổ sung ngoài SDD gốc, xác
        // nhận với người dùng 2026-07-30) — kể cả khi không gán phòng.
        Site otherSite = newSite();
        CurriculumResponse otherCurriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        CurriculumResponse otherActiveCurriculum = curriculumService.update(otherCurriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());
        ClassResponse otherClass = classService.create(
                new CreateClassRequest(classCode(), "7A4-2", otherSite.getId(), otherActiveCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        assignPrimaryTeacher(otherClass, teacher, "VIETNAMESE");

        LocalDate date = LocalDate.now().plusDays(6);
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, null, "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        assertThatThrownBy(() -> classSessionService.createSession(otherClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, null, "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId()))
                .isInstanceOf(TeacherScheduleConflictException.class);
    }

    @Test
    void createSession_boSung_allowTeacherOverlapBypassesConflictForSplitGroupClasses() {
        // Lớp tách nhóm (VD KT-7A4-1/KT-7A4-2) dùng chung 1 GV VÀ chung khung giờ — bổ sung ngoài
        // SDD gốc, xác nhận với người dùng 2026-09-16. allowTeacherOverlap=true chỉ bỏ qua
        // checkTeacherConflict, không đụng checkRoomConflict/checkClassConflict.
        Site otherSite = newSite();
        CurriculumResponse otherCurriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        CurriculumResponse otherActiveCurriculum = curriculumService.update(otherCurriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());
        ClassResponse otherClass = classService.create(
                new CreateClassRequest(classCode(), "7A4-2", otherSite.getId(), otherActiveCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        assignPrimaryTeacher(otherClass, teacher, "VIETNAMESE");

        LocalDate date = LocalDate.now().plusDays(7);
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, null, "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        ClassSessionResponse second = classSessionService.createSession(otherClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, null, "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, true),
                headAcademic.getId());

        assertThat(second.primaryTeacherId()).isEqualTo(teacher.getId());
    }

    @Test
    void cancelSession_UC48_A2_cancelsScheduledSessionAndFreesRoom() {
        LocalDate date = LocalDate.now().plusDays(4);
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        ClassSessionResponse cancelled = classSessionService.cancelSession(schoolClass.id(), session.id(),
                new CancelClassSessionRequest("Giáo viên nghỉ đột xuất"), headAcademic.getId());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancellationReason()).isEqualTo("Giáo viên nghỉ đột xuất");

        ClassSessionResponse another = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        assertThat(another.id()).isNotNull();
    }

    @Test
    void cancelSession_UC48_rejectsWhenSessionNotScheduled() {
        LocalDate date = LocalDate.now().plusDays(5);
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
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
                new CreateClassSessionRequest(oldDate, "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        LocalDate newDate = oldDate.plusDays(1);
        ClassSessionResponse newSession = classSessionService.rescheduleSession(schoolClass.id(), oldSession.id(),
                new RescheduleClassSessionRequest(newDate, "MORNING", SLOT_C, room.getId(), "Phòng bảo trì", null, null),
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

    /**
     * Bổ sung (đã xác nhận với người dùng 2026-07-29, mở rộng 2026-08-19):
     * dời lịch không đổi loại giáo viên LẪN giáo viên chính — copy nguyên
     * từ buổi cũ (đảo ngược re-derive theo class_teachers của 2026-08-13).
     */
    @Test
    void rescheduleSession_boSung_copiesTeacherTypeAndPrimaryTeacherFromOldSession() {
        LocalDate oldDate = LocalDate.now().plusDays(7);
        ClassSessionResponse oldSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(oldDate, "MORNING", SLOT_A, room.getId(), "REGULAR", "FOREIGN",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        ClassSessionResponse newSession = classSessionService.rescheduleSession(schoolClass.id(), oldSession.id(),
                new RescheduleClassSessionRequest(oldDate.plusDays(1), "MORNING", SLOT_C, room.getId(), "Phòng bảo trì", null, null),
                headAcademic.getId());

        assertThat(newSession.teacherType()).isEqualTo("FOREIGN");
        assertThat(newSession.primaryTeacherId()).isEqualTo(teacher.getId());
    }

    /** UC-18 changeTeacher (bổ sung ngoài SDD gốc, xác nhận 2026-08-13): đổi GV chính cascade sang buổi SCHEDULED tương lai cùng loại GV. */
    @Test
    void changeTeacher_UC18_CascadesFutureScheduledSessionsMatchingTeacherType() {
        ClassSessionResponse futureSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(15), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        User newTeacher = newUser("teacher.cascade.new");
        assignRole(newTeacher, "TEACHER");
        Long vnAssignmentId = classService.listTeachers(schoolClass.id()).stream()
                .filter(t -> "VIETNAMESE".equals(t.teacherType())).findFirst().orElseThrow().id();

        classService.changeTeacher(schoolClass.id(), vnAssignmentId,
                new ChangeTeacherRequest(newTeacher.getId(), LocalDate.now()), headAcademic.getId());

        ClassSession reloaded = classSessionRepository.findById(futureSession.id()).orElseThrow();
        assertThat(reloaded.getPrimaryTeacher().getId()).isEqualTo(newTeacher.getId());
    }

    /** UC-18 changeTeacher: KHÔNG cascade sang buổi đã qua hoặc buổi không còn SCHEDULED. */
    @Test
    void changeTeacher_UC18_DoesNotCascadeToPastOrNonScheduledSessions() {
        LocalDate pastDate = LocalDate.now().minusDays(5);
        // Buổi trong quá khứ: tạo hợp lệ (ngày tương lai) rồi chỉnh sessionDate trực tiếp qua repository để giả lập buổi đã qua.
        ClassSessionResponse createdPast = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(16), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        ClassSession pastSession = classSessionRepository.findById(createdPast.id()).orElseThrow();
        pastSession.setSessionDate(pastDate);
        classSessionRepository.save(pastSession);

        ClassSessionResponse cancelledSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(17), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        classSessionService.cancelSession(schoolClass.id(), cancelledSession.id(), new CancelClassSessionRequest(null), headAcademic.getId());

        User newTeacher = newUser("teacher.cascade.excluded");
        assignRole(newTeacher, "TEACHER");
        Long vnAssignmentId = classService.listTeachers(schoolClass.id()).stream()
                .filter(t -> "VIETNAMESE".equals(t.teacherType())).findFirst().orElseThrow().id();

        classService.changeTeacher(schoolClass.id(), vnAssignmentId,
                new ChangeTeacherRequest(newTeacher.getId(), LocalDate.now()), headAcademic.getId());

        assertThat(classSessionRepository.findById(pastSession.getId()).orElseThrow().getPrimaryTeacher().getId())
                .isEqualTo(teacher.getId());
        assertThat(classSessionRepository.findById(cancelledSession.id()).orElseThrow().getPrimaryTeacher().getId())
                .isEqualTo(teacher.getId());
    }

    /** UC-18 changeTeacher: KHÔNG ghi đè buổi đang có GV dạy thay active (leave_substitutions) — không phá vỡ UC-10/UC-11. */
    @Test
    void changeTeacher_UC18_DoesNotOverrideSessionsCoveredByActiveLeaveSubstitution() {
        ClassSessionResponse coveredSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(18), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        User substituteTeacher = newUser("teacher.substitute");
        assignRole(substituteTeacher, "TEACHER");
        ClassSession session = classSessionRepository.findById(coveredSession.id()).orElseThrow();
        session.setPrimaryTeacher(substituteTeacher);
        classSessionRepository.save(session);

        ClassTeacher vnPrimary = classTeacherRepository.findBySchoolClassIdAndTeacherRoleAndTeacherTypeAndSubjectIdIsNullAndAssignedToIsNull(
                schoolClass.id(), ClassTeacher.TeacherRole.PRIMARY, ClassSession.TeacherType.VIETNAMESE).orElseThrow();

        Employee employee = new Employee();
        employee.setUser(teacher);
        employee.setEmployeeCode("NV-CASCADE-" + SEQ.incrementAndGet());
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setEmployeeType(Employee.EmployeeType.TEACHER);
        employee.setHireDate(LocalDate.now().minusYears(1));
        employee = employeeRepository.save(employee);

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(employee);
        leaveRequest.setLeaveType(LeaveRequest.LeaveType.SICK);
        leaveRequest.setStartDate(LocalDate.now());
        leaveRequest.setEndDate(LocalDate.now().plusDays(20));
        leaveRequest.setTotalDays(java.math.BigDecimal.ONE);
        leaveRequest.setReason("Nghỉ ốm");
        leaveRequest.setStatus(LeaveRequest.Status.APPROVED);
        leaveRequest.setCurrentApprover(headAcademic);
        leaveRequest = leaveRequestRepository.save(leaveRequest);

        LeaveSubstitution substitution = new LeaveSubstitution();
        substitution.setLeaveRequest(leaveRequest);
        substitution.setClassSession(session);
        substitution.setClassTeacher(vnPrimary);
        substitution.setOriginalTeacher(teacher);
        substitution.setSubstituteTeacher(substituteTeacher);
        leaveSubstitutionRepository.save(substitution);

        User newTeacher = newUser("teacher.cascade.blocked");
        assignRole(newTeacher, "TEACHER");

        classService.changeTeacher(schoolClass.id(), vnPrimary.getId(),
                new ChangeTeacherRequest(newTeacher.getId(), LocalDate.now()), headAcademic.getId());

        assertThat(classSessionRepository.findById(coveredSession.id()).orElseThrow().getPrimaryTeacher().getId())
                .isEqualTo(substituteTeacher.getId());
    }

    @Test
    void listSessions_teacherWithoutSiteAssignment_seesNoSessions() {
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(10), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        User outsider = newUser("teacher.outsider.session");
        assignRole(outsider, "TEACHER");

        assertThat(classSessionService.listSessions(schoolClass.id(), outsider.getId())).isEmpty();
        assertThat(classSessionService.listPeriods(session.id(), outsider.getId())).isEmpty();
    }

    @Test
    void listSessions_teacherWithSiteAssignment_seesOwnSiteSessions() {
        // setUp() đã gán teacher làm PRIMARY (VIETNAMESE + FOREIGN) cho schoolClass, tự động liên kết site_teachers.
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(11), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

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
                new CreateClassSessionRequest(LocalDate.now().plusDays(12), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
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
                new CreateClassSessionRequest(oldDate, "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        LocalDate blockedDate = oldDate.plusDays(1);
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(blockedDate, "MORNING", SLOT_C, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        assertThatThrownBy(() -> classSessionService.rescheduleSession(schoolClass.id(), oldSession.id(),
                new RescheduleClassSessionRequest(blockedDate, "MORNING", SLOT_D, room.getId(), null, null, null),
                headAcademic.getId()))
                .isInstanceOf(RoomConflictException.class);
    }

    /**
     * Bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-09-29: popup Sửa
     * buổi học — allowRoomOverlap=true cho 2 nhóm lớp gộp học chung phòng.
     */
    @Test
    void updateAssignment_boSung_allowRoomOverlapBypassesRoomConflictForMergedGroups() {
        LocalDate date = LocalDate.now().plusDays(75);
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        ClassResponse otherGroup = classService.create(new CreateClassRequest(classCode(), "7A4-2", schoolClass.siteId(),
                schoolClass.curriculumId(), "OPEN", 20, null, LocalDate.now(), null, null), headAcademic.getId());
        User otherTeacher = newUser("teacher.room.overlap.edit");
        assignRole(otherTeacher, "TEACHER");
        ClassSessionResponse otherSession = classSessionService.createSession(otherGroup.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, null, "REGULAR", "VIETNAMESE",
                        otherTeacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        assertThatThrownBy(() -> classSessionService.updateAssignment(otherGroup.id(), otherSession.id(),
                new UpdateSessionAssignmentRequest(room.getId(), "VIETNAMESE", otherTeacher.getId(), null, null, "MORNING", SLOT_A, null, null, null, null),
                headAcademic.getId()))
                .isInstanceOf(RoomConflictException.class);

        ClassSessionResponse updated = classSessionService.updateAssignment(otherGroup.id(), otherSession.id(),
                new UpdateSessionAssignmentRequest(room.getId(), "VIETNAMESE", otherTeacher.getId(), null, null, "MORNING", SLOT_A, null, null, true, null),
                headAcademic.getId());
        assertThat(updated.roomId()).isEqualTo(room.getId());
    }

    @Test
    void rescheduleSession_UC48_rejectsWhenOldSessionNotScheduled() {
        LocalDate date = LocalDate.now().plusDays(8);
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        classSessionService.cancelSession(schoolClass.id(), session.id(), new CancelClassSessionRequest(null), headAcademic.getId());

        assertThatThrownBy(() -> classSessionService.rescheduleSession(schoolClass.id(), session.id(),
                new RescheduleClassSessionRequest(date.plusDays(1), "MORNING", SLOT_A, room.getId(), null, null, null),
                headAcademic.getId()))
                .isInstanceOf(InvalidClassSessionStatusTransitionException.class);
    }

    @Test
    void bulkCreateSessions_UC56_MainFlow_generatesSessionsOnMatchingWeekdays() {
        LocalDate startDate = nextWeekday(LocalDate.now().plusDays(20), DayOfWeek.MONDAY);
        LocalDate endDate = startDate.plusDays(13); // 2 tuần trọn vẹn -> đúng 2 Monday + 2 Wednesday

        BulkCreateClassSessionResponse response = classSessionService.bulkCreateSessions(schoolClass.id(),
                new BulkCreateClassSessionRequest(startDate, endDate, List.of("MONDAY", "WEDNESDAY"), "MORNING", SLOT_A, room.getId(),
                        "REGULAR", "VIETNAMESE", teacher.getId(), null, null, null, null, null),
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
                new CreateClassSessionRequest(conflictDate, "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        BulkCreateClassSessionResponse response = classSessionService.bulkCreateSessions(schoolClass.id(),
                new BulkCreateClassSessionRequest(startDate, endDate, List.of("MONDAY"), "MORNING", SLOT_A, room.getId(),
                        "REGULAR", "VIETNAMESE", teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        assertThat(response.totalDates()).isEqualTo(2); // 2 Monday trong khoảng 14 ngày
        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(response.skipped().get(0).get("date")).isEqualTo(conflictDate.toString());
    }

    /**
     * Bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-09-29: 2 nhóm lớp
     * gộp học chung 1 phòng — allowRoomOverlap=true bỏ qua riêng chặn trùng
     * phòng (lớp/GV khác nhau nên các chặn khác không bị kích hoạt).
     */
    @Test
    void bulkCreateSessions_boSung_allowRoomOverlapBypassesRoomConflictForMergedGroups() {
        LocalDate date = nextWeekday(LocalDate.now().plusDays(60), DayOfWeek.MONDAY);
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        ClassResponse otherGroup = classService.create(new CreateClassRequest(classCode(), "7A4-2", schoolClass.siteId(),
                schoolClass.curriculumId(), "OPEN", 20, null, LocalDate.now(), null, null), headAcademic.getId());
        User otherTeacher = newUser("teacher.room.overlap");
        assignRole(otherTeacher, "TEACHER");

        BulkCreateClassSessionResponse blocked = classSessionService.bulkCreateSessions(otherGroup.id(),
                new BulkCreateClassSessionRequest(date, date, List.of("MONDAY"), "MORNING", SLOT_A, room.getId(),
                        "REGULAR", "VIETNAMESE", otherTeacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        assertThat(blocked.skippedCount()).isEqualTo(1);

        BulkCreateClassSessionResponse allowed = classSessionService.bulkCreateSessions(otherGroup.id(),
                new BulkCreateClassSessionRequest(date, date, List.of("MONDAY"), "MORNING", SLOT_A, room.getId(),
                        "REGULAR", "VIETNAMESE", otherTeacher.getId(), null, null, null, null, true),
                headAcademic.getId());
        assertThat(allowed.createdCount()).isEqualTo(1);
        assertThat(allowed.created().get(0).roomId()).isEqualTo(room.getId());
    }

    /** Bổ sung (đã xác nhận với người dùng 2026-07-29): 1 giá trị teacherType dùng chung cho cả lô sinh lịch. */
    @Test
    void bulkCreateSessions_boSung_appliesTeacherTypeToAllCreatedSessions() {
        LocalDate startDate = nextWeekday(LocalDate.now().plusDays(100), DayOfWeek.MONDAY);
        LocalDate endDate = startDate.plusDays(13);

        BulkCreateClassSessionResponse response = classSessionService.bulkCreateSessions(schoolClass.id(),
                new BulkCreateClassSessionRequest(startDate, endDate, List.of("MONDAY"), "MORNING", SLOT_A, room.getId(),
                        "REGULAR", "VIETNAMESE", teacher.getId(), null, null, null, null, null),
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
                new CreateClassSessionRequest(base, "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        ClassSessionResponse session2 = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(base.plusDays(2), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        ClassSessionResponse session3 = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(base.plusDays(4), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
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
                new CreateClassSessionRequest(LocalDate.now(), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        assertThat(classSessionService.listTodaySessions(schoolClass.id(), headAcademic.getId()))
                .extracting(ClassSessionResponse::id).containsExactly(today.id());
    }

    @Test
    void listTodaySessions_boSung_returnsEmptyWhenNoSessionToday() {
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        assertThat(classSessionService.listTodaySessions(schoolClass.id(), headAcademic.getId())).isEmpty();
    }

    @Test
    void listTodaySessions_boSung_excludesCancelledSessionToday() {
        ClassSessionResponse today = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now(), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        classSessionService.cancelSession(schoolClass.id(), today.id(), new CancelClassSessionRequest(null), headAcademic.getId());

        assertThat(classSessionService.listTodaySessions(schoolClass.id(), headAcademic.getId())).isEmpty();
    }

    // ===================== Sửa nhanh tại chỗ (bổ sung ngoài SDD gốc, xác nhận 2026-08-19) =====================

    @Test
    void updateAssignment_boSung_updatesRoomTeacherAndPeriodsForScheduledSession() {
        Site site = siteRepository.findById(schoolClass.siteId()).orElseThrow();
        Room newRoom = newRoom(site, false);
        User newTeacher = newUser("teacher.assignment.new");
        assignRole(newTeacher, "TEACHER");
        User assistant = newUser("teacher.assignment.assistant");
        assignRole(assistant, "TEACHER");

        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(9), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        ClassSessionResponse updated = classSessionService.updateAssignment(schoolClass.id(), session.id(),
                new UpdateSessionAssignmentRequest(newRoom.getId(), "FOREIGN", newTeacher.getId(), assistant.getId(), null, "MORNING", SLOT_C, null, null, null, null),
                headAcademic.getId());

        assertThat(updated.id()).isEqualTo(session.id());
        assertThat(updated.roomId()).isEqualTo(newRoom.getId());
        assertThat(updated.teacherType()).isEqualTo("FOREIGN");
        assertThat(updated.primaryTeacherId()).isEqualTo(newTeacher.getId());
        assertThat(updated.assistantTeacherId()).isEqualTo(assistant.getId());
        List<SessionPeriodResponse> periods = classSessionService.listPeriods(session.id(), headAcademic.getId());
        assertThat(periods).hasSize(2);
        assertThat(periods.get(0).periodNumber()).isEqualTo(5);
    }

    @Test
    void updateAssignment_boSung_rejectsWhenSessionNotScheduled() {
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(9), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        classSessionService.cancelSession(schoolClass.id(), session.id(), new CancelClassSessionRequest(null), headAcademic.getId());

        assertThatThrownBy(() -> classSessionService.updateAssignment(schoolClass.id(), session.id(),
                new UpdateSessionAssignmentRequest(room.getId(), "VIETNAMESE", teacher.getId(), null, null, "MORNING", SLOT_A, null, null, null, null),
                headAcademic.getId()))
                .isInstanceOf(InvalidClassSessionStatusTransitionException.class);
    }

    /**
     * Regression: đổi tiết từ [1,2] sang [2,3] (period 2 dùng lại ở cả 2
     * phía) trước đây ném DataIntegrityViolationException vì Hibernate
     * chạy INSERT trước DELETE trong cùng 1 flush, vi phạm UNIQUE
     * (class_session_id, period_number) — xem
     * ClassSessionService.updateAssignment (flush() bắt buộc sau xoá).
     */
    @Test
    void updateAssignment_boSung_allowsReusingSamePeriodNumberAcrossOldAndNewSelection() {
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(9), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        ClassSessionResponse updated = classSessionService.updateAssignment(schoolClass.id(), session.id(),
                new UpdateSessionAssignmentRequest(room.getId(), "VIETNAMESE", teacher.getId(), null, null, "MORNING", List.of(2, 3), null, null, null, null),
                headAcademic.getId());

        assertThat(updated.id()).isEqualTo(session.id());
        List<SessionPeriodResponse> periods = classSessionService.listPeriods(session.id(), headAcademic.getId());
        assertThat(periods).extracting(SessionPeriodResponse::periodNumber).containsExactly(2, 3);
    }

    // ===================== Case 1: liên kết buổi hủy↔bù (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29) =====================

    private ClassSession cancelledSession(LocalDate date) {
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        classSessionService.cancelSession(schoolClass.id(), session.id(), new CancelClassSessionRequest(null), headAcademic.getId());
        return classSessionRepository.findById(session.id()).orElseThrow();
    }

    @Test
    void createSession_boSung_linksMakeupSessionToCancelledSession() {
        ClassSession cancelled = cancelledSession(LocalDate.now().plusDays(120));

        ClassSessionResponse makeup = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(121), "MORNING", SLOT_A, room.getId(), "MAKEUP", "VIETNAMESE",
                        teacher.getId(), null, null, cancelled.getId(), null, null),
                headAcademic.getId());

        assertThat(makeup.makeupForSessionId()).isEqualTo(cancelled.getId());
    }

    @Test
    void createSession_boSung_rejectsMakeupWithoutMakeupForSessionId() {
        assertThatThrownBy(() -> classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(122), "MORNING", SLOT_A, room.getId(), "MAKEUP", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSession_boSung_rejectsMakeupForSessionIdWhenNotMakeupType() {
        ClassSession cancelled = cancelledSession(LocalDate.now().plusDays(123));

        assertThatThrownBy(() -> classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(124), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, cancelled.getId(), null, null),
                headAcademic.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSession_boSung_rejectsMakeupForSessionNotCancelled() {
        ClassSessionResponse scheduled = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(125), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        assertThatThrownBy(() -> classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(126), "MORNING", SLOT_A, room.getId(), "MAKEUP", "VIETNAMESE",
                        teacher.getId(), null, null, scheduled.id(), null, null),
                headAcademic.getId()))
                .isInstanceOf(InvalidClassSessionStatusTransitionException.class);
    }

    @Test
    void createSession_boSung_rejectsMakeupForSessionAlreadyLinked() {
        ClassSession cancelled = cancelledSession(LocalDate.now().plusDays(127));
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(128), "MORNING", SLOT_A, room.getId(), "MAKEUP", "VIETNAMESE",
                        teacher.getId(), null, null, cancelled.getId(), null, null),
                headAcademic.getId());

        assertThatThrownBy(() -> classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(129), "MORNING", SLOT_A, room.getId(), "MAKEUP", "VIETNAMESE",
                        teacher.getId(), null, null, cancelled.getId(), null, null),
                headAcademic.getId()))
                .isInstanceOf(MakeupSessionAlreadyLinkedException.class);
    }

    @Test
    void rescheduleSession_boSung_copiesMakeupForSessionFromOldSession() {
        ClassSession cancelled = cancelledSession(LocalDate.now().plusDays(130));
        ClassSessionResponse makeup = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(131), "MORNING", SLOT_A, room.getId(), "MAKEUP", "VIETNAMESE",
                        teacher.getId(), null, null, cancelled.getId(), null, null),
                headAcademic.getId());

        ClassSessionResponse rescheduled = classSessionService.rescheduleSession(schoolClass.id(), makeup.id(),
                new RescheduleClassSessionRequest(LocalDate.now().plusDays(132), "MORNING", SLOT_C, room.getId(), "Đổi giờ", null, null),
                headAcademic.getId());

        assertThat(rescheduled.makeupForSessionId()).isEqualTo(cancelled.getId());
    }

    @Test
    void listCancelledSessionsPendingMakeup_boSung_excludesSessionsAlreadyLinked() {
        ClassSession pending = cancelledSession(LocalDate.now().plusDays(133));
        ClassSession alreadyLinked = cancelledSession(LocalDate.now().plusDays(134));
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(135), "MORNING", SLOT_A, room.getId(), "MAKEUP", "VIETNAMESE",
                        teacher.getId(), null, null, alreadyLinked.getId(), null, null),
                headAcademic.getId());

        List<ClassSessionResponse> result = classSessionService.listCancelledSessionsPendingMakeup(schoolClass.id(), headAcademic.getId());

        assertThat(result).extracting(ClassSessionResponse::id)
                .contains(pending.getId())
                .doesNotContain(alreadyLinked.getId());
    }

    @Test
    void listMySessions_UC58_MainFlow_returnsSessionsAcrossAllClassesForActorOnly() {
        ClassSessionResponse session1 = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(60), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        Site site2 = newSite();
        ClassResponse class2 = classService.create(new CreateClassRequest(classCode(), "9A1", site2.getId(),
                schoolClass.curriculumId(), "OPEN", 20, null, LocalDate.now(), null, null), headAcademic.getId());
        assignPrimaryTeacher(class2, teacher, "VIETNAMESE");
        Room room2 = newRoom(site2, false);
        ClassSessionResponse session2 = classSessionService.createSession(class2.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(61), "MORNING", SLOT_A, room2.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        // schoolClass đã có otherTeacher làm PRIMARY FOREIGN (khác teacher=PRIMARY VIETNAMESE) — buổi FOREIGN này không thuộc "của teacher".
        User otherTeacher = newUser("teacher.other.myschedule");
        assignRole(otherTeacher, "TEACHER");
        classService.endTeacherAssignment(schoolClass.id(),
                classService.listTeachers(schoolClass.id()).stream()
                        .filter(t -> "FOREIGN".equals(t.teacherType())).findFirst().orElseThrow().id(),
                new vn.com.pps.education.academic.dto.EndTeacherAssignmentRequest(LocalDate.now()), headAcademic.getId());
        assignPrimaryTeacher(schoolClass, otherTeacher, "FOREIGN");
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(62), "MORNING", SLOT_A, room.getId(), "REGULAR", "FOREIGN",
                        otherTeacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        List<ClassSessionResponse> mySessions = classSessionService.listMySessions(teacher.getId(), null, null);

        assertThat(mySessions).extracting(ClassSessionResponse::id).contains(session1.id(), session2.id());
        assertThat(mySessions).extracting(ClassSessionResponse::primaryTeacherId).containsOnly(teacher.getId());
    }

    @Test
    void listMySessions_UC58_filtersByFromDateToDate() {
        ClassSessionResponse early = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(70), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        ClassSessionResponse late = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(80), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        List<ClassSessionResponse> filtered = classSessionService.listMySessions(teacher.getId(),
                LocalDate.now().plusDays(75), LocalDate.now().plusDays(85));

        assertThat(filtered).extracting(ClassSessionResponse::id).contains(late.id()).doesNotContain(early.id());
    }

    @Test
    void listMySessionsForStudent_UC59_MainFlow_returnsSessionsAcrossAllEnrolledClasses() {
        ClassSessionResponse session1 = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(90), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        Site site2 = newSite();
        ClassResponse class2 = classService.create(new CreateClassRequest(classCode(), "9A1", site2.getId(),
                schoolClass.curriculumId(), "OPEN", 20, null, LocalDate.now(), null, null), headAcademic.getId());
        assignPrimaryTeacher(class2, teacher, "VIETNAMESE");
        Room room2 = newRoom(site2, false);
        ClassSessionResponse session2 = classSessionService.createSession(class2.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(91), "MORNING", SLOT_A, room2.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        // Buổi của 1 lớp khác mà học sinh KHÔNG ghi danh -- không được xuất hiện.
        Site site3 = newSite();
        ClassResponse class3 = classService.create(new CreateClassRequest(classCode(), "10A1", site3.getId(),
                schoolClass.curriculumId(), "OPEN", 20, null, LocalDate.now(), null, null), headAcademic.getId());
        assignPrimaryTeacher(class3, teacher, "VIETNAMESE");
        Room room3 = newRoom(site3, false);
        ClassSessionResponse otherClassSession = classSessionService.createSession(class3.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(92), "MORNING", SLOT_A, room3.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
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
                new CreateClassSessionRequest(LocalDate.now().plusDays(93), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        Site site2 = newSite();
        ClassResponse class2 = classService.create(new CreateClassRequest(classCode(), "9A1", site2.getId(),
                schoolClass.curriculumId(), "OPEN", 20, null, LocalDate.now(), null, null), headAcademic.getId());
        assignPrimaryTeacher(class2, teacher, "VIETNAMESE");
        Room room2 = newRoom(site2, false);
        ClassSessionResponse session2 = classSessionService.createSession(class2.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(94), "MORNING", SLOT_A, room2.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
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

    // ===================== UC-48 A5–A7: vòng đời trạng thái buổi học (xác nhận 2026-10-01) =====================

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Ghim "now" = 09:00 giờ Việt Nam của ngày day — SLOT_A (8:00-9:40) đang diễn ra, SLOT_C (10:00-11:40) chưa tới giờ. */
    private void fixNowAtNineAm(LocalDate day) {
        clock.setFixedInstant(day.atTime(9, 0).atZone(APP_ZONE).toInstant(), APP_ZONE);
    }

    private ClassSessionResponse createMorningSession(LocalDate date, List<Integer> periods) {
        return classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(date, "MORNING", periods, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
    }

    private ClassSession.Status statusOf(Long sessionId) {
        return classSessionRepository.findById(sessionId).orElseThrow().getStatus();
    }

    /** Tạo 1 buổi ngày hôm trước + chạy job lúc 09:00 hôm nay → buổi đó COMPLETED. */
    private ClassSessionResponse createCompletedSession(LocalDate today) {
        ClassSessionResponse session = createMorningSession(today.minusDays(1), SLOT_A);
        fixNowAtNineAm(today);
        classSessionStatusSchedulerService.processSessionStatusTransitions();
        assertThat(statusOf(session.id())).isEqualTo(ClassSession.Status.COMPLETED);
        return session;
    }

    @Test
    void processSessionStatusTransitions_UC48_A5_movesSessionsByTimeAndCountsOnlyCompletedPeriods() {
        LocalDate today = LocalDate.now(APP_ZONE).plusDays(30);
        ClassSessionResponse yesterday = createMorningSession(today.minusDays(1), SLOT_A);
        ClassSessionResponse ongoing = createMorningSession(today, SLOT_A);
        ClassSessionResponse later = createMorningSession(today, SLOT_C);
        ClassSessionResponse cancelled = createMorningSession(today.minusDays(1), SLOT_C);
        classSessionService.cancelSession(schoolClass.id(), cancelled.id(), new CancelClassSessionRequest("GV nghỉ"), headAcademic.getId());

        fixNowAtNineAm(today);
        classSessionStatusSchedulerService.processSessionStatusTransitions();

        assertThat(statusOf(yesterday.id())).isEqualTo(ClassSession.Status.COMPLETED);
        assertThat(statusOf(ongoing.id())).isEqualTo(ClassSession.Status.IN_PROGRESS);
        assertThat(statusOf(later.id())).isEqualTo(ClassSession.Status.SCHEDULED);
        assertThat(statusOf(cancelled.id())).isEqualTo(ClassSession.Status.CANCELLED);

        // Báo cáo "Số tiết thực tế theo lớp" chỉ đếm 2 tiết của buổi COMPLETED.
        Long siteId = classSessionRepository.findById(yesterday.id()).orElseThrow().getSchoolClass().getSite().getId();
        List<SessionPeriodRepository.ClassActualPeriodCount> counts = sessionPeriodRepository.countActualPeriodsBySite(
                siteId, today.minusDays(1), today, schoolClass.id());
        assertThat(counts).singleElement().satisfies(row -> assertThat(row.getPeriodCount()).isEqualTo(2L));
    }

    @Test
    void processSessionStatusTransitions_UC48_A5_runningAgainChangesNothingAndFinishesLaterSessions() {
        LocalDate today = LocalDate.now(APP_ZONE).plusDays(30);
        ClassSessionResponse ongoing = createMorningSession(today, SLOT_A);
        fixNowAtNineAm(today);
        classSessionStatusSchedulerService.processSessionStatusTransitions();
        classSessionStatusSchedulerService.processSessionStatusTransitions();
        assertThat(statusOf(ongoing.id())).isEqualTo(ClassSession.Status.IN_PROGRESS);

        // Lượt sau (sau giờ kết thúc 9:40) tự hoàn tất — không cần lượt nào đúng lúc 9:40.
        clock.setFixedInstant(today.atTime(13, 0).atZone(APP_ZONE).toInstant(), APP_ZONE);
        classSessionStatusSchedulerService.processSessionStatusTransitions();
        assertThat(statusOf(ongoing.id())).isEqualTo(ClassSession.Status.COMPLETED);
    }

    @Test
    void cancelSession_UC48_A6_cancelsCompletedSessionWithReasonAndRecordsHistory() {
        LocalDate today = LocalDate.now(APP_ZONE).plusDays(30);
        ClassSessionResponse session = createCompletedSession(today);

        ClassSessionResponse cancelled = classSessionService.cancelSession(schoolClass.id(), session.id(),
                new CancelClassSessionRequest("  GV vắng, không có người dạy thay  "), headAcademic.getId());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancellationReason()).isEqualTo("GV vắng, không có người dạy thay");
        assertThat(classSessionService.listCancelledSessionsPendingMakeup(schoolClass.id(), headAcademic.getId()))
                .extracting(ClassSessionResponse::id).contains(session.id());
        List<ClassSessionHistory> history = classSessionHistoryRepository.findAll().stream()
                .filter(h -> h.getClassSession().getId().equals(session.id()))
                .filter(h -> Boolean.TRUE.equals(h.getDetails().get("retroactive")))
                .toList();
        assertThat(history).singleElement().satisfies(h -> {
            assertThat(h.getDetails()).containsEntry("status", "CANCELLED");
            assertThat(h.getDetails()).containsEntry("reason", "GV vắng, không có người dạy thay");
        });
    }

    @Test
    void cancelSession_UC48_A6_rejectsCompletedSessionWithoutCorrectPastPermission() {
        LocalDate today = LocalDate.now(APP_ZONE).plusDays(30);
        ClassSessionResponse session = createCompletedSession(today);

        assertThatThrownBy(() -> classSessionService.cancelSession(schoolClass.id(), session.id(),
                new CancelClassSessionRequest("GV vắng"), teacher.getId()))
                .isInstanceOf(NotAllowedToCorrectPastSessionException.class);
        assertThat(statusOf(session.id())).isEqualTo(ClassSession.Status.COMPLETED);
    }

    @Test
    void cancelSession_UC48_A6_rejectsCompletedSessionWithoutReason() {
        LocalDate today = LocalDate.now(APP_ZONE).plusDays(30);
        ClassSessionResponse session = createCompletedSession(today);

        assertThatThrownBy(() -> classSessionService.cancelSession(schoolClass.id(), session.id(),
                new CancelClassSessionRequest("   "), headAcademic.getId()))
                .isInstanceOf(InvalidClassSessionStatusTransitionException.class);
    }

    @Test
    void rescheduleSession_UC48_A5_rejectsCompletedSession() {
        LocalDate today = LocalDate.now(APP_ZONE).plusDays(30);
        ClassSessionResponse session = createCompletedSession(today);

        assertThatThrownBy(() -> classSessionService.rescheduleSession(schoolClass.id(), session.id(),
                new RescheduleClassSessionRequest(today.plusDays(2), "MORNING", SLOT_A, room.getId(), null, null, null),
                headAcademic.getId()))
                .isInstanceOf(InvalidClassSessionStatusTransitionException.class);
    }

    @Test
    void updateAssignment_UC48_A7_correctsCompletedSessionAndKeepsCompleted() {
        LocalDate today = LocalDate.now(APP_ZONE).plusDays(30);
        ClassSessionResponse session = createCompletedSession(today);
        User substitute = newUser("teacher.substitute");
        assignRole(substitute, "TEACHER");

        ClassSessionResponse updated = classSessionService.updateAssignment(schoolClass.id(), session.id(),
                new UpdateSessionAssignmentRequest(room.getId(), "VIETNAMESE", substitute.getId(), null, null, "MORNING",
                        SLOT_C, null, null, null, "Ghi nhầm GV và tiết"),
                headAcademic.getId());

        assertThat(updated.status()).isEqualTo("COMPLETED");
        assertThat(updated.primaryTeacherId()).isEqualTo(substitute.getId());
        assertThat(updated.periodNumbers()).containsExactlyElementsOf(SLOT_C);
        assertThat(classSessionHistoryRepository.findAll().stream()
                .filter(h -> h.getClassSession().getId().equals(session.id()))
                .anyMatch(h -> "Ghi nhầm GV và tiết".equals(h.getDetails().get("reason")))).isTrue();
    }

    @Test
    void updateAssignment_UC48_A7_rederivesStatusWhenCorrectedToLaterPeriodsToday() {
        LocalDate today = LocalDate.now(APP_ZONE).plusDays(30);
        ClassSessionResponse ongoing = createMorningSession(today, SLOT_A);
        fixNowAtNineAm(today);
        classSessionStatusSchedulerService.processSessionStatusTransitions();
        assertThat(statusOf(ongoing.id())).isEqualTo(ClassSession.Status.IN_PROGRESS);

        ClassSessionResponse updated = classSessionService.updateAssignment(schoolClass.id(), ongoing.id(),
                new UpdateSessionAssignmentRequest(room.getId(), "VIETNAMESE", teacher.getId(), null, null, "MORNING",
                        SLOT_C, null, null, null, "Lớp học tiết 5-6, không phải tiết 1-2"),
                headAcademic.getId());

        assertThat(updated.status()).isEqualTo("SCHEDULED");
    }

    @Test
    void updateAssignment_UC48_A7_rejectsCompletedSessionWithoutReason() {
        LocalDate today = LocalDate.now(APP_ZONE).plusDays(30);
        ClassSessionResponse session = createCompletedSession(today);

        assertThatThrownBy(() -> classSessionService.updateAssignment(schoolClass.id(), session.id(),
                new UpdateSessionAssignmentRequest(room.getId(), "VIETNAMESE", teacher.getId(), null, null, "MORNING",
                        SLOT_A, null, null, null, null),
                headAcademic.getId()))
                .isInstanceOf(InvalidClassSessionStatusTransitionException.class);
    }

    @Test
    void updateAssignment_UC48_A7_rejectsCompletedSessionWithoutCorrectPastPermission() {
        LocalDate today = LocalDate.now(APP_ZONE).plusDays(30);
        ClassSessionResponse session = createCompletedSession(today);

        assertThatThrownBy(() -> classSessionService.updateAssignment(schoolClass.id(), session.id(),
                new UpdateSessionAssignmentRequest(room.getId(), "VIETNAMESE", teacher.getId(), null, null, "MORNING",
                        SLOT_A, null, null, null, "Sửa GV"),
                teacher.getId()))
                .isInstanceOf(NotAllowedToCorrectPastSessionException.class);
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

    // ===================== V209 — Tự gán giáo viên theo lịch dạy (bổ sung ngoài SDD gốc, xác nhận 2026-10-02) =====================

    /** V209: GV chính/phụ/CM mới (chưa gán lớp) được xếp buổi → tự có phân công SCHEDULED + điểm trường của lớp. */
    @Test
    void createSession_V209_autoAssignsUnassignedPrimaryAssistantAndCmTeachersToClassAndSite() {
        User newPrimary = newUser("teacher.v209.primary");
        assignRole(newPrimary, "TEACHER");
        User assistant = newUser("teacher.v209.assistant");
        assignRole(assistant, "TEACHER");
        User cm = newUser("teacher.v209.cm");
        assignRole(cm, "TEACHER");

        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), "MORNING", SLOT_B, room.getId(), "REGULAR", "FOREIGN",
                        newPrimary.getId(), assistant.getId(), cm.getId(), null, null, true),
                headAcademic.getId());

        for (User u : List.of(newPrimary, assistant, cm)) {
            assertThat(activeAssignments(u)).extracting(ClassTeacher::getTeacherRole)
                    .containsExactly(ClassTeacher.TeacherRole.SCHEDULED);
            assertThat(siteTeacherRepository.existsBySiteIdAndTeacherIdAndAssignedToIsNull(schoolClass.siteId(), u.getId())).isTrue();
        }
    }

    /** V209: GV đã có phân công gán tay ở lớp → không tạo thêm dòng SCHEDULED. */
    @Test
    void createSession_V209_doesNotDuplicateWhenTeacherAlreadyAssigned() {
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        assertThat(activeAssignments(teacher)).extracting(ClassTeacher::getTeacherRole)
                .containsOnly(ClassTeacher.TeacherRole.PRIMARY);
    }

    /** V209: sinh lịch hàng loạt (UC-56) cho GV mới → đúng 1 phân công SCHEDULED dù nhiều buổi. */
    @Test
    void bulkCreateSessions_V209_createsSingleScheduledAssignmentForManySessions() {
        User newTeacher = newUser("teacher.v209.bulk");
        assignRole(newTeacher, "TEACHER");
        LocalDate startDate = LocalDate.now().plusDays(1);

        BulkCreateClassSessionResponse result = classSessionService.bulkCreateSessions(schoolClass.id(),
                new BulkCreateClassSessionRequest(startDate, startDate.plusDays(13), List.of("MONDAY", "WEDNESDAY"), "MORNING", SLOT_A,
                        room.getId(), "REGULAR", "VIETNAMESE", newTeacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        assertThat(result.created()).hasSizeGreaterThan(1);
        assertThat(activeAssignments(newTeacher)).hasSize(1);
    }

    /** V209: đổi GV trên Lịch làm việc (sửa nhanh) → GV mới tự được gán vào lớp. */
    @Test
    void updateAssignment_V209_autoAssignsNewTeacher() {
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), "MORNING", SLOT_A, room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        User replacement = newUser("teacher.v209.replacement");
        assignRole(replacement, "TEACHER");

        classSessionService.updateAssignment(schoolClass.id(), session.id(),
                new UpdateSessionAssignmentRequest(room.getId(), "VIETNAMESE", replacement.getId(), null, null, "MORNING", SLOT_A,
                        null, null, null, null),
                headAcademic.getId());

        assertThat(activeAssignments(replacement)).extracting(ClassTeacher::getTeacherRole)
                .containsExactly(ClassTeacher.TeacherRole.SCHEDULED);
    }

    /** V209 job: không còn buổi nào trong 30 ngày gần nhất / sắp tới → kết thúc phân công SCHEDULED. */
    @Test
    void revokeStaleScheduledAssignments_V209_endsAssignmentWhenLastSessionOlderThanWindow() {
        User rotating = newUser("teacher.v209.rotating");
        assignRole(rotating, "TEACHER");
        moveSessionTo(createSessionFor(rotating, 1), LocalDate.now().minusDays(31));

        scheduledTeacherAssignmentService.revokeStaleScheduledAssignments();

        assertThat(activeAssignments(rotating)).isEmpty();
        assertThat(classTeacherRepository.findBySchoolClassId(schoolClass.id()))
                .filteredOn(ct -> ct.getTeacher().getId().equals(rotating.getId()))
                .singleElement()
                .satisfies(ct -> assertThat(ct.getAssignedTo()).isEqualTo(LocalDate.now()));
    }

    /** V209 job: buổi cuối vẫn trong cửa sổ 30 ngày → giữ quyền để GV nhập nốt điểm/nhận xét. */
    @Test
    void revokeStaleScheduledAssignments_V209_keepsAssignmentWithinGraceWindow() {
        User rotating = newUser("teacher.v209.grace");
        assignRole(rotating, "TEACHER");
        moveSessionTo(createSessionFor(rotating, 1), LocalDate.now().minusDays(29));

        scheduledTeacherAssignmentService.revokeStaleScheduledAssignments();

        assertThat(activeAssignments(rotating)).hasSize(1);
    }

    /** V209 job: buổi đã huỷ không tính là "còn dạy" → vẫn thu hồi. */
    @Test
    void revokeStaleScheduledAssignments_V209_ignoresCancelledSessions() {
        User rotating = newUser("teacher.v209.cancelled");
        assignRole(rotating, "TEACHER");
        Long sessionId = createSessionFor(rotating, 3);
        classSessionService.cancelSession(schoolClass.id(), sessionId, new CancelClassSessionRequest(null), headAcademic.getId());

        scheduledTeacherAssignmentService.revokeStaleScheduledAssignments();

        assertThat(activeAssignments(rotating)).isEmpty();
    }

    /** V209 job: phân công gán tay (PRIMARY) không bao giờ bị thu hồi tự động. */
    @Test
    void revokeStaleScheduledAssignments_V209_neverTouchesManualAssignments() {
        scheduledTeacherAssignmentService.revokeStaleScheduledAssignments();

        assertThat(activeAssignments(teacher)).hasSize(2);
    }

    /** V209: giáo vụ gán tay (UC-18) GV đang "Dạy theo lịch" → phân công SCHEDULED kết thúc, chỉ còn phân công gán tay. */
    @Test
    void assignTeacher_V209_endsScheduledAssignmentSupersededByManual() {
        User rotating = newUser("teacher.v209.manual");
        assignRole(rotating, "TEACHER");
        createSessionFor(rotating, 1);

        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(rotating.getId(), "ASSISTANT", null, LocalDate.now(), null), headAcademic.getId());

        assertThat(activeAssignments(rotating)).extracting(ClassTeacher::getTeacherRole)
                .containsExactly(ClassTeacher.TeacherRole.ASSISTANT);
    }

    /** V209: vai trò SCHEDULED chỉ do hệ thống tạo — gán tay bị từ chối. */
    @Test
    void assignTeacher_V209_rejectsManualScheduledRole() {
        User other = newUser("teacher.v209.reject");
        assignRole(other, "TEACHER");

        assertThatThrownBy(() -> classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(other.getId(), "SCHEDULED", null, LocalDate.now(), null), headAcademic.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Long createSessionFor(User primaryTeacher, int daysFromNow) {
        return classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(daysFromNow), "MORNING", SLOT_D, room.getId(), "REGULAR", "FOREIGN",
                        primaryTeacher.getId(), null, null, null, null, true),
                headAcademic.getId()).id();
    }

    /** Giả lập buổi đã qua: chỉnh sessionDate trực tiếp (tạo qua Service bị chặn trước ngày bắt đầu lớp). */
    private void moveSessionTo(Long sessionId, LocalDate date) {
        ClassSession session = classSessionRepository.findById(sessionId).orElseThrow();
        session.setSessionDate(date);
        classSessionRepository.saveAndFlush(session);
    }

    private List<ClassTeacher> activeAssignments(User user) {
        return classTeacherRepository.findBySchoolClassIdAndAssignedToIsNull(schoolClass.id()).stream()
                .filter(ct -> ct.getTeacher().getId().equals(user.getId()))
                .toList();
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
        s = siteRepository.save(s);
        seedDefaultPeriods(s);
        return s;
    }

    /**
     * 8 tiết cố định phủ đúng 4 khung giờ dùng xuyên suốt file test này —
     * xem hằng số SLOT_A/B/C/D. Bổ sung ngoài SDD gốc, xác nhận 2026-08-19
     * (thay cho system_settings.academic.default_periods_per_session cũ).
     */
    private void seedDefaultPeriods(Site site) {
        seedPeriod(site, 1, LocalTime.of(8, 0), LocalTime.of(8, 50));
        seedPeriod(site, 2, LocalTime.of(8, 50), LocalTime.of(9, 40));
        seedPeriod(site, 3, LocalTime.of(9, 0), LocalTime.of(9, 45));
        seedPeriod(site, 4, LocalTime.of(9, 45), LocalTime.of(10, 30));
        seedPeriod(site, 5, LocalTime.of(10, 0), LocalTime.of(10, 50));
        seedPeriod(site, 6, LocalTime.of(10, 50), LocalTime.of(11, 40));
        seedPeriod(site, 7, LocalTime.of(10, 30), LocalTime.of(11, 15));
        seedPeriod(site, 8, LocalTime.of(11, 15), LocalTime.of(12, 0));
    }

    private void seedPeriod(Site site, int periodNumber, LocalTime start, LocalTime end) {
        SitePeriodTemplate template = new SitePeriodTemplate();
        template.setSite(site);
        template.setPeriodNumber(periodNumber);
        template.setDayPart(SitePeriodTemplate.DayPart.MORNING);
        template.setStartTime(start);
        template.setEndTime(end);
        template.setCreatedBy(headAcademic);
        sitePeriodTemplateRepository.save(template);
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
