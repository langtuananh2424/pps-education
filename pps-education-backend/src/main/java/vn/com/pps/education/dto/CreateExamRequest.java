package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * tạo 1 "Đề" mới (VD: IELTS Grade 6). teacherType/examType bắt buộc chọn
 * 1 trong 2 (V74, bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-04) — teacherType: VIETNAMESE/FOREIGN; examType: REVIEW/HOMEWORK
 * (độc lập với Exercise.exerciseType, không thay thế).
 *
 * V144 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) —
 * skillCategory bắt buộc chọn (READING/WRITING/VOCAB_GRAMMAR/LISTENING,
 * xem Exam.SkillCategory) — chuyển từ Exercise lên Đề, 1 Đề = 1 nhóm kỹ
 * năng thuần. allowRetake/maxAttempts/passThresholdPercent (cũng chuyển từ
 * Exercise) tùy chọn — NULL/không truyền = dùng mặc định (allowRetake=true,
 * maxAttempts=không giới hạn, passThresholdPercent=70%).
 */
public record CreateExamRequest(
        @NotBlank String code,
        @NotBlank String title,
        @NotNull Long curriculumId,
        @NotBlank String teacherType,
        @NotBlank String examType,
        @NotBlank String skillCategory,
        Boolean allowRetake,
        Integer maxAttempts,
        BigDecimal passThresholdPercent
) {
}
