package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AttendanceRecord;
import vn.com.pps.education.domain.AttendanceRecordHistory;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * UC-09: Chấm công (FR-HRM-02).
 * Xem docs/uc/phan-he-04-nhan-su.md — Main Flow, A1 (ngoài cửa sổ), A2 (GPS
 * ngoài bán kính), A3 (xác thực sinh trắc thất bại/chấm thủ công) và
 * docs/diagrams/activity/ActivityDiagram-ChamCong.mmd cho chi tiết nhánh rẽ.
 *
 * TODO(Phân hệ 6 — class_sessions chưa có migration/entity): Main Flow bước
 * 4 "cửa sổ theo lịch dạy" cho Giáo viên có tiết dạy hôm nay (A12/A13 trong
 * activity diagram) hiện CHƯA implement — mọi nhân sự (kể cả Giáo viên) chỉ
 * được đánh giá theo cửa sổ ca cố định (is_default_shift_required). Đã xác
 * nhận với PM để triển khai phần còn lại của Main Flow trước, bổ sung nhánh
 * lịch dạy khi Phân hệ 6 có class_sessions.
 */
@Service
public class AttendanceService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeShiftRepository employeeShiftRepository;
    private final WorkCalendarRepository workCalendarRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceRecordHistoryRepository attendanceRecordHistoryRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;
    private final List<AttendanceMethodValidator> methodValidators;

    public AttendanceService(EmployeeRepository employeeRepository,
                              EmployeeShiftRepository employeeShiftRepository,
                              WorkCalendarRepository workCalendarRepository,
                              AttendanceRecordRepository attendanceRecordRepository,
                              AttendanceRecordHistoryRepository attendanceRecordHistoryRepository,
                              SiteRepository siteRepository,
                              UserRepository userRepository,
                              List<AttendanceMethodValidator> methodValidators) {
        this.employeeRepository = employeeRepository;
        this.employeeShiftRepository = employeeShiftRepository;
        this.workCalendarRepository = workCalendarRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceRecordHistoryRepository = attendanceRecordHistoryRepository;
        this.siteRepository = siteRepository;
        this.userRepository = userRepository;
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

        // Main Flow bước 3 -- xác định ngày D có phải ngày làm việc.
        if (!isWorkingDay(workDate, employee.getId(), activeShift)) {
            throw new NotAWorkingDayException("Ngày " + workDate + " không phải ngày làm việc.");
        }

        // Main Flow bước 4-5 -- cửa sổ hợp lệ (chỉ cửa sổ ca cố định, xem TODO ở đầu file).
        if (activeShift == null || !employee.isDefaultShiftRequired()
                || !isWithinShiftWindow(activeShift.getShift(), now, isCheckIn)) {
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
        Shift shift = activeShift.getShift();

        if (isCheckIn) {
            record.setCheckInAt(now);
            record.setCheckInMethod(method);
            record.setSite(site);
            record.setCheckInMatchedSource(AttendanceRecord.MatchedSource.SHIFT);
            record.setCheckInMatchedReferenceId(shift.getId());
            record.setStatus(now.toLocalTime().isAfter(shift.getCheckInTime())
                    ? AttendanceRecord.Status.LATE : AttendanceRecord.Status.NORMAL);
        } else {
            record.setCheckOutAt(now);
            record.setCheckOutMethod(method);
            // Chỉ ghi đè status nếu check-in không phải LATE -- không có trạng thái
            // gộp LATE+EARLY_LEAVE trong SDD, giữ nguyên cảnh báo sớm nhất đã ghi nhận.
            if (record.getStatus() == AttendanceRecord.Status.NORMAL
                    && now.toLocalTime().isBefore(shift.getCheckOutTime())) {
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

    private boolean isWorkingDay(LocalDate date, Long employeeId, EmployeeShift activeShift) {
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
        return activeShift != null && matchesShiftPattern(activeShift.getShift(), date);
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
