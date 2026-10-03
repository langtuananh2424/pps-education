package vn.com.pps.education.lms.dto;

import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.lms.domain.ReflexQuestionProgressHistory;

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
        /** V204 — "cách luyện" (§D.5, bản 30/9) của CHÍNH lần chấm này; null = trước V204 hoặc rubric không hỗ trợ. */
        String hint,
        String transcript,
        List<CriteriaScoreItem> criteriaScores,
        OffsetDateTime gradedAt,
        /** V198 — lần ghi âm cần giáo viên soát điểm Ngữ pháp (≥2 lỗi đỏ ngữ pháp ở nhánh chấm lại từ transcript). */
        boolean grammarReviewRequired,
        /** V198 — các đoạn transcript bị tô đỏ ngữ pháp; rỗng khi không cần soát. */
        List<String> grammarReviewQuotes,
        /** V199 — true = bản ghi đã lọc, false = thô, null = không rõ (trước V199 / bài viết). */
        Boolean recordingFilter
) {
}
