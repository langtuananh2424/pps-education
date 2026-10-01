package vn.com.pps.education.service;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.SystemSettingRepository;

/**
 * Đọc cấu hình system_settings.session_report_alert.* (V207, bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-10-01) — mirror {@link ClassCheckInAlertSettings}. Chỉnh ngay trên trang Cài đặt hệ
 * thống (nhóm NOTIFICATION), không cần restart. Các hạn dùng chung cho trang theo dõi, cảnh báo và tổng hợp.
 */
@Component
public class SessionReportSettings {

    private static final String ENABLED = "session_report_alert.enabled";
    private static final String SUBMIT_DEADLINE_HOURS = "session_report_alert.submit_deadline_hours";
    private static final String SUBMIT_DUE_SOON_MINUTES = "session_report_alert.submit_due_soon_minutes";
    private static final String APPROVAL_DEADLINE_HOURS = "session_report_alert.approval_deadline_hours";
    private static final String APPROVAL_DUE_SOON_MINUTES = "session_report_alert.approval_due_soon_minutes";
    private static final String RESUBMIT_DEADLINE_HOURS = "session_report_alert.resubmit_deadline_hours";
    private static final String DAILY_DIGEST_HOUR = "session_report_alert.daily_digest_hour";

    private final SystemSettingRepository systemSettingRepository;

    public SessionReportSettings(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    /** Bật/tắt toàn bộ cảnh báo — trang theo dõi vẫn tính trạng thái dù tắt. */
    public boolean isAlertEnabled() {
        return readSetting(ENABLED).getSettingValue().asBoolean();
    }

    /** Số giờ giáo viên có để gửi duyệt nhận xét, tính từ lúc buổi học kết thúc (tối thiểu 1). */
    public int submitDeadlineHours() {
        return Math.max(1, readSetting(SUBMIT_DEADLINE_HOURS).getSettingValue().asInt());
    }

    public int submitDueSoonMinutes() {
        return Math.max(0, readSetting(SUBMIT_DUE_SOON_MINUTES).getSettingValue().asInt());
    }

    /** Số giờ Quản lý điểm trường có để duyệt/từ chối, tính từ lúc giáo viên gửi duyệt (tối thiểu 1). */
    public int approvalDeadlineHours() {
        return Math.max(1, readSetting(APPROVAL_DEADLINE_HOURS).getSettingValue().asInt());
    }

    public int approvalDueSoonMinutes() {
        return Math.max(0, readSetting(APPROVAL_DUE_SOON_MINUTES).getSettingValue().asInt());
    }

    /** Số giờ giáo viên có để gửi lại sau khi bị từ chối (tối thiểu 1). */
    public int resubmitDeadlineHours() {
        return Math.max(1, readSetting(RESUBMIT_DEADLINE_HOURS).getSettingValue().asInt());
    }

    /** Giờ gửi tổng hợp hằng ngày (0-23), -1 = không gửi. */
    public int dailyDigestHour() {
        int hour = readSetting(DAILY_DIGEST_HOUR).getSettingValue().asInt();
        return hour < 0 || hour > 23 ? -1 : hour;
    }

    private SystemSetting readSetting(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("error.sessionReportSettings.missingSystemSetting", new Object[]{key},
                        "Thiếu cấu hình system_settings: " + key));
    }
}
