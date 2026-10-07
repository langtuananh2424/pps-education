package vn.com.pps.education.lms.dto;

import vn.com.pps.education.common.CriteriaScoreItem;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * V139 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22) — UC-23b V2: tiến trình tuần tự
 * (viết → AI chấm ngữ pháp → đạt → ghi âm → AI chấm nội dung → đạt) của 1 câu hỏi. FE tự suy ra khoá/mở
 * câu tiếp theo từ danh sách response này (câu N mở khi mọi câu TRƯỚC đã {@code questionFinalized=true}
 * — KHÔNG dùng {@code questionPassed}: từ 2026-10-07, hết lượt nộp bước nói vẫn mở câu tiếp
 * theo dù câu đó không đạt thật, xem Javadoc {@code questionFinalized} + lớp ReflexSequentialGradingService).
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
        /**
         * V204 (bổ sung ngoài SDD gốc, bản bàn giao 30/9, §D.5 quy tắc chung) — "cách luyện" cho học sinh
         * tự luyện, TÁCH khỏi {@code writingFeedback} (vẫn cấm gợi ý sửa, dành cho giáo viên). NULL khi bài
         * không có lỗi hoặc chưa chấm được bằng rubric có hỗ trợ trường này. FE chỉ hiện từ lần nộp thứ 2
         * trở đi.
         */
        String writingHint,
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
        /** V204 — như {@code writingHint}, cho bước nói. */
        String speakingHint,
        /** V178 — % từng tiêu chí rubric, tách riêng khỏi {@code speakingFeedback}. */
        List<CriteriaScoreItem> speakingCriteriaScores,
        boolean speakingPassed,
        int speakingAttemptCount,
        /** true khi CẢ 2 bước đã đạt THẬT (điểm >= ngưỡng) — KHÁC {@code questionFinalized} (xem field đó). */
        boolean questionPassed,
        /**
         * (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — đã dùng hết {@code writingMaxAttempts} lần nộp bước viết mà vẫn CHƯA đạt
         * ngưỡng %. true thì {@code writingUnlocked} cũng true (bước ghi âm vẫn được mở, chỉ là mở do hết
         * lượt chứ không phải đạt thật) — FE dùng field này để hiện "Hết lượt viết" thay vì "Đạt".
         */
        boolean writingExhausted,
        /** (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — số lần nộp tối đa bước viết (mirror ReflexSequentialGradingService#MAX_STEP_ATTEMPTS). */
        int writingMaxAttempts,
        /**
         * (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — bước viết đã "xong" (đạt thật HOẶC hết lượt) — FE dùng field này (KHÔNG
         * dùng {@code writingPassed}) để quyết định mở khoá panel ghi âm, vì hết lượt cũng phải mở khoá.
         */
        boolean writingUnlocked,
        /** (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — như {@code writingExhausted}, cho bước nói. */
        boolean speakingExhausted,
        /** (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — số lần nộp tối đa bước nói. */
        int speakingMaxAttempts,
        /**
         * (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — câu hỏi đã "xong" (đạt thật CẢ 2 bước HOẶC bước nói hết lượt) — FE dùng
         * field này (KHÔNG dùng {@code questionPassed}) để quyết định mở khoá câu tiếp theo/chạy tiếp video,
         * vì hết lượt bước nói cũng phải cho qua câu, dù câu đó coi là KHÔNG đạt ({@code questionPassed=false}).
         */
        boolean questionFinalized,
        OffsetDateTime updatedAt
) {
}
