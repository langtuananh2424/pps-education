package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.AttendanceMarkResponse;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateClassSessionRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateGradeComponentRequest;
import vn.com.pps.education.dto.CreateGradePeriodRequest;
import vn.com.pps.education.dto.CreateStudentCommentRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.DecideCommentsRequest;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.EnterAttendanceMarkRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeComponentResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradePeriodResponse;
import vn.com.pps.education.dto.MarkAttendanceRequest;
import vn.com.pps.education.dto.PublishGradesRequest;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.SubmitCommentsRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.NotAuthorizedForPortalAccessException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC-25: Xem Portal Phụ huynh — Main Flow (bước 2-4), A1 (dữ liệu chưa công bố/chưa duyệt không hiển thị). */
@Transactional
class ParentPortalServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ParentPortalService parentPortalService;

    @Autowired
    private ClassService classService;

    @Autowired
    private ClassSessionService classSessionService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private GradeService gradeService;

    @Autowired
    private StudentCommentService studentCommentService;

    @Autowired
    private StudentAttendanceService studentAttendanceService;

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
    private SiteManagerRepository siteManagerRepository;

    private User headAcademic;
    private User teacher;
    private User siteManagerUser;
    private ClassResponse schoolClass;
    private Student student;
    private User parentUser;
    private ClassSessionResponse session;

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
                        LocalDate.now(), null, null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());

        siteManagerUser = newUser("site.manager");
        assignRole(siteManagerUser, "SITE_MANAGER");
        vn.com.pps.education.domain.SiteManager siteManager = new vn.com.pps.education.domain.SiteManager();
        siteManager.setSite(site);
        siteManager.setUser(siteManagerUser);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(siteManagerUser);
        siteManagerRepository.save(siteManager);

        student = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        parentUser = newUser("parent");
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent = parentRepository.save(parent);
        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationship(ParentStudent.Relationship.MOTHER);
        parentStudentRepository.save(link);

        session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(9, 40), null, teacher.getId(), "REGULAR"),
                headAcademic.getId());
    }

    @Test
    void listMyChildren_UC25_MainFlow_returnsLinkedChild() {
        List<vn.com.pps.education.dto.ChildResponse> children = parentPortalService.listMyChildren(parentUser.getId());

        assertThat(children).extracting(vn.com.pps.education.dto.ChildResponse::studentId).contains(student.getId());
    }

    @Test
    void listGrades_UC25_A1_onlyPublishedGradesVisible() {
        GradePeriodResponse period = gradeService.createGradePeriod(schoolClass.curriculumId(),
                new CreateGradePeriodRequest("MID_1", "Giữa kỳ 1", 1, new BigDecimal("50"), null, null), headAcademic.getId());
        GradeComponentResponse component = gradeService.addGradeComponent(period.id(),
                new CreateGradeComponentRequest(null, null, "SPEAKING", "Nói", new BigDecimal("10.00"), null, null, 1),
                headAcademic.getId());
        GradeEntryResponse publishedEntry = gradeService.enterGrade(schoolClass.id(), component.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, null), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest(List.of(publishedEntry.id()), null), siteManagerUser.getId());

        // A1 -- 1 bản ghi khác vẫn DRAFT, chưa công bố.
        GradeComponentResponse component2 = gradeService.addGradeComponent(period.id(),
                new CreateGradeComponentRequest(null, null, "WRITING", "Viết", new BigDecimal("10.00"), null, null, 2),
                headAcademic.getId());
        gradeService.enterGrade(schoolClass.id(), component2.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("7"), false, null), teacher.getId());

        List<GradeEntryResponse> grades = parentPortalService.listGrades(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(grades).hasSize(1);
        assertThat(grades.get(0).status()).isEqualTo("PUBLISHED");
        assertThat(grades.get(0).id()).isEqualTo(publishedEntry.id());
    }

    @Test
    void listComments_UC25_A1_onlyApprovedCommentsVisible() {
        StudentCommentResponse approved = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", session.id(), null,
                        LocalDate.now(), "Chăm chỉ.", null, null, false), teacher.getId());
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(approved.id())), teacher.getId());
        studentCommentService.decideComments(new DecideCommentsRequest(List.of(approved.id()), "APPROVED", null), siteManagerUser.getId());

        // A1 -- nhận xét khác vẫn DRAFT.
        studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", session.id(), null,
                        LocalDate.now(), "Nội dung chưa gửi.", null, null, false), teacher.getId());

        List<StudentCommentResponse> comments = parentPortalService.listComments(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).status()).isEqualTo("APPROVED");
    }

    @Test
    void listAttendance_UC25_MainFlow_returnsAttendanceForClass() {
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "ABSENT", null, null, "Ốm"))),
                teacher.getId());

        List<AttendanceMarkResponse> attendance = parentPortalService.listAttendance(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(attendance).hasSize(1);
        assertThat(attendance.get(0).status()).isEqualTo("ABSENT");
    }

    @Test
    void listSchedule_UC25_MainFlow_returnsClassSessions() {
        List<ClassSessionResponse> schedule = parentPortalService.listSchedule(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(schedule).extracting(ClassSessionResponse::id).contains(session.id());
    }

    @Test
    void listGrades_rejectsWhenActorNotLinkedParent() {
        User outsider = newUser("outsider.parent");
        Parent outsiderParent = new Parent();
        outsiderParent.setUser(outsider);
        parentRepository.save(outsiderParent);

        assertThatThrownBy(() -> parentPortalService.listGrades(student.getId(), schoolClass.id(), outsider.getId()))
                .isInstanceOf(NotAuthorizedForPortalAccessException.class);
    }

    @Test
    void listGrades_rejectsWhenStudentNeverEnrolledInClass() {
        Site otherSite = newSite();
        ClassResponse otherClass = classService.create(
                new CreateClassRequest(classCode(), "9B1", otherSite.getId(), schoolClass.curriculumId(), "OPEN", 20, null,
                        LocalDate.now(), null, null, null), headAcademic.getId());

        assertThatThrownBy(() -> parentPortalService.listGrades(student.getId(), otherClass.id(), parentUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
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
