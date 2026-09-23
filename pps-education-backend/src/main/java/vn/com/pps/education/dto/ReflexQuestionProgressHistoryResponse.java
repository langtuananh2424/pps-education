package vn.com.pps.education.dto;

import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.domain.ReflexQuestionProgressHistory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * V191 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21) — UC-23b (Video phản xạ): 1 dòng
 * lịch sử AI chấm (viết hoặc ghi âm) của giáo viên xem trong trang thống kê BTVN Video Ôn tập. Xem
 * {@link ReflexQuestionProgressHistory}.
 */
public record ReflexQuestionProgressHistoryResponse(
        Long questionId,
        String questionPrompt,
        int questionDisplayOrder,
        ReflexQuestionProgressHistory.AttemptType attemptType,
        int attemptNumber,
        String answerText,
        String audioUrl,
        BigDecimal score,
        BigDecimal maxScore,
        String feedback,
        String markedAnswer,
        String transcript,
        List<CriteriaScoreItem> criteriaScores,
        OffsetDateTime gradedAt
) {
}
