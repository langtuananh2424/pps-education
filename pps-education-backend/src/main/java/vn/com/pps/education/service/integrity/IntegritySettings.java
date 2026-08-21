package vn.com.pps.education.service.integrity;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.SystemSettingRepository;

/**
 * Đọc cờ system_settings.integrity.* dùng chung cho AttemptIntegrityService
 * (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — xem
 * migration V70) — mirror AttendanceSettings.
 */
@Component
public class IntegritySettings {

    private static final String MONITORING_ENABLED = "integrity.monitoring_enabled";
    private static final String MIN_VIOLATION_DURATION_SECONDS = "integrity.min_violation_duration_seconds";
    private static final String NOTIFY_VIOLATION_COUNT_THRESHOLD = "integrity.notify_violation_count_threshold";
    private static final String NOTIFY_CUMULATIVE_DURATION_SECONDS_THRESHOLD = "integrity.notify_cumulative_duration_seconds_threshold";

    private final SystemSettingRepository systemSettingRepository;

    public IntegritySettings(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    public boolean isMonitoringEnabled() {
        return readBoolean(MONITORING_ENABLED);
    }

    public int minViolationDurationSeconds() {
        return readInt(MIN_VIOLATION_DURATION_SECONDS);
    }

    public int notifyViolationCountThreshold() {
        return readInt(NOTIFY_VIOLATION_COUNT_THRESHOLD);
    }

    public int notifyCumulativeDurationSecondsThreshold() {
        return readInt(NOTIFY_CUMULATIVE_DURATION_SECONDS_THRESHOLD);
    }

    private boolean readBoolean(String key) {
        return readSetting(key).getSettingValue().asBoolean();
    }

    private int readInt(String key) {
        return readSetting(key).getSettingValue().asInt();
    }

    private SystemSetting readSetting(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("error.integrity.settingMissing",
                        new Object[]{key}, "Thiếu cấu hình system_settings: " + key));
    }
}
