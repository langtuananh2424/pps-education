package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.node.IntNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CommentEditWindowResponse;
import vn.com.pps.education.dto.GradeEditWindowResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.SystemSettingRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;

/**
 * Đọc/ghi system_settings.academic.* (UC-19/20, FR-ACA-03, bổ sung ngoài
 * SDD gốc, đã xác nhận với người dùng) — số ngày X đánh dấu mốc "lần đầu
 * nhập điểm" của 1 (lớp, kỳ đánh giá), V39 (thông tin, không còn gắn job
 * tự động nào từ V44 — xem grade_period_edit_windows). Theo đúng pattern
 * AttendanceSettings (đọc qua SystemSettingRepository, không cache), cộng
 * thêm phần ghi vì đây là API cấu hình đầu tiên trong dự án cho phép sửa
 * system_settings qua REST thay vì chỉ qua migration.
 */
@Service
public class AcademicSettingsService {

    private static final String GRADE_EDIT_WINDOW_DAYS = "academic.grade_edit_window_days";
    private static final String COMMENT_EDIT_WINDOW_DAYS = "academic.comment_edit_window_days";

    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;

    public AcademicSettingsService(SystemSettingRepository systemSettingRepository, UserRepository userRepository) {
        this.systemSettingRepository = systemSettingRepository;
        this.userRepository = userRepository;
    }

    /** V39 (V44: chỉ còn ý nghĩa thông tin, không còn gắn job tự động nào): số ngày X kể từ lần đầu nhập điểm cho 1 (lớp, kỳ đánh giá). */
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

    /**
     * UC-21 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-24):
     * số ngày X kể từ NGÀY BUỔI HỌC DIỄN RA, Giáo viên được nhập/sửa nhận
     * xét Hàng ngày — xem StudentCommentService.requireCanWriteDailyComment.
     */
    @Transactional(readOnly = true)
    public int commentEditWindowDays() {
        return readSetting(COMMENT_EDIT_WINDOW_DAYS).getSettingValue().asInt();
    }

    @Transactional(readOnly = true)
    public CommentEditWindowResponse getCommentEditWindow() {
        return new CommentEditWindowResponse(commentEditWindowDays());
    }

    /** Trưởng phòng đào tạo đổi số ngày X — validate days > 0. */
    @Transactional
    public CommentEditWindowResponse updateCommentEditWindowDays(int days, Long actorUserId) {
        if (days <= 0) {
            throw new IllegalArgumentException("Số ngày phải lớn hơn 0.");
        }
        SystemSetting setting = readSetting(COMMENT_EDIT_WINDOW_DAYS);
        setting.setSettingValue(IntNode.valueOf(days));
        setting.setUpdatedBy(getUserOrThrow(actorUserId));
        setting.setUpdatedAt(OffsetDateTime.now());
        systemSettingRepository.save(setting);
        return new CommentEditWindowResponse(days);
    }

    private User getUserOrThrow(Long actorUserId) {
        return userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.academicSettings.actorNotFound", new Object[]{actorUserId},
                        "Không tìm thấy tài khoản id=" + actorUserId));
    }

    private SystemSetting readSetting(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.academicSettings.settingMissing", new Object[]{key},
                        "Thiếu cấu hình system_settings: " + key));
    }
}
