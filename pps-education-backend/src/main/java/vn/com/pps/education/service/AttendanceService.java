package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AttendanceRecord;
import vn.com.pps.education.domain.AttendanceRecordHistory;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.EmployeeShift;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.WorkCalendar;
import vn.com.pps.education.dto.AttendanceCheckRequest;
import vn.com.pps.education.dto.AttendanceRecordResponse;
import vn.com.pps.education.exception.AttendanceMethodNotAvailableException;
import vn.com.pps.education.exception.ManagementExemptFromAttendanceException;
import vn.com.pps.education.exception.NotAWorkingDayException;
import vn.com.pps.education.exception.OutsideAttendanceWindowException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AttendanceRecordHistoryRepository;
import vn.com.pps.education.repository.AttendanceRecordRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.EmployeeShiftRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.WorkCalendarRepository;
import vn.com.pps.education.service.attendance.AttendanceCheckContext;
import vn.com.pps.education.service.attendance.AttendanceMethodValidator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.WeekFields;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * UC-09: Chấm công (FR-HRM-02).
 * Xem docs/uc/phan-he-04-nhan-su.md — Main Flow, A1 (ngoài cửa sổ), A2 (GPS
 * ngoài bán kính), A3 (xác thực sinh trắc thất bại/chấm thủ công) và
 * docs/diagrams/activity/ActivityDiagram-ChamCong.mmd cho chi tiết nhánh rẽ
 * (A12/A13 — cửa sổ theo lịch dạy cho Giáo viên, A14/A15 — cửa sổ ca cố định).
 *
 * Cửa sổ theo lịch dạy KHÔNG có buffer trước/sau riêng (khác Shift) — SDD
 * (Nhóm 4B, mục d) chỉ định nghĩa "tiết dạy sớm/muộn nhất trong ngày", nên
 * cửa sổ đúng bằng [startTime tiết sớm nhất, endTime tiết muộn nhất]. Ưu
 * tiên xét cửa sổ lịch dạy trước cửa sổ ca cố định (đúng thứ tự Main Flow
 * bước 4 và A12→A14 trong activity diagram) khi cả 2 cùng khớp T.
 *
 * Main Flow bước 3 (D có phải ngày làm việc — A8/A9): GV có tiết dạy hôm nay
 * cũng được coi là ngày làm việc, nhưng CHỈ khi không có work_calendar
 * override tường minh nào (mọi scope ALL/SHIFT/EMPLOYEE) — tiết dạy KHÔNG
 * bao giờ ghi đè 1 quyết định HOLIDAY/OFF đã khai báo (VD buổi MAKEUP xếp
 * vào ngày Lễ vẫn bị chặn trừ khi HR bổ sung override COMPENSATORY cho ngày
 * đó). Xác nhận với PM 2026-07-15 — xem isWorkingDay(...).
 */
@Service
public class AttendanceService {

