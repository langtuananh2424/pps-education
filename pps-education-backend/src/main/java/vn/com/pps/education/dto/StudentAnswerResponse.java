package vn.com.pps.education.dto;

import vn.com.pps.education.common.CriteriaScoreItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * correctChoiceIds/correctAnswerText/correctStructuredContent chỉ được
 * điền khi lượt làm bài đã nộp (không còn IN_PROGRESS) VÀ
 * exercise.showCorrectAnswers=true — xem Javadoc ExerciseAttemptService
 * (UC-24 Main Flow "hiển thị đáp án đúng"). explanation được điền thêm
 * trong điều kiện đó khi: câu KHÔNG tự chấm được (ESSAY/SPEAKING, luôn
 * hiện) HOẶC câu tự chấm được nhưng trả lời SAI (isCorrect=false).
 *
 * structuredAnswer/correctStructuredContent (V85, bổ sung ngoài SDD gốc,
 * đã xác nhận với người dùng 2026-08-04): WORD_BANK/SENTENCE_BUILDING.
 */
public record StudentAnswerResponse(
        Long id,
        Long exerciseAttemptId,
        Long questionId,
        String answerText,
        List<Long> selectedChoiceIds,
        String audioAnswerUrl,
        boolean isAutoGradable,
        BigDecimal autoScore,
        Boolean isCorrect,
        List<Long> correctChoiceIds,
        String correctAnswerText,
        String explanation,
        List<String> structuredAnswer,
        Map<String, Object> correctStructuredContent,
        /**
         * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — điểm/nhận xét câu tự luận/nói
         * (ESSAY/SPEAKING) đã được chấm (tay hoặc AI), lấy từ StudentAnswerGrading mới nhất
         * (latest=true). NULL khi câu tự chấm được (isAutoGradable=true, xem autoScore/isCorrect) HOẶC
         * chưa được chấm. Trước đây học sinh nộp bài tự luận xong không hề thấy lý do đạt/không đạt.
         */
        BigDecimal gradingScore,
        BigDecimal gradingMaxScore,
        String gradingFeedback,
        String gradingSource,
        /**
         * V182 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16, PILOT Khối 7 IELTS) —
         * chính bài viết của học sinh, đánh dấu lỗi bằng markup {@code {{mã|đoạn văn bản}}} — FE tự
         * regex-split để tô màu theo mã lỗi. NULL khi chưa chấm bằng rubric "v3" (vẫn dùng gradingFeedback
         * dạng văn bản như trước).
         */
        String gradingMarkedAnswer,
        /** V182 — % từng tiêu chí rubric v3, tách riêng khỏi gradingFeedback. NULL cùng điều kiện trên. */
        List<CriteriaScoreItem> gradingCriteriaScores,
        /**
         * V177 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-15) — UC-24/UC-27 A2: câu này
         * được mang nguyên nội dung từ lượt làm TRƯỚC (đã đúng) sang lượt "Làm lại" hiện tại — FE phải
         * hiện dạng chỉ xem/khoá, không cho sửa (BE cũng chặn ở saveAnswer nếu cố sửa).
         */
        boolean carriedOverFromPreviousAttempt
) {}
