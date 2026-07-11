package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateCommendationRequest;
import vn.com.pps.education.dto.CreateEmployeeRequest;
import vn.com.pps.education.dto.CreateEmploymentContractRequest;
import vn.com.pps.education.dto.CreateQualificationRequest;
import vn.com.pps.education.dto.EmployeeResponse;
import vn.com.pps.education.dto.EmploymentContractResponse;
import vn.com.pps.education.dto.ExpiringContractResponse;
import vn.com.pps.education.exception.ActiveContractAlreadyExistsException;
import vn.com.pps.education.exception.DuplicateEmployeeCodeException;
import vn.com.pps.education.exception.EmployeeAlreadyExistsException;
import vn.com.pps.education.repository.EmployeeHistoryRepository;
import vn.com.pps.education.repository.EmploymentContractHistoryRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-08: Quản lý hồ sơ nhân sự — Main Flow (bước 1-5), A2 (cảnh báo hợp đồng
 * sắp/đã hết hạn). Xem docs/uc/phan-he-04-nhan-su.md.
 * A1 (nhân sự nhiều điểm trường) không có code ở Service này — không cần test.
 */
@Transactional
class EmployeeServiceTest extends AbstractIntegrationTest {

    // employees.employee_code là VARCHAR(20) — System.nanoTime() (19 chữ số) làm
    // quá dài, dùng counter ngắn để vẫn duy nhất trong 1 lần chạy test.
    private static final AtomicLong CODE_SEQ = new AtomicLong();

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeHistoryRepository employeeHistoryRepository;

    @Autowired
    private EmploymentContractHistoryRepository employmentContractHistoryRepository;

    private User hrManager;

    @BeforeEach
    void setUp() {
        hrManager = newUser("hr.manager");
    }

    @Test
    void create_UC08_MainFlow_createsEmployeeAndWritesHistory() {
        User target = newUser("employee.new");

        EmployeeResponse response = employeeService.create(
                new CreateEmployeeRequest(target.getId(), employeeCode(),
                        LocalDate.of(1995, 1, 1), "001095000123", null, null, null, null, null, null, null, null,
                        "TEACHER", "Giáo viên tiếng Anh", true, LocalDate.of(2024, 1, 1)),
                hrManager.getId());

        assertThat(response.id()).isNotNull();
        assertThat(response.employeeType()).isEqualTo("TEACHER");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(employeeHistoryRepository.findByEmployeeIdOrderByCreatedAtDesc(response.id()))
                .hasSize(1)
                .allMatch(h -> h.getAction() == vn.com.pps.education.domain.EmployeeHistory.Action.CREATED);
    }

    @Test
    void create_rejectsDuplicateEmployeeCode() {
        User targetA = newUser("employee.a");
        User targetB = newUser("employee.b");
        String code = employeeCode();
        employeeService.create(baseEmployeeRequest(targetA.getId(), code), hrManager.getId());

        assertThatThrownBy(() -> employeeService.create(baseEmployeeRequest(targetB.getId(), code), hrManager.getId()))
                .isInstanceOf(DuplicateEmployeeCodeException.class);
    }

    @Test
    void create_rejectsWhenUserAlreadyHasEmployeeProfile() {
        User target = newUser("employee.dup.user");
        employeeService.create(baseEmployeeRequest(target.getId(), employeeCode()), hrManager.getId());

        assertThatThrownBy(() -> employeeService.create(
                baseEmployeeRequest(target.getId(), employeeCode()), hrManager.getId()))
                .isInstanceOf(EmployeeAlreadyExistsException.class);
    }

    @Test
    void addQualificationAndCommendation_UC08_MainFlow_persistsSubRecords() {
        User target = newUser("employee.subrecords");
        EmployeeResponse employee = employeeService.create(
                baseEmployeeRequest(target.getId(), employeeCode()), hrManager.getId());

        var qualification = employeeService.addQualification(employee.id(),
                new CreateQualificationRequest("LANGUAGE_CERT", "IELTS 8.0", "British Council",
                        LocalDate.of(2023, 6, 1), LocalDate.of(2025, 6, 1), null));
        var commendation = employeeService.addCommendation(employee.id(),
                new CreateCommendationRequest("COMMENDATION", LocalDate.of(2024, 12, 1), "Giáo viên xuất sắc quý 4",
                        new BigDecimal("500000")),
                hrManager.getId());

        assertThat(employeeService.listQualifications(employee.id())).containsExactly(qualification);
        assertThat(employeeService.listCommendations(employee.id())).containsExactly(commendation);
    }