    private static final List<ClassSession.Status> TEACHING_WINDOW_EXCLUDED_STATUSES =
            List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED);

    private final EmployeeRepository employeeRepository;
    private final EmployeeShiftRepository employeeShiftRepository;
    private final WorkCalendarRepository workCalendarRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceRecordHistoryRepository attendanceRecordHistoryRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;
    private final ClassSessionRepository classSessionRepository;
    private final List<AttendanceMethodValidator> methodValidators;

    public AttendanceService(EmployeeRepository employeeRepository,
                              EmployeeShiftRepository employeeShiftRepository,
                              WorkCalendarRepository workCalendarRepository,
                              AttendanceRecordRepository attendanceRecordRepository,
                              AttendanceRecordHistoryRepository attendanceRecordHistoryRepository,
                              SiteRepository siteRepository,
                              UserRepository userRepository,
                              ClassSessionRepository classSessionRepository,
                              List<AttendanceMethodValidator> methodValidators) {
        this.employeeRepository = employeeRepository;
        this.employeeShiftRepository = employeeShiftRepository;
        this.workCalendarRepository = workCalendarRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceRecordHistoryRepository = attendanceRecordHistoryRepository;
        this.siteRepository = siteRepository;
        this.userRepository = userRepository;
        this.classSessionRepository = classSessionRepository;
        this.methodValidators = methodValidators;
    }

    @Transactional
    public AttendanceRecordResponse checkIn(Long actorUserId, AttendanceCheckRequest request) {
        return process(actorUserId, request, true);
    }

    @Transactional
    public AttendanceRecordResponse checkOut(Long actorUserId, AttendanceCheckRequest request) {
        return process(actorUserId, request, false);
    }

    private AttendanceRecordResponse process(Long actorUserId, AttendanceCheckRequest request, boolean isCheckIn) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));
        Employee employee = employeeRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản chưa có hồ sơ nhân sự."));

        // Main Flow bước 2 -- cấp quản lý miễn trừ hoàn toàn.
        if (actor.isManagement()) {
            throw new ManagementExemptFromAttendanceException("Cấp quản lý được miễn trừ chấm công.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        LocalDate workDate = now.toLocalDate();
        EmployeeShift activeShift = employeeShiftRepository.findByEmployeeIdAndEffectiveToIsNull(employee.getId())
                .orElse(null);
        List<ClassSession> todaySessions = employee.getEmployeeType() == Employee.EmployeeType.TEACHER
                ? classSessionRepository.findByPrimaryTeacherIdAndSessionDateAndStatusNotIn(
                        actorUserId, workDate, TEACHING_WINDOW_EXCLUDED_STATUSES)
                : List.of();

        // Main Flow bước 3 -- xác định ngày D có phải ngày làm việc. GV có tiết dạy hôm nay cũng được
        // coi là ngày làm việc, nhưng CHỈ khi không có work_calendar override tường minh nào (mọi scope)
        // -- không bao giờ ghi đè quyết định HOLIDAY/OFF đã khai báo (xác nhận với PM, xem UC-09 A2 mới).
        if (!isWorkingDay(workDate, employee.getId(), activeShift, !todaySessions.isEmpty())) {
            throw new NotAWorkingDayException("Ngày " + workDate + " không phải ngày làm việc.");
        }

        // Main Flow bước 4-5 -- xác định cửa sổ hợp lệ: A12/A13 cửa sổ theo lịch dạy
        // (GV có tiết dạy hôm nay) ưu tiên trước, A14/A15 cửa sổ ca cố định xét sau.
        WindowMatch windowMatch = resolveTeachingScheduleWindow(todaySessions, now, isCheckIn);
        if (windowMatch == null) {
            windowMatch = resolveShiftWindow(activeShift, employee.isDefaultShiftRequired(), now, isCheckIn);
        }
        if (windowMatch == null) {
            throw new OutsideAttendanceWindowException(
                    "Thời điểm " + now + " ngoài cửa sổ chấm công cho phép.");
        }

        // Main Flow bước 6-7 -- chọn + xác thực phương thức.
        AttendanceRecord.CheckMethod method = parseMethod(request.method());
        AttendanceMethodValidator validator = methodValidators.stream()
                .filter(v -> v.method() == method)
                .findFirst()
                .orElseThrow(() -> new AttendanceMethodNotAvailableException("Phương thức không hỗ trợ: " + request.method()));
        if (!validator.isEnabled()) {
            throw new AttendanceMethodNotAvailableException("Phương thức " + method + " hiện không khả dụng.");
        }
        validator.validate(new AttendanceCheckContext(
                employee, request.siteId(), request.latitude(), request.longitude(), request.biometricVerified()));

        // Main Flow bước 8 -- ghi nhận.
        AttendanceRecord record = attendanceRecordRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate)
                .orElseGet(() -> {
                    AttendanceRecord created = new AttendanceRecord();
                    created.setEmployee(employee);
                    created.setWorkDate(workDate);
                    return created;
                });
        boolean isNewRecord = record.getId() == null;

        Site site = request.siteId() == null ? null : siteRepository.findById(request.siteId()).orElse(null);

        if (isCheckIn) {
            record.setCheckInAt(now);
            record.setCheckInMethod(method);
            record.setSite(site);
            record.setCheckInMatchedSource(windowMatch.source());
            record.setCheckInMatchedReferenceId(windowMatch.referenceId());
            record.setStatus(now.toLocalTime().isAfter(windowMatch.anchorTime())
                    ? AttendanceRecord.Status.LATE : AttendanceRecord.Status.NORMAL);
        } else {
            record.setCheckOutAt(now);
            record.setCheckOutMethod(method);
            // Chỉ ghi đè status nếu check-in không phải LATE -- không có trạng thái
            // gộp LATE+EARLY_LEAVE trong SDD, giữ nguyên cảnh báo sớm nhất đã ghi nhận.
            if (record.getStatus() == AttendanceRecord.Status.NORMAL
                    && now.toLocalTime().isBefore(windowMatch.anchorTime())) {
                record.setStatus(AttendanceRecord.Status.EARLY_LEAVE);
            }
        }
        record = attendanceRecordRepository.save(record);

        if (method == AttendanceRecord.CheckMethod.GPS) {
            attendanceRecordRepository.updateGpsLocation(record.getId(), request.latitude(), request.longitude());
        }

        writeHistory(record, actor, isNewRecord ? AttendanceRecordHistory.Action.CREATED : AttendanceRecordHistory.Action.UPDATED);

        return toResponse(record);
    }

    private boolean isWorkingDay(LocalDate date, Long employeeId, EmployeeShift activeShift, boolean hasTeachingSessionToday) {
        Optional<WorkCalendar> override = workCalendarRepository
                .findByCalendarDateAndAppliesToScopeAndEmployeeId(date, WorkCalendar.Scope.EMPLOYEE, employeeId);
        if (override.isEmpty() && activeShift != null) {
            override = workCalendarRepository.findByCalendarDateAndAppliesToScopeAndShiftId(
                    date, WorkCalendar.Scope.SHIFT, activeShift.getShift().getId());
        }
        if (override.isEmpty()) {
            override = workCalendarRepository.findByCalendarDateAndAppliesToScope(date, WorkCalendar.Scope.ALL);
        }
        if (override.isPresent()) {
            WorkCalendar.DayType dayType = override.get().getDayType();
            return dayType == WorkCalendar.DayType.WORKING || dayType == WorkCalendar.DayType.COMPENSATORY;
        }
        // Không có override tường minh nào -- fallback theo pattern ca cố định, hoặc (mới) GV có
        // tiết dạy hôm nay. Chỉ áp dụng ở fallback cuối cùng này, không ghi đè HOLIDAY/OFF đã khai báo.
        return (activeShift != null && matchesShiftPattern(activeShift.getShift(), date)) || hasTeachingSessionToday;
    }

    private boolean matchesShiftPattern(Shift shift, LocalDate date) {
        if (!shift.isActive()) {
            return false;
        }
        // getDayOfWeek().getValue() = 1(Thứ Hai)..7(Chủ Nhật), khớp đúng quy ước "1=T2...7=CN".
        String isoDay = String.valueOf(date.getDayOfWeek().getValue());
        List<String> weekdays = Arrays.asList(shift.getAppliesToWeekdays().split(","));
        if (!weekdays.contains(isoDay)) {
            return false;
        }
        if (shift.getWeekParity() == Shift.WeekParity.ALL) {
            return true;
        }
        boolean oddWeek = date.get(WeekFields.ISO.weekOfWeekBasedYear()) % 2 != 0;
        return shift.getWeekParity() == Shift.WeekParity.ODD ? oddWeek : !oddWeek;
    }

    /** A12/A13: cửa sổ theo lịch dạy = [startTime tiết sớm nhất, endTime tiết muộn nhất] trong ngày, không buffer. */
    private WindowMatch resolveTeachingScheduleWindow(List<ClassSession> todaySessions, OffsetDateTime now, boolean isCheckIn) {
        if (todaySessions.isEmpty()) {
            return null;
        }
        ClassSession earliest = todaySessions.stream().min(Comparator.comparing(ClassSession::getStartTime)).orElseThrow();
        ClassSession latest = todaySessions.stream().max(Comparator.comparing(ClassSession::getEndTime)).orElseThrow();
        LocalTime t = now.toLocalTime();
        if (t.isBefore(earliest.getStartTime()) || t.isAfter(latest.getEndTime())) {
            return null;
        }
        return isCheckIn
                ? new WindowMatch(AttendanceRecord.MatchedSource.TEACHING_SCHEDULE, earliest.getId(), earliest.getStartTime())
                : new WindowMatch(AttendanceRecord.MatchedSource.TEACHING_SCHEDULE, latest.getId(), latest.getEndTime());
    }

    /** A14/A15: cửa sổ theo ca cố định, chỉ áp dụng khi is_default_shift_required=TRUE và có ca đang active. */
    private WindowMatch resolveShiftWindow(EmployeeShift activeShift, boolean defaultShiftRequired, OffsetDateTime now, boolean isCheckIn) {
        if (activeShift == null || !defaultShiftRequired) {
            return null;
        }
        Shift shift = activeShift.getShift();
        if (!isWithinShiftWindow(shift, now, isCheckIn)) {
            return null;
        }
        LocalTime anchor = isCheckIn ? shift.getCheckInTime() : shift.getCheckOutTime();
        return new WindowMatch(AttendanceRecord.MatchedSource.SHIFT, shift.getId(), anchor);
    }

    private record WindowMatch(AttendanceRecord.MatchedSource source, Long referenceId, LocalTime anchorTime) {}

    private boolean isWithinShiftWindow(Shift shift, OffsetDateTime now, boolean isCheckIn) {
        LocalTime t = now.toLocalTime();
        LocalTime anchor = isCheckIn ? shift.getCheckInTime() : shift.getCheckOutTime();
        int beforeMin = isCheckIn ? shift.getCheckInWindowBeforeMinutes() : shift.getCheckOutWindowBeforeMinutes();
        int afterMin = isCheckIn ? shift.getCheckInWindowAfterMinutes() : shift.getCheckOutWindowAfterMinutes();
        LocalTime windowStart = anchor.minusMinutes(beforeMin);
        LocalTime windowEnd = anchor.plusMinutes(afterMin);
        if (!windowStart.isAfter(windowEnd)) {
            return !t.isBefore(windowStart) && !t.isAfter(windowEnd);
        }
        // Cửa sổ vắt qua nửa đêm (VD ca đêm check_in_time gần 00:00).
        return !t.isBefore(windowStart) || !t.isAfter(windowEnd);
    }

    private AttendanceRecord.CheckMethod parseMethod(String raw) {
        try {
            return AttendanceRecord.CheckMethod.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new AttendanceMethodNotAvailableException("Phương thức không hợp lệ: " + raw);
        }
    }

    private void writeHistory(AttendanceRecord record, User actor, AttendanceRecordHistory.Action action) {
        AttendanceRecordHistory history = new AttendanceRecordHistory();
        history.setAttendanceRecord(record);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(snapshot(record));
        attendanceRecordHistoryRepository.save(history);
    }

    private Map<String, Object> snapshot(AttendanceRecord r) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("workDate", String.valueOf(r.getWorkDate()));
        details.put("checkInAt", r.getCheckInAt() == null ? null : r.getCheckInAt().toString());
        details.put("checkOutAt", r.getCheckOutAt() == null ? null : r.getCheckOutAt().toString());
        details.put("checkInMethod", r.getCheckInMethod() == null ? null : r.getCheckInMethod().name());
        details.put("checkOutMethod", r.getCheckOutMethod() == null ? null : r.getCheckOutMethod().name());
        details.put("status", r.getStatus().name());
        return details;
    }

    private AttendanceRecordResponse toResponse(AttendanceRecord r) {
        return new AttendanceRecordResponse(
                r.getId(),
                r.getEmployee().getId(),
                r.getWorkDate(),
                r.getCheckInAt(),
                r.getCheckOutAt(),
                r.getCheckInMethod() == null ? null : r.getCheckInMethod().name(),
                r.getCheckOutMethod() == null ? null : r.getCheckOutMethod().name(),
                r.getSite() == null ? null : r.getSite().getId(),
                r.getStatus().name());
    }
}
