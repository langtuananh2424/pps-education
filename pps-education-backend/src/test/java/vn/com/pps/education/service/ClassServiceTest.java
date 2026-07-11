package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateCustomCurriculumRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.DecideCurriculumApprovalRequest;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.WithdrawEnrollmentRequest;
import vn.com.pps.education.exception.ClassEnrollmentAlreadyActiveException;
import vn.com.pps.education.exception.CurriculumNotActiveException;
import vn.com.pps.education.exception.CurriculumNotAvailableForSiteException;
import vn.com.pps.education.exception.LinkedClassRequiresPartnerSiteException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-18: Xếp lớp & gán khóa học — Main Flow (bước 1-6), A2 (thiếu Điểm
 * trường cho Lớp liên kết). Xem docs/uc/phan-he-06-hoc-thuat.md.
 * A1 (trùng phòng học) không áp dụng — xem Javadoc ClassService.
 */
@Transactional
class ClassServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

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
    private StudentRepository studentRepository;

    @Autowired
    private SiteManagerRepository siteManagerRepository;

    private User headAcademic;
    private CurriculumResponse activeCurriculum;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());
    }

    @Test
    void create_UC18_MainFlow_createsOpenClassAndWritesHistory() {
        Site site = newSite(Site.SiteType.OWNED);

        ClassResponse response = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN",
                        25, 10, LocalDate.now(), null, "2026-2027", "S1"),
                headAcademic.getId());

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo("PLANNED");
        assertThat(response.classCategory()).isEqualTo(activeCurriculum.classCategory());
    }

    @Test
    void create_rejectsWhenCurriculumNotActive() {
        CurriculumResponse draft = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Draft", "MAIN", null, null, null), headAcademic.getId());
        Site site = newSite(Site.SiteType.OWNED);

        assertThatThrownBy(() -> classService.create(
                new CreateClassRequest(classCode(), "X", site.getId(), draft.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null, null), headAcademic.getId()))
                .isInstanceOf(CurriculumNotActiveException.class);
    }

    @Test
    void create_UC18_A2_rejectsLinkedClassAtNonPartnerSite() {
        Site ownedSite = newSite(Site.SiteType.OWNED);

        assertThatThrownBy(() -> classService.create(
                new CreateClassRequest(classCode(), "Lớp liên kết", ownedSite.getId(), activeCurriculum.id(), "LINKED",
                        20, null, LocalDate.now(), null, null, null), headAcademic.getId()))
                .isInstanceOf(LinkedClassRequiresPartnerSiteException.class);
    }

    @Test
    void create_UC18_A2_allowsLinkedClassAtPartnerSite() {
        Site partnerSite = newSite(Site.SiteType.PARTNER);

        ClassResponse response = classService.create(
                new CreateClassRequest(classCode(), "Lớp liên kết hợp lệ", partnerSite.getId(), activeCurriculum.id(),
                        "LINKED", 20, null, LocalDate.now(), null, null, null),
                headAcademic.getId());

        assertThat(response.classType()).isEqualTo("LINKED");
    }

    @Test
    void create_rejectsCustomCurriculumUsedAtWrongSite() {
        Site customSite = newSite(Site.SiteType.OWNED);
        User siteManagerUser = newUser("site.manager.forclass");
        assignRole(siteManagerUser, "SITE_MANAGER");
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(customSite);
        siteManager.setUser(siteManagerUser);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(siteManagerUser);
        siteManagerRepository.save(siteManager);
        CurriculumResponse customCurriculum = curriculumService.createCustomCopy(
                new CreateCustomCurriculumRequest(curriculumCode(), activeCurriculum.id(), customSite.getId(), null),
                siteManagerUser.getId());
        var submitted = curriculumService.submitForApproval(customCurriculum.id(), siteManagerUser.getId());
        curriculumService.decideApproval(submitted.id(),
                new DecideCurriculumApprovalRequest("APPROVED", null), headAcademic.getId());

        Site otherSite = newSite(Site.SiteType.OWNED);

        assertThatThrownBy(() -> classService.create(
                new CreateClassRequest(classCode(), "Sai site", otherSite.getId(), customCurriculum.id(), "OPEN",
                        20, null, LocalDate.now(), null, null, null), headAcademic.getId()))
                .isInstanceOf(CurriculumNotAvailableForSiteException.class);
    }

    @Test
    void assignTeacher_UC18_MainFlow_persistsAssignment() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Có GV", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null, null), headAcademic.getId());
        User teacher = newUser("teacher.assigned");
        assignRole(teacher, "TEACHER");

        var assignment = classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());

        assertThat(classService.listTeachers(schoolClass.id())).containsExactly(assignment);
    }

    @Test
    void enroll_MainFlow_persistsEnrollment() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Có HS", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null, null), headAcademic.getId());
        Student student = newStudent();

        var enrollment = classService.enroll(schoolClass.id(),
                new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        assertThat(enrollment.status()).isEqualTo("ACTIVE");
        assertThat(classService.listEnrollments(schoolClass.id())).containsExactly(enrollment);
    }

    @Test
    void enroll_rejectsDuplicateActiveEnrollment() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Trùng ghi danh", site.getId(), activeCurriculum.id(), "OPEN",
                        20, null, LocalDate.now(), null, null, null), headAcademic.getId());
        Student student = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        assertThatThrownBy(() -> classService.enroll(schoolClass.id(),
                new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId()))
                .isInstanceOf(ClassEnrollmentAlreadyActiveException.class);
    }

    @Test
    void withdraw_MainFlow_setsWithdrawnStatus() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Rút lớp", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null, null), headAcademic.getId());
        Student student = newStudent();
        var enrollment = classService.enroll(schoolClass.id(),
                new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        var withdrawn = classService.withdraw(schoolClass.id(), enrollment.id(),
                new WithdrawEnrollmentRequest(LocalDate.now(), "Chuyển trường"), headAcademic.getId());

        assertThat(withdrawn.status()).isEqualTo("WITHDRAWN");
        assertThat(withdrawn.withdrawReason()).isEqualTo("Chuyển trường");
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

    private Site newSite(Site.SiteType type) {
        Site site = new Site();
        site.setCode("SITE-" + SEQ.incrementAndGet());
        site.setName("Test Site");
        site.setSiteType(type);
        return siteRepository.save(site);
    }

    private Student newStudent() {
        User user = newUser("student.forclass");
        Student student = new Student();
        student.setUser(user);
        student.setStudentCode("HS-TEST-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(student);
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
