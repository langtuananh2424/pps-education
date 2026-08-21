package vn.com.pps.education.service.attendance;

import org.springframework.stereotype.Component;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.SystemSettingRepository;

/**
 * Đọc cờ system_settings.attendance.* dùng chung cho các
 * AttendanceMethodValidator (UC-09 Precondition + A3) — tránh mỗi
 * implementation tự lặp lại logic đọc/parse JSONB.
 */
@Component
public class AttendanceSettings {

    private static final String GPS_ENABLED = "attendance.gps_enabled";
    private static final String FINGERPRINT_ENABLED = "attendance.fingerprint_enabled";
    private static final String FACE_ENABLED = "attendance.face_enabled";
    private static final String MANUAL_WHEN_ALL_DISABLED = "attendance.manual_when_all_disabled";
    private static final String GPS_RADIUS_METERS = "attendance.gps_radius_meters";

    private final SystemSettingRepository systemSettingRepository;

    public AttendanceSettings(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    public boolean isGpsEnabled() {
        return readBoolean(GPS_ENABLED);
    }

    public boolean isFingerprintEnabled() {
        return readBoolean(FINGERPRINT_ENABLED);
    }

    public boolean isFaceEnabled() {
        return readBoolean(FACE_ENABLED);
    }

    /** A3 -- cho phép chấm công thủ công khi cả 3 phương thức tự động đều tắt. */
    public boolean isManualAllowedWhenAllDisabled() {
        return readBoolean(MANUAL_WHEN_ALL_DISABLED);
    }

    public boolean isAnyAutoMethodEnabled() {
        return isGpsEnabled() || isFingerprintEnabled() || isFaceEnabled();
    }

    public double gpsRadiusMeters() {
        return readSetting(GPS_RADIUS_METERS).getSettingValue().asDouble();
    }

    private boolean readBoolean(String key) {
        return readSetting(key).getSettingValue().asBoolean();
    }

    private vn.com.pps.education.domain.SystemSetting readSetting(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("error.attendanceSettings.missingSystemSetting", new Object[]{key}, "Thiếu cấu hình system_settings: " + key));
    }
}
