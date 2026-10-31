package vn.com.pps.education.lms.dto;

import vn.com.pps.education.common.CriteriaScoreItem;

import java.util.List;
import java.util.Map;

/**
 * SPIKE/TEST riêng (2026-10-22, đã xác nhận với người dùng) — response khi chấm bước VIẾT (câu trả lời
 * ngắn, trước khi mở khoá Nói) bằng ĐÚNG rubric checkpoint thật UC-23b (qua
 * {@code ReflexV2AiGradingService#gradeWriting}), KHÔNG phải DTO của 1 UC đã đặc tả, và KHÔNG phải rubric
 * Essay UC-40/41 ({@link WritingRealRubricTestResponse}) — hai luồng dùng 2 bộ rubric khác nhau hoàn toàn.
 * {@code gradedSuccessfully=false} khi {@code task} không tồn tại cho tổ hợp Khối/Track/định dạng đã chọn,
 * hoặc AI chấm/parse thất bại.
 */
public record WritingReflexRealRubricTestResponse(
        boolean gradedSuccessfully,
        String taskId,
        Integer step1Percent,
        Integer grammarPercent,
        Integer redCount,
        List<CriteriaScoreItem> criteria,
        String markedText,
        String feedback,
        String hint,
        String gateNote,
        List<String> gates,
        Map<String, Object> audit,
        String modelUsed,
        Integer promptTokens,
        Integer cacheReadTokens,
        Integer completionTokens,
        Long costVnd
) {
}
