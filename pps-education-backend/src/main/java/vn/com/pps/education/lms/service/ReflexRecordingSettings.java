package vn.com.pps.education.lms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.lms.dto.ReflexRecordingConfigResponse;
import vn.com.pps.education.system.repository.SystemSettingRepository;

/**
 * Đọc công tắc {@code system_settings.reflex.recording_filter_enabled} (migration V199, bổ sung ngoài SDD gốc,
 * đã xác nhận với người dùng 2026-09-29) — UC-23b: màn ghi âm của học sinh có lọc bản ghi trước khi nộp hay
 * không. Bật/tắt ngay trên trang Cài đặt hệ thống (nhóm FEATURE_FLAG), không cần restart.
 *
 * Thiếu key (DB chưa chạy V199) → coi là TẮT thay vì ném lỗi: công tắc này không được làm hỏng việc ghi âm.
 */
@Service
public class ReflexRecordingSettings {

    private static final Logger log = LoggerFactory.getLogger(ReflexRecordingSettings.class);
    static final String FILTER_ENABLED = "reflex.recording_filter_enabled";

    private final SystemSettingRepository systemSettingRepository;

    public ReflexRecordingSettings(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    @Transactional(readOnly = true)
    public ReflexRecordingConfigResponse config() {
        boolean enabled = systemSettingRepository.findBySettingKey(FILTER_ENABLED)
                .map(s -> s.getSettingValue().asBoolean(false))
                .orElseGet(() -> {
                    log.warn("ReflexRecordingSettings: thiếu system_settings {} — coi như tắt bộ lọc thu âm.", FILTER_ENABLED);
                    return false;
                });
        return new ReflexRecordingConfigResponse(enabled);
    }
}
