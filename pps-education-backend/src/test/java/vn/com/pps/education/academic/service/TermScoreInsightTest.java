package vn.com.pps.education.academic.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-76 bước 4 — phân tích điểm Giữa kỳ/Cuối kỳ thành lời (thuần tính toán). Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
class TermScoreInsightTest {

    private static final TermScoreInsight.Settings SETTINGS = new TermScoreInsight.Settings(85, 50, 10, 10, 0.5);

    private static TermScoreInsight.ComponentScore score(String key, String name, String score, String max) {
        return new TermScoreInsight.ComponentScore(key, name, new BigDecimal(score), new BigDecimal(max), null, false, false);
    }

    private static TermScoreInsight.SkillInsight skill(TermScoreInsight.Insight insight, String name) {
        return insight.skills().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void analyze_UC76_step4a_levelsByPercentOfMaxScore() {
        TermScoreInsight.Insight insight = TermScoreInsight.analyze(List.of(
                        score("READING", "Đọc", "9", "10"),
                        score("LISTENING", "Nghe", "4", "10"),
                        score("WRITING", "Viết", "7", "10")),
                new TermScoreInsight.OverallScore(new BigDecimal("6.7"), BigDecimal.TEN, false), Map.of(), null, SETTINGS);

        assertThat(skill(insight, "Đọc").assessment()).containsExactly(TermScoreInsight.STRONG, TermScoreInsight.RELATIVE_STRONG);
        assertThat(skill(insight, "Nghe").assessment()).containsExactly(TermScoreInsight.WEAK, TermScoreInsight.RELATIVE_WEAK);
        assertThat(skill(insight, "Viết").assessment()).isEmpty();
        assertThat(insight.overall()).isEqualTo("kết quả chung ở mức đạt yêu cầu");
        assertThat(insight.summary()).doesNotContainPattern("\\d");
    }

    @Test
    void analyze_UC76_step4b_belowPassThresholdOverridesPercentLevel() {
        TermScoreInsight.Insight insight = TermScoreInsight.analyze(List.of(new TermScoreInsight.ComponentScore(
                        "SPEAKING", "Nói", new BigDecimal("6"), BigDecimal.TEN, new BigDecimal("6.5"), false, false)),
                null, Map.of(), null, SETTINGS);

        assertThat(skill(insight, "Nói").assessment()).containsExactly(TermScoreInsight.BELOW_PASS);
    }

    @Test
    void analyze_UC76_step4a_bandScaleIsNotLevelledByPercent() {
        TermScoreInsight.Insight insight = TermScoreInsight.analyze(List.of(
                        new TermScoreInsight.ComponentScore("READING", "Reading", new BigDecimal("4"), new BigDecimal("9"), null, false, true)),
                new TermScoreInsight.OverallScore(new BigDecimal("4"), new BigDecimal("9"), true), Map.of(), null, SETTINGS);

        assertThat(skill(insight, "Reading").assessment()).isEmpty();
        assertThat(insight.overall()).isNull();
    }

    @Test
    void analyze_UC76_step4d_endTermTrendAgainstMidTermByKey() {
        TermScoreInsight.Insight insight = TermScoreInsight.analyze(List.of(
                        score("SPEAKING", "Nói", "8", "10"),
                        score("READING", "Đọc", "6", "10"),
                        score("WRITING", "Viết", "7", "10")),
                new TermScoreInsight.OverallScore(new BigDecimal("7"), BigDecimal.TEN, false),
                Map.of("SPEAKING", score("SPEAKING", "Speaking GK", "6", "10"),
                        "READING", score("READING", "Đọc", "7.5", "10"),
                        "WRITING", score("WRITING", "Viết", "6.5", "10")),
                new TermScoreInsight.OverallScore(new BigDecimal("7"), BigDecimal.TEN, false), SETTINGS);

        assertThat(insight.trend()).containsExactly("Nói tiến bộ rõ so với Giữa kỳ", "Đọc giảm so với Giữa kỳ");
    }

    @Test
    void analyze_UC76_step4e_absentPartsAreExcludedFromSkills() {
        TermScoreInsight.Insight insight = TermScoreInsight.analyze(List.of(
                        new TermScoreInsight.ComponentScore("LISTENING", "Nghe", BigDecimal.ZERO, BigDecimal.TEN, null, true, false)),
                null, Map.of(), null, SETTINGS);

        assertThat(insight.skills()).isEmpty();
        assertThat(insight.absentParts()).containsExactly("Nghe");
        assertThat(insight.hasData()).isFalse();
    }
}
