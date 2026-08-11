package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AcademicTermResponse;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateAcademicTermRequest;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.EnrollmentMovementStatsResponse;
import vn.com.pps.education.dto.EnrollmentMovementTrendPoint;
import vn.com.pps.education.dto.EnrollmentMovementTrendResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
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

/** UC-69: Thống kê biến động học sinh các lớp theo kỳ — Main Flow, A1..A4. */
@Transactional
class EnrollmentMovementReportServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private EnrollmentMovementReportService enrollmentMovementReportService;

    @Autowired
    private AcademicTermService academicTermService;

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

    @Autowired
    private ClassEnrollmentRepository classEnrollmentRepository;

    @Autowired
    private AcademicTermRepository academicTermRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    private User headAcademic;
    private User siteManagerUser;
    private User outsiderSiteManagerUser;
    private Site site;
    private ClassResponse schoolClass;
    private AcademicTerm term;
    private LocalDate termStart;
    private LocalDate termEnd;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");

        var curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        var activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());

        site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now().minusYears(1), null, null), headAcademic.getId());

        termStart = LocalDate.now().minusMonths(2);
        termEnd = LocalDate.now().plusMonths(2);
        AcademicTermResponse termResponse = academicTermService.create(
                new CreateAcademicTermRequest(site.getId(), "HK-" + SEQ.incrementAndGet(), "Học kỳ test", termStart, termEnd),
                headAcademic.getId());
        term = academicTermRepository.findById(termResponse.id()).orElseThrow();

        siteManagerUser = newUser("site.manager");
        assignRole(siteManagerUser, "SITE_MANAGER");
        saveSiteManager(siteManagerUser, site);

        Site otherSite = newSite();
        outsiderSiteManagerUser = newUser("outsider.site.manager");
        assignRole(outsiderSiteManagerUser, "SITE_MANAGER");
        saveSiteManager(outsiderSiteManagerUser, otherSite);
    }

    @Test
    void getStats_UC69_MainFlow_computesOpeningClosingAndMovementCounts() {
        // A: đã ở lớp trước kỳ, còn ACTIVE tới cuối kỳ -> đầu kỳ=1, cuối kỳ=1
        studentEnrolledBefore();
        // B: nhập học mới trong kỳ, còn ACTIVE tới cuối kỳ -> nhập mới=1, cuối kỳ +1
        newStudentIn(termStart.plusDays(5));
        // C: đã ở lớp trước kỳ, nghỉ trong kỳ -> đầu kỳ +1, nghỉ/rút=1, KHÔNG tính cuối kỳ
        withdraw(studentEnrolledBefore(), termStart.plusDays(10), ClassEnrollment.Status.WITHDRAWN);
        // D: đã ở lớp trước kỳ, chuyển lớp trong kỳ -> đầu kỳ +1, chuyển lớp=1, KHÔNG tính cuối kỳ
        withdraw(studentEnrolledBefore(), termStart.plusDays(15), ClassEnrollment.Status.TRANSFERRED);
        // E: đã ở lớp trước kỳ, hoàn thành trong kỳ -> đầu kỳ +1, hoàn thành=1, KHÔNG tính cuối kỳ
        withdraw(studentEnrolledBefore(), termStart.plusDays(20), ClassEnrollment.Status.COMPLETED);

        EnrollmentMovementStatsResponse stats = enrollmentMovementReportService.getStats(term.getId(), schoolClass.id(), headAcademic.getId());

        assertThat(stats.classes()).hasSize(1);
        var row = stats.classes().get(0);
        assertThat(row.openingHeadcount()).isEqualTo(4); // A, C, D, E đã có mặt đầu kỳ
        assertThat(row.newEnrollments()).isEqualTo(1); // B
        assertThat(row.withdrawnCount()).isEqualTo(1); // C
        assertThat(row.transferredCount()).isEqualTo(1); // D
        assertThat(row.completedCount()).isEqualTo(1); // E
        assertThat(row.closingHeadcount()).isEqualTo(2); // A, B còn ACTIVE cuối kỳ

        // Chỉ 1 lớp trong phạm vi -> dòng tổng cộng khớp số liệu dòng lớp (khác classId/tên, xem sumTotals()).
        var totals = stats.totals();
        assertThat(totals.classId()).isNull();
        assertThat(totals.openingHeadcount()).isEqualTo(row.openingHeadcount());
        assertThat(totals.newEnrollments()).isEqualTo(row.newEnrollments());
        assertThat(totals.withdrawnCount()).isEqualTo(row.withdrawnCount());
        assertThat(totals.transferredCount()).isEqualTo(row.transferredCount());
        assertThat(totals.completedCount()).isEqualTo(row.completedCount());
        assertThat(totals.closingHeadcount()).isEqualTo(row.closingHeadcount());
    }

    @Test
    void getStats_UC69_A1_notFoundWhenAcademicTermDoesNotExist() {
        assertThatThrownBy(() -> enrollmentMovementReportService.getStats(-1L, null, headAcademic.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStats_UC69_A1_notFoundWhenClassDoesNotExist() {
        assertThatThrownBy(() -> enrollmentMovementReportService.getStats(term.getId(), -1L, headAcademic.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStats_UC69_A2_rejectsClassOutsideTermSite() {
        Site otherSite = newSite();
        var otherCurriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn 2", "MAIN", null, null, null), headAcademic.getId());
        var activeOtherCurriculum = curriculumService.update(otherCurriculum.id(),
                new UpdateCurriculumRequest("Chuẩn 2", null, null, null, "ACTIVE", false), headAcademic.getId());
        ClassResponse otherClass = classService.create(
                new CreateClassRequest(classCode(), "9A1", otherSite.getId(), activeOtherCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now().minusYears(1), null, null), headAcademic.getId());

        assertThatThrownBy(() -> enrollmentMovementReportService.getStats(term.getId(), otherClass.id(), headAcademic.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStats_UC69_A3_rejectsSiteManagerOutsideOwnSite() {
        assertThatThrownBy(() -> enrollmentMovementReportService.getStats(term.getId(), null, outsiderSiteManagerUser.getId()))
                .isInstanceOf(NotSiteManagerForSiteException.class);
    }

    @Test
    void getStats_UC69_MainFlow_allowsSiteManagerOfTermSite() {
        EnrollmentMovementStatsResponse stats = enrollmentMovementReportService.getStats(term.getId(), null, siteManagerUser.getId());

        assertThat(stats.siteId()).isEqualTo(site.getId());
    }

    @Test
    void getStats_UC69_A4_returnsZeroRowWhenNoMovement() {
        EnrollmentMovementStatsResponse stats = enrollmentMovementReportService.getStats(term.getId(), schoolClass.id(), headAcademic.getId());

        assertThat(stats.classes()).hasSize(1);
        var row = stats.classes().get(0);
        assertThat(row.openingHeadcount()).isZero();
        assertThat(row.newEnrollments()).isZero();
        assertThat(row.withdrawnCount()).isZero();
        assertThat(row.transferredCount()).isZero();
        assertThat(row.completedCount()).isZero();
        assertThat(row.closingHeadcount()).isZero();
    }

    @Test
    void getTrend_UC69_bo_sung_monthlyPointsSumUpToSameTotalsAsGetStats() {
        studentEnrolledBefore();
        newStudentIn(termStart.plusDays(5));
        withdraw(studentEnrolledBefore(), termStart.plusDays(10), ClassEnrollment.Status.WITHDRAWN);
        withdraw(studentEnrolledBefore(), termStart.plusDays(15), ClassEnrollment.Status.TRANSFERRED);
        withdraw(studentEnrolledBefore(), termStart.plusDays(20), ClassEnrollment.Status.COMPLETED);

        EnrollmentMovementStatsResponse stats = enrollmentMovementReportService.getStats(term.getId(), schoolClass.id(), headAcademic.getId());
        EnrollmentMovementTrendResponse trend = enrollmentMovementReportService.getTrend(term.getId(), schoolClass.id(), headAcademic.getId());

        assertThat(trend.points()).isNotEmpty();
        assertThat(trend.points().get(0).periodStart()).isEqualTo(termStart);
        assertThat(trend.points().get(trend.points().size() - 1).periodEnd()).isEqualTo(termEnd);
        // monthIndex liên tục 1..N, không nhảy cóc/lặp.
        for (int i = 0; i < trend.points().size(); i++) {
            assertThat(trend.points().get(i).monthIndex()).isEqualTo(i + 1);
        }

        int sumNew = trend.points().stream().mapToInt(EnrollmentMovementTrendPoint::newEnrollments).sum();
        int sumWithdrawn = trend.points().stream().mapToInt(EnrollmentMovementTrendPoint::withdrawnCount).sum();
        int sumTransferred = trend.points().stream().mapToInt(EnrollmentMovementTrendPoint::transferredCount).sum();
        int sumCompleted = trend.points().stream().mapToInt(EnrollmentMovementTrendPoint::completedCount).sum();
        assertThat(sumNew).isEqualTo(stats.totals().newEnrollments());
        assertThat(sumWithdrawn).isEqualTo(stats.totals().withdrawnCount());
        assertThat(sumTransferred).isEqualTo(stats.totals().transferredCount());
        assertThat(sumCompleted).isEqualTo(stats.totals().completedCount());
        // headcount điểm cuối cùng (asOf termEnd) phải khớp closingHeadcount của getStats.
        assertThat(trend.points().get(trend.points().size() - 1).headcount()).isEqualTo(stats.totals().closingHeadcount());
    }

    @Test
    void getTrend_UC69_A1_notFoundWhenAcademicTermDoesNotExist() {
        assertThatThrownBy(() -> enrollmentMovementReportService.getTrend(-1L, null, headAcademic.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTrend_UC69_A3_rejectsSiteManagerOutsideOwnSite() {
        assertThatThrownBy(() -> enrollmentMovementReportService.getTrend(term.getId(), null, outsiderSiteManagerUser.getId()))
                .isInstanceOf(NotSiteManagerForSiteException.class);
    }

    @Test
    void exportStatsExcel_UC69_MainFlow_producesNonEmptyXlsx() {
        newStudentIn(termStart.plusDays(5));

        byte[] content = enrollmentMovementReportService.exportStatsExcel(term.getId(), schoolClass.id(), headAcademic.getId());

        assertThat(content).isNotEmpty();
    }

    // ===================== Helpers =====================

    private Student studentEnrolledBefore() {
        Student student = newStudent();
        ClassEnrollment enrollment = new ClassEnrollment();
        enrollment.setSchoolClass(classEntity());
        enrollment.setStudent(student);
        enrollment.setEnrolledDate(termStart.minusMonths(6));
        enrollment.setStatus(ClassEnrollment.Status.ACTIVE);
        enrollment.setEnrolledBy(headAcademic);
        classEnrollmentRepository.save(enrollment);
        return student;
    }

    private Student newStudentIn(LocalDate enrolledDate) {
        Student student = newStudent();
        ClassEnrollment enrollment = new ClassEnrollment();
        enrollment.setSchoolClass(classEntity());
        enrollment.setStudent(student);
        enrollment.setEnrolledDate(enrolledDate);
        enrollment.setStatus(ClassEnrollment.Status.ACTIVE);
        enrollment.setEnrolledBy(headAcademic);
        classEnrollmentRepository.save(enrollment);
        return student;
    }

    private void withdraw(Student student, LocalDate withdrawnDate, ClassEnrollment.Status status) {
        ClassEnrollment enrollment = classEnrollmentRepository
                .findBySchoolClassIdAndStudentIdAndStatus(schoolClass.id(), student.getId(), ClassEnrollment.Status.ACTIVE)
                .orElseThrow();
        enrollment.setStatus(status);
        enrollment.setWithdrawnDate(withdrawnDate);
        classEnrollmentRepository.save(enrollment);
    }

    private SchoolClass classEntity() {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(schoolClass.id()).orElseThrow();
    }

    private Student newStudent() {
        User studentUser = newUser("student");
        Student student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-TEST-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(student);
    }

    private void saveSiteManager(User user, Site targetSite) {
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(targetSite);
        siteManager.setUser(user);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(user);
        siteManagerRepository.save(siteManager);
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

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
