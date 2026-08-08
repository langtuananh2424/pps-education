package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.domain.SystemSettingHistory;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.SystemSettingHistoryResponse;
import vn.com.pps.education.dto.SystemSettingResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.SystemSettingHistoryRepository;
import vn.com.pps.education.repository.SystemSettingRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Cài đặt hệ thống (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-08) — cho Quản trị viên hệ thống sửa giá trị system_settings qua
 * UI thay vì phải sửa tay qua SQL/migration (đúng mục đích cột đã ghi sẵn
 * trong SDD > 02-nen-tang.md > k: "Cho phép Quản trị viên bật/tắt tính
 * năng qua UI không cần deploy lại"). Chỉ SỬA giá trị key có sẵn — không hỗ
 * trợ tạo/xoá key qua UI (key mới vẫn tạo qua migration như hiện tại, xem
 * quyết định phạm vi đã xác nhận).
 */
@Service
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;
    private final SystemSettingHistoryRepository systemSettingHistoryRepository;
    private final UserRepository userRepository;

    public SystemSettingService(SystemSettingRepository systemSettingRepository,
                                 SystemSettingHistoryRepository systemSettingHistoryRepository,
                                 UserRepository userRepository) {
        this.systemSettingRepository = systemSettingRepository;
        this.systemSettingHistoryRepository = systemSettingHistoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<SystemSettingResponse> listAll() {
        return systemSettingRepository.findAll().stream()
                .sorted(Comparator.comparing(SystemSetting::getCategory).thenComparing(SystemSetting::getSettingKey))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Sửa giá trị 1 setting — chặn đổi khác KIỂU dữ liệu JSON (VD đang là
     * boolean mà sửa thành chuỗi) để không làm hỏng code đang đọc giá trị
     * này qua .asBoolean()/.asInt() ở nơi khác (VD AttendanceSettings đọc
     * attendance.gps_enabled) — bảo vệ tối thiểu, không phải validate theo
     * từng key cụ thể (không có đặc tả schema per-key trong SDD).
     */
    @Transactional
    public SystemSettingResponse update(String settingKey, JsonNode newValue, Long actorUserId) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(settingKey)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình key=" + settingKey));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        if (setting.getSettingValue().getNodeType() != newValue.getNodeType()) {
            throw new IllegalArgumentException(
                    "Giá trị mới phải cùng kiểu dữ liệu với giá trị hiện tại (" +
                            setting.getSettingValue().getNodeType() + ")");
        }

        SystemSettingHistory history = new SystemSettingHistory();
        history.setSystemSetting(setting);
        history.setChangedBy(actor);
        history.setOldValue(setting.getSettingValue());
        history.setNewValue(newValue);
        systemSettingHistoryRepository.save(history);

        setting.setSettingValue(newValue);
        setting.setUpdatedBy(actor);
        setting.setUpdatedAt(OffsetDateTime.now());
        return toResponse(systemSettingRepository.save(setting));
    }

    @Transactional(readOnly = true)
    public List<SystemSettingHistoryResponse> getHistory(String settingKey) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(settingKey)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình key=" + settingKey));
        return systemSettingHistoryRepository.findBySystemSettingIdOrderByCreatedAtDesc(setting.getId()).stream()
                .map(h -> new SystemSettingHistoryResponse(
                        h.getId(), h.getOldValue(), h.getNewValue(),
                        h.getChangedBy().getFullName(), h.getCreatedAt()))
                .toList();
    }

    private SystemSettingResponse toResponse(SystemSetting s) {
        return new SystemSettingResponse(
                s.getId(), s.getSettingKey(), s.getSettingValue(), s.getCategory(), s.getDescription(),
                s.getUpdatedBy() == null ? null : s.getUpdatedBy().getFullName(), s.getUpdatedAt());
    }
}
