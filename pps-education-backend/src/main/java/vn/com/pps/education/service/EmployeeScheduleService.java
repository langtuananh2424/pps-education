package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.EmployeeResponse;
import vn.com.pps.education.dto.EmployeeScheduleOverviewResponse;
import vn.com.pps.education.dto.EmployeeShiftResponse;
import vn.com.pps.education.dto.ShiftResponse;
import vn.com.pps.education.dto.WorkCalendarResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bổ sung ngoài SDD gốc, xác nhận 2026-08-17: trang "Lịch làm việc" (roster
 * toàn công ty, gộp ca làm cố định + lịch dạy + lịch nghỉ lễ) cho HR/Điều
 * hành, gate bằng quyền hrm.employee-schedule.view (xem
 * EmployeeScheduleController). Thuần orchestration -- gọi các Service anh
 * em, không tự query Repository trực tiếp. Xem
 * docs/uc/phan-he-04-nhan-su.md (UC-70).
 */
@Service
public class EmployeeScheduleService {

    private static final String TEACHER_TYPE = "TEACHER";

    private final EmployeeService employeeService;
    private final ClassSessionService classSessionService;
    private final EmployeeShiftService employeeShiftService;
    private final ShiftService shiftService;
    private final WorkCalendarService workCalendarService;

    public EmployeeScheduleService(EmployeeService employeeService, ClassSessionService classSessionService,
                                    EmployeeShiftService employeeShiftService, ShiftService shiftService,
                                    WorkCalendarService workCalendarService) {
        this.employeeService = employeeService;
        this.classSessionService = classSessionService;
        this.employeeShiftService = employeeShiftService;
        this.shiftService = shiftService;
        this.workCalendarService = workCalendarService;
    }

    @Transactional(readOnly = true)
    public EmployeeScheduleOverviewResponse getOverview(LocalDate from, LocalDate to, Long departmentId,
                                                          Long siteId, Long classId, Long employeeId) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Khoảng ngày không hợp lệ: from phải trước hoặc bằng to.");
        }

        List<EmployeeResponse> baseEmployees = employeeService.search(null, departmentId).stream()
                .filter(e -> employeeId == null || e.id().equals(employeeId))
                .toList();

        List<Long> teacherUserIds = baseEmployees.stream()
                .filter(e -> TEACHER_TYPE.equals(e.employeeType()))
                .map(EmployeeResponse::userId)
                .toList();

        List<ClassSessionResponse> sessions = classSessionService.listForScheduleOverview(teacherUserIds, siteId, classId, from, to);

        List<EmployeeResponse> finalEmployees = baseEmployees;
        if (siteId != null || classId != null) {
            Set<Long> sessionTeacherUserIds = sessions.stream()
                    .map(ClassSessionResponse::primaryTeacherId)
                    .collect(Collectors.toSet());
            finalEmployees = baseEmployees.stream()
                    .filter(e -> TEACHER_TYPE.equals(e.employeeType()) && sessionTeacherUserIds.contains(e.userId()))
                    .toList();
        }

        List<Long> employeeIds = finalEmployees.stream().map(EmployeeResponse::id).toList();
        List<EmployeeShiftResponse> employeeShifts = employeeShiftService.listForScheduleOverview(employeeIds, from, to);
        List<ShiftResponse> shiftDefinitions = shiftService.listShifts();
        List<WorkCalendarResponse> workCalendarOverrides = workCalendarService.listByDateRange(from, to);

        return new EmployeeScheduleOverviewResponse(finalEmployees, shiftDefinitions, employeeShifts, sessions, workCalendarOverrides);
    }
}
