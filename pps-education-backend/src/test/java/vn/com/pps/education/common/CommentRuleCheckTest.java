package vn.com.pps.education.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** UC-74/UC-75: kiểm tra theo quy tắc dùng chung cho trợ lý soạn nháp và trợ lý duyệt. */
class CommentRuleCheckTest {

    @Test
    void bestMatch_UC74_returnsMostSimilarCandidateWithItsLabel() {
        CommentRuleCheck.Match match = CommentRuleCheck.bestMatch("Hôm nay con tập trung rất tốt trong giờ học.",
                List.of(Map.entry("An", "Con hăng hái phát biểu."), Map.entry("Bình", "Hôm nay con tập trung rất tốt trong giờ học.")),
                Map.Entry::getValue, Map.Entry::getKey);

        assertThat(match.source()).isEqualTo("Bình");
        assertThat(match.similarity()).isEqualTo(1.0);
        assertThat(match.atLeast(0.5)).isTrue();
        assertThat(CommentRuleCheck.similarInSessionMessage(match)).isEqualTo("Giống nhận xét của Bình 100%.");
    }

    @Test
    void bestMatch_UC74_noCandidateIsNeverAboveThreshold() {
        CommentRuleCheck.Match match = CommentRuleCheck.bestMatch("Con học tốt.", List.<String>of(), s -> s, s -> s);

        assertThat(match.source()).isNull();
        assertThat(match.atLeast(0.0)).isFalse();
    }

    @Test
    void containsDigitsAndLessonTitle_UC75_ruleChecks() {
        assertThat(CommentRuleCheck.containsDigits("Con được 8 điểm.")).isTrue();
        assertThat(CommentRuleCheck.containsDigits("Con được tám điểm.")).isFalse();
        assertThat(CommentRuleCheck.mentionsLessonTitle("Bài Unit 1: Hello Friend con học tốt.", "Unit 1 - Hello Friend")).isTrue();
        assertThat(CommentRuleCheck.mentionsLessonTitle("Con học tốt.", "Unit 1 - Hello Friend")).isFalse();
        assertThat(CommentRuleCheck.mentionsLessonTitle("Con học tốt.", "U1")).isFalse();
    }
}
