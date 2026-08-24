package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * UC-40 Main Flow bước 2: soạn "Bài" (chưa gắn câu hỏi — gắn qua
 * addQuestion). Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-07-30): examId bắt buộc — mỗi Bài phải thuộc 1 "Đề" (Exam), không
 * còn gán khung chương trình trực tiếp (khung chương trình nay thuộc Đề).
 *
 * V144 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) —
 * skillCategory/allowRetake/maxAttempts/passThresholdPercent đã CHUYỂN HẲN
 * lên {@code CreateExamRequest} (cấu hình chung cho cả Đề, xem Javadoc
 * Exam.SkillCategory) — không còn ở đây.
 */
public record CreateExerciseRequest(
        @NotBlank String code,
        @NotBlank String title,
        @NotNull Long examId,
        Long subjectId,
        @NotBlank String exerciseType,
        @NotNull BigDecimal totalPoints,
        Integer timeLimitMinutes,
        boolean showCorrectAnswers
) {
}
