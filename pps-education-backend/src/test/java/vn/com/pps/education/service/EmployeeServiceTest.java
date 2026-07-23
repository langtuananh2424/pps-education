package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.Position;
import vn.com.pps.education.domain.PositionDefaultRole;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.CreateCommendationRequest;
import vn.com.pps.education.dto.CreateEmployeeRequest;
import vn.com.pps.education.dto.CreateEmploymentContractRequest;
import vn.com.pps.education.dto.CreateUserRequest;
import vn.com.pps.education.dto.CreateQualificationRequest;
import vn.com.pps.education.dto.EmployeeResponse;
import vn.com.pps.education.dto.EmploymentContractResponse;
import vn.com.pps.education.dto.ExpiringContractResponse;
import vn.com.pps.education.dto.UpdateEmployeeRequest;
import vn.com.pps.education.exception.ActiveContractAlreadyExistsException;
import vn.com.pps.education.exception.DuplicateEmployeeCodeException;
import vn.com.pps.education.exception.EmployeeAlreadyExistsException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.EmployeeHistoryRepository;
import vn.com.pps.education.repository.EmploymentContractHistoryRepository;
import vn.com.pps.education.repository.PositionDefaultRoleRepository;
import vn.com.pps.education.repository.PositionRepository;
import vn.com.pps.education.repository.RoleRepository;
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
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeHistoryRepository employeeHistoryRepository;

    @Autowired
    private EmploymentContractHistoryRepository employmentContractHistoryRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private PositionDefaultRoleRepository positionDefaultRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private User hrManager;

    @BeforeEach
    void setUp() {
        hrManager = newUser("hr.manager");
    }

    @Test
    void create_UC08_MainFlow_createsEmployeeAndWritesHistory() {
        User target = newUser("employee.new");

        EmployeeResponse response = employeeService.create(
                new CreateEmployeeRequest(target.getId(), null, employeeCode(),
                        LocalDate.of(1995, 1, 1), "001095000123", null, null, null, null, null, null, null, null,
                        "TEACHER", null, null, null, true, LocalDate.of(2024, 1, 1), null),
                hrManager.getId());

        assertThat(response.id()).isNotNull();
        assertThat(response.employeeType()).isEqualTo("TEACHER");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(employeeHistoryRepository.findByEmployeeIdOrderByCreatedAtDesc(response.id()))
                .hasSize(1)
                .allMatch(h -> h.getAction() == vn.com.pps.education.domain.EmployeeHistory.Action.CREATED);
    }

    @Test
    void create_UC08_MainFlow_withNewAccount_createsUserAndEmployeeInOneTransaction() {
        CreateUserRequest newAccount = new CreateUserRequest(
                "nv.moi." + CODE_SEQ.incrementAndGet(), "nv.moi." + CODE_SEQ.get() + "@pps.edu.vn",
                "Nhân Viên Mới", null, "MatKhau@8kytu");

        EmployeeResponse response = employeeService.create(
                new CreateEmployeeRequest(null, newAccount, employeeCode(),
                        LocalDate.of(1995, 1, 1), null, null, null, null, null, null, null, null, null,
                        "TEACHER", null, null, null, true, LocalDate.of(2024, 1, 1), null),
                hrManager.getId());

        assertThat(response.id()).isNotNull();
        User created = userRepository.findByUsername(newAccount.username()).orElseThrow();
        assertThat(created.getEmail()).isEqualTo(newAccount.email());
        assertThat(created.getPasswordHash()).startsWith("$2"); // BCrypt (NFR-SEC-01)
        assertThat(response.userId()).isEqualTo(created.getId());
    }

    @Test
    void create_UC08_rejectsWhenBothUserIdAndNewAccountProvided() {
        User target = newUser("employee.both");
        CreateUserRequest newAccount = new CreateUserRequest(
                "nv.both." + CODE_SEQ.incrementAndGet(), "nv.both." + CODE_SEQ.get() + "@pps.edu.vn",
                "Nhân Viên Both", null, null);

        assertThatThrownBy(() -> employeeService.create(
                new CreateEmployeeRequest(target.getId(), newAccount, employeeCode(),
                        LocalDate.of(1995, 1, 1), null, null, null, null, null, null, null, null, null,
                        "STAFF", null, null, null, true, LocalDate.of(2024, 1, 1), null),
                hrManager.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_UC08_rejectsWhenNeitherUserIdNorNewAccountProvided() {
        assertThatThrownBy(() -> employeeService.create(
                new CreateEmployeeRequest(null, null, employeeCode(),
                        LocalDate.of(1995, 1, 1), null, null, null, null, null, null, null, null, null,
                        "STAFF", null, null, null, true, LocalDate.of(2024, 1, 1), null),
                hrManager.getId()))
                .isInstanceOf(IllegalArgumentException.class);
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
    void create_UC08_MainFlow_setsDepartmentAndManagement() {
        User target = newUser("employee.dept");
        Department department = newDepartment();

        EmployeeResponse response = employeeService.create(
                baseEmployeeRequest(target.getId(), employeeCode(), department.getId(), true), hrManager.getId());

        assertThat(response.departmentId()).isEqualTo(department.getId());
        assertThat(response.isManagement()).isTrue();
    }

    @Test
    void create_UC08_A4_rejectsUnknownDepartment() {
        User target = newUser("employee.dept.unknown");

        assertThatThrownBy(() -> employeeService.create(
                baseEmployeeRequest(target.getId(), employeeCode(), -1L, false), hrManager.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_UC08_MainFlow_updatesDepartmentAndManagement() {
        User target = newUser("employee.dept.update");
        EmployeeResponse employee = employeeService.create(
                baseEmployeeRequest(target.getId(), employeeCode()), hrManager.getId());
        Department department = newDepartment();

        EmployeeResponse updated = employeeService.update(employee.id(),
                updateRequest(department.getId(), true), hrManager.getId());

        assertThat(updated.departmentId()).isEqualTo(department.getId());
        assertThat(updated.isManagement()).isTrue();
    }

    @Test
    void update_UC08_A3_clearsDepartmentWhenNull() {
        User target = newUser("employee.dept.clear");
        Department department = newDepartment();
        EmployeeResponse employee = employeeService.create(
                baseEmployeeRequest(target.getId(), employeeCode(), department.getId(), false), hrManager.getId());

        EmployeeResponse cleared = employeeService.update(employee.id(),
                updateRequest(null, false), hrManager.getId());

        assertThat(cleared.departmentId()).isNull();
    }

    @Test
    void update_UC08_A4_rejectsUnknownDepartment() {
        User target = newUser("employee.dept.update.unknown");
        EmployeeResponse employee = employeeService.create(
                baseEmployeeRequest(target.getId(), employeeCode()), hrManager.getId());

        assertThatThrownBy(() -> employeeService.update(employee.id(),
                updateRequest(-1L, false), hrManager.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_UC08_A5_autoAssignsDefaultRolesForPosition() {
        User target = newUser("employee.position.new");
        Position teacherLead = newPositionWithDefaultRoles("TEACHER-LEAD-" + CODE_SEQ.incrementAndGet(), "TEACHER", "HEAD_ACADEMIC");

        EmployeeResponse response = employeeService.create(
                new CreateEmployeeRequest(target.getId(), null, employeeCode(), LocalDate.of(1995, 1, 1),
                        null, null, null, null, null, null, null, null, null,
                        "MANAGER", teacherLead.getId(), null, null, true, LocalDate.of(2024, 1, 1), null),
                hrManager.getId());

        assertThat(response.positionId()).isEqualTo(teacherLead.getId());
        assertThat(assignedRoleCodes(target.getId())).containsExactlyInAnyOrder("TEACHER", "HEAD_ACADEMIC");
        assertThat(userRoleRepository.findByUserId(target.getId()))
                .allMatch(ur -> ur.getGrantedViaPosition() != null
                        && ur.getGrantedViaPosition().getId().equals(teacherLead.getId()));
    }

    @Test
    void update_UC08_A5_changingPositionRevokesStaleAutoRoleAndAssignsNew() {
        User target = newUser("employee.position.change");
        Position teacher = newPositionWithDefaultRoles("TEACHER-" + CODE_SEQ.incrementAndGet(), "TEACHER");
        Position staff = newPositionWithDefaultRoles("STAFF-" + CODE_SEQ.incrementAndGet(), "STAFF");
        EmployeeResponse employee = employeeService.create(
                new CreateEmployeeRequest(target.getId(), null, employeeCode(), LocalDate.of(1995, 1, 1),
                        null, null, null, null, null, null, null, null, null,
                        "TEACHER", teacher.getId(), null, null, true, LocalDate.of(2024, 1, 1), null),
                hrManager.getId());
        assertThat(assignedRoleCodes(target.getId())).containsExactly("TEACHER");

        employeeService.update(employee.id(), new UpdateEmployeeRequest(LocalDate.of(1995, 1, 1),
                        null, null, null, null, null, null, null, null, null,
                        "STAFF", staff.getId(), null, false, true, "ACTIVE", null, null),
                hrManager.getId());

        assertThat(assignedRoleCodes(target.getId())).containsExactly("STAFF");
    }

    @Test
    void update_UC08_A5_doesNotTouchManuallyAssignedRole() {
        User target = newUser("employee.position.manual.role");
        Role manuallyGranted = roleRepository.findByCode("SYS_ADMIN").orElseThrow();
        UserRole manualAssignment = new UserRole();
        manualAssignment.setUser(target);
        manualAssignment.setRole(manuallyGranted);
        manualAssignment.setAssignedBy(hrManager);
        userRoleRepository.save(manualAssignment);
        Position staff = newPositionWithDefaultRoles("STAFF-MAN-" + CODE_SEQ.incrementAndGet(), "STAFF");
        EmployeeResponse employee = employeeService.create(
                new CreateEmployeeRequest(target.getId(), null, employeeCode(), LocalDate.of(1995, 1, 1),
                        null, null, null, null, null, null, null, null, null,
                        "STAFF", staff.getId(), null, null, true, LocalDate.of(2024, 1, 1), null),
                hrManager.getId());

        Position teacher = newPositionWithDefaultRoles("TEACHER-MAN-" + CODE_SEQ.incrementAndGet(), "TEACHER");
        employeeService.update(employee.id(), new UpdateEmployeeRequest(LocalDate.of(1995, 1, 1),
                        null, null, null, null, null, null, null, null, null,
                        "TEACHER", teacher.getId(), null, false, true, "ACTIVE", null, null),
                hrManager.getId());

        // SYS_ADMIN gán tay -- vẫn còn dù chức vụ đổi và không nằm trong role mặc định của chức vụ nào ở trên.
        assertThat(assignedRoleCodes(target.getId())).containsExactlyInAnyOrder("SYS_ADMIN", "TEACHER");
    }

    @Test
    void update_UC08_A5_clearingPositionRevokesAutoAssignedRole() {
        User target = newUser("employee.position.clear");
        Position teacher = newPositionWithDefaultRoles("TEACHER-CLR-" + CODE_SEQ.incrementAndGet(), "TEACHER");
        EmployeeResponse employee = employeeService.create(
                new CreateEmployeeRequest(target.getId(), null, employeeCode(), LocalDate.of(1995, 1, 1),
                        null, null, null, null, null, null, null, null, null,
                        "TEACHER", teacher.getId(), null, null, true, LocalDate.of(2024, 1, 1), null),
                hrManager.getId());
        assertThat(assignedRoleCodes(target.getId())).containsExactly("TEACHER");

        employeeService.update(employee.id(), new UpdateEmployeeRequest(LocalDate.of(1995, 1, 1),
                        null, null, null, null, null, null, null, null, null,
                        "STAFF", null, null, false, true, "ACTIVE", null, null),
                hrManager.getId());

        assertThat(assignedRoleCodes(target.getId())).isEmpty();
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

    /**
     * Bổ sung (audit FE 2026-07-20): GET /api/employees?departmentId=X trước
     * đây không có tham số này ở bất kỳ tầng nào (Controller/Service/
     * Repository) — Spring MVC âm thầm bỏ qua tham số lạ, FE truyền
     * departmentId nhưng không lọc được gì.
     */
    @Test
    void search_boSung_filtersByDepartmentId() {
        Department deptA = newDepartment();
        Department deptB = newDepartment();
        EmployeeResponse employeeInA = employeeService.create(
                baseEmployeeRequest(newUser("employee.deptA").getId(), employeeCode(), deptA.getId(), false), hrManager.getId());
        employeeService.create(
                baseEmployeeRequest(newUser("employee.deptB").getId(), employeeCode(), deptB.getId(), false), hrManager.getId());

        List<EmployeeResponse> result = employeeService.search(null, deptA.getId());

        assertThat(result).extracting(EmployeeResponse::id).contains(employeeInA.id());
        assertThat(result).extracting(EmployeeResponse::departmentId).containsOnly(deptA.getId());
    }

    @Test
    void search_boSung_combinesQueryAndDepartmentIdFilters() {
        Department department = newDepartment();
        EmployeeResponse matching = employeeService.create(
                baseEmployeeRequest(newUser("employee.combo.match").getId(), "NVCOMBOX" + CODE_SEQ.incrementAndGet(),
                        department.getId(), false),
                hrManager.getId());
        employeeService.create(
                baseEmployeeRequest(newUser("employee.combo.otherdept").getId(), "NVCOMBOX" + CODE_SEQ.incrementAndGet(),
                        newDepartment().getId(), false),
                hrManager.getId());

        List<EmployeeResponse> result = employeeService.search("NVCOMBOX", department.getId());

        assertThat(result).extracting(EmployeeResponse::id).containsExactly(matching.id());
    }

    private String employeeCode() {
        return "NV" + CODE_SEQ.incrementAndGet();
    }

    private CreateEmployeeRequest baseEmployeeRequest(Long userId, String employeeCode) {
        return baseEmployeeRequest(userId, employeeCode, null, null);
    }

    private CreateEmployeeRequest baseEmployeeRequest(Long userId, String employeeCode, Long departmentId, Boolean isManagement) {
        return new CreateEmployeeRequest(userId, null, employeeCode, LocalDate.of(1995, 1, 1), null, null, null, null,
                null, null, null, null, null, "STAFF", null, departmentId, isManagement, true, LocalDate.of(2024, 1, 1), null);
    }

    private UpdateEmployeeRequest updateRequest(Long departmentId, boolean isManagement) {
        return new UpdateEmployeeRequest(LocalDate.of(1995, 1, 1), null, null, null, null, null, null, null, null,
                null, "STAFF", null, departmentId, isManagement, true, "ACTIVE", null, null);
    }

    private Position newPositionWithDefaultRoles(String code, String... roleCodes) {
        Position position = new Position();
        position.setCode(code);
        position.setName("Test Position " + code);
        position = positionRepository.save(position);
        for (String roleCode : roleCodes) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            PositionDefaultRole pdr = new PositionDefaultRole();
            pdr.setPosition(position);
            pdr.setRole(role);
            positionDefaultRoleRepository.save(pdr);
        }
        return position;
    }

    private List<String> assignedRoleCodes(Long userId) {
        return userRoleRepository.findByUserId(userId).stream().map(ur -> ur.getRole().getCode()).toList();
    }

    private Department newDepartment() {
        Department department = new Department();
        department.setCode("DEPT-EMP-" + CODE_SEQ.incrementAndGet());
        department.setName("Employee Test Dept " + CODE_SEQ.get());
        return departmentRepository.save(department);
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
