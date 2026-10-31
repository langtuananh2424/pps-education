package vn.com.pps.education.lms.dto;

import vn.com.pps.education.common.CriteriaScoreItem;

import java.util.List;

/**
 * SPIKE/TEST riêng (2026-10-22, đã xác nhận với người dùng) — response khi chấm Writing bằng ĐÚNG rubric
 * v3 thật (qua {@code WritingAiGradingService}), KHÔNG phải DTO của 1 UC đã đặc tả. Khác
 * {@link WritingAiGradingTestResponse} (rubric placeholder, 4 tiêu chí cố định) — ở đây tiêu chí/số lượng
 * hàng điểm tuỳ theo khối thật ({@code criteriaScores}), {@code null} nếu chưa xác định được rubric cho
 * khối/track đã chọn hoặc AI chấm thất bại/dở dang sau 3 lần thử (xem {@code WritingAiGradingService#grade}).
 */
public record WritingRealRubricTestResponse(
        boolean gradedSuccessfully,
        Integer scorePercent,
        String feedback,
        String markedAnswer,
        List<CriteriaScoreItem> criteriaScores,
        String modelUsed,
        Integer promptTokens,
        Integer cacheReadTokens,
        Integer completionTokens
) {
}
