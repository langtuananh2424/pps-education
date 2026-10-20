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
        Boolean recordingFilter,
        /** reflex_question_progress.id của câu hỏi này — dùng để gọi chấm lại điểm chính thức. */
        Long progressId,
        /**
         * Lần này đang là điểm CHÍNH THỨC của bước (viết/nói) của câu hỏi — mặc định là lần nộp MỚI NHẤT, hoặc lần
         * giáo viên đã chọn (V217); các lần còn lại chỉ để xem lịch sử, không tính.
         */
        boolean official,
        /** Giáo viên đã chấm lại điểm chính thức (chỉ có thể true ở lần official, V216). */
        boolean reviewed,
        /** Điểm AI gốc trước khi giáo viên chấm lại; null khi chưa chấm lại. Khi reviewed, score/feedback là của giáo viên. */
        BigDecimal aiScore,
        String aiFeedback,
        String reviewerName,
        OffsetDateTime reviewedAt,
        /** V217 — giáo viên đã chọn lần này làm kết quả chính thức (khác mặc định "lần mới nhất"). */
        boolean selectedByTeacher
) {
}
