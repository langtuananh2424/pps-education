package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.Test;
import vn.com.pps.education.lms.domain.ReflexQuestionProgress;
import vn.com.pps.education.lms.domain.ReviewVideoQuestion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V213 — "đạt cả bài" REFLEX theo ngưỡng cấu hình: điểm TB (viết + nói mọi câu) >= ngưỡng, phải nộp đủ
 * mọi câu; KHÔNG còn bắt buộc từng câu đạt (trường hợp thật: TB 76.39 nhưng 1 câu dưới ngưỡng).
 */
class ReviewVideoReportServiceOverallPassTest {

    private static ReviewVideoQuestion question(long id) {
        ReviewVideoQuestion q = new ReviewVideoQuestion();
        q.setId(id);
        return q;
    }

    private static ReflexQuestionProgress progress(String writing, String speaking) {
        ReflexQuestionProgress p = new ReflexQuestionProgress();
        p.setWritingScore(writing == null ? null : new BigDecimal(writing));
        p.setSpeakingScore(speaking == null ? null : new BigDecimal(speaking));
        return p;
    }

    private static boolean passed(List<ReviewVideoQuestion> qs, Map<String, ReflexQuestionProgress> progress, int threshold) {
        return ReviewVideoReportService.isReflexVideoOverallPassed(qs, 7L, progress, threshold);
    }

    @Test
    void averageAboveThresholdPassesEvenIfOneQuestionIsBelowIt() {
        List<ReviewVideoQuestion> qs = List.of(question(1), question(2));
        Map<String, ReflexQuestionProgress> progress = Map.of(
                "1:7", progress("100", "90"),
                "2:7", progress("40", "75"));
        // TB = (100 + 90 + 40 + 75) / 4 = 76.25
        assertTrue(passed(qs, progress, 70));
    }

    @Test
    void averageBelowThresholdFails() {
        List<ReviewVideoQuestion> qs = List.of(question(1), question(2));
        Map<String, ReflexQuestionProgress> progress = Map.of(
                "1:7", progress("100", "90"),
                "2:7", progress("40", "75"));
        assertFalse(passed(qs, progress, 80));
    }

    @Test
    void exactlyAtThresholdPasses() {
        List<ReviewVideoQuestion> qs = List.of(question(1));
        assertTrue(passed(qs, Map.of("1:7", progress("70", "70")), 70));
    }

    @Test
    void missingQuestionProgressFails() {
        List<ReviewVideoQuestion> qs = List.of(question(1), question(2));
        assertFalse(passed(qs, Map.of("1:7", progress("100", "100")), 50));
    }

    @Test
    void noGradedScoreFails() {
        List<ReviewVideoQuestion> qs = List.of(question(1));
        assertFalse(passed(qs, Map.of("1:7", progress(null, null)), 1));
    }
}
