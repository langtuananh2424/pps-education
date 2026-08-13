package vn.com.pps.education.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.WorkCalendar;
import vn.com.pps.education.dto.CreateWorkCalendarRequest;
import vn.com.pps.education.dto.WorkCalendarResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.WorkCalendarOverrideAlreadyExistsException;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.ShiftRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.WorkCalendarRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-70: Lịch làm việc/nghỉ lễ — override theo ngày (work_calendar) — bổ
 * sung ngoài SDD gốc, xác nhận với người dùng 2026-08-13. Xem ShiftService
 * (Javadoc đầy đủ bối cảnh) và docs/uc/phan-he-04-nhan-su.md.
 */
@Service
public class WorkCalendarService {

    private final WorkCalendarRepository workCalendarRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftRepository shiftRepository;
    private final UserRepository userRepository;

    public WorkCalendarService(WorkCalendarRepository workCalendarRepository, EmployeeRepository employeeRepository,
                                ShiftRepository shiftRepository, UserRepository userRepository) {
        this.workCalendarRepository = workCalendarRepository;
        this.employeeRepository = employeeRepository;
        this.shiftRepository = shiftRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<WorkCalendarResponse> listByDateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Khoảng ngày không hợp lệ: from phải trước hoặc bằng to.");
        }
        return workCalendarRepository.findByCalendarDateBetweenOrderByCalendarDateAsc(from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * scope=SHIFT bắt buộc shiftId, scope=EMPLOYEE bắt buộc employeeId, scope=ALL
     * không nhận cả 2 (WorkCalendar.java: "chỉ set khi scope tương ứng" -- ràng
     * buộc này trước đây chỉ là comment, không có gì enforce, tự validate ở đây).
     * Bắt trùng override cùng calendar_date/scope qua unique index V120 (vá
     * thiếu sót thiết kế gốc) thay vì query-then-insert để tránh race condition.
     */
    @Transactional
    public WorkCalendarResponse createOverride(CreateWorkCalendarRequest request, Long actorUserId) {
        WorkCalendar.DayType dayType = parseDayType(request.dayType());
        WorkCalendar.Scope scope = parseScope(request.appliesToScope());

        Shift shift = null;
        Employee employee = null;
        switch (scope) {
            case SHIFT -> {
                if (request.shiftId() == null) {
                    throw new IllegalArgumentException("appliesToScope=SHIFT bắt buộc phải có shiftId.");
                }
                shift = shiftRepository.findById(request.shiftId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc id=" + request.shiftId()));
            }
            case EMPLOYEE -> {
                if (request.employeeId() == null) {
                    throw new IllegalArgumentException("appliesToScope=EMPLOYEE bắt buộc phải có employeeId.");
                }
                employee = employeeRepository.findById(request.employeeId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự id=" + request.employeeId()));
            }
            case ALL -> {
                if (request.shiftId() != null || request.employeeId() != null) {
                    throw new IllegalArgumentException("appliesToScope=ALL không được kèm shiftId/employeeId.");
                }
            }
        }

        WorkCalendar override = new WorkCalendar();
        override.setCalendarDate(request.calendarDate());
        override.setDayType(dayType);
        override.setAppliesToScope(scope);
        override.setShift(shift);
        override.setEmployee(employee);
        override.setDescription(request.description());
        override.setCreatedBy(getUserOrThrow(actorUserId));

        try {
            override = workCalendarRepository.saveAndFlush(override);
        } catch (DataIntegrityViolationException ex) {
            throw new WorkCalendarOverrideAlreadyExistsException(
                    "Đã có override lịch làm việc cho ngày " + request.calendarDate() + " (phạm vi " + scope + ").");
        }
        return toResponse(override);
    }

    @Transactional
    public void deleteOverride(Long id) {
        if (!workCalendarRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy override lịch làm việc id=" + id);
        }
        workCalendarRepository.deleteById(id);
    }

    private WorkCalendar.DayType parseDayType(String raw) {
        try {
            return WorkCalendar.DayType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("dayType không hợp lệ (WORKING/OFF/HOLIDAY/COMPENSATORY): " + raw);
        }
    }

    private WorkCalendar.Scope parseScope(String raw) {
        try {
            return WorkCalendar.Scope.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("appliesToScope không hợp lệ (ALL/SHIFT/EMPLOYEE): " + raw);
        }
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + userId));
    }

    private WorkCalendarResponse toResponse(WorkCalendar wc) {
        return new WorkCalendarResponse(
                wc.getId(), wc.getCalendarDate(), wc.getDayType().name(), wc.getAppliesToScope().name(),
                wc.getShift() == null ? null : wc.getShift().getId(),
                wc.getShift() == null ? null : wc.getShift().getName(),
                wc.getEmployee() == null ? null : wc.getEmployee().getId(),
                wc.getEmployee() == null ? null : wc.getEmployee().getUser().getFullName(),
                wc.getDescription());
    }
}
