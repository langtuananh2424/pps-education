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
import vn.com.pps.education.dto.AssignTuitionPlanRequest;
import vn.com.pps.education.dto.ChainFinancialReportResponse;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateOperatingExpenseRequest;
import vn.com.pps.education.dto.CreateTuitionPlanRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.FinancialReportResponse;
import vn.com.pps.education.dto.GenerateInvoicesRequest;
import vn.com.pps.education.dto.InvoiceResponse;
import vn.com.pps.education.dto.RecordManualPaymentRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-32: Xem báo cáo tài chính — Main Flow bước 2 (Quản lý điểm trường xem
 * site mình phụ trách), bước 3 (Ban giám đốc xem tổng hợp), A1 (chưa có
 * dữ liệu kỳ được chọn). Xem docs/uc/phan-he-08-tai-chinh.md.
 */
@Transactional
class FinanceReportServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private FinanceReportService financeReportService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private TuitionPlanService tuitionPlanService;

    @Autowired
    private OperatingExpenseService operatingExpenseService;

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
    private StudentRepository studentRepository;

    private User accountant;
    private User siteManagerUser;
    private User executiveUser;
    private Site site;
    private ClassResponse schoolClass;
    private LocalDate periodFrom;
    private LocalDate periodTo;

    @BeforeEach
    void setUp() {
        User headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        accountant = newUser("accountant");
        assignRole(accountant, "STAFF");
        executiveUser = newUser("executive");
        assignRole(executiveUser, "EXECUTIVE");

        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());

        site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        siteManagerUser = newUser("site.manager");
        assignRole(siteManagerUser, "SITE_MANAGER");
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(site);
        siteManager.setUser(siteManagerUser);
        siteManager.setRoleType(SiteManager.RoleType.SITE_MANAGER);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(siteManagerUser);
        siteManagerRepository.save(siteManager);

        Student student = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        var plan = tuitionPlanService.createPlan(new CreateTuitionPlanRequest(
                planCode(), "Học phí tháng", activeCurriculum.id(), "MONTHLY", null,
                new BigDecimal("2000000"), new BigDecimal("2000000"), null, null, null), accountant.getId());
        tuitionPlanService.assignToClass(new AssignTuitionPlanRequest(
                schoolClass.id(), plan.id(), null, null, null), accountant.getId());

        periodFrom = LocalDate.now().withDayOfMonth(1);
        periodTo = periodFrom.plusMonths(1).minusDays(1);
        InvoiceResponse invoice = invoiceService.generateInvoices(new GenerateInvoicesRequest(
                schoolClass.id(), periodFrom, periodTo, LocalDate.now(), LocalDate.now().plusDays(15)), accountant.getId()).get(0);
        invoiceService.recordManualPayment(invoice.id(),
                new RecordManualPaymentRequest(new BigDecimal("2000000"), "CASH", OffsetDateTime.now(), "RC-1"), accountant.getId());

        operatingExpenseService.create(new CreateOperatingExpenseRequest(
                "RENT", site.getId(), LocalDate.now(), new BigDecimal("500000"), "Thuê mặt bằng tháng",
                "BANK_TRANSFER", "Chủ nhà", null, null), accountant.getId());
    }

    @Test
    void getMySiteReports_UC32_MainFlow_siteManagerSeesOwnSiteRevenueAndExpense() {
        List<FinancialReportResponse> reports = financeReportService.getMySiteReports(periodFrom, periodTo, siteManagerUser.getId());

        assertThat(reports).hasSize(1);
        FinancialReportResponse report = reports.get(0);
        assertThat(report.siteId()).isEqualTo(site.getId());
        assertThat(report.totalRevenue()).isEqualByComparingTo("2000000");
        assertThat(report.totalExpense()).isEqualByComparingTo("500000");
        assertThat(report.totalOutstanding()).isEqualByComparingTo("0");
    }

    @Test
    void getMySiteReports_UC32_A1_emptyPeriodReturnsZeroValues() {
        LocalDate farFuture = LocalDate.now().plusYears(5);
        List<FinancialReportResponse> reports = financeReportService.getMySiteReports(
                farFuture, farFuture.plusDays(30), siteManagerUser.getId());

        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).totalRevenue()).isEqualByComparingTo("0");
        assertThat(reports.get(0).totalExpense()).isEqualByComparingTo("0");
    }

    @Test
    void getMySiteReports_rejectsWhenActorNotSiteManagerOfAnySite() {
        User outsider = newUser("outsider.sitemanager");
        assignRole(outsider, "SITE_MANAGER");

        assertThatThrownBy(() -> financeReportService.getMySiteReports(periodFrom, periodTo, outsider.getId()))
                .isInstanceOf(NotSiteManagerForSiteException.class);
    }

    @Test
    void getChainReport_UC32_MainFlow_executiveSeesChainWideTotals() {
        ChainFinancialReportResponse report = financeReportService.getChainReport(periodFrom, periodTo, executiveUser.getId());

        assertThat(report.totalRevenue()).isEqualByComparingTo("2000000");
        assertThat(report.totalExpense()).isEqualByComparingTo("500000");
        assertThat(report.bySite()).anyMatch(s -> s.siteId().equals(site.getId()));
    }

    private String curriculumCode() {
        return "CUR-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-" + SEQ.incrementAndGet();
    }

    private String planCode() {
        return "PLAN-" + SEQ.incrementAndGet();
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
