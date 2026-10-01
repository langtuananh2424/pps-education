package vn.com.pps.education.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/** UC-74 — quy đổi điểm BTVN buổi trước ra lời theo từng kỹ năng, ngưỡng đã chốt (≥85 / 51–84 / ≤50 / chưa làm). */
class HomeworkScoreInsightTest {

    @Test
    void levelOf_UC74_appliesAgreedThresholds() {
        assertThat(HomeworkScoreInsight.levelOf("85%")).isEqualTo(HomeworkScoreInsight.Level.GOOD);
        assertThat(HomeworkScoreInsight.levelOf("84%")).isEqualTo(HomeworkScoreInsight.Level.OK);
        assertThat(HomeworkScoreInsight.levelOf("51%")).isEqualTo(HomeworkScoreInsight.Level.OK);
        assertThat(HomeworkScoreInsight.levelOf("50")).isEqualTo(HomeworkScoreInsight.Level.LOW);
        assertThat(HomeworkScoreInsight.levelOf("5/10")).isEqualTo(HomeworkScoreInsight.Level.LOW);
        assertThat(HomeworkScoreInsight.levelOf("4/10")).isEqualTo(HomeworkScoreInsight.Level.LOW);
        assertThat(HomeworkScoreInsight.levelOf("Chưa làm bài")).isEqualTo(HomeworkScoreInsight.Level.NOT_DONE);
    }

    @Test
    void levelOf_UC74_skipsPendingOrUnreadable() {
        assertThat(HomeworkScoreInsight.levelOf("Đang chờ chấm")).isNull();
        assertThat(HomeworkScoreInsight.levelOf("Unit 2 trang 18")).isNull();
        assertThat(HomeworkScoreInsight.levelOf(" ")).isNull();
    }

    @Test
    void channelSkill_UC74_dependsOnSessionTeacherType() {
        assertThat(HomeworkScoreInsight.mainChannelSkill(true)).isEqualTo("nghe");
        assertThat(HomeworkScoreInsight.mainChannelSkill(false)).isEqualTo("ngữ pháp");
        assertThat(HomeworkScoreInsight.videoChannelSkill(true)).isEqualTo("phản xạ nói");
        assertThat(HomeworkScoreInsight.videoChannelSkill(false)).isEqualTo("từ vựng");
    }

    @Test
    void describe_UC74_namesEachNotableSkillWithoutNumbers() {
        String note = HomeworkScoreInsight.describe(List.of(
                new HomeworkScoreInsight.Channel("nghe", "bài online", "40%"),
                new HomeworkScoreInsight.Channel("phản xạ nói", "video ôn tập", "60%"),
                new HomeworkScoreInsight.Channel("đọc", "bài online", "90%"),
                new HomeworkScoreInsight.Channel("viết", "bài trên giấy", "Chưa làm bài")),
                false, OptionalInt.empty(), OptionalInt.empty(), 20);

        assertThat(note).isEqualTo("BTVN buổi trước theo kỹ năng: nghe — cần cố gắng; đọc — làm tốt; viết — chưa hoàn thành.");
        assertThat(note).doesNotContainPattern("\\d");
    }

    @Test
    void describe_UC74_sameSkillDifferentResultsShowsSource() {
        String note = HomeworkScoreInsight.describe(List.of(
                new HomeworkScoreInsight.Channel("đọc", "bài online", "95%"),
                new HomeworkScoreInsight.Channel("đọc", "bài trên giấy", "Chưa làm bài"),
                new HomeworkScoreInsight.Channel("viết", "bài online", "30%"),
                new HomeworkScoreInsight.Channel("viết", "bài trên giấy", "2/10")),
                false, OptionalInt.empty(), OptionalInt.empty(), 20);

        assertThat(note).isEqualTo("BTVN buổi trước theo kỹ năng: đọc — làm tốt (bài online), chưa hoàn thành (bài trên giấy); "
                + "viết — cần cố gắng.");
    }

    @Test
    void describe_UC74_nothingNotableReturnsNull() {
        assertThat(HomeworkScoreInsight.describe(List.of(new HomeworkScoreInsight.Channel("ngữ pháp", "bài online", "80%")),
                false, OptionalInt.of(80), OptionalInt.of(70), 20)).isNull();
    }

    @Test
    void describe_UC75_includeOkListsEveryReadableSkill() {
        assertThat(HomeworkScoreInsight.describe(List.of(new HomeworkScoreInsight.Channel("ngữ pháp", "bài tập", "80%")),
                true, OptionalInt.empty(), OptionalInt.empty(), 0))
                .isEqualTo("BTVN buổi trước theo kỹ năng: ngữ pháp — làm được, cần cẩn thận hơn.");
    }

    @Test
    void describe_UC74_clearTrendIsMentioned() {
        assertThat(HomeworkScoreInsight.describe(List.of(new HomeworkScoreInsight.Channel("ngữ pháp", "bài trên giấy", "70%")),
                false, OptionalInt.of(70), OptionalInt.of(40), 20))
                .isEqualTo("BTVN buổi trước theo kỹ năng: nhìn chung tiến bộ rõ so với buổi trước.");
        assertThat(HomeworkScoreInsight.describe(List.of(new HomeworkScoreInsight.Channel("ngữ pháp", "bài trên giấy", "55%")),
                false, OptionalInt.of(55), OptionalInt.of(90), 20))
                .isEqualTo("BTVN buổi trước theo kỹ năng: nhìn chung giảm rõ so với buổi trước.");
    }
}
