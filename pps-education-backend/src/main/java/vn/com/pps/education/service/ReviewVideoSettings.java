package vn.com.pps.education.service;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.SystemSettingRepository;

/**
 * Đọc cờ system_settings.review_video.* dùng cho ReviewVideoService (V93,
 * bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) — mirror
 * IntegritySettings.
 */
@Component
public class ReviewVideoSettings {

    private static final String COMPLETION_PASS_THRESHOLD_PERCENT = "review_video.completion_pass_threshold_percent";

    private final SystemSettingRepository systemSettingRepository;

    public ReviewVideoSettings(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    /** Ngưỡng % lượt xem đạt/yêu cầu để tính "đạt" BTVN Video Kết nối (CONNECTION). */
    public int completionPassThresholdPercent() {
        return systemSettingRepository.findBySettingKey(COMPLETION_PASS_THRESHOLD_PERCENT)
                .orElseThrow(() -> new ResourceNotFoundException("Thiếu cấu hình system_settings: " + COMPLETION_PASS_THRESHOLD_PERCENT))
                .getSettingValue().asInt();
    }
}
