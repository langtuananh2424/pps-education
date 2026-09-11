package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AttendanceRecord;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.EmployeeShift;
import vn.com.pps.education.domain.LeaveRequest;
import vn.com.pps.education.repository.AttendanceRecordRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.EmployeeShiftRepository;
import vn.com.pps.education.repository.LeaveRequestRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-09: Chấm công (FR-HRM-02) — bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-09-11: khi 1 nhân sự QUÊN không chấm công (không hề bấm,
 * không bị từ chối, không gì cả), hệ thống trước đây KHÔNG lưu bất kỳ bản
 * ghi nào — HR không có cách nào biết ai quên. Scheduler này quét định kỳ,
 * với nhân sự đã qua cửa sổ chấm công VÀO hôm nay mà vẫn chưa có
 * {@link AttendanceRecord} nào, tạo 1 bản ghi status=MISSING (enum đã có
 * sẵn từ trước nhưng chưa từng được set ở đâu).
 *
 * Vì cửa sổ chấm công (xem {@link AttendanceWindowResolver}) không bao giờ
 * mở lại trong ngày sau khi đã đóng (tối đa 1 ca/lịch dạy áp dụng mỗi
 * ngày/nhân sự), quét định kỳ ngay sau khi cửa sổ đóng là an toàn — không
 * có nguy cơ báo sai kiểu "MISSING rồi nhân sự vẫn chấm công được".
 *
 * Loại trừ nhân sự đang nghỉ phép ĐÃ DUYỆT nguyên ngày (xem
 * {@link LeaveRequestRepository#findEmployeeIdsOnApprovedFullDayLeave}) —
 * AttendanceService hiện không liên kết với leave_requests, nếu không loại
 * trừ sẽ báo MISSING sai cho nhân sự nghỉ phép hợp lệ.
 *
 * Loại trừ GV part-time (trả lương theo giờ, xem
 * {@link AttendanceWindowResolver#filterHourlyPaidEmployeeIds}) — mirror
 * is_management, GV nhóm này không liên quan gì tới UC-09, chỉ dùng UC-71
 * Nhận lớp.
 *
 * PHẠM VI CỐ Ý KHÔNG LÀM ở bản này (giữ đúng yêu cầu, tránh phình phạm vi):
 * không gửi thông báo khi phát hiện MISSING, không backfill ngày quá khứ
 * trước khi scheduler này lên, không xử lý riêng ca đêm vắt qua nửa đêm.
 *
 * Cron 5 phút — mirror ExerciseAttemptTimeoutSchedulerService/
 * HomeworkDeadlineSchedulerService (đơn giản/bền hơn quét chính xác theo
 * lịch, không mất lịch khi app restart giữa chừng).
 */
@Service
public class AttendanceMissingSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceMissingSchedulerService.class);

    /** Nghỉ phép nguyên ngày — loại trừ khỏi quét MISSING. LATE/EARLY_LEAVE (nghỉ 1 phần buổi) KHÔNG nằm trong danh sách này, xem Javadoc LeaveRequestRepository. */
    private static final List<LeaveRequest.LeaveType> FULL_DAY_LEAVE_TYPES =
            List.of(LeaveRequest.LeaveType.ANNUAL, LeaveRequest.LeaveType.SICK,
                    LeaveRequest.LeaveType.UNPAID, LeaveRequest.LeaveType.PERSONAL);

    private static final List<ClassSession.Status> TEACHING_WINDOW_EXCLUDED_STATUSES =
            List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED);

    private final EmployeeRepository employeeRepository;
    private final EmployeeShiftRepository employeeShiftRepository;
    private final ClassSessionRepository classSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceWindowResolver windowResolver;

    public AttendanceMissingSchedulerService(EmployeeRepository employeeRepository,
                                              EmployeeShiftRepository employeeShiftRepository,
                                              ClassSessionRepository classSessionRepository,
                                              AttendanceRecordRepository attendanceRecordRepository,
                                              LeaveRequestRepository leaveRequestRepository,
                                              AttendanceWindowResolver windowResolver) {
        this.employeeRepository = employeeRepository;
        this.employeeShiftRepository = employeeShiftRepository;
        this.classSessionRepository = classSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.windowResolver = windowResolver;
    }

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void runMissingScan() {
        LocalDate today = LocalDate.now();
        OffsetDateTime now = OffsetDateTime.now();

        List<Employee> allNonManagement = employeeRepository.findAllActive(null).stream()
                .filter(e -> !e.isManagement() && e.getStatus() == Employee.Status.ACTIVE)
                .toList();
        if (allNonManagement.isEmpty()) {
            return;
        }
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-11 -- GV part-time (trả lương theo
        // giờ) miễn trừ HOÀN TOÀN khỏi UC-09, loại ngay từ đầu giống is_management, không chỉ ẩn tiết
        // dạy của họ (mirror PartTimeTeacherExemptFromAttendanceException ở AttendanceService#process).
        Set<Long> hourlyPaidTeacherIds = windowResolver.filterHourlyPaidEmployeeIds(allNonManagement.stream()
                .filter(e -> e.getEmployeeType() == Employee.EmployeeType.TEACHER)
                .map(Employee::getId)
                .toList());
        List<Employee> candidates = allNonManagement.stream()
                .filter(e -> !hourlyPaidTeacherIds.contains(e.getId()))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        List<Long> candidateIds = candidates.stream().map(Employee::getId).toList();

        Set<Long> alreadyRecorded = attendanceRecordRepository.findByWorkDateAndEmployeeIdIn(today, candidateIds).stream()
                .map(r -> r.getEmployee().getId())
                .collect(Collectors.toSet());
        Set<Long> onApprovedLeave = leaveRequestRepository.findEmployeeIdsOnApprovedFullDayLeave(candidateIds, today, FULL_DAY_LEAVE_TYPES);

        List<Employee> toEvaluate = candidates.stream()
                .filter(e -> !alreadyRecorded.contains(e.getId()) && !onApprovedLeave.contains(e.getId()))
                .toList();
        if (toEvaluate.isEmpty()) {
            return;
        }

        Map<Long, List<EmployeeShift>> shiftsByEmployeeId = employeeShiftRepository
                .findByEmployeeIdInAndEffectiveToIsNull(toEvaluate.stream().map(Employee::getId).toList()).stream()
                .collect(Collectors.groupingBy(es -> es.getEmployee().getId()));

        List<Long> teacherUserIds = toEvaluate.stream()
                .filter(e -> e.getEmployeeType() == Employee.EmployeeType.TEACHER)
                .map(e -> e.getUser().getId())
                .toList();
        Map<Long, List<ClassSession>> sessionsByTeacherUserId = teacherUserIds.isEmpty()
                ? Map.of()
                : classSessionRepository.findByPrimaryTeacherIdInAndSessionDateAndStatusNotIn(teacherUserIds, today, TEACHING_WINDOW_EXCLUDED_STATUSES)
                        .stream().collect(Collectors.groupingBy(cs -> cs.getPrimaryTeacher().getId()));

        int created = 0;
        for (Employee employee : toEvaluate) {
            List<EmployeeShift> activeShifts = shiftsByEmployeeId.getOrDefault(employee.getId(), List.of());
            List<ClassSession> todaySessions = employee.getEmployeeType() == Employee.EmployeeType.TEACHER
                    ? sessionsByTeacherUserId.getOrDefault(employee.getUser().getId(), List.of())
                    : List.of();

            if (!windowResolver.isWorkingDay(today, employee.getId(), activeShifts, !todaySessions.isEmpty())) {
                continue;
            }

            LocalTime windowEnd = windowResolver.teachingScheduleCheckInWindowEnd(todaySessions);
            if (windowEnd == null) {
                EmployeeShift matchedShift = windowResolver.resolveApplicableShift(activeShifts, today);
                if (matchedShift != null && employee.isDefaultShiftRequired()) {
                    windowEnd = windowResolver.shiftCheckInWindowEnd(matchedShift.getShift());
                }
            }
            if (windowEnd == null || !now.toLocalTime().isAfter(windowEnd)) {
                continue;
            }

            AttendanceRecord record = new AttendanceRecord();
            record.setEmployee(employee);
            record.setWorkDate(today);
            record.setStatus(AttendanceRecord.Status.MISSING);
            attendanceRecordRepository.save(record);
            created++;
        }

        if (created > 0) {
            log.info("AttendanceMissingSchedulerService: đánh dấu MISSING cho {} nhân sự quên chấm công ngày {}.", created, today);
        }
    }
}
