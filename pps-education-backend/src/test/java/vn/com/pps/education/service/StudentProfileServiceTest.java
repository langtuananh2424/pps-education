package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AttendanceMark;
import vn.com.pps.education.domain.AttendanceSession;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateAcademicTermRequest;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateGradeComponentSetupRequest;
import vn.com.pps.education.dto.CreateGradeEvaluationComponentRequest;
import vn.com.pps.education.dto.CreateStudentRequest;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.EnterGradeEvaluationResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeComponentSetupResponse;
import vn.com.pps.education.dto.GradeEvaluationComponentResponse;
import vn.com.pps.education.dto.StudentProfileResponse;
import vn.com.pps.education.dto.StudentResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AttendanceMarkRepository;
import vn.com.pps.education.repository.AttendanceSessionRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentCommentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bổ sung ngoài SDD gốc (FR-REP-04) — hồ sơ học tập tổng hợp. Main Flow
 * (gộp đủ lớp/điểm tổng kết/điểm kỹ năng/nhận xét/điểm danh vào 1 response)
 * + rào site-scope (mirror StudentServiceTest).
 */
@Transactional
class StudentProfileServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private StudentProfileService studentProfileService;
    @Autowired
    private StudentService studentService;
    @Autowired
    private ClassService classService;
    @Autowired
    private CurriculumService curriculumService;
    @Autowired
    private AcademicTermService academicTermService;
    @Autowired
    private GradeService gradeService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private vn.com.pps.education.repository.RoleRepository roleRepository;
    @Autowired
    private vn.com.pps.education.repository.UserRoleRepository userRoleRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private SiteManagerRepository siteManagerRepository;
    @Autowired
    private ClassSessionRepository classSessionRepository;
    @Autowired
    private StudentCommentRepository studentCommentRepository;
    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;
    @Autowired
    private AttendanceMarkRepository attendanceMarkRepository;

    private User headAcademic;
    private User teacher;
    private Site site;
    private ClassResponse schoolClass;
    private StudentResponse student;
    private LocalDate termStart;
    private LocalDate termEnd;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        teacher = newUser("teacher.profile");
        site = newSite();

        var curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        var activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());

        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now().minusMonths(1), null, null), headAcademic.getId());
        classService.assignTeacher(schoolClass.id(),
                new vn.com.pps.education.dto.AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now().minusMonths(1), "VIETNAMESE"),
                headAcademic.getId());

        termStart = LocalDate.now().minusMonths(1);
        termEnd = LocalDate.now().plusMonths(3);
        var term = academicTermService.create(
                new CreateAcademicTermRequest(site.getId(), "T-" + SEQ.incrementAndGet(), "Kỳ test", termStart, termEnd),
                headAcademic.getId());

        User studentUser = newUser("student.profile");
        student = studentService.create(
                new CreateStudentRequest(studentUser.getId(), null, "HSPROFILE-" + SEQ.incrementAndGet(),
                        LocalDate.of(2012, 5, 1), "MALE", null, site.getId(), null, null, termStart, null),
                headAcademic.getId());
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.id(), termStart), headAcademic.getId());

        // Điểm: 1 setup (MID_TERM) + 1 thành phần (LISTENING) + 1 điểm + 1 Overall/Level.
        GradeComponentSetupResponse setup = gradeService.createGradeComponentSetup(schoolClass.id(),
                new CreateGradeComponentSetupRequest(term.id(), "MID_TERM", "POINT_10", termStart, false), headAcademic.getId());
        GradeEvaluationComponentResponse component = gradeService.addGradeEvaluationComponent(setup.id(),
                new CreateGradeEvaluationComponentRequest(null, null, "LISTENING", "Nghe", new BigDecimal("10.00"), new BigDecimal("5.00"), "NUMERIC", 1),
                headAcademic.getId());
        gradeService.enterGrade(schoolClass.id(), component.id(),
                new EnterGradeRequest(student.id(), new BigDecimal("8.5"), false, null), headAcademic.getId());
        gradeService.enterEvaluationResult(schoolClass.id(), student.id(), setup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("8.0"), "NUMERIC", "B1", "Tiến bộ tốt", null, null), headAcademic.getId());

        // 1 buổi học + 1 nhận xét DAILY + 1 điểm danh -- ghi thẳng qua repository (không phải trọng tâm test này).
        ClassSession session = new ClassSession();
        session.setSchoolClass(classEntity(schoolClass.id()));
        session.setSessionDate(termStart.plusDays(3));
        session.setStartTime(LocalTime.of(18, 0));
        session.setEndTime(LocalTime.of(20, 0));
        session.setPrimaryTeacher(teacher);
        session.setCreatedBy(headAcademic);
        session = classSessionRepository.save(session);

        StudentComment comment = new StudentComment();
        comment.setStudent(studentEntity(student.id()));
        comment.setSchoolClass(classEntity(schoolClass.id()));
        comment.setTeacher(teacher);
        comment.setCommentType(StudentComment.CommentType.DAILY);
        comment.setClassSession(session);
        comment.setCommentDate(session.getSessionDate());
        comment.setContent("Tích cực phát biểu.");
        studentCommentRepository.save(comment);

        AttendanceSession attSession = new AttendanceSession();
        attSession.setClassSession(session);
        attSession.setMarkedBy(teacher);
        attSession.setMarkedAt(java.time.OffsetDateTime.now());
        attSession = attendanceSessionRepository.save(attSession);

        AttendanceMark mark = new AttendanceMark();
        mark.setAttendanceSession(attSession);
        mark.setStudent(studentEntity(student.id()));
        mark.setStatus(AttendanceMark.Status.PRESENT);
        attendanceMarkRepository.save(mark);
    }

    @Test
    void getProfile_MainFlow_aggregatesEnrollmentGradeCommentAttendance() {
        StudentProfileResponse profile = studentProfileService.getProfile(student.id(), headAcademic.getId());

        assertThat(profile.student().id()).isEqualTo(student.id());
        assertThat(profile.enrollments()).hasSize(1);
        assertThat(profile.enrollments().get(0).classId()).isEqualTo(schoolClass.id());

        assertThat(profile.gradeResults()).hasSize(1);
        assertThat(profile.gradeResults().get(0).overallScore()).isEqualByComparingTo("8.0");
        assertThat(profile.gradeResults().get(0).level()).isEqualTo("B1");
        assertThat(profile.gradeResults().get(0).academicTermName()).isEqualTo("Kỳ test");

        assertThat(profile.skillScores()).hasSize(1);
        assertThat(profile.skillScores().get(0).skillCode()).isEqualTo("LISTENING");
        assertThat(profile.skillScores().get(0).score()).isEqualByComparingTo("8.5");

        assertThat(profile.comments()).hasSize(1);
        assertThat(profile.comments().get(0).content()).isEqualTo("Tích cực phát biểu.");
        assertThat(profile.comments().get(0).sessionNumber()).isEqualTo(1);
        // Nhận xét DAILY không có academicTermId trực tiếp -- phải suy đúng kỳ theo ngày.
        assertThat(profile.comments().get(0).academicTermName()).isEqualTo("Kỳ test");

        assertThat(profile.attendance()).hasSize(1);
        assertThat(profile.attendance().get(0).status()).isEqualTo("PRESENT");
        assertThat(profile.attendance().get(0).sessionNumber()).isEqualTo(1);
        assertThat(profile.attendance().get(0).academicTermName()).isEqualTo("Kỳ test");
    }

    @Test
    void getProfile_A1_notFoundWhenStudentDoesNotExist() {
        assertThatThrownBy(() -> studentProfileService.getProfile(-1L, headAcademic.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getProfile_rejectsSiteManagerOutsideOwnSite() {
        Site otherSite = newSite();
        User outsiderSiteManager = newUser("outsider.site.manager");
        SiteManager sm = new SiteManager();
        sm.setSite(otherSite);
        sm.setUser(outsiderSiteManager);
        sm.setRoleType(SiteManager.RoleType.SITE_MANAGER);
        sm.setAssignedFrom(LocalDate.now().minusMonths(1));
        sm.setAssignedBy(outsiderSiteManager);
        siteManagerRepository.save(sm);

        assertThatThrownBy(() -> studentProfileService.getProfile(student.id(), outsiderSiteManager.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getProfile_allowsSiteManagerOfStudentSite() {
        User siteManagerUser = newUser("own.site.manager");
        SiteManager sm = new SiteManager();
        sm.setSite(site);
        sm.setUser(siteManagerUser);
        sm.setRoleType(SiteManager.RoleType.SITE_MANAGER);
        sm.setAssignedFrom(LocalDate.now().minusMonths(1));
        sm.setAssignedBy(siteManagerUser);
        siteManagerRepository.save(sm);

        StudentProfileResponse profile = studentProfileService.getProfile(student.id(), siteManagerUser.getId());
        assertThat(profile.student().id()).isEqualTo(student.id());
    }

    // ===================== Helpers =====================

    @Autowired
    private vn.com.pps.education.repository.SchoolClassRepository schoolClassRepository;
    @Autowired
    private vn.com.pps.education.repository.StudentRepository studentRepository;

    private vn.com.pps.education.domain.SchoolClass classEntity(Long id) {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();
    }

    private vn.com.pps.education.domain.Student studentEntity(Long id) {
        return studentRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();
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

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }

    private Site newSite() {
        Site s = new Site();
        s.setCode("SITE-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }
}
