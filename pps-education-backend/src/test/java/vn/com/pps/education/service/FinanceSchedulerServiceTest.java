package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Invoice;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AssignTuitionPlanRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateTuitionPlanRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.repository.InvoiceRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.SystemSettingRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-30 A1 (cron nightly đánh dấu OVERDUE, độc lập với các bước còn lại)
 * + Main Flow bước 1 (tự động sinh hóa đơn định kỳ đúng ngày cấu hình).
 * Gọi thẳng {@code runNightlyJob()} thay vì đợi cron trigger, giống
 * TaskSchedulerServiceTest.
 */
@Transactional
class FinanceSchedulerServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final String GENERATION_DAY_KEY = "finance.invoice_generation_day_of_month";

    @Autowired
    private FinanceSchedulerService financeSchedulerService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TuitionPlanService tuitionPlanService;

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
    private ParentRepository parentRepository;

    @Autowired
    private ParentStudentRepository parentStudentRepository;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User accountant;
    private CurriculumResponse activeCurriculum;
    private ClassResponse schoolClass;

    @BeforeEach
    void setUp() {
        User headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        accountant = newUser("accountant");
        assignRole(accountant, "STAFF");

        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        Student student = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        Parent parent = new Parent();
        parent.setUser(newUser("parent"));
        parent = parentRepository.save(parent);
        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationship(ParentStudent.Relationship.MOTHER);
        link.setFinancialResponsible(true);
        parentStudentRepository.save(link);

        var plan = tuitionPlanService.createPlan(new CreateTuitionPlanRequest(
                planCode(), "Học phí tháng", activeCurriculum.id(), "MONTHLY", null,
                new BigDecimal("2000000"), new BigDecimal("2000000"), null, null, null), accountant.getId());
        tuitionPlanService.assignToClass(new AssignTuitionPlanRequest(
                schoolClass.id(), plan.id(), null, null, null), accountant.getId());
    }

    @Test
    void runNightlyJob_UC30_A1_marksOverdueInvoicesIndependentOfGenerationDay() {
        Invoice overdueInvoice = createInvoiceDirectly(LocalDate.now().minusDays(5), Invoice.Status.ISSUED);
        Invoice futureInvoice = createInvoiceDirectly(LocalDate.now().plusDays(5), Invoice.Status.ISSUED);

        financeSchedulerService.runNightlyJob();

        assertThat(invoiceRepository.findById(overdueInvoice.getId()).orElseThrow().getStatus()).isEqualTo(Invoice.Status.OVERDUE);
        assertThat(invoiceRepository.findById(futureInvoice.getId()).orElseThrow().getStatus()).isEqualTo(Invoice.Status.ISSUED);
    }

    @Test
    void runNightlyJob_UC30_MainFlow_autoGeneratesInvoiceOnConfiguredDay() {
        setGenerationDay(LocalDate.now().getDayOfMonth());

        financeSchedulerService.runNightlyJob();

        List<Invoice> invoices = invoiceRepository.findAll();
        assertThat(invoices).anyMatch(i -> i.getClassEnrollment() != null
                && i.getClassEnrollment().getSchoolClass().getId().equals(schoolClass.id()));
    }

    @Test
    void runNightlyJob_doesNotAutoGenerateWhenNotConfiguredDay() {
        int notToday = LocalDate.now().getDayOfMonth() == 1 ? 2 : 1;
        setGenerationDay(notToday);

        financeSchedulerService.runNightlyJob();

        List<Invoice> invoices = invoiceRepository.findAll();
        assertThat(invoices).noneMatch(i -> i.getClassEnrollment() != null
                && i.getClassEnrollment().getSchoolClass().getId().equals(schoolClass.id()));
    }

    private void setGenerationDay(int day) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(GENERATION_DAY_KEY).orElseThrow();
        setting.setSettingValue(objectMapper.valueToTree(day));
        systemSettingRepository.save(setting);
    }

    private Invoice createInvoiceDirectly(LocalDate dueDate, Invoice.Status status) {
        Student student = newStudent();
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-TEST-" + SEQ.incrementAndGet());
        invoice.setStudent(student);
        invoice.setIssueDate(LocalDate.now().minusDays(10));
        invoice.setDueDate(dueDate);
        invoice.setSubtotal(new BigDecimal("1000000"));
        invoice.setTotalAmount(new BigDecimal("1000000"));
        invoice.setStatus(status);
        return invoiceRepository.save(invoice);
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
