package vn.com.pps.education.service;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.AttendanceRecord;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.EmploymentContract;
import vn.com.pps.education.domain.EmployeeShift;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.domain.WorkCalendar;
import vn.com.pps.education.repository.EmploymentContractRepository;
import vn.com.pps.education.repository.WorkCalendarRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.WeekFields;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-09: Chấm công (FR-HRM-02) — tách khỏi {@link AttendanceService} (bổ
 * sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-11) để tái dùng
 * NGUYÊN VẸN quy tắc "ngày làm việc"/"cửa sổ chấm công" cho cả check-in/
 * check-out THẬT (AttendanceService.process) lẫn scheduler quét MISSING
 * (AttendanceMissingSchedulerService) — tránh 2 nơi tự định nghĩa lại cùng
 * 1 quy tắc UC-09 rồi lệch nhau theo thời gian. Xem docs/uc/phan-he-04-nhan-su.md.
 */
@Component
public class AttendanceWindowResolver {

    private final WorkCalendarRepository workCalendarRepository;
    private final EmploymentContractRepository employmentContractRepository;

    public AttendanceWindowResolver(WorkCalendarRepository workCalendarRepository,
                                     EmploymentContractRepository employmentContractRepository) {
        this.workCalendarRepository = workCalendarRepository;
        this.employmentContractRepository = employmentContractRepository;
    }

    public record WindowMatch(AttendanceRecord.MatchedSource source, Long referenceId, LocalTime anchorTime) {}

    /** Main Flow bước 3 (A8/A9) — xem Javadoc gốc ở AttendanceService trước khi tách. */
    public boolean isWorkingDay(LocalDate date, Long employeeId, List<EmployeeShift> activeShifts, boolean hasTeachingSessionToday) {
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
        return activeShifts.stream().anyMatch(es -> matchesShiftPattern(es.getShift(), date)) || hasTeachingSessionToday;
    }

    /** V124 (2026-08-14): trong số các ca active, ca nào khớp pattern ngày hôm nay (nếu có). */
    public EmployeeShift resolveApplicableShift(List<EmployeeShift> activeShifts, LocalDate date) {
        return activeShifts.stream()
                .filter(es -> matchesShiftPattern(es.getShift(), date))
                .findFirst()
                .orElse(null);
    }

    public boolean matchesShiftPattern(Shift shift, LocalDate date) {
        if (!shift.isActive()) {
            return false;
        }
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
    public WindowMatch resolveTeachingScheduleWindow(List<ClassSession> todaySessions, OffsetDateTime now, boolean isCheckIn) {
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
    public WindowMatch resolveShiftWindow(EmployeeShift activeShift, boolean defaultShiftRequired, OffsetDateTime now, boolean isCheckIn) {
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

    public boolean isWithinShiftWindow(Shift shift, OffsetDateTime now, boolean isCheckIn) {
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

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-11 — dùng
     * riêng cho AttendanceMissingSchedulerService: giờ ĐÓNG cửa sổ chấm công
     * VÀO theo lịch dạy trong ngày (endTime tiết muộn nhất), null nếu không
     * có tiết dạy nào hôm đó. Khác resolveTeachingScheduleWindow(...,now,...)
     * — hàm đó trả lời "có đang khớp cửa sổ NGAY BÂY GIỜ không", hàm này chỉ
     * cần biên "đóng" để so sánh "đã trôi qua chưa", không cần biết now.
     */
    public LocalTime teachingScheduleCheckInWindowEnd(List<ClassSession> todaySessions) {
        if (todaySessions.isEmpty()) {
            return null;
        }
        return todaySessions.stream().map(ClassSession::getEndTime).max(Comparator.naturalOrder()).orElseThrow();
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-11 — giờ
     * ĐÓNG cửa sổ chấm công VÀO theo ca cố định (checkInTime +
     * checkInWindowAfterMinutes). Không xử lý riêng ca vắt qua nửa đêm ở bản
     * đầu này (giới hạn đã biết, xem Javadoc AttendanceMissingSchedulerService).
     */
    public LocalTime shiftCheckInWindowEnd(Shift shift) {
        return shift.getCheckInTime().plusMinutes(shift.getCheckInWindowAfterMinutes());
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-11: GV có
     * hợp đồng ACTIVE trả lương theo giờ (salary_type=HOURLY) = GV
     * part-time, miễn trừ HOÀN TOÀN khỏi UC-09 Chấm công ca (chỉ dùng Nhận
     * lớp UC-71) — mirror is_management. Không có hợp đồng ACTIVE nào (VD
     * HR chưa kịp tạo) = mặc định coi là full-time, KHÔNG loại trừ (an toàn
     * hơn, tránh bỏ sót người thật sự cần chấm công).
     */
    public boolean isHourlyPaidTeacher(Long employeeId) {
        return employmentContractRepository.findByEmployeeIdAndStatusAndDeletedAtIsNull(employeeId, EmploymentContract.Status.ACTIVE)
                .map(c -> c.getSalaryType() == EmploymentContract.SalaryType.HOURLY)
                .orElse(false);
    }

    /** Bản batch của isHourlyPaidTeacher — dùng ở AttendanceMissingSchedulerService. */
    public Set<Long> filterHourlyPaidEmployeeIds(List<Long> employeeIds) {
        if (employeeIds.isEmpty()) {
            return Set.of();
        }
        return employmentContractRepository.findByEmployeeIdInAndStatusAndDeletedAtIsNull(employeeIds, EmploymentContract.Status.ACTIVE).stream()
                .filter(c -> c.getSalaryType() == EmploymentContract.SalaryType.HOURLY)
                .map(c -> c.getEmployee().getId())
                .collect(Collectors.toSet());
    }
}