    @Test
    void addContract_UC08_MainFlow_addsActiveContractAndWritesHistory() {
        User target = newUser("employee.contract");
        EmployeeResponse employee = employeeService.create(
                baseEmployeeRequest(target.getId(), employeeCode()), hrManager.getId());

        EmploymentContractResponse contract = employeeService.addContract(employee.id(),
                new CreateEmploymentContractRequest("HD2026-" + System.nanoTime(), "INDEFINITE",
                        LocalDate.of(2024, 1, 1), null, new BigDecimal("15000000"), "MONTHLY", "ACTIVE", null),
                hrManager.getId());

        assertThat(contract.status()).isEqualTo("ACTIVE");
        assertThat(employeeService.listContracts(employee.id())).containsExactly(contract);
        assertThat(employmentContractHistoryRepository.findByContractIdOrderByCreatedAtDesc(contract.id()))
                .hasSize(1)
                .allMatch(h -> h.getAction() == vn.com.pps.education.domain.EmploymentContractHistory.Action.CREATED);
    }

    @Test
    void addContract_rejectsSecondActiveContractForSameEmployee() {
        User target = newUser("employee.two.active.contracts");
        EmployeeResponse employee = employeeService.create(
                baseEmployeeRequest(target.getId(), employeeCode()), hrManager.getId());
        employeeService.addContract(employee.id(),
                new CreateEmploymentContractRequest("HD2026-A-" + System.nanoTime(), "FIXED_TERM",
                        LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), new BigDecimal("12000000"), "MONTHLY",
                        "ACTIVE", null),
                hrManager.getId());

        assertThatThrownBy(() -> employeeService.addContract(employee.id(),
                new CreateEmploymentContractRequest("HD2026-B-" + System.nanoTime(), "INDEFINITE",
                        LocalDate.of(2025, 1, 1), null, new BigDecimal("13000000"), "MONTHLY", "ACTIVE", null),
                hrManager.getId()))
                .isInstanceOf(ActiveContractAlreadyExistsException.class);
    }

    @Test
    void listExpiringContracts_UC08_A2_returnsOnlyActiveContractsEndingWithinWindow() {
        User target = newUser("employee.expiring");
        EmployeeResponse employee = employeeService.create(
                baseEmployeeRequest(target.getId(), employeeCode()), hrManager.getId());

        EmploymentContractResponse expiringSoon = employeeService.addContract(employee.id(),
                new CreateEmploymentContractRequest("HD2026-SOON-" + System.nanoTime(), "FIXED_TERM",
                        LocalDate.now().minusMonths(11), LocalDate.now().plusDays(10),
                        new BigDecimal("12000000"), "MONTHLY", "ACTIVE", null),
                hrManager.getId());

        User target2 = newUser("employee.expiring.far");
        EmployeeResponse employee2 = employeeService.create(
                baseEmployeeRequest(target2.getId(), employeeCode()), hrManager.getId());
        employeeService.addContract(employee2.id(),
                new CreateEmploymentContractRequest("HD2026-FAR-" + System.nanoTime(), "FIXED_TERM",
                        LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6),
                        new BigDecimal("12000000"), "MONTHLY", "ACTIVE", null),
                hrManager.getId());

        List<ExpiringContractResponse> expiring = employeeService.listExpiringContracts(30);

        assertThat(expiring).extracting(ExpiringContractResponse::contractId).contains(expiringSoon.id());
        assertThat(expiring).extracting(ExpiringContractResponse::employeeId).doesNotContain(employee2.id());
    }

    private String employeeCode() {
        return "NV" + CODE_SEQ.incrementAndGet();
    }

    private CreateEmployeeRequest baseEmployeeRequest(Long userId, String employeeCode) {
        return new CreateEmployeeRequest(userId, employeeCode, LocalDate.of(1995, 1, 1), null, null, null, null,
                null, null, null, null, null, "STAFF", "Nhân viên", true, LocalDate.of(2024, 1, 1));
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
