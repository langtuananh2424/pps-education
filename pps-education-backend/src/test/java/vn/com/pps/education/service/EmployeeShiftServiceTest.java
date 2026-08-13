package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AssignShiftRequest;
import vn.com.pps.education.dto.BulkAssignShiftRequest;
import vn.com.pps.education.dto.BulkAssignShiftResponse;
import vn.com.pps.education.dto.EmployeeShiftResponse;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.EmployeeShiftRepository;
import vn.com.pps.education.repository.ShiftRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Gán ca cho nhân sự (đơn lẻ/hàng loạt) — bổ sung 2026-08-13, dưới
 * UC-09/FR-HRM-02. Xem docs/uc/phan-he-04-nhan-su.md (khối bổ sung sau UC-09).
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

    @Autowired
    private EmployeeShiftRepository employeeShiftRepository;

    private User actor;
    private Employee employee;
    private Shift shiftA;
    private Shift shiftB;

    @BeforeEach
    void setUp() {
        actor = newUser();
        employee = newEmployee(newUser());
        shiftA = newShift(LocalTime.of(8, 0), LocalTime.of(17, 0));
        shiftB = newShift(LocalTime.of(13, 0), LocalTime.of(21, 0));
    }

    @Test
    void assign_UC09Extension_MainFlow_createsFirstActiveAssignment() {
        EmployeeShiftResponse response = employeeShiftService.assign(
                new AssignShiftRequest(employee.getId(), shiftA.getId(), LocalDate.now()), actor.getId());

        assertThat(response.shiftId()).isEqualTo(shiftA.getId());
        assertThat(response.effectiveTo()).isNull();
    }

    @Test
    void assign_UC09Extension_MainFlow_closesPreviousActiveAssignmentOnReassign() {
        LocalDate firstStart = LocalDate.now().minusMonths(1);
        employeeShiftService.assign(new AssignShiftRequest(employee.getId(), shiftA.getId(), firstStart), actor.getId());
        LocalDate secondStart = LocalDate.now();

        employeeShiftService.assign(new AssignShiftRequest(employee.getId(), shiftB.getId(), secondStart), actor.getId());

        List<EmployeeShiftResponse> history = employeeShiftService.getHistory(employee.getId());
        assertThat(history).hasSize(2);
        EmployeeShiftResponse previous = history.stream().filter(h -> h.shiftId().equals(shiftA.getId())).findFirst().orElseThrow();
        EmployeeShiftResponse current = history.stream().filter(h -> h.shiftId().equals(shiftB.getId())).findFirst().orElseThrow();
        assertThat(previous.effectiveTo()).isEqualTo(secondStart.minusDays(1));
        assertThat(current.effectiveTo()).isNull();
    }

    @Test
    void assign_UC09Extension_A1_rejectsEffectiveFromNotAfterCurrentAssignment() {
        LocalDate currentStart = LocalDate.now();
        employeeShiftService.assign(new AssignShiftRequest(employee.getId(), shiftA.getId(), currentStart), actor.getId());

        assertThatThrownBy(() -> employeeShiftService.assign(
                new AssignShiftRequest(employee.getId(), shiftB.getId(), currentStart), actor.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bulkAssign_UC09Extension_MainFlow_assignsShiftToEveryEmployee() {
        Employee employee2 = newEmployee(newUser());

        BulkAssignShiftResponse response = employeeShiftService.bulkAssign(
                new BulkAssignShiftRequest(List.of(employee.getId(), employee2.getId()), shiftA.getId(), LocalDate.now()),
                actor.getId());

        assertThat(response.successCount()).isEqualTo(2);
        assertThat(response.failures()).isEmpty();
    }

    @Test
    void bulkAssign_UC09Extension_A1_collectsFailureForInvalidEmployeeWithoutRollingBackOthers() {
        Long nonExistentEmployeeId = -1L;

        BulkAssignShiftResponse response = employeeShiftService.bulkAssign(
                new BulkAssignShiftRequest(List.of(employee.getId(), nonExistentEmployeeId), shiftA.getId(), LocalDate.now()),
                actor.getId());

        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.failures()).hasSize(1);
        assertThat(response.failures().get(0).employeeId()).isEqualTo(nonExistentEmployeeId);
        assertThat(employeeShiftService.getHistory(employee.getId())).isNotEmpty();
    }

    private Shift newShift(LocalTime checkIn, LocalTime checkOut) {
        Shift shift = new Shift();
        shift.setCode("SHIFT-EST-" + SEQ.incrementAndGet());
        shift.setName("Test shift");
        shift.setCheckInTime(checkIn);
        shift.setCheckOutTime(checkOut);
        shift.setAppliesToWeekdays("1,2,3,4,5,6");
        shift.setWeekParity(Shift.WeekParity.ALL);
        shift.setActive(true);
        return shiftRepository.save(shift);
    }

    private User newUser() {
        User user = new User();
        user.setUsername("empshift.user." + SEQ.incrementAndGet());
        user.setEmail("empshift.user." + SEQ.incrementAndGet() + "@pps.edu.vn");
        user.setFullName("Employee Shift Test User");
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }

    private Employee newEmployee(User forUser) {
        Employee employee = new Employee();
        employee.setUser(forUser);
        employee.setEmployeeCode("NVES" + SEQ.incrementAndGet());
        employee.setDateOfBirth(LocalDate.of(1995, 1, 1));
        employee.setEmployeeType(Employee.EmployeeType.STAFF);
        employee.setManagement(false);
        employee.setDefaultShiftRequired(true);
        employee.setHireDate(LocalDate.of(2024, 1, 1));
        return employeeRepository.save(employee);
    }
}
