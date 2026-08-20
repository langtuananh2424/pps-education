package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.node.IntNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.TaskCancelledRetentionResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.SystemSettingRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;

/**
 * UC-06/07 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): đọc/ghi
 * system_settings.task.cancelled_retention_days — số ngày giữ task CANCELLED
 * trước khi cron nightly xóa cứng (TaskSchedulerService). Theo đúng pattern
 * {@link AcademicSettingsService} (đọc/ghi system_settings qua REST, không
 * cache).
 */
@Service
public class TaskSettingsService {

    private static final String CANCELLED_RETENTION_DAYS = "task.cancelled_retention_days";

    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;

    public TaskSettingsService(SystemSettingRepository systemSettingRepository, UserRepository userRepository) {
        this.systemSettingRepository = systemSettingRepository;
        this.userRepository = userRepository;
    }

    /** Số ngày giữ task CANCELLED trước khi xóa cứng (dùng bởi TaskSchedulerService). */
    @Transactional(readOnly = true)
    public int cancelledRetentionDays() {
        return readSetting(CANCELLED_RETENTION_DAYS).getSettingValue().asInt();
    }

    @Transactional(readOnly = true)
    public TaskCancelledRetentionResponse getCancelledRetention() {
        return new TaskCancelledRetentionResponse(cancelledRetentionDays());
    }

    /** Quản trị công việc (task.manage) đổi số ngày — validate days > 0. */
    @Transactional
    public TaskCancelledRetentionResponse updateCancelledRetentionDays(int days, Long actorUserId) {
        if (days <= 0) {
            throw new IllegalArgumentException("Số ngày phải lớn hơn 0.");
        }
        SystemSetting setting = readSetting(CANCELLED_RETENTION_DAYS);
        setting.setSettingValue(IntNode.valueOf(days));
        setting.setUpdatedBy(getUserOrThrow(actorUserId));
        setting.setUpdatedAt(OffsetDateTime.now());
        systemSettingRepository.save(setting);
        return new TaskCancelledRetentionResponse(days);
    }

    private User getUserOrThrow(Long actorUserId) {
        return userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.taskSettings.accountNotFound", new Object[]{actorUserId}, "Không tìm thấy tài khoản id=" + actorUserId));
    }

    private SystemSetting readSetting(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("error.taskSettings.systemSettingMissing", new Object[]{key}, "Thiếu cấu hình system_settings: " + key));
    }
}
