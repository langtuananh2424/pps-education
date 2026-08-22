package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * UC-40 Main Flow bước 2: soạn "Bài" (chưa gắn câu hỏi — gắn qua
 * addQuestion). Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-07-30): examId bắt buộc — mỗi Bài phải thuộc 1 "Đề" (Exam), không
 * còn gán khung chương trình trực tiếp (khung chương trình nay thuộc Đề).
 */
public record CreateExerciseRequest(
        @NotBlank String code,
        @NotBlank String title,
        @NotNull Long examId,
        Long subjectId,
        @NotBlank String exerciseType,
        @NotNull BigDecimal totalPoints,
        Integer timeLimitMinutes,
        boolean allowRetake,
        Integer maxAttempts,
        boolean showCorrectAnswers,
        /** V89/V100, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05: NULL = dùng mặc định 70% (Exercise.passThresholdPercent). */
        BigDecimal passThresholdPercent,
        /** V136, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-21 — "READING"/"WRITING"/"VOCAB_GRAMMAR", NULL = chưa phân loại. Cố định từ lúc tạo (không sửa được qua UpdateExerciseRequest), xem Javadoc Exercise.SkillCategory. */
        String skillCategory
) {
    public CreateExerciseRequest(String code, String title, Long examId, Long subjectId, String exerciseType,
                                  BigDecimal totalPoints, Integer timeLimitMinutes, boolean allowRetake,
                                  Integer maxAttempts, boolean showCorrectAnswers) {
        this(code, title, examId, subjectId, exerciseType, totalPoints, timeLimitMinutes, allowRetake,
                maxAttempts, showCorrectAnswers, null, null);
    }
}
