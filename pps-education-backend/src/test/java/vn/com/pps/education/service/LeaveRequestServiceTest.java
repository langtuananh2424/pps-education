package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.CreateLeaveRequestRequest;
import vn.com.pps.education.dto.DecideLeaveRequestRequest;
import vn.com.pps.education.dto.LeaveRequestResponse;
import vn.com.pps.education.exception.ExecutiveExemptFromLeaveRequestException;
import vn.com.pps.education.exception.NotCurrentApproverException;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-10: Nộp đơn từ + UC-11: Duyệt đơn từ — Main Flow, A1 (UC-10: Ban giám
 * đốc miễn trừ), A2 (UC-10: phòng ban không có trưởng phòng), A1 (UC-11: từ
 * chối giữa chừng). Xem docs/uc/phan-he-04-nhan-su.md.
 */
@Transactional
class LeaveRequestServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void submit_UC10_MainFlow_departmentWithHead_createsTwoStepWorkflow() {
        User head = newUser("dept.head");
        Department department = newDepartment(head);
        User staffUser = newUser("staff.with.head");
        staffUser.setDepartment(department);
        userRepository.save(staffUser);
        newEmployee(staffUser);

        LeaveRequestResponse response = leaveRequestService.submit(staffUser.getId(),
                new CreateLeaveRequestRequest("ANNUAL", LocalDate.now().plusDays(5), LocalDate.now().plusDays(7),
                        null, null, "Nghỉ phép năm", null));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.currentStep()).isEqualTo(1);
        assertThat(response.currentApproverUserId()).isEqualTo(head.getId());
        assertThat(response.totalDays()).isEqualByComparingTo("3.00");
    }

    @Test
    void submit_UC10_A2_departmentWithoutHead_skipsDirectlyToOperationsManager() {
        Department department = newDepartment(null);
        User staffUser = newUser("staff.no.head");
        staffUser.setDepartment(department);
        userRepository.save(staffUser);
        newEmployee(staffUser);

        LeaveRequestResponse response = leaveRequestService.submit(staffUser.getId(),
                new CreateLeaveRequestRequest("SICK", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                        null, null, "Ốm", null));

        assertThat(response.currentApproverUserId()).isNull(); // role-based (OPERATIONS_MANAGER), chưa xác định người cụ thể
    }

    @Test
    void submit_UC10_managementEmployee_singleStepExecutiveWorkflow() {
        User siteManagerUser = newUser("site.manager");
        assignRole(siteManagerUser, "SITE_MANAGER");
        newEmployee(siteManagerUser);

        LeaveRequestResponse response = leaveRequestService.submit(siteManagerUser.getId(),
                new CreateLeaveRequestRequest("PERSONAL", LocalDate.now().plusDays(2), LocalDate.now().plusDays(2),
                        null, null, "Việc riêng", null));

        assertThat(response.currentStep()).isEqualTo(1);
        assertThat(response.currentApproverUserId()).isNull(); // EXECUTIVE -- role-based
    }

    @Test
    void submit_UC10_A1_rejectsExecutiveRole() {
        User executiveUser = newUser("executive");
        assignRole(executiveUser, "EXECUTIVE");
        newEmployee(executiveUser);

        assertThatThrownBy(() -> leaveRequestService.submit(executiveUser.getId(),
                new CreateLeaveRequestRequest("ANNUAL", LocalDate.now(), LocalDate.now(), null, null, "x", null)))
                .isInstanceOf(ExecutiveExemptFromLeaveRequestException.class);
    }

    @Test
    void decide_UC11_MainFlow_bothStepsApproved_finalizesApproved() {
        User head = newUser("decide.head");
        Department department = newDepartment(head);
        User staffUser = newUser("decide.staff");
        staffUser.setDepartment(department);
        userRepository.save(staffUser);
        newEmployee(staffUser);
        User opsManagerUser = newUser("decide.ops");
        assignRole(opsManagerUser, "OPS_MANAGER");

        LeaveRequestResponse submitted = leaveRequestService.submit(staffUser.getId(),
                new CreateLeaveRequestRequest("ANNUAL", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                        null, null, "Nghỉ 1 ngày", null));

        LeaveRequestResponse afterStep1 = leaveRequestService.decide(head.getId(), submitted.id(),
                new DecideLeaveRequestRequest("APPROVED", "OK"));
        assertThat(afterStep1.status()).isEqualTo("PENDING");
        assertThat(afterStep1.currentStep()).isEqualTo(2);

        LeaveRequestResponse afterStep2 = leaveRequestService.decide(opsManagerUser.getId(), submitted.id(),
                new DecideLeaveRequestRequest("APPROVED", "Duyệt"));
        assertThat(afterStep2.status()).isEqualTo("APPROVED");
        assertThat(afterStep2.finalizedAt()).isNotNull();
    }

    @Test
    void decide_UC11_A1_departmentHeadRejects_finalizesImmediatelyWithoutSecondStep() {
        User head = newUser("reject.head");
        Department department = newDepartment(head);
        User staffUser = newUser("reject.staff");
        staffUser.setDepartment(department);
        userRepository.save(staffUser);
        newEmployee(staffUser);

        LeaveRequestResponse submitted = leaveRequestService.submit(staffUser.getId(),
                new CreateLeaveRequestRequest("UNPAID", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                        null, null, "Nghỉ không lương", null));

        LeaveRequestResponse afterReject = leaveRequestService.decide(head.getId(), submitted.id(),
                new DecideLeaveRequestRequest("REJECTED", "Không đủ điều kiện"));

        assertThat(afterReject.status()).isEqualTo("REJECTED");
        assertThat(afterReject.currentStep()).isEqualTo(1); // dừng ngay, không sang bước 2
        assertThat(afterReject.finalizedAt()).isNotNull();
    }

    @Test
    void decide_rejectsWhenActorIsNotCurrentApprover() {
        User head = newUser("wrongapprover.head");
        Department department = newDepartment(head);
        User staffUser = newUser("wrongapprover.staff");
        staffUser.setDepartment(department);
        userRepository.save(staffUser);
        newEmployee(staffUser);
        User randomUser = newUser("wrongapprover.random");

        LeaveRequestResponse submitted = leaveRequestService.submit(staffUser.getId(),
                new CreateLeaveRequestRequest("ANNUAL", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                        null, null, "x", null));

        assertThatThrownBy(() -> leaveRequestService.decide(randomUser.getId(), submitted.id(),
                new DecideLeaveRequestRequest("APPROVED", null)))
                .isInstanceOf(NotCurrentApproverException.class);
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private Department newDepartment(User head) {
        Department department = new Department();
        department.setCode("DEPT-" + SEQ.incrementAndGet());
        department.setName("Test Department");
        department.setHeadUser(head);
        return departmentRepository.save(department);
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + SEQ.incrementAndGet());
        user.setEmail(prefix + "." + SEQ.incrementAndGet() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }

    private Employee newEmployee(User forUser) {
        Employee employee = new Employee();
        employee.setUser(forUser);
        employee.setEmployeeCode("NVLR" + SEQ.incrementAndGet());
        employee.setDateOfBirth(LocalDate.of(1995, 1, 1));
        employee.setEmployeeType(Employee.EmployeeType.STAFF);
        employee.setDefaultShiftRequired(true);
        employee.setHireDate(LocalDate.of(2024, 1, 1));
        return employeeRepository.save(employee);
    }
}
