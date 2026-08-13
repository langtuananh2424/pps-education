package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AssignEmployeeShiftRequest;
import vn.com.pps.education.dto.EmployeeShiftResponse;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.ShiftRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-70: Gán/đổi ca làm việc cho nhân sự (employee_shifts) — bổ sung ngoài
 * SDD gốc, xác nhận với người dùng 2026-08-13.
 */
@Transactional
class EmployeeShiftServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private EmployeeShiftService employeeShiftService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    private Employee newEmployee() {
        User user = new User();
        user.setUsername("empshift.user." + SEQ.incrementAndGet());
        user.setEmail("empshift.user." + SEQ.incrementAndGet() + "@pps.edu.vn");
        user.setFullName("Employee Shift Test");
        user.setStatus(User.Status.ACTIVE);
        user = userRepository.save(user);

        Employee employee = new Employee();
        employee.setUser(user);
        employee.setEmployeeCode("ESHIFT" + SEQ.incrementAndGet());
        employee.setDateOfBirth(LocalDate.of(1995, 1, 1));
        employee.setEmployeeType(Employee.EmployeeType.STAFF);
        employee.setHireDate(LocalDate.of(2024, 1, 1));
        return employeeRepository.save(employee);
    }

    private Shift newShift() {
        Shift shift = new Shift();
        shift.setCode("ESHIFT-SHIFT-" + SEQ.incrementAndGet());
        shift.setName("Test shift");
        shift.setCheckInTime(LocalTime.of(8, 0));
        shift.setCheckOutTime(LocalTime.of(17, 0));
        return shiftRepository.save(shift);
    }

    @Test
    void assignShift_UC70_MainFlow_createsActiveAssignment() {
        Employee employee = newEmployee();
        Shift shift = newShift();

        EmployeeShiftResponse response = employeeShiftService.assignShift(
                new AssignEmployeeShiftRequest(employee.getId(), shift.getId(), LocalDate.now()));

        assertThat(response.id()).isNotNull();
        assertThat(response.effectiveTo()).isNull();
        assertThat(response.shiftId()).isEqualTo(shift.getId());
    }

    @Test
    void assignShift_UC70_reassign_closesPreviousActiveAssignment() {
        Employee employee = newEmployee();
        Shift firstShift = newShift();
        Shift secondShift = newShift();

        EmployeeShiftResponse first = employeeShiftService.assignShift(
                new AssignEmployeeShiftRequest(employee.getId(), firstShift.getId(), LocalDate.now().minusDays(10)));
        EmployeeShiftResponse second = employeeShiftService.assignShift(
                new AssignEmployeeShiftRequest(employee.getId(), secondShift.getId(), LocalDate.now()));

        var history = employeeShiftService.listByEmployee(employee.getId());
        assertThat(history).hasSize(2);
        var closedFirst = history.stream().filter(h -> h.id().equals(first.id())).findFirst().orElseThrow();
        assertThat(closedFirst.effectiveTo()).isEqualTo(LocalDate.now().minusDays(1));
        assertThat(second.effectiveTo()).isNull();
    }
}
