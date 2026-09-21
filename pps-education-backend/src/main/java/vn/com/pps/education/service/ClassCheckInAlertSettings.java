package vn.com.pps.education.service;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.SystemSettingRepository;

/**
 * Đọc cờ system_settings.class_checkin_alert.* (migration V184, bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21) — mirror
 * {@link HomeworkAlertSettings}. Bật/tắt được ngay trên trang Cài đặt hệ
 * thống (nhóm NOTIFICATION), không cần restart.
 */
@Component
public class ClassCheckInAlertSettings {

    private static final String ENABLED = "class_checkin_alert.enabled";
    private static final String LATE_AFTER_MINUTES = "class_checkin_alert.late_after_minutes";

    private final SystemSettingRepository systemSettingRepository;

    public ClassCheckInAlertSettings(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    public boolean isEnabled() {
        return readSetting(ENABLED).getSettingValue().asBoolean();
    }

    /** Số phút sau giờ bắt đầu buổi học mới coi là "chưa nhận lớp" (0 = ngay khi tới giờ). */
    public int lateAfterMinutes() {
        return Math.max(0, readSetting(LATE_AFTER_MINUTES).getSettingValue().asInt());
    }

    private SystemSetting readSetting(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("error.classCheckInAlertSettings.missingSystemSetting", new Object[]{key}, "Thiếu cấu hình system_settings: " + key));
    }
}
