package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

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
        String writingFeedback,
        boolean writingPassed,
        int writingAttemptCount,
        String audioUrl,
        Integer speakingScorePercent,
        String speakingFeedback,
        boolean speakingPassed,
        int speakingAttemptCount,
        /** true khi CẢ 2 bước đã đạt — câu tiếp theo được mở khoá. */
        boolean questionPassed,
        OffsetDateTime updatedAt
) {
}
