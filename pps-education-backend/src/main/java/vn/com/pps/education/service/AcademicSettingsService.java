package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.node.IntNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.GradeEditWindowResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.SystemSettingRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;

/**
 * Đọc/ghi system_settings.academic.* (UC-19/20, FR-ACA-03, bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng) — cửa sổ chỉnh sửa điểm X
 * ngày. Theo đúng pattern AttendanceSettings (đọc qua SystemSettingRepository,
 * không cache), cộng thêm phần ghi vì đây là API cấu hình đầu tiên trong
 * dự án cho phép sửa system_settings qua REST thay vì chỉ qua migration.
 */
@Service
public class AcademicSettingsService {

    private static final String GRADE_EDIT_WINDOW_DAYS = "academic.grade_edit_window_days";

    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;

    public AcademicSettingsService(SystemSettingRepository systemSettingRepository, UserRepository userRepository) {
        this.systemSettingRepository = systemSettingRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public int gradeEditWindowDays() {
        return readSetting().getSettingValue().asInt();
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
        SystemSetting setting = readSetting();
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));
        setting.setSettingValue(IntNode.valueOf(days));
        setting.setUpdatedBy(actor);
        setting.setUpdatedAt(OffsetDateTime.now());
        systemSettingRepository.save(setting);
        return new GradeEditWindowResponse(days);
    }

    private SystemSetting readSetting() {
        return systemSettingRepository.findBySettingKey(GRADE_EDIT_WINDOW_DAYS)
                .orElseThrow(() -> new ResourceNotFoundException("Thiếu cấu hình system_settings: " + GRADE_EDIT_WINDOW_DAYS));
    }
}
