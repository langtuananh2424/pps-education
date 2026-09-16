package vn.com.pps.education.dto;

import vn.com.pps.education.common.CriteriaScoreItem;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * V139 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22) — UC-23b V2: tiến trình tuần tự
 * (viết → AI chấm ngữ pháp → đạt → ghi âm → AI chấm nội dung → đạt) của 1 câu hỏi. FE tự suy ra khoá/mở
 * câu tiếp theo từ danh sách response này (câu N mở khi mọi câu TRƯỚC đã {@code questionPassed=true}).
 *
 * writingScorePercent/speakingScorePercent NULL = CHƯA nộp hoặc AI chấm lỗi (xem
 * writingFeedback/speakingFeedback để phân biệt — "Không chấm được tự động..." nghĩa là lỗi, còn null
 * kèm answerText/audioUrl cũng null nghĩa là chưa nộp).
 */
public record ReflexQuestionProgressResponse(
        Long questionId,
        String answerText,
        Integer writingScorePercent,
        /** V181 — nay CHỈ có giá trị khi AI chấm thất bại (thông báo lỗi); chấm thành công xem {@code writingMarkedAnswer}. */
        String writingFeedback,
        /**
         * V181 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16) — chính câu trả lời của
         * học sinh, đánh dấu lỗi bằng markup {@code {{err}}...{{/err}}} — FE tự regex-split để bôi
         * đỏ/gạch chân, thay cho feedback văn xuôi dài dòng cũ. NULL khi chưa nộp/chưa chấm được.
         */
        String writingMarkedAnswer,
        boolean writingPassed,
        int writingAttemptCount,
        /**
         * V141 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — gợi ý câu trả lời đã
         * sửa lỗi ngữ pháp (CHỈ sửa lỗi trong câu học sinh viết, không phải câu mẫu tự bịa). NULL khi
         * đã đạt (không cần gợi ý sửa) hoặc chưa nộp/chưa chấm được — FE chỉ hiện ra khi
         * writingAttemptCount >= 3 VÀ vẫn chưa đạt.
         */
        String writingCorrectedAnswer,
        String audioUrl,
        Integer speakingScorePercent,
        String speakingFeedback,
        /**
         * V178 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16) — transcript audio, có
         * đánh dấu lỗi bằng markup {@code {{err}}...{{/err}}} — FE tự regex-split để bôi đỏ/gạch chân.
         */
        String speakingTranscript,
        /** V178 — % từng tiêu chí rubric, tách riêng khỏi {@code speakingFeedback}. */
        List<CriteriaScoreItem> speakingCriteriaScores,
        boolean speakingPassed,
        int speakingAttemptCount,
        /** true khi CẢ 2 bước đã đạt — câu tiếp theo được mở khoá. */
        boolean questionPassed,
        OffsetDateTime updatedAt
) {
}
