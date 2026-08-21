package vn.com.pps.education.service;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.SystemSettingRepository;

/**
 * Đọc cờ system_settings.homework_alert.* (migration V92, bổ sung ngoài
 * SDD gốc, đã xác nhận với người dùng 2026-08-06) — mirror IntegritySettings.
 */
@Component
public class HomeworkAlertSettings {

    private static final String ENABLED = "homework_alert.enabled";
    private static final String REFLEX_PASS_THRESHOLD_PERCENT = "homework_alert.reflex_pass_threshold_percent";
    private static final String REMINDER_BEFORE_DUE_HOURS = "homework_alert.reminder_before_due_hours";

    private final SystemSettingRepository systemSettingRepository;

    public HomeworkAlertSettings(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    public boolean isEnabled() {
        return readSetting(ENABLED).getSettingValue().asBoolean();
    }

    public int reflexPassThresholdPercent() {
        return readSetting(REFLEX_PASS_THRESHOLD_PERCENT).getSettingValue().asInt();
    }

    public int reminderBeforeDueHours() {
        return readSetting(REMINDER_BEFORE_DUE_HOURS).getSettingValue().asInt();
    }

    private SystemSetting readSetting(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("error.homeworkAlertSettings.missingSystemSetting", new Object[]{key}, "Thiếu cấu hình system_settings: " + key));
    }
}
