package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.Permission;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserPermissionOverride;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.AttendanceMarkResponse;
import vn.com.pps.education.dto.AttendanceSessionResponse;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateClassSessionRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.EnterAttendanceMarkRequest;
import vn.com.pps.education.dto.MarkAttendanceRequest;
import vn.com.pps.education.dto.PartnerAttendanceSummaryResponse;
import vn.com.pps.education.dto.RecordTransferRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdatePeriodMarkRequest;
import vn.com.pps.education.exception.AttendanceSessionNotEditableException;
import vn.com.pps.education.exception.NotAssignedTeacherForSessionException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AttendanceSessionRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.NotificationRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.PermissionRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserPermissionOverrideRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-15: Điểm danh học sinh — Main Flow (bước 1-6), A2 (toàn bộ có mặt).
 * Xem docs/uc/phan-he-05-hoc-sinh.md.
 */
@Transactional
class StudentAttendanceServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private StudentAttendanceService studentAttendanceService;

    @Autowired
    private ClassSessionService classSessionService;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private ParentStudentRepository parentStudentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private SiteManagerRepository siteManagerRepository;

    @Autowired
    private ClassSessionRepository classSessionRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserPermissionOverrideRepository userPermissionOverrideRepository;

    private User headAcademic;
    private User teacher;
    private ClassSessionResponse session;
    private Student student1;
    private Student student2;
    private User parentUser;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());
        Site site = newSite();
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(9, 40), null, teacher.getId(), "REGULAR", null),
                headAcademic.getId());

        student1 = newStudent();
        student2 = newStudent();

        parentUser = newUser("parent");
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent = parentRepository.save(parent);
        linkParent(parent, student1);
    }

    private void linkParent(Parent parent, Student student) {
        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationship(ParentStudent.Relationship.MOTHER);
        parentStudentRepository.save(link);
    }

    @Test
    void markAttendance_UC15_MainFlow_savesDraftAndAutoFillsPeriodMarks() {
        AttendanceSessionResponse result = studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "ABSENT", null, null, "Ốm"),
                        new EnterAttendanceMarkRequest(student2.getId(), "PRESENT", null, null, null))),
                teacher.getId());

        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.marks()).hasSize(2);
        AttendanceMarkResponse absentMark = result.marks().stream()
                .filter(m -> m.studentId().equals(student1.getId())).findFirst().orElseThrow();
        assertThat(absentMark.status()).isEqualTo("ABSENT");
        assertThat(absentMark.absenceReason()).isEqualTo("Ốm");
    }

    @Test
    void markAttendance_rejectsWhenActorNotAssignedTeacher() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "PRESENT", null, null, null))),
                outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForSessionException.class);
    }

    @Test
    void updateLessonContent_savesLessonContentForSession() {
        var result = studentAttendanceService.updateLessonContent(session.id(), "Unit 4: Present simple tense.", teacher.getId());

        assertThat(result.classSessionId()).isEqualTo(session.id());
        assertThat(result.lessonContent()).isEqualTo("Unit 4: Present simple tense.");
    }

    @Test
    void updateLessonContent_rejectsWhenActorNotAssignedTeacher() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> studentAttendanceService.updateLessonContent(session.id(), "Nội dung", outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForSessionException.class);
    }

    @Test
    void updatePeriodMark_UC15_MainFlow_overridesSinglePeriod() {
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        Long periodId = classSessionService.listPeriods(session.id(), headAcademic.getId()).get(1).id();

        AttendanceMarkResponse updated = studentAttendanceService.updatePeriodMark(session.id(), student1.getId(), periodId,
                new UpdatePeriodMarkRequest("LATE", "Muộn tiết 2"), teacher.getId());

        assertThat(updated.status()).isEqualTo("PRESENT"); // trang thai tong buoi khong doi, chi period thay doi
    }

    @Test
    void submitAttendance_UC15_A2_allPresentTriggersNoNotification() {
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "PRESENT", null, null, null),
                        new EnterAttendanceMarkRequest(student2.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        long before = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(parentUser.getId(), PageRequest.of(0, 10))
                .getTotalElements();

        AttendanceSessionResponse result = studentAttendanceService.submitAttendance(session.id(), teacher.getId());

        assertThat(result.status()).isEqualTo("SUBMITTED");
        long after = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(parentUser.getId(), PageRequest.of(0, 10))
                .getTotalElements();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void submitAttendance_UC15_MainFlow_notifiesLinkedParentsWhenAbsent() {
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "ABSENT", null, null, "Ốm"))),
                teacher.getId());

        AttendanceSessionResponse result = studentAttendanceService.submitAttendance(session.id(), teacher.getId());

        assertThat(result.marks().get(0).notifiedParentAt()).isNotNull();
        assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(parentUser.getId(), PageRequest.of(0, 10))
                .getTotalElements()).isGreaterThan(0);
    }

    @Test
    void markAttendance_UC15_editableUntilEndOfDay_afterSubmit() {
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        studentAttendanceService.submitAttendance(session.id(), teacher.getId());

        // Sửa lại trong ngày (session.sessionDate = hôm nay) sau khi Lưu -> được phép
        AttendanceSessionResponse edited = studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "ABSENT", null, null, "Sửa sau khi Lưu"))),
                teacher.getId());

        AttendanceMarkResponse mark = edited.marks().stream()
                .filter(m -> m.studentId().equals(student1.getId())).findFirst().orElseThrow();
        assertThat(mark.status()).isEqualTo("ABSENT");
    }

    @Test
    void markAttendance_UC15_rejectsWhenSessionNotToday() {
        setSessionDate(session.id(), LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "PRESENT", null, null, null))),
                teacher.getId()))
                .isInstanceOf(AttendanceSessionNotEditableException.class);
    }

    @Test
    void submitAttendance_UC15_resubmitSameDay_doesNotDuplicateParentNotification() {
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "ABSENT", null, null, "Ốm"))),
                teacher.getId());
        studentAttendanceService.submitAttendance(session.id(), teacher.getId());
        long afterFirst = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(parentUser.getId(), PageRequest.of(0, 10))
                .getTotalElements();

        // Submit lại trong ngày -> không gửi trùng thông báo cho PH
        studentAttendanceService.submitAttendance(session.id(), teacher.getId());
        long afterSecond = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(parentUser.getId(), PageRequest.of(0, 10))
                .getTotalElements();

        assertThat(afterSecond).isEqualTo(afterFirst);
    }

    @Test
    void markAttendance_UC15_adminCreate_bypassesTeacherAndDayConstraints() {
        // Buổi 3 ngày trước + người thao tác KHÔNG phải GV được phân công
        setSessionDate(session.id(), LocalDate.now().minusDays(3));
        User admin = newUser("attendance.admin.create");
        grantPermission(admin, "academic.attendance.create");

        AttendanceSessionResponse result = studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "PRESENT", null, null, null))),
                admin.getId());

        assertThat(result.marks()).hasSize(1);
        assertThat(result.marks().get(0).studentId()).isEqualTo(student1.getId());
    }

    @Test
    void updatePeriodMark_UC15_adminUpdate_bypassesTeacherAndDayConstraints() {
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        Long periodId = classSessionService.listPeriods(session.id(), headAcademic.getId()).get(0).id();
        // Đưa buổi về quá khứ -> GV không sửa được, nhưng admin update thì được
        setSessionDate(session.id(), LocalDate.now().minusDays(2));
        User admin = newUser("attendance.admin.update");
        grantPermission(admin, "academic.attendance.update");

        AttendanceMarkResponse updated = studentAttendanceService.updatePeriodMark(session.id(), student1.getId(), periodId,
                new UpdatePeriodMarkRequest("ABSENT", "Sửa bởi quản trị"), admin.getId());

        assertThat(updated.studentId()).isEqualTo(student1.getId());
    }

    @Test
    void deleteAttendance_UC15_adminDelete_removesSessionAndMarks() {
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "ABSENT", null, null, "Ốm"),
                        new EnterAttendanceMarkRequest(student2.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        studentAttendanceService.submitAttendance(session.id(), teacher.getId());

        studentAttendanceService.deleteAttendance(session.id(), headAcademic.getId());

        assertThat(attendanceSessionRepository.findByClassSessionId(session.id())).isEmpty();
    }

    @Test
    void getAttendanceSession_teacherWithoutSiteAssignment_throwsResourceNotFound() {
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        User outsider = newUser("teacher.outsider.attendance");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> studentAttendanceService.getAttendanceSession(session.id(), outsider.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAttendanceSession_teacherWithSiteAssignment_returnsSession() {
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        classService.assignTeacher(session.classId(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());

        AttendanceSessionResponse result = studentAttendanceService.getAttendanceSession(session.id(), teacher.getId());

        assertThat(result.classSessionId()).isEqualTo(session.id());
    }

    /**
     * Bổ sung (audit FE 2026-07-20): resolveAllowedSiteIds trước đây chỉ
     * cộng site theo site_teachers, bỏ sót site_managers -- Quản lý điểm
     * trường không kiêm giáo viên luôn nhận ResourceNotFoundException dù
     * đã có báo cáo tổng hợp riêng (getSiteSummary) cho đúng site này.
     */
    @Test
    void getAttendanceSession_boSung_siteManagerForSiteCanViewSession() {
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        Long siteId = classService.getById(session.classId()).siteId();
        Site managedSite = siteRepository.findById(siteId).orElseThrow();
        User siteManagerUser = newUser("site.manager.getsession");
        newSiteManager(siteManagerUser, managedSite);

        AttendanceSessionResponse result = studentAttendanceService.getAttendanceSession(session.id(), siteManagerUser.getId());

        assertThat(result.classSessionId()).isEqualTo(session.id());
    }

    @Test
    void getSiteSummary_UC15b_MainFlow_returnsAttendanceScopedToManagedSite() {
        Site site = newSite();
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null, null), headAcademic.getId());
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student1.getId(), LocalDate.now()), headAcademic.getId());
        ClassSessionResponse newSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(9, 40), null, teacher.getId(), "REGULAR", null),
                headAcademic.getId());
        studentAttendanceService.markAttendance(newSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        studentAttendanceService.submitAttendance(newSession.id(), teacher.getId());
        User siteManagerUser = newUser("site.manager.attendance");
        newSiteManager(siteManagerUser, site);

        List<PartnerAttendanceSummaryResponse> result = studentAttendanceService.getSiteSummary(site.getId(), siteManagerUser.getId());

        assertThat(result).extracting(PartnerAttendanceSummaryResponse::studentId).contains(student1.getId());
    }

    @Test
    void getSiteSummary_UC15b_rejectsWhenActorNotSiteManagerForSite() {
        Site site = newSite();
        User outsider = newUser("site.manager.outsider");

        assertThatThrownBy(() -> studentAttendanceService.getSiteSummary(site.getId(), outsider.getId()))
                .isInstanceOf(NotSiteManagerForSiteException.class);
    }

    @Test
    void listMyAttendance_UC64_MainFlow_returnsOwnAttendanceForEnrolledClass() {
        classService.enroll(session.classId(), new EnrollStudentRequest(student1.getId(), LocalDate.now()), headAcademic.getId());
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "ABSENT", null, null, "Ốm"))),
                teacher.getId());

        List<AttendanceMarkResponse> result = studentAttendanceService.listMyAttendance(session.classId(), student1.getUser().getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("ABSENT");
    }

    @Test
    void listMyAttendance_rejectsWhenNotEnrolledInClass() {
        assertThatThrownBy(() -> studentAttendanceService.listMyAttendance(session.classId(), student1.getUser().getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Bổ sung (đã xác nhận với người dùng 2026-07-29): điểm danh lớp cũ vẫn tự xem được sau khi chuyển lớp. */
    @Test
    void listMyAttendance_boSung_stillVisibleAfterTransferToAnotherClass() {
        classService.enroll(session.classId(), new EnrollStudentRequest(student1.getId(), LocalDate.now()), headAcademic.getId());
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student1.getId(), "ABSENT", null, null, "Ốm"))),
                teacher.getId());
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());
        ClassResponse otherClass = classService.create(
                new CreateClassRequest(classCode(), "8A3", newSite().getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null, null), headAcademic.getId());
        studentService.recordTransfer(student1.getId(),
                new RecordTransferRequest("CLASS_CHANGE", session.classId(), otherClass.id(), null, LocalDate.now(), "Chuyển lớp test"),
                headAcademic.getId());

        List<AttendanceMarkResponse> result = studentAttendanceService.listMyAttendance(session.classId(), student1.getUser().getId());

        assertThat(result).hasSize(1);
    }

    private SiteManager newSiteManager(User user, Site site) {
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(site);
        siteManager.setUser(user);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(user);
        return siteManagerRepository.save(siteManager);
    }

    private void grantPermission(User user, String permissionCode) {
        Permission permission = permissionRepository.findByCode(permissionCode).orElseThrow();
        UserPermissionOverride override = new UserPermissionOverride();
        override.setUser(user);
        override.setPermission(permission);
        override.setOverrideType(UserPermissionOverride.OverrideType.GRANT);
        override.setReason("test");
        override.setGrantedBy(user);
        userPermissionOverrideRepository.save(override);
    }

    private void setSessionDate(Long classSessionId, LocalDate date) {
        ClassSession classSession = classSessionRepository.findById(classSessionId).orElseThrow();
        classSession.setSessionDate(date);
        classSessionRepository.save(classSession);
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

    private Student newStudent() {
        User user = newUser("student");
        Student s = new Student();
        s.setUser(user);
        s.setStudentCode("HS-TEST-" + SEQ.incrementAndGet());
        s.setDateOfBirth(LocalDate.of(2012, 5, 1));
        s.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(s);
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
