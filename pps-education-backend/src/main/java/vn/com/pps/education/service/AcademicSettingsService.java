package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.node.IntNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.GradeAppealWindowResponse;
import vn.com.pps.education.dto.GradeEditWindowResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.SystemSettingRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;

/**
 * Đọc/ghi system_settings.academic.* (UC-19/20/62, FR-ACA-03, bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng) — độ trễ tự động công bố
 * điểm dự kiến X ngày (V39), và hạn phúc khảo Y ngày kể từ lúc công bố
 * điểm dự kiến (V43). Theo đúng pattern AttendanceSettings (đọc qua
 * SystemSettingRepository, không cache), cộng thêm phần ghi vì đây là
 * API cấu hình đầu tiên trong dự án cho phép sửa system_settings qua
 * REST thay vì chỉ qua migration.
 */
@Service
public class AcademicSettingsService {

    private static final String GRADE_EDIT_WINDOW_DAYS = "academic.grade_edit_window_days";
    private static final String GRADE_APPEAL_WINDOW_DAYS = "academic.grade_appeal_window_days";

    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;

    public AcademicSettingsService(SystemSettingRepository systemSettingRepository, UserRepository userRepository) {
        this.systemSettingRepository = systemSettingRepository;
        this.userRepository = userRepository;
    }

    /** UC-20 A3: số ngày X kể từ lần đầu nhập điểm, hệ thống tự động công bố dự kiến nếu không ai công bố thủ công. */
    @Transactional(readOnly = true)
    public int gradeEditWindowDays() {
        return readSetting(GRADE_EDIT_WINDOW_DAYS).getSettingValue().asInt();
    }

    @Transactional(readOnly = true)
    public GradeEditWindowResponse getGradeEditWindow() {
        return new GradeEditWindowResponse(gradeEditWindowDays());
    }

    /** Trưởng phòng đào tạo đổi số ngày X — validate days > 0 (không có ý nghĩa nếu <= 0). */
    @Transactional
    public GradeEditWindowResponse updateGradeEditWindowDays(int days, Long actorUserId) {
        if (days <= 0) {
            throw new IllegalArgumentException("Số ngày phải lớn hơn 0.");
        }
        SystemSetting setting = readSetting(GRADE_EDIT_WINDOW_DAYS);
        setting.setSettingValue(IntNode.valueOf(days));
        setting.setUpdatedBy(getUserOrThrow(actorUserId));
        setting.setUpdatedAt(OffsetDateTime.now());
        systemSettingRepository.save(setting);
        return new GradeEditWindowResponse(days);
    }

    /** UC-62 A3: số ngày Y kể từ công bố điểm dự kiến, hết hạn thì hệ thống tự động chuyển Chính thức. */
    @Transactional(readOnly = true)
    public int gradeAppealWindowDays() {
        return readSetting(GRADE_APPEAL_WINDOW_DAYS).getSettingValue().asInt();
    }

    @Transactional(readOnly = true)
    public GradeAppealWindowResponse getGradeAppealWindow() {
        return new GradeAppealWindowResponse(gradeAppealWindowDays());
    }

    /** Trưởng phòng đào tạo đổi số ngày Y — validate days > 0. */
    @Transactional
    public GradeAppealWindowResponse updateGradeAppealWindowDays(int days, Long actorUserId) {
        if (days <= 0) {
            throw new IllegalArgumentException("Số ngày phải lớn hơn 0.");
        }
        SystemSetting setting = readSetting(GRADE_APPEAL_WINDOW_DAYS);
        setting.setSettingValue(IntNode.valueOf(days));
        setting.setUpdatedBy(getUserOrThrow(actorUserId));
        setting.setUpdatedAt(OffsetDateTime.now());
        systemSettingRepository.save(setting);
        return new GradeAppealWindowResponse(days);
    }

    private User getUserOrThrow(Long actorUserId) {
        return userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));
    }

    private SystemSetting readSetting(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Thiếu cấu hình system_settings: " + key));
    }
}
