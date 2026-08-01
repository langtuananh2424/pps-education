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
import vn.com.pps.education.dto.AssignTuitionPlanRequest;
import vn.com.pps.education.dto.BankWebhookPaymentRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateScholarshipRequest;
import vn.com.pps.education.dto.CreateTuitionPlanRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.GenerateInvoicesRequest;
import vn.com.pps.education.dto.InvoiceResponse;
import vn.com.pps.education.dto.PaymentResponse;
import vn.com.pps.education.dto.RecordManualPaymentRequest;
import vn.com.pps.education.dto.TuitionPlanResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.NotAuthorizedForPortalAccessException;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.RoleRepository;
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
 * UC-30: Xem hóa đơn & thanh toán học phí — Main Flow (bước 1-7, sinh hóa
 * đơn + QR + webhook), A2 (thanh toán thủ công). Xem
 * docs/uc/phan-he-08-tai-chinh.md. A1 (cron OVERDUE) xem
 * FinanceSchedulerServiceTest.
 */
@Transactional
class InvoiceServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private TuitionPlanService tuitionPlanService;

    @Autowired
    private ScholarshipService scholarshipService;

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

    private User headAcademic;
    private User accountant;
    private User parentUser;
    private CurriculumResponse activeCurriculum;
    private ClassResponse schoolClass;
    private Student student;
    private TuitionPlanResponse plan;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
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

        student = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        parentUser = newUser("parent");
        assignRole(parentUser, "PARENT");
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent = parentRepository.save(parent);
        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationship(ParentStudent.Relationship.MOTHER);
        link.setFinancialResponsible(true);
        parentStudentRepository.save(link);

        plan = tuitionPlanService.createPlan(new CreateTuitionPlanRequest(
                planCode(), "Học phí tháng", activeCurriculum.id(), "MONTHLY", null,
                new BigDecimal("2000000"), new BigDecimal("2000000"), null, null, null), accountant.getId());
        tuitionPlanService.assignToClass(new AssignTuitionPlanRequest(
                schoolClass.id(), plan.id(), null, null, null), accountant.getId());
    }

    @Test
    void generateInvoices_UC30_MainFlow_createsIssuedInvoiceWithQrCode() {
        List<InvoiceResponse> invoices = invoiceService.generateInvoices(billingRequest(), accountant.getId());

        assertThat(invoices).hasSize(1);
        InvoiceResponse invoice = invoices.get(0);
        assertThat(invoice.status()).isEqualTo("ISSUED");
        assertThat(invoice.totalAmount()).isEqualByComparingTo("2000000");
        assertThat(invoice.qrCodeData()).isNotBlank();
        assertThat(invoice.payerParentId()).isNotNull();
    }

    @Test
    void generateInvoices_doesNotDuplicateForSameBillingPeriod() {
        invoiceService.generateInvoices(billingRequest(), accountant.getId());
        List<InvoiceResponse> second = invoiceService.generateInvoices(billingRequest(), accountant.getId());

        assertThat(second).isEmpty();
    }

    @Test
    void generateInvoices_UC30_A3_appliesActiveScholarshipDiscount() {
        scholarshipService.create(new CreateScholarshipRequest(
                student.getId(), scholarshipCode(), "Học bổng tài năng", "PERCENTAGE",
                new BigDecimal("10"), null, null, null, null), accountant.getId());

        List<InvoiceResponse> invoices = invoiceService.generateInvoices(billingRequest(), accountant.getId());

        InvoiceResponse invoice = invoices.get(0);
        assertThat(invoice.discountTotal()).isEqualByComparingTo("200000");
        assertThat(invoice.totalAmount()).isEqualByComparingTo("1800000");
    }

    @Test
    void getInvoice_allowsLinkedParentButRejectsUnlinkedParent() {
        InvoiceResponse invoice = invoiceService.generateInvoices(billingRequest(), accountant.getId()).get(0);

        assertThat(invoiceService.getInvoice(invoice.id(), parentUser.getId()).id()).isEqualTo(invoice.id());

        User outsiderParent = newUser("outsider.parent");
        assignRole(outsiderParent, "PARENT");
        Parent outsider = new Parent();
        outsider.setUser(outsiderParent);
        parentRepository.save(outsider);

        assertThatThrownBy(() -> invoiceService.getInvoice(invoice.id(), outsiderParent.getId()))
                .isInstanceOf(NotAuthorizedForPortalAccessException.class);
    }

    @Test
    void listMyInvoices_returnsInvoicesOfLinkedChildren() {
        invoiceService.generateInvoices(billingRequest(), accountant.getId());

        assertThat(invoiceService.listMyInvoices(parentUser.getId())).hasSize(1);
    }

    @Test
    void recordManualPayment_UC30_A2_partialThenFullMarksInvoicePaid() {
        InvoiceResponse invoice = invoiceService.generateInvoices(billingRequest(), accountant.getId()).get(0);

        PaymentResponse partial = invoiceService.recordManualPayment(invoice.id(),
                new RecordManualPaymentRequest(new BigDecimal("1000000"), "CASH", OffsetDateTime.now(), "RC-001"),
                accountant.getId());
        assertThat(partial.status()).isEqualTo("CONFIRMED");
        assertThat(invoiceService.getInvoice(invoice.id(), parentUser.getId()).status()).isEqualTo("PARTIAL_PAID");

        invoiceService.recordManualPayment(invoice.id(),
                new RecordManualPaymentRequest(new BigDecimal("1000000"), "CASH", OffsetDateTime.now(), "RC-002"),
                accountant.getId());
        assertThat(invoiceService.getInvoice(invoice.id(), parentUser.getId()).status()).isEqualTo("PAID");
    }

    @Test
    void confirmBankWebhook_UC30_MainFlow_bankStep5to7_marksInvoicePaid() {
        InvoiceResponse invoice = invoiceService.generateInvoices(billingRequest(), accountant.getId()).get(0);

        PaymentResponse payment = invoiceService.confirmBankWebhook(new BankWebhookPaymentRequest(
                invoice.invoiceNumber(), new BigDecimal("2000000"), "BANK-TXN-001", OffsetDateTime.now()));

        assertThat(payment.paymentMethod()).isEqualTo("QR_BANK");
        assertThat(invoiceService.getInvoice(invoice.id(), parentUser.getId()).status()).isEqualTo("PAID");
    }

    private GenerateInvoicesRequest billingRequest() {
        LocalDate from = LocalDate.now().withDayOfMonth(1);
        return new GenerateInvoicesRequest(schoolClass.id(), from, from.plusMonths(1).minusDays(1),
                LocalDate.now(), LocalDate.now().plusDays(15));
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

    private String scholarshipCode() {
        return "SCH-" + SEQ.incrementAndGet();
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
