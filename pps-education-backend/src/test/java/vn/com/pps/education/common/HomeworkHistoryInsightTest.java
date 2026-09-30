package vn.com.pps.education.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** UC-74 (bổ sung 2026-09-30) — thống kê BTVN nhiều buổi quy ra lời theo quy tắc đã chốt. */
class HomeworkHistoryInsightTest {

    private static HomeworkHistoryInsight.SkillResult skill(String name, Integer percent) {
        return new HomeworkHistoryInsight.SkillResult(name, percent, false);
    }

    private static HomeworkHistoryInsight.SkillResult notDone(String name) {
        return new HomeworkHistoryInsight.SkillResult(name, null, true);
    }

    private static HomeworkHistoryInsight.Round round(HomeworkHistoryInsight.SkillResult... skills) {
        return new HomeworkHistoryInsight.Round(List.of(skills), false);
    }

    private static HomeworkHistoryInsight.Round lateRound(HomeworkHistoryInsight.SkillResult... skills) {
        return new HomeworkHistoryInsight.Round(List.of(skills), true);
    }

    private static List<String> describe(List<HomeworkHistoryInsight.Round> rounds) {
        return HomeworkHistoryInsight.describe(new HomeworkHistoryInsight.Input(rounds, false, List.of()));
    }

    @Test
    void describe_UC74_trendUpWhenThreeRoundsRiseByAtLeastTwentyPoints() {
        // Mới nhất trước: 80 ← 65 ← 50.
        assertThat(describe(List.of(round(skill("nghe", 80)), round(skill("nghe", 65)), round(skill("nghe", 50)))))
                .containsExactly("kỹ năng nghe tiến bộ đều qua các buổi gần đây");
    }

    @Test
    void describe_UC74_trendDownWhenThreeRoundsFallByAtLeastTwentyPoints() {
        assertThat(describe(List.of(round(skill("đọc", 55)), round(skill("đọc", 70)), round(skill("đọc", 90)))))
                .containsExactly("kỹ năng đọc đi xuống qua các buổi gần đây");
    }

    @Test
    void describe_UC74_noTrendWhenNotMonotonicOrBelowTwentyPointsOrMissingRound() {
        assertThat(describe(List.of(round(skill("nghe", 80)), round(skill("nghe", 40)), round(skill("nghe", 60))))).isEmpty();
        assertThat(describe(List.of(round(skill("nghe", 69)), round(skill("nghe", 60)), round(skill("nghe", 50))))).isEmpty();
        assertThat(describe(List.of(round(skill("nghe", 90)), round(skill("nghe", 60))))).isEmpty();
    }

    @Test
    void describe_UC74_sameSkillNotDoneTwiceInARowIsReminded() {
        assertThat(describe(List.of(round(notDone("viết"), skill("đọc", 70)), round(notDone("viết")))))
                .containsExactly("chưa làm BTVN kỹ năng viết hai lần liền (nhắc nhẹ hoàn thành bài)");
        // Khác kỹ năng thì không nhắc.
        assertThat(describe(List.of(round(notDone("viết")), round(notDone("đọc"))))).isEmpty();
    }

    @Test
    void describe_UC74_lateAtLeastTwiceInLastThreeRoundsIsReminded() {
        assertThat(describe(List.of(lateRound(skill("nghe", 70)), round(skill("nghe", 70)), lateRound(skill("nghe", 70)))))
                .containsExactly("nộp BTVN muộn nhiều lần gần đây (nhắc nhẹ nộp đúng hạn)");
        assertThat(describe(List.of(lateRound(skill("nghe", 70)), round(skill("nghe", 70)), round(skill("nghe", 70))))).isEmpty();
    }

    @Test
    void describe_UC74_regularWhenFourRoundsAllComplete() {
        HomeworkHistoryInsight.Round done = round(skill("ngữ pháp", 70));
        assertThat(describe(List.of(done, done, done, done))).containsExactly("làm đủ BTVN đều đặn nhiều buổi liên tiếp");
        assertThat(describe(List.of(done, done, round(notDone("ngữ pháp")), done))).isEmpty();
        assertThat(describe(List.of(done, done, done))).isEmpty();
    }

    @Test
    void describe_UC74_retryImprovedIsPraised() {
        assertThat(HomeworkHistoryInsight.describe(new HomeworkHistoryInsight.Input(List.of(), true, List.of())))
                .containsExactly("chăm làm lại bài để cải thiện điểm");
    }

    @Test
    void describe_UC74_onlyWeakestPointWhenAnyAtOrBelowFifty() {
        List<String> result = HomeworkHistoryInsight.describe(new HomeworkHistoryInsight.Input(List.of(), false, List.of(
                new HomeworkHistoryInsight.Point("dạng câu điền từ trong bài ngữ pháp", 40, true),
                new HomeworkHistoryInsight.Point("tiêu chí \"Phát âm\" trong bài nói", 30, true),
                new HomeworkHistoryInsight.Point("dạng câu trắc nghiệm trong bài ngữ pháp", 95, true))));

        assertThat(result).containsExactly("điểm cần cải thiện cụ thể: tiêu chí \"Phát âm\" trong bài nói");
    }

    @Test
    void describe_UC74_strongestPraisablePointWhenNoWeakPoint() {
        List<String> result = HomeworkHistoryInsight.describe(new HomeworkHistoryInsight.Input(List.of(), false, List.of(
                new HomeworkHistoryInsight.Point("dạng câu trắc nghiệm trong bài đọc", 100, false),
                new HomeworkHistoryInsight.Point("tiêu chí \"Từ vựng\" trong bài viết", 90, true),
                new HomeworkHistoryInsight.Point("câu khó trong bài đọc", 70, true))));

        assertThat(result).containsExactly("làm tốt tiêu chí \"Từ vựng\" trong bài viết");
    }

    @Test
    void describe_UC74_ordersRemindersThenWeakPointThenTrendThenPraise() {
        List<String> result = HomeworkHistoryInsight.describe(new HomeworkHistoryInsight.Input(List.of(
                lateRound(notDone("viết"), skill("nghe", 80)),
                lateRound(notDone("viết"), skill("nghe", 65)),
                round(skill("nghe", 50))), true, List.of(
                new HomeworkHistoryInsight.Point("câu khó trong bài nghe", 20, true))));

        assertThat(result).containsExactly(
                "chưa làm BTVN kỹ năng viết hai lần liền (nhắc nhẹ hoàn thành bài)",
                "nộp BTVN muộn nhiều lần gần đây (nhắc nhẹ nộp đúng hạn)",
                "điểm cần cải thiện cụ thể: câu khó trong bài nghe",
                "kỹ năng nghe tiến bộ đều qua các buổi gần đây",
                "chăm làm lại bài để cải thiện điểm");
        assertThat(String.join(" ", result)).doesNotContainPattern("\\d");
    }
}
