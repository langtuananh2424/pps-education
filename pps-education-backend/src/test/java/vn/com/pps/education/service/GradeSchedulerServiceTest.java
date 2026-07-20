package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.GradePeriodEditWindow;
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
import vn.com.pps.education.dto.GradeComponentResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradePeriodResponse;
import vn.com.pps.education.dto.GradePeriodResultResponse;
import vn.com.pps.education.dto.PublishGradesRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.repository.GradePeriodEditWindowRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-20/A3 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): tự động
 * công bố DRAFT sau khi hết hạn X ngày, chạy song song với công bố thủ
 * công (UC-20 Main Flow). Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Transactional
class GradeSchedulerServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private GradeSchedulerService gradeSchedulerService;

    @Autowired
    private GradeService gradeService;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private AcademicSettingsService academicSettingsService;

    @Autowired
    private GradePeriodEditWindowRepository gradePeriodEditWindowRepository;

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

    @Autowired
    private NotificationService notificationService;

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
    void autoPublishExpiredGrades_UC20_A3_MainFlow_publishesDraftEntriesAndResultsPastWindow() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8.0"), false, null), teacher.getId());
        GradePeriodResultResponse result = gradeService.enterPeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(),
                new EnterGradePeriodResultRequest(new BigDecimal("8.0"), "BAND", "B2"), teacher.getId());
        expireEditWindow(schoolClass.id(), gradePeriod.id());

        gradeSchedulerService.autoPublishExpiredGrades();

        var publishedEntry = gradeService.listEntries(schoolClass.id(), gradeComponent.id()).stream()
                .filter(e -> e.id().equals(entry.id())).findFirst().orElseThrow();
        assertThat(publishedEntry.status()).isEqualTo("PUBLISHED");
        assertThat(publishedEntry.publishedBy()).isNull();
        assertThat(publishedEntry.publishedAt()).isNotNull();

        var publishedResult = gradeService.listPeriodResults(schoolClass.id(), gradePeriod.id()).stream()
                .filter(r -> r.id().equals(result.id())).findFirst().orElseThrow();
        assertThat(publishedResult.status()).isEqualTo("PUBLISHED");
        assertThat(publishedResult.publishedBy()).isNull();
        assertThat(publishedResult.publishedAt()).isNotNull();
    }

    @Test
    void autoPublishExpiredGrades_UC20_A3_doesNotTouchEntriesStillWithinWindow() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8.0"), false, null), teacher.getId());
        // Không expire window -- còn trong hạn X ngày mặc định.

        gradeSchedulerService.autoPublishExpiredGrades();

        var stillDraft = gradeService.listEntries(schoolClass.id(), gradeComponent.id()).stream()
                .filter(e -> e.id().equals(entry.id())).findFirst().orElseThrow();
        assertThat(stillDraft.status()).isEqualTo("DRAFT");
        assertThat(stillDraft.publishedAt()).isNull();
    }

    @Test
    void autoPublishExpiredGrades_UC20_A3_doesNotAffectAlreadyPublishedEntries() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8.0"), false, null), teacher.getId());
        var manuallyPublished = gradeService.publishGrades(
                new PublishGradesRequest(java.util.List.of(entry.id()), null), siteManagerUser.getId()).get(0);
        expireEditWindow(schoolClass.id(), gradePeriod.id());

        gradeSchedulerService.autoPublishExpiredGrades();

        var reloaded = gradeService.listEntries(schoolClass.id(), gradeComponent.id()).stream()
                .filter(e -> e.id().equals(entry.id())).findFirst().orElseThrow();
        assertThat(reloaded.status()).isEqualTo("PUBLISHED");
        // Job không đụng tới bản ghi đã PUBLISHED thủ công -- vẫn giữ đúng người đã công bố tay, không bị ghi đè thành NULL.
        assertThat(reloaded.publishedBy()).isEqualTo(manuallyPublished.publishedBy()).isEqualTo(siteManagerUser.getId());
    }

    @Test
    void autoPublishExpiredGrades_UC20_A3_boSung_notifiesLinkedParent() {
        User parentUser = newUser("parent.autopublish");
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent = parentRepository.save(parent);
        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationship(ParentStudent.Relationship.MOTHER);
        parentStudentRepository.save(link);
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8.0"), false, null), teacher.getId());
        expireEditWindow(schoolClass.id(), gradePeriod.id());

        gradeSchedulerService.autoPublishExpiredGrades();

        var notifications = notificationService.listMine(parentUser.getId(), PageRequest.of(0, 10));
        assertThat(notifications.getContent())
                .anySatisfy(n -> {
                    assertThat(n.notificationType()).isEqualTo("GRADE_PUBLISHED");
                    assertThat(n.entityType()).isEqualTo("GRADE_ENTRY");
                    assertThat(n.entityId()).isEqualTo(entry.id());
                });
    }

    /** Đẩy lùi mốc "lần đầu nhập" (grade_period_edit_windows) quá hạn X ngày hiện hành để mô phỏng hết hạn (UC-20 A3). */
    private void expireEditWindow(Long classId, Long gradePeriodId) {
        GradePeriodEditWindow window = gradePeriodEditWindowRepository
                .findBySchoolClassIdAndGradePeriodId(classId, gradePeriodId).orElseThrow();
        window.setFirstEnteredAt(OffsetDateTime.now().minusDays(academicSettingsService.gradeEditWindowDays() + 1L));
        gradePeriodEditWindowRepository.save(window);
    }

    private String curriculumCode() {
        return "CUR-SCHED-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-SCHED-" + SEQ.incrementAndGet();
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
        s.setCode("SITE-SCHED-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private Student newStudent() {
        User user = newUser("student");
        Student s = new Student();
        s.setUser(user);
        s.setStudentCode("HS-SCHED-" + SEQ.incrementAndGet());
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
