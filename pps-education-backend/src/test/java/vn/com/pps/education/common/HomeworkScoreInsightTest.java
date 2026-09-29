package vn.com.pps.education.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/** UC-74 — quy đổi điểm BTVN buổi trước ra lời theo ngưỡng đã chốt (≥80 / 50–79 / <50 / chưa làm). */
class HomeworkScoreInsightTest {

    @Test
    void levelOf_UC74_appliesAgreedThresholds() {
        assertThat(HomeworkScoreInsight.levelOf("80%")).isEqualTo(HomeworkScoreInsight.Level.GOOD);
        assertThat(HomeworkScoreInsight.levelOf("79%")).isEqualTo(HomeworkScoreInsight.Level.OK);
        assertThat(HomeworkScoreInsight.levelOf("50")).isEqualTo(HomeworkScoreInsight.Level.OK);
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
    void describe_UC74_onlyNotableChannelsAndNoNumbers() {
        String note = HomeworkScoreInsight.describe(List.of(
                new HomeworkScoreInsight.Channel("bài tập online", "90%"),
                new HomeworkScoreInsight.Channel("video ôn tập", "60%"),
                new HomeworkScoreInsight.Channel("bài Writing offline", "Chưa làm bài")),
                OptionalInt.empty(), OptionalInt.empty(), 20);

        assertThat(note).isEqualTo("BTVN buổi trước: làm tốt (bài tập online); chưa hoàn thành (bài Writing offline).");
    }

    @Test
    void describe_UC74_nothingNotableReturnsNull() {
        assertThat(HomeworkScoreInsight.describe(List.of(new HomeworkScoreInsight.Channel("bài tập online", "65%")),
                OptionalInt.of(65), OptionalInt.of(60), 20)).isNull();
    }

    @Test
    void describe_UC74_clearTrendIsMentioned() {
        assertThat(HomeworkScoreInsight.describe(List.of(new HomeworkScoreInsight.Channel("bài tập offline", "70%")),
                OptionalInt.of(70), OptionalInt.of(40), 20)).isEqualTo("BTVN buổi trước: tiến bộ rõ so với buổi trước.");
        assertThat(HomeworkScoreInsight.describe(List.of(new HomeworkScoreInsight.Channel("bài tập offline", "55%")),
                OptionalInt.of(55), OptionalInt.of(90), 20)).isEqualTo("BTVN buổi trước: giảm rõ so với buổi trước.");
    }
}
