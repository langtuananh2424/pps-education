package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AssignEmployeeShiftRequest;
import vn.com.pps.education.dto.EmployeeShiftResponse;
import vn.com.pps.education.dto.EndEmployeeShiftRequest;
import vn.com.pps.education.exception.EmployeeShiftAlreadyEndedException;
import vn.com.pps.education.exception.ShiftAssignmentOverlapException;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.ShiftRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-70: Gán/gỡ ca làm việc cho nhân sự (employee_shifts) — bổ sung ngoài
 * SDD gốc, xác nhận với người dùng 2026-08-13. V124 (2026-08-14, thay thế
 * hướng thiết kế ban đầu "1 ca có ngày xen kẽ"): 1 nhân sự có thể có NHIỀU
 * ca active song song, miễn không chồng chéo lịch. Xem
 * docs/uc/phan-he-04-nhan-su.md > UC-70 > "T7 xen kẽ...".
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
        return newShift("1,2,3,4,5,6", Shift.WeekParity.ALL);
    }

    private Shift newShift(String appliesToWeekdays, Shift.WeekParity weekParity) {
        Shift shift = new Shift();
        shift.setCode("ESHIFT-SHIFT-" + SEQ.incrementAndGet());
        shift.setName("Test shift");
        shift.setCheckInTime(LocalTime.of(8, 0));
        shift.setCheckOutTime(LocalTime.of(17, 0));
        shift.setAppliesToWeekdays(appliesToWeekdays);
        shift.setWeekParity(weekParity);
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

    /**
     * "T7 xen kẽ" (V124, 2026-08-14): 2 ca không chồng chéo (parity khác nhau,
     * không phải ALL) được phép cùng active song song cho 1 nhân sự.
     */
    @Test
    void assignShift_UC70_MultipleActive_allowsNonOverlappingShifts() {
        Employee employee = newEmployee();
        Shift evenShift = newShift("1,2,3,4,5,6", Shift.WeekParity.EVEN);
        Shift oddShift = newShift("1,2,3,4,5", Shift.WeekParity.ODD);

        employeeShiftService.assignShift(new AssignEmployeeShiftRequest(employee.getId(), evenShift.getId(), LocalDate.now()));
        employeeShiftService.assignShift(new AssignEmployeeShiftRequest(employee.getId(), oddShift.getId(), LocalDate.now()));

        var active = employeeShiftService.listByEmployee(employee.getId());
        assertThat(active).hasSize(2);
        assertThat(active).allSatisfy(es -> assertThat(es.effectiveTo()).isNull());
    }

    @Test
    void assignShift_UC70_A1_rejectsOverlappingActiveShift() {
        Employee employee = newEmployee();
        Shift firstShift = newShift("1,2,3,4,5,6", Shift.WeekParity.ALL);
        Shift secondShift = newShift("1,2,3,4,5,6", Shift.WeekParity.ALL);

        employeeShiftService.assignShift(new AssignEmployeeShiftRequest(employee.getId(), firstShift.getId(), LocalDate.now()));

        assertThatThrownBy(() -> employeeShiftService.assignShift(
                new AssignEmployeeShiftRequest(employee.getId(), secondShift.getId(), LocalDate.now())))
                .isInstanceOf(ShiftAssignmentOverlapException.class);
    }

    @Test
    void endShift_UC70_MainFlow_endsActiveAssignment() {
        Employee employee = newEmployee();
        Shift shift = newShift();
        EmployeeShiftResponse assigned = employeeShiftService.assignShift(
                new AssignEmployeeShiftRequest(employee.getId(), shift.getId(), LocalDate.now().minusDays(10)));

        EmployeeShiftResponse ended = employeeShiftService.endShift(assigned.id(), new EndEmployeeShiftRequest(LocalDate.now()));

        assertThat(ended.effectiveTo()).isEqualTo(LocalDate.now());
    }

    @Test
    void endShift_UC70_A1_rejectsWhenAlreadyEnded() {
        Employee employee = newEmployee();
        Shift shift = newShift();
        EmployeeShiftResponse assigned = employeeShiftService.assignShift(
                new AssignEmployeeShiftRequest(employee.getId(), shift.getId(), LocalDate.now().minusDays(10)));
        employeeShiftService.endShift(assigned.id(), new EndEmployeeShiftRequest(LocalDate.now()));

        assertThatThrownBy(() -> employeeShiftService.endShift(assigned.id(), new EndEmployeeShiftRequest(LocalDate.now())))
                .isInstanceOf(EmployeeShiftAlreadyEndedException.class);
    }

    @Test
    void endShift_UC70_A2_rejectsEffectiveToBeforeEffectiveFrom() {
        Employee employee = newEmployee();
        Shift shift = newShift();
        EmployeeShiftResponse assigned = employeeShiftService.assignShift(
                new AssignEmployeeShiftRequest(employee.getId(), shift.getId(), LocalDate.now()));

        assertThatThrownBy(() -> employeeShiftService.endShift(assigned.id(), new EndEmployeeShiftRequest(LocalDate.now().minusDays(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Sau khi kết thúc ca cũ, gán ca mới chồng chéo vẫn được phép (ca cũ không còn active). */
    @Test
    void assignShift_UC70_MainFlow_allowsReassignAfterEndingOverlappingShift() {
        Employee employee = newEmployee();
        Shift firstShift = newShift("1,2,3,4,5,6", Shift.WeekParity.ALL);
        Shift secondShift = newShift("1,2,3,4,5,6", Shift.WeekParity.ALL);

        EmployeeShiftResponse first = employeeShiftService.assignShift(
                new AssignEmployeeShiftRequest(employee.getId(), firstShift.getId(), LocalDate.now().minusDays(10)));
        employeeShiftService.endShift(first.id(), new EndEmployeeShiftRequest(LocalDate.now().minusDays(1)));

        EmployeeShiftResponse second = employeeShiftService.assignShift(
                new AssignEmployeeShiftRequest(employee.getId(), secondShift.getId(), LocalDate.now()));

        assertThat(second.effectiveTo()).isNull();
    }
}
