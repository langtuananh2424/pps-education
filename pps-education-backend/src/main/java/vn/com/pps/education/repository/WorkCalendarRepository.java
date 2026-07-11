package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.WorkCalendar;

import java.time.LocalDate;
import java.util.Optional;

public interface WorkCalendarRepository extends JpaRepository<WorkCalendar, Long> {

    // UC-09 Main Flow bước 3: ưu tiên override cụ thể nhất -- EMPLOYEE > SHIFT > ALL.
    Optional<WorkCalendar> findByCalendarDateAndAppliesToScopeAndEmployeeId(
            LocalDate calendarDate, WorkCalendar.Scope scope, Long employeeId);

    Optional<WorkCalendar> findByCalendarDateAndAppliesToScopeAndShiftId(
            LocalDate calendarDate, WorkCalendar.Scope scope, Long shiftId);

    Optional<WorkCalendar> findByCalendarDateAndAppliesToScope(LocalDate calendarDate, WorkCalendar.Scope scope);
}
