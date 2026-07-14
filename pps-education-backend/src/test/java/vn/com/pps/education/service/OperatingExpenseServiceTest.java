package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.CreateOperatingExpenseRequest;
import vn.com.pps.education.dto.DecideOperatingExpenseRequest;
import vn.com.pps.education.dto.OperatingExpenseResponse;
import vn.com.pps.education.exception.OperatingExpenseAlreadyDecidedException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
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
 * UC-31: Ghi nhận chi vận hành — Main Flow (bước 1-4), A1 (chi phí dùng
 * chung nhiều điểm trường, siteId để trống), A2 (Ban giám đốc duyệt/từ
 * chối, bổ sung FR-FIN-03). Xem docs/uc/phan-he-08-tai-chinh.md.
 */
@Transactional
class OperatingExpenseServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private OperatingExpenseService operatingExpenseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SiteRepository siteRepository;

    private User accountant;
    private User executive;
    private Site site;

    @BeforeEach
    void setUp() {
        accountant = newUser("accountant");
        assignRole(accountant, "STAFF");
        executive = newUser("executive");
        assignRole(executive, "EXECUTIVE");
        site = newSite();
    }

    @Test
    void create_UC31_MainFlow_recordsExpenseForSpecificSite() {
        OperatingExpenseResponse expense = operatingExpenseService.create(new CreateOperatingExpenseRequest(
                "RENT", site.getId(), LocalDate.now(), new BigDecimal("1500000"), "Thuê mặt bằng tháng 7",
                "BANK_TRANSFER", "Chủ nhà A", "RC-100", null), accountant.getId());

        assertThat(expense.status()).isEqualTo("RECORDED");
        assertThat(expense.siteId()).isEqualTo(site.getId());
        assertThat(expense.expenseNumber()).startsWith("EXP-");

        List<OperatingExpenseResponse> list = operatingExpenseService.listBySiteAndPeriod(
                site.getId(), LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertThat(list).extracting(OperatingExpenseResponse::id).contains(expense.id());
    }

    @Test
    void create_UC31_A1_sharedExpenseAcrossSitesLeavesSiteIdNull() {
        OperatingExpenseResponse expense = operatingExpenseService.create(new CreateOperatingExpenseRequest(
                "CDN", null, LocalDate.now(), new BigDecimal("3000000"), "Hạ tầng CDN dùng chung toàn hệ thống",
                "BANK_TRANSFER", "Nhà cung cấp CDN", null, null), accountant.getId());

        assertThat(expense.siteId()).isNull();

        List<OperatingExpenseResponse> list = operatingExpenseService.listBySiteAndPeriod(
                null, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertThat(list).extracting(OperatingExpenseResponse::id).contains(expense.id());
    }

    @Test
    void decide_UC31_A2_approvesRecordedExpense() {
        OperatingExpenseResponse expense = operatingExpenseService.create(new CreateOperatingExpenseRequest(
                "RENT", site.getId(), LocalDate.now(), new BigDecimal("1500000"), "Thuê mặt bằng tháng 7",
                "BANK_TRANSFER", "Chủ nhà A", "RC-100", null), accountant.getId());

        OperatingExpenseResponse decided = operatingExpenseService.decide(expense.id(),
                new DecideOperatingExpenseRequest("APPROVED", null), executive.getId());

        assertThat(decided.status()).isEqualTo("APPROVED");
        assertThat(decided.approvedBy()).isEqualTo(executive.getId());
        assertThat(decided.rejectionReason()).isNull();
    }

    @Test
    void decide_UC31_A2_rejectsWithReasonRecordsRejectionReason() {
        OperatingExpenseResponse expense = operatingExpenseService.create(new CreateOperatingExpenseRequest(
                "RENT", site.getId(), LocalDate.now(), new BigDecimal("1500000"), "Thuê mặt bằng tháng 7",
                "BANK_TRANSFER", "Chủ nhà A", "RC-100", null), accountant.getId());

        OperatingExpenseResponse decided = operatingExpenseService.decide(expense.id(),
                new DecideOperatingExpenseRequest("REJECTED", "Thiếu chứng từ hợp lệ"), executive.getId());

        assertThat(decided.status()).isEqualTo("REJECTED");
        assertThat(decided.rejectionReason()).isEqualTo("Thiếu chứng từ hợp lệ");
    }

    @Test
    void decide_UC31_rejectsMissingReasonWhenRejecting() {
        OperatingExpenseResponse expense = operatingExpenseService.create(new CreateOperatingExpenseRequest(
                "RENT", site.getId(), LocalDate.now(), new BigDecimal("1500000"), "Thuê mặt bằng tháng 7",
                "BANK_TRANSFER", "Chủ nhà A", "RC-100", null), accountant.getId());

        assertThatThrownBy(() -> operatingExpenseService.decide(expense.id(),
                new DecideOperatingExpenseRequest("REJECTED", null), executive.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decide_UC31_rejectsWhenAlreadyDecided() {
        OperatingExpenseResponse expense = operatingExpenseService.create(new CreateOperatingExpenseRequest(
                "RENT", site.getId(), LocalDate.now(), new BigDecimal("1500000"), "Thuê mặt bằng tháng 7",
                "BANK_TRANSFER", "Chủ nhà A", "RC-100", null), accountant.getId());
        operatingExpenseService.decide(expense.id(), new DecideOperatingExpenseRequest("APPROVED", null), executive.getId());

        assertThatThrownBy(() -> operatingExpenseService.decide(expense.id(),
                new DecideOperatingExpenseRequest("REJECTED", "Đổi ý"), executive.getId()))
                .isInstanceOf(OperatingExpenseAlreadyDecidedException.class);
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
