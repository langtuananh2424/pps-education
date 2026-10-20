package vn.com.pps.education.lms.dto;

import vn.com.pps.education.common.CriteriaScoreItem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Chấm lần 2 bài Writing do AI chấm (bổ sung ngoài SDD gốc, xác nhận 2026-10-20): 1 câu trả lời ESSAY của 1 lượt làm
 * kèm toàn bộ bản chấm (AI + giáo viên), mới nhất trước. Bản có {@code official=true} là bản chấm hiện hành
 * ({@code student_answer_grading.is_final}) — điểm chính thức; các bản cũ chỉ để đối chiếu, không tính điểm.
 */
public record EssayGradingHistoryResponse(
        Long studentAnswerId,
        Long attemptId,
        int attemptNumber,
        OffsetDateTime submittedAt,
        Long questionId,
        int questionDisplayOrder,
        String questionContent,
        String answerText,
        List<Version> versions
) {
    public record Version(
            Long id,
            BigDecimal score,
            BigDecimal maxScore,
            String feedback,
            /** Bài viết có đánh dấu lỗi (rubric v3); null khi không có. */
            String markedAnswer,
            List<CriteriaScoreItem> criteriaScores,
            OffsetDateTime gradedAt,
            /** "AI" hoặc "HUMAN". */
            String gradingSource,
            String graderName,
            boolean official
    ) {
    }
}
