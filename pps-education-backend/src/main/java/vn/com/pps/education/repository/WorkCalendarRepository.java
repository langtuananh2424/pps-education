package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.WorkCalendar;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkCalendarRepository extends JpaRepository<WorkCalendar, Long> {

    // UC-70 (bổ sung ngoài SDD gốc) -- danh sách override cho trang admin theo khoảng ngày.
    List<WorkCalendar> findByCalendarDateBetweenOrderByCalendarDateAsc(LocalDate from, LocalDate to);

    // UC-09 Main Flow bước 3: ưu tiên override cụ thể nhất -- EMPLOYEE > SHIFT > ALL.
    Optional<WorkCalendar> findByCalendarDateAndAppliesToScopeAndEmployeeId(
            LocalDate calendarDate, WorkCalendar.Scope scope, Long employeeId);

    Optional<WorkCalendar> findByCalendarDateAndAppliesToScopeAndShiftId(
            LocalDate calendarDate, WorkCalendar.Scope scope, Long shiftId);

    Optional<WorkCalendar> findByCalendarDateAndAppliesToScope(LocalDate calendarDate, WorkCalendar.Scope scope);
}
