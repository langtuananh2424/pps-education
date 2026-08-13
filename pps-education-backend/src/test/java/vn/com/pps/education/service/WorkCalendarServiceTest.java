package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateWorkCalendarRequest;
import vn.com.pps.education.dto.WorkCalendarResponse;
import vn.com.pps.education.exception.WorkCalendarOverrideAlreadyExistsException;
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
 * UC-70: Lịch làm việc/nghỉ lễ (work_calendar) — bổ sung ngoài SDD gốc,
 * xác nhận với người dùng 2026-08-13. Bao gồm test unique index V120 (vá
 * thiếu sót thiết kế gốc, chặn override trùng ngày/scope).
 */
@Transactional
class WorkCalendarServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private WorkCalendarService workCalendarService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    private Long newActor() {
        User user = new User();
        user.setUsername("wc.actor." + SEQ.incrementAndGet());
        user.setEmail("wc.actor." + SEQ.incrementAndGet() + "@pps.edu.vn");
        user.setFullName("WorkCalendar Actor");
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user).getId();
    }

    private Employee newEmployee() {
        User user = new User();
        user.setUsername("wc.emp." + SEQ.incrementAndGet());
        user.setEmail("wc.emp." + SEQ.incrementAndGet() + "@pps.edu.vn");
        user.setFullName("WorkCalendar Employee");
        user.setStatus(User.Status.ACTIVE);
        user = userRepository.save(user);

        Employee employee = new Employee();
        employee.setUser(user);
        employee.setEmployeeCode("WCEMP" + SEQ.incrementAndGet());
        employee.setDateOfBirth(LocalDate.of(1995, 1, 1));
        employee.setEmployeeType(Employee.EmployeeType.STAFF);
        employee.setHireDate(LocalDate.of(2024, 1, 1));
        return employeeRepository.save(employee);
    }

    private Shift newShift() {
        Shift shift = new Shift();
        shift.setCode("WC-SHIFT-" + SEQ.incrementAndGet());
        shift.setName("Test shift");
        shift.setCheckInTime(LocalTime.of(8, 0));
        shift.setCheckOutTime(LocalTime.of(17, 0));
        return shiftRepository.save(shift);
    }

    @Test
    void createOverride_UC70_MainFlow_createsAllScopeOverride() {
        LocalDate date = LocalDate.now().plusDays(SEQ.incrementAndGet());
        WorkCalendarResponse response = workCalendarService.createOverride(
                new CreateWorkCalendarRequest(date, "HOLIDAY", "ALL", null, null, "Nghỉ lễ test"), newActor());

        assertThat(response.id()).isNotNull();
        assertThat(response.dayType()).isEqualTo("HOLIDAY");
        assertThat(response.appliesToScope()).isEqualTo("ALL");
    }

    @Test
    void createOverride_UC70_MainFlow_createsShiftScopeOverride() {
        Shift shift = newShift();
        LocalDate date = LocalDate.now().plusDays(SEQ.incrementAndGet());

        WorkCalendarResponse response = workCalendarService.createOverride(
                new CreateWorkCalendarRequest(date, "OFF", "SHIFT", shift.getId(), null, null), newActor());

        assertThat(response.shiftId()).isEqualTo(shift.getId());
    }

    @Test
    void createOverride_UC70_MainFlow_createsEmployeeScopeOverride() {
        Employee employee = newEmployee();
        LocalDate date = LocalDate.now().plusDays(SEQ.incrementAndGet());

        WorkCalendarResponse response = workCalendarService.createOverride(
                new CreateWorkCalendarRequest(date, "COMPENSATORY", "EMPLOYEE", null, employee.getId(), null), newActor());

        assertThat(response.employeeId()).isEqualTo(employee.getId());
    }

    @Test
    void createOverride_UC70_A1_rejectsShiftScopeWithoutShiftId() {
        LocalDate date = LocalDate.now().plusDays(SEQ.incrementAndGet());
        assertThatThrownBy(() -> workCalendarService.createOverride(
                new CreateWorkCalendarRequest(date, "OFF", "SHIFT", null, null, null), newActor()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createOverride_UC70_A1_rejectsEmployeeScopeWithoutEmployeeId() {
        LocalDate date = LocalDate.now().plusDays(SEQ.incrementAndGet());
        assertThatThrownBy(() -> workCalendarService.createOverride(
                new CreateWorkCalendarRequest(date, "OFF", "EMPLOYEE", null, null, null), newActor()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createOverride_UC70_A2_rejectsDuplicateAllScopeOverrideSameDate() {
        LocalDate date = LocalDate.now().plusDays(SEQ.incrementAndGet());
        Long actor = newActor();
        workCalendarService.createOverride(new CreateWorkCalendarRequest(date, "HOLIDAY", "ALL", null, null, null), actor);

        assertThatThrownBy(() -> workCalendarService.createOverride(
                new CreateWorkCalendarRequest(date, "OFF", "ALL", null, null, null), actor))
                .isInstanceOf(WorkCalendarOverrideAlreadyExistsException.class);
    }

    @Test
    void deleteOverride_UC70_removesRecord() {
        LocalDate date = LocalDate.now().plusDays(SEQ.incrementAndGet());
        WorkCalendarResponse created = workCalendarService.createOverride(
                new CreateWorkCalendarRequest(date, "HOLIDAY", "ALL", null, null, null), newActor());

        workCalendarService.deleteOverride(created.id());

        assertThat(workCalendarService.listByDateRange(date, date)).isEmpty();
    }
}
