package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.PayrollEntry;
import vn.com.pps.education.domain.PayrollPeriod;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.PayrollEntryResponse;
import vn.com.pps.education.exception.NotHrManagerException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.PayrollEntryRepository;
import vn.com.pps.education.repository.PayrollPeriodRepository;
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
 * UC-12: Xem bảng lương — Main Flow (tự xem + Quản lý nhân sự xem toàn hệ
 * thống), A1 (kỳ chưa có dữ liệu, fallback kỳ gần nhất).
 * Xem docs/uc/phan-he-04-nhan-su.md.
 */
@Transactional
class PayrollServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PayrollPeriodRepository payrollPeriodRepository;

    @Autowired
    private PayrollEntryRepository payrollEntryRepository;

    @Test
    void getMyPayroll_UC12_MainFlow_returnsOwnEntryForRequestedPeriod() {
        User user = newUser("payroll.staff");
        Employee employee = newEmployee(user);
        PayrollPeriod period = newPeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        newEntry(period, employee, new BigDecimal("15000000"));

        PayrollEntryResponse response = payrollService.getMyPayroll(user.getId(), period.getPeriodCode());

        assertThat(response.employeeId()).isEqualTo(employee.getId());
        assertThat(response.netSalary()).isEqualByComparingTo("14000000");
        assertThat(response.fallbackToLatestAvailable()).isFalse();
    }

    @Test
    void getMyPayroll_UC12_A1_fallsBackToLatestAvailableWhenRequestedPeriodMissing() {
        User user = newUser("payroll.fallback");
        Employee employee = newEmployee(user);
        PayrollPeriod olderPeriod = newPeriod(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        newEntry(olderPeriod, employee, new BigDecimal("15000000"));
        // Kỳ 2026-06 chưa có dữ liệu cho nhân sự này.
        newPeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        PayrollEntryResponse response = payrollService.getMyPayroll(user.getId(), "2026-06");

        assertThat(response.periodCode()).isEqualTo(olderPeriod.getPeriodCode());
        assertThat(response.fallbackToLatestAvailable()).isTrue();
    }

    @Test
    void getMyPayroll_rejectsWhenNoPayrollDataExistsAtAll() {
        User user = newUser("payroll.nodata");
        newEmployee(user);

        assertThatThrownBy(() -> payrollService.getMyPayroll(user.getId(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listPayroll_UC12_MainFlow_hrManagerSeesAllEntriesInPeriod() {
        User hrManager = newUser("payroll.hr");
        assignRole(hrManager, "HR_MANAGER");
        User staffA = newUser("payroll.staffA");
        Employee employeeA = newEmployee(staffA);
        User staffB = newUser("payroll.staffB");
        Employee employeeB = newEmployee(staffB);
        PayrollPeriod period = newPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        newEntry(period, employeeA, new BigDecimal("15000000"));
        newEntry(period, employeeB, new BigDecimal("16000000"));

        List<PayrollEntryResponse> entries = payrollService.listPayroll(hrManager.getId(), period.getId(), null, null);

        assertThat(entries).hasSize(2)
                .extracting(PayrollEntryResponse::employeeId)
                .containsExactlyInAnyOrder(employeeA.getId(), employeeB.getId());
    }

    @Test
    void listPayroll_rejectsWhenActorIsNotHrManager() {
        User plainStaff = newUser("payroll.notallowed");
        PayrollPeriod period = newPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThatThrownBy(() -> payrollService.listPayroll(plainStaff.getId(), period.getId(), null, null))
                .isInstanceOf(NotHrManagerException.class);
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private PayrollPeriod newPeriod(LocalDate start, LocalDate end) {
        PayrollPeriod period = new PayrollPeriod();
        period.setPeriodCode(start.getYear() + "-" + String.format("%02d", start.getMonthValue()) + "-" + SEQ.incrementAndGet());
        period.setStartDate(start);
        period.setEndDate(end);
        return payrollPeriodRepository.save(period);
    }

    private PayrollEntry newEntry(PayrollPeriod period, Employee employee, BigDecimal baseSalary) {
        PayrollEntry entry = new PayrollEntry();
        entry.setPayrollPeriod(period);
        entry.setEmployee(employee);
        entry.setBaseSalary(baseSalary);
        entry.setWorkDays(new BigDecimal("22"));
        entry.setTax(new BigDecimal("500000"));
        entry.setSocialInsurance(new BigDecimal("500000"));
        entry.setGrossSalary(baseSalary);
        entry.setTotalDeductions(new BigDecimal("1000000"));
        entry.setNetSalary(baseSalary.subtract(new BigDecimal("1000000")));
        return payrollEntryRepository.save(entry);
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
        employee.setEmployeeCode("NVPR" + SEQ.incrementAndGet());
        employee.setDateOfBirth(LocalDate.of(1995, 1, 1));
        employee.setEmployeeType(Employee.EmployeeType.STAFF);
        employee.setDefaultShiftRequired(true);
        employee.setHireDate(LocalDate.of(2024, 1, 1));
        return employeeRepository.save(employee);
    }
}
