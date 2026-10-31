package vn.com.pps.education.lms.dto;

/**
 * SPIKE/TEST riêng (2026-10-22, đã xác nhận với người dùng) — response cho nhánh chấm Writing độc lập
 * (không cần audio) trong {@code SpeakingAiGradingTestService#gradeWritingOnly}, KHÔNG phải DTO của 1 UC
 * đã đặc tả. Rubric vẫn là PLACEHOLDER — xem Javadoc {@code WRITING_PLACEHOLDER_RUBRIC}.
 *
 * {@code taskAchievementScorePercent} có thể null nếu gọi không kèm đề bài (essayPrompt rỗng).
 */
public record WritingAiGradingTestResponse(
        Integer taskAchievementScorePercent,
        Integer coherenceCohesionScorePercent,
        Integer lexicalResourceScorePercent,
        Integer grammaticalRangeScorePercent,
        String feedback,
        String modelUsed,
        String gradeTrackUsed,
        Long costVnd
) {
}
