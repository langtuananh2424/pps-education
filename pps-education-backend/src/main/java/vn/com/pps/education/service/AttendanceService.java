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
import vn.com.pps.education.dto.AttendanceRecordAdminResponse;
import vn.com.pps.education.dto.AttendanceRecordResponse;
import vn.com.pps.education.dto.DetectedSiteResponse;
import vn.com.pps.education.exception.AlreadyCheckedInException;
import vn.com.pps.education.exception.AlreadyCheckedOutException;
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
import vn.com.pps.education.service.attendance.AttendanceSettings;

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
    private final AttendanceSettings attendanceSettings;

    public AttendanceService(EmployeeRepository employeeRepository,
                              EmployeeShiftRepository employeeShiftRepository,
                              WorkCalendarRepository workCalendarRepository,
                              AttendanceRecordRepository attendanceRecordRepository,
                              AttendanceRecordHistoryRepository attendanceRecordHistoryRepository,
                              SiteRepository siteRepository,
                              UserRepository userRepository,
                              ClassSessionRepository classSessionRepository,
                              List<AttendanceMethodValidator> methodValidators,
                              AttendanceSettings attendanceSettings) {
        this.employeeRepository = employeeRepository;
        this.employeeShiftRepository = employeeShiftRepository;
        this.workCalendarRepository = workCalendarRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceRecordHistoryRepository = attendanceRecordHistoryRepository;
        this.siteRepository = siteRepository;
        this.userRepository = userRepository;
        this.classSessionRepository = classSessionRepository;
        this.methodValidators = methodValidators;
        this.attendanceSettings = attendanceSettings;
    }

    /**
     * UC-09 bổ sung ngoài Main Flow gốc (tự nhận diện điểm chấm công theo GPS,
     * xác nhận với người dùng 2026-08-17). Trả về điểm trường ACTIVE gần nhất
     * trong bán kính attendance.gps_radius_meters quanh vị trí hiện tại, hoặc
     * null nếu không điểm trường nào khớp -- FE fallback sang chọn thủ công
     * (dropdown điểm trường đã có sẵn ở SelfAttendanceCard), không chặn luồng
     * chấm công GPS thường (validate bán kính vẫn theo siteId FE gửi lên).
     */
    @Transactional(readOnly = true)
    public DetectedSiteResponse detectSite(double latitude, double longitude) {
        return siteRepository.findNearestWithinRadius(latitude, longitude, attendanceSettings.gpsRadiusMeters())
                .map(s -> new DetectedSiteResponse(s.getId(), s.getName(), s.getDistanceMeters()))
                .orElse(null);
    }

    @Transactional
    public AttendanceRecordResponse checkIn(Long actorUserId, AttendanceCheckRequest request) {
        return process(actorUserId, request, true);
    }

    @Transactional
    public AttendanceRecordResponse checkOut(Long actorUserId, AttendanceCheckRequest request) {
        return process(actorUserId, request, false);
    }

    /**
     * UC-09 — bổ sung ngoài Main Flow gốc, xác nhận với người dùng 2026-08-12.
     * Danh sách tổng hợp chấm công cho HR/Điều hành, gate bằng quyền
     * hrm.attendance.view-all (xem AttendanceController).
     */
    public List<AttendanceRecordAdminResponse> listRecords(Long employeeId, Long siteId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Khoảng ngày không hợp lệ: from phải trước hoặc bằng to.");
        }
        return attendanceRecordRepository.search(employeeId, siteId, from, to).stream()
                .map(this::toAdminResponse)
                .toList();
    }

    /**
     * UC-09 — bổ sung ngoài Main Flow gốc, xác nhận với người dùng 2026-08-12.
     * Trạng thái chấm công hôm nay của chính người dùng (Header). Trả về null
     * (204, ẩn hẳn pill ở FE) khi thuộc diện miễn trừ (Main Flow bước 2) hoặc
     * tài khoản không có hồ sơ nhân sự (không phải Giáo viên/Nhân viên); trả
     * về response với id=null/status=null khi có hồ sơ nhưng CHƯA chấm công
     * hôm nay (khác với 204, để FE phân biệt "không áp dụng" và "chưa làm").
     */
    public AttendanceRecordResponse getMyTodayRecord(Long actorUserId) {
        return employeeRepository.findByUserId(actorUserId)
                .filter(e -> !e.isManagement())
                .map(e -> attendanceRecordRepository.findByEmployeeIdAndWorkDate(e.getId(), LocalDate.now())
                        .map(this::toResponse)
                        .orElseGet(() -> new AttendanceRecordResponse(
                                null, e.getId(), LocalDate.now(), null, null, null, null, null, null)))
                .orElse(null);
    }

    private AttendanceRecordResponse process(Long actorUserId, AttendanceCheckRequest request, boolean isCheckIn) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.attendance.userNotFound", new Object[]{actorUserId}, "Không tìm thấy tài khoản id=" + actorUserId));
        Employee employee = employeeRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.attendance.employeeProfileMissing", new Object[]{}, "Tài khoản chưa có hồ sơ nhân sự."));

        // Main Flow bước 2 -- cấp quản lý miễn trừ hoàn toàn.
        if (employee.isManagement()) {
            throw new ManagementExemptFromAttendanceException("error.managementExemptFromAttendance.default", new Object[]{}, "Cấp quản lý được miễn trừ chấm công.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        LocalDate workDate = now.toLocalDate();
        // V124 (2026-08-14): 1 nhân sự có thể có NHIỀU ca active song song (VD "T7 xen kẽ" =
        // 2 ca weekParity ODD/EVEN cùng active) -- không còn 1 activeShift duy nhất.
        List<EmployeeShift> activeShifts = employeeShiftRepository.findByEmployeeIdAndEffectiveToIsNull(employee.getId());
        List<ClassSession> todaySessions = employee.getEmployeeType() == Employee.EmployeeType.TEACHER
                ? classSessionRepository.findByPrimaryTeacherIdAndSessionDateAndStatusNotIn(
                        actorUserId, workDate, TEACHING_WINDOW_EXCLUDED_STATUSES)
                : List.of();

        // Main Flow bước 3 -- xác định ngày D có phải ngày làm việc. GV có tiết dạy hôm nay cũng được
        // coi là ngày làm việc, nhưng CHỈ khi không có work_calendar override tường minh nào (mọi scope)
        // -- không bao giờ ghi đè quyết định HOLIDAY/OFF đã khai báo (xác nhận với PM, xem UC-09 A2 mới).
        if (!isWorkingDay(workDate, employee.getId(), activeShifts, !todaySessions.isEmpty())) {
            throw new NotAWorkingDayException("error.notAWorkingDay.default", new Object[]{workDate}, "Ngày " + workDate + " không phải ngày làm việc.");
        }

        // Ca khớp đúng pattern ngày hôm nay trong số các ca active (chống chồng chéo đã được
        // EmployeeShiftService đảm bảo tối đa 1 ca khớp mỗi ngày -- xem overlaps()).
        EmployeeShift matchedShift = resolveApplicableShift(activeShifts, workDate);

        // Main Flow bước 4-5 -- xác định cửa sổ hợp lệ: A12/A13 cửa sổ theo lịch dạy
        // (GV có tiết dạy hôm nay) ưu tiên trước, A14/A15 cửa sổ ca cố định xét sau.
        WindowMatch windowMatch = resolveTeachingScheduleWindow(todaySessions, now, isCheckIn);
        if (windowMatch == null) {
            windowMatch = resolveShiftWindow(matchedShift, employee.isDefaultShiftRequired(), now, isCheckIn);
        }
        if (windowMatch == null) {
            throw new OutsideAttendanceWindowException(
                    "error.outsideAttendanceWindow.default", new Object[]{now},
                    "Thời điểm " + now + " ngoài cửa sổ chấm công cho phép.");
        }

        // Main Flow bước 6-7 -- chọn + xác thực phương thức.
        AttendanceRecord.CheckMethod method = parseMethod(request.method());
        AttendanceMethodValidator validator = methodValidators.stream()
                .filter(v -> v.method() == method)
                .findFirst()
                .orElseThrow(() -> new AttendanceMethodNotAvailableException("error.attendanceMethodNotAvailable.notSupported", new Object[]{request.method()}, "Phương thức không hỗ trợ: " + request.method()));
        if (!validator.isEnabled()) {
            throw new AttendanceMethodNotAvailableException("error.attendanceMethodNotAvailable.disabled", new Object[]{method}, "Phương thức " + method + " hiện không khả dụng.");
        }
        // resolvedSiteId: id điểm trường THỰC SỰ áp dụng cho bản ghi -- với GPS có thể khác
        // request.siteId() do validator tự phân giải lại theo vị trí thực tế (xem
        // GpsAttendanceMethodValidator, bổ sung 2026-08-18 -- sửa lỗi chấm công bị chặn/ghi
        // nhầm điểm trường khi 2 điểm trường có bán kính chồng lấn hoặc FE gửi siteId lệch).
        Long resolvedSiteId = validator.validate(new AttendanceCheckContext(
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

        // Bổ sung ngoài Main Flow gốc (xác nhận với người dùng 2026-08-18) -- chặn ghi đè giờ
        // vào/ra thật đã ghi nhận bằng 1 lượt chấm công lặp lại cùng ngày (VD bấm nhầm 2 lần),
        // tránh mất giờ chấm công thật + status (LATE/EARLY_LEAVE) tính sai theo lần bấm sau.
        if (isCheckIn && record.getCheckInAt() != null) {
            throw new AlreadyCheckedInException(
                    "error.alreadyCheckedIn.attendance", new Object[]{record.getCheckInAt()},
                    "Đã chấm công vào lúc " + record.getCheckInAt() + " hôm nay, không thể chấm công vào lại.");
        }
        if (!isCheckIn && record.getCheckOutAt() != null) {
            throw new AlreadyCheckedOutException(
                    "error.alreadyCheckedOut.default", new Object[]{record.getCheckOutAt()},
                    "Đã chấm công ra lúc " + record.getCheckOutAt() + " hôm nay, không thể chấm công ra lại.");
        }

        Site site = resolvedSiteId == null ? null : siteRepository.findById(resolvedSiteId).orElse(null);

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

    private boolean isWorkingDay(LocalDate date, Long employeeId, List<EmployeeShift> activeShifts, boolean hasTeachingSessionToday) {
        Optional<WorkCalendar> override = workCalendarRepository
                .findByCalendarDateAndAppliesToScopeAndEmployeeId(date, WorkCalendar.Scope.EMPLOYEE, employeeId);
        if (override.isEmpty()) {
            for (EmployeeShift activeShift : activeShifts) {
                override = workCalendarRepository.findByCalendarDateAndAppliesToScopeAndShiftId(
                        date, WorkCalendar.Scope.SHIFT, activeShift.getShift().getId());
                if (override.isPresent()) {
                    break;
                }
            }
        }
        if (override.isEmpty()) {
            override = workCalendarRepository.findByCalendarDateAndAppliesToScope(date, WorkCalendar.Scope.ALL);
        }
        if (override.isPresent()) {
            WorkCalendar.DayType dayType = override.get().getDayType();
            return dayType == WorkCalendar.DayType.WORKING || dayType == WorkCalendar.DayType.COMPENSATORY;
        }
        // Không có override tường minh nào -- fallback theo pattern của bất kỳ ca active nào, hoặc
        // (mới) GV có tiết dạy hôm nay. Chỉ áp dụng ở fallback cuối cùng này, không ghi đè HOLIDAY/OFF
        // đã khai báo.
        return activeShifts.stream().anyMatch(es -> matchesShiftPattern(es.getShift(), date)) || hasTeachingSessionToday;
    }

    /** V124 (2026-08-14): trong số các ca active, ca nào khớp pattern ngày hôm nay (nếu có). */
    private EmployeeShift resolveApplicableShift(List<EmployeeShift> activeShifts, LocalDate date) {
        return activeShifts.stream()
                .filter(es -> matchesShiftPattern(es.getShift(), date))
                .findFirst()
                .orElse(null);
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
            throw new AttendanceMethodNotAvailableException("error.attendanceMethodNotAvailable.invalid", new Object[]{raw}, "Phương thức không hợp lệ: " + raw);
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

    private AttendanceRecordAdminResponse toAdminResponse(AttendanceRecord r) {
        return new AttendanceRecordAdminResponse(
                r.getId(),
                r.getEmployee().getId(),
                r.getEmployee().getUser().getFullName(),
                r.getEmployee().getEmployeeCode(),
                r.getWorkDate(),
                r.getCheckInAt(),
                r.getCheckOutAt(),
                r.getCheckInMethod() == null ? null : r.getCheckInMethod().name(),
                r.getCheckOutMethod() == null ? null : r.getCheckOutMethod().name(),
                r.getSite() == null ? null : r.getSite().getId(),
                r.getSite() == null ? null : r.getSite().getName(),
                r.getStatus().name());
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
