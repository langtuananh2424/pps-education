package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.GradeEntry;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentStudent;
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
import vn.com.pps.education.dto.CreateGradeComponentRequest;
import vn.com.pps.education.dto.CreateGradePeriodRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnterGradePeriodResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeAppealResponse;
import vn.com.pps.education.dto.GradeComponentResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradePeriodResponse;
import vn.com.pps.education.dto.GradePeriodResultResponse;
import vn.com.pps.education.dto.PublishGradesRequest;
import vn.com.pps.education.dto.SubmitGradeAppealRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.AppealAlreadyAcceptedException;
import vn.com.pps.education.exception.AppealAlreadyOpenException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.NotAuthorizedForPortalAccessException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.GradeEntryRepository;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-62: Phúc khảo điểm (bổ sung ngoài SDD gốc, đã xác nhận với người dùng,
 * V43) — Main Flow bước 1-3 (gửi + tiếp nhận), A1 (gửi khi không đủ điều
 * kiện), A2 (giáo viên khác cố tiếp nhận). Phần "sửa điểm trong lúc APPEAL"
 * (bước 4-5) test ở GradeServiceTest (thuộc GradeService, không lặp lại ở
 * đây). Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Transactional
class GradeAppealServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private GradeAppealService gradeAppealService;

    @Autowired
    private GradeService gradeService;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private GradeEntryRepository gradeEntryRepository;

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
    private StudentRepository studentRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private ParentStudentRepository parentStudentRepository;

    private User headAcademic;
    private User teacher;
    private User siteManagerUser;
    private ClassResponse schoolClass;
    private GradePeriodResponse gradePeriod;
    private GradeComponentResponse gradeComponent;
    private Student student;

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
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(site);
        siteManager.setUser(siteManagerUser);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(siteManagerUser);
        siteManagerRepository.save(siteManager);

        gradePeriod = gradeService.createGradePeriod(activeCurriculum.id(),
                new CreateGradePeriodRequest("MID_1", "Giữa kỳ 1", 1, new BigDecimal("50"), null, null), headAcademic.getId());
        gradeComponent = gradeService.addGradeComponent(gradePeriod.id(),
                new CreateGradeComponentRequest(null, null, "SPEAKING", "Nói", new BigDecimal("10.00"), null, null, 1),
                headAcademic.getId());

        student = newStudent();
    }

    @Test
    void submitAppeal_UC62_MainFlow_byStudent_createsAppealAndChangesEntryStatusToAppeal() {
        GradeEntryResponse entry = publishedEntry();

        GradeAppealResponse appeal = gradeAppealService.submitAppeal(
                new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), "Nghĩ là chấm sai"), student.getUser().getId());

        assertThat(appeal.status()).isEqualTo("PENDING");
        assertThat(appeal.entityType()).isEqualTo("GRADE_ENTRY");
        assertThat(appeal.entityId()).isEqualTo(entry.id());
        assertThat(appeal.reason()).isEqualTo("Nghĩ là chấm sai");
        assertThat(gradeEntryRepository.findById(entry.id()).orElseThrow().getStatus()).isEqualTo(GradeEntry.Status.APPEAL);
    }

    @Test
    void submitAppeal_UC62_MainFlow_byLinkedParent_succeeds() {
        GradeEntryResponse entry = publishedEntry();
        User parentUser = newUser("parent.appeal");
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent = parentRepository.save(parent);
        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationship(ParentStudent.Relationship.MOTHER);
        parentStudentRepository.save(link);

        GradeAppealResponse appeal = gradeAppealService.submitAppeal(
                new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), null), parentUser.getId());

        assertThat(appeal.requestedByUserId()).isEqualTo(parentUser.getId());
    }

    @Test
    void submitAppeal_UC62_MainFlow_forPeriodResult_succeeds() {
        GradePeriodResultResponse result = gradeService.enterPeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(),
                new EnterGradePeriodResultRequest(new BigDecimal("7.5"), "BAND", "B2"), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest(null, List.of(result.id())), siteManagerUser.getId());

        GradeAppealResponse appeal = gradeAppealService.submitAppeal(
                new SubmitGradeAppealRequest("GRADE_PERIOD_RESULT", result.id(), null), student.getUser().getId());

        assertThat(appeal.entityType()).isEqualTo("GRADE_PERIOD_RESULT");
        assertThat(gradeService.listPeriodResults(schoolClass.id(), gradePeriod.id()))
                .filteredOn(r -> r.id().equals(result.id()))
                .extracting(GradePeriodResultResponse::status).containsExactly("APPEAL");
    }

    @Test
    void submitAppeal_UC62_A1_rejectsWhenStillDraft() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());

        assertThatThrownBy(() -> gradeAppealService.submitAppeal(
                new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), null), student.getUser().getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitAppeal_UC62_A1_rejectsWhenAlreadyOfficial() {
        GradeEntryResponse entry = publishedEntry();
        GradeEntry raw = gradeEntryRepository.findById(entry.id()).orElseThrow();
        raw.setStatus(GradeEntry.Status.OFFICIAL);
        gradeEntryRepository.save(raw);

        assertThatThrownBy(() -> gradeAppealService.submitAppeal(
                new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), null), student.getUser().getId()))
                .isInstanceOf(vn.com.pps.education.exception.GradeNotEditableException.class);
    }

    @Test
    void submitAppeal_UC62_A_rejectsDuplicateOpenAppeal() {
        GradeEntryResponse entry = publishedEntry();
        gradeAppealService.submitAppeal(new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), null), student.getUser().getId());

        assertThatThrownBy(() -> gradeAppealService.submitAppeal(
                new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), null), student.getUser().getId()))
                .isInstanceOf(AppealAlreadyOpenException.class);
    }

    @Test
    void submitAppeal_rejectsWhenActorNotOwnerOrLinkedParent() {
        GradeEntryResponse entry = publishedEntry();
        Student outsider = newStudent();

        assertThatThrownBy(() -> gradeAppealService.submitAppeal(
                new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), null), outsider.getUser().getId()))
                .isInstanceOf(NotAuthorizedForPortalAccessException.class);
    }

    @Test
    void acceptAppeal_UC62_MainFlow_setsAcceptedStatus() {
        GradeEntryResponse entry = publishedEntry();
        GradeAppealResponse appeal = gradeAppealService.submitAppeal(
                new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), null), student.getUser().getId());

        GradeAppealResponse accepted = gradeAppealService.acceptAppeal(appeal.id(), teacher.getId());

        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(accepted.acceptedByUserId()).isEqualTo(teacher.getId());
        assertThat(accepted.acceptedAt()).isNotNull();
    }

    @Test
    void acceptAppeal_UC62_A2_rejectsWhenAlreadyAccepted() {
        GradeEntryResponse entry = publishedEntry();
        GradeAppealResponse appeal = gradeAppealService.submitAppeal(
                new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), null), student.getUser().getId());
        gradeAppealService.acceptAppeal(appeal.id(), teacher.getId());

        User otherTeacher = newUser("teacher.other");
        assignRole(otherTeacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(otherTeacher.getId(), "ASSISTANT", null, LocalDate.now()), headAcademic.getId());

        assertThatThrownBy(() -> gradeAppealService.acceptAppeal(appeal.id(), otherTeacher.getId()))
                .isInstanceOf(AppealAlreadyAcceptedException.class);
    }

    @Test
    void acceptAppeal_rejectsWhenActorNotAssignedTeacherForClass() {
        GradeEntryResponse entry = publishedEntry();
        GradeAppealResponse appeal = gradeAppealService.submitAppeal(
                new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), null), student.getUser().getId());
        User outsiderTeacher = newUser("teacher.outsider");
        assignRole(outsiderTeacher, "TEACHER");

        assertThatThrownBy(() -> gradeAppealService.acceptAppeal(appeal.id(), outsiderTeacher.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void listPendingForMyClasses_UC62_MainFlow_returnsOnlyPendingRequestsForOwnClasses() {
        GradeEntryResponse entry = publishedEntry();
        GradeAppealResponse appeal = gradeAppealService.submitAppeal(
                new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), null), student.getUser().getId());

        assertThat(gradeAppealService.listPendingForMyClasses(teacher.getId()))
                .extracting(GradeAppealResponse::id).contains(appeal.id());

        User outsiderTeacher = newUser("teacher.other.class");
        assignRole(outsiderTeacher, "TEACHER");
        assertThat(gradeAppealService.listPendingForMyClasses(outsiderTeacher.getId())).isEmpty();
    }

    @Test
    void listMyAppeals_returnsRequesterOwnHistory() {
        GradeEntryResponse entry = publishedEntry();
        GradeAppealResponse appeal = gradeAppealService.submitAppeal(
                new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), "Lý do"), student.getUser().getId());

        assertThat(gradeAppealService.listMyAppeals(student.getUser().getId()))
                .extracting(GradeAppealResponse::id).containsExactly(appeal.id());
    }

    private GradeEntryResponse publishedEntry() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest(List.of(entry.id()), null), siteManagerUser.getId());
        return entry;
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
