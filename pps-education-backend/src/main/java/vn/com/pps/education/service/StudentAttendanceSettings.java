package vn.com.pps.education.service;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.SystemSettingRepository;

/**
 * Đọc cờ system_settings.student_attendance.* (migration V136, bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22) — mirror
 * HomeworkAlertSettings. Tiền tố "student_attendance." (không dùng
 * "attendance." trần) để không lẫn với cấu hình chấm công NHÂN VIÊN UC-09
 * (xem vn.com.pps.education.service.attendance.AttendanceSettings).
 */
@Component
public class StudentAttendanceSettings {

    private static final String GRACE_PERIOD_MINUTES = "student_attendance.grace_period_minutes";

    private final SystemSettingRepository systemSettingRepository;

    public StudentAttendanceSettings(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    /** UC-15: số phút nới thêm sau class_sessions.end_time để Giáo viên vẫn điểm danh/sửa được. */
    public int gracePeriodMinutes() {
        return readSetting(GRACE_PERIOD_MINUTES).getSettingValue().asInt();
    }

    private SystemSetting readSetting(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("error.studentAttendanceSettings.missingSystemSetting", new Object[]{key}, "Thiếu cấu hình system_settings: " + key));
    }
}
