package vn.com.pps.education.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.domain.Curriculum;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm tra tính điểm tất định của bộ tiêu chí Speaking v2 (xem ReflexV2Scoring) — dùng lại các ví dụ
 * số của người training trong {@code ma-nguon-tham-chieu/vi-du-dau-vao.md}.
 */
class ReflexV2ScoringTest {

    private static final ReflexV2Task G6 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_6, null, 20).orElseThrow();
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    @Test
    void computeScores_trainerExampleStep1_gv30() throws Exception {
        JsonNode data = json("{\"insufficient_data\":false,\"gates_triggered\":[],\"criteria\":[{\"code\":\"GV\",\"checkpoints\":[1,0,0,0.5,0],\"cap_percent\":100}]}");
        ReflexV2Scoring.ScoreSet set = ReflexV2Scoring.computeScores(G6, List.of("GV"), data);
        assertThat(set.criteria().get(0).percent()).isEqualTo(30);
        assertThat(set.finalPercent()).isEqualTo(30);
    }

    @Test
    void computeScores_capAppliesAsMinimum() throws Exception {
        JsonNode data = json("{\"criteria\":[{\"code\":\"P\",\"checkpoints\":[1,1,1,1,1],\"cap_percent\":40}],\"gates_triggered\":[\"C2\"]}");
        ReflexV2Scoring.ScoreSet set = ReflexV2Scoring.computeScores(G6, List.of("P"), data);
        assertThat(set.criteria().get(0).percent()).isEqualTo(40);
        assertThat(set.gates()).containsExactly("C2");
    }

    @Test
    void computeScores_insufficientDataGivesZero() throws Exception {
        JsonNode data = json("{\"insufficient_data\":true,\"criteria\":[]}");
        assertThat(ReflexV2Scoring.computeScores(G6, List.of("GV"), data).finalPercent()).isZero();
    }

    @Test
    void computeScores_missingCriterionOrWrongCheckpointCount_isRejectedNotPadded() {
        assertThatThrownBy(() -> ReflexV2Scoring.computeScores(G6, List.of("GV"), json("{\"criteria\":[]}")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ReflexV2Scoring.computeScores(G6, List.of("GV"),
                json("{\"criteria\":[{\"code\":\"GV\",\"checkpoints\":[1,1,1,1],\"cap_percent\":100}]}")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void average_trainerExampleFinal40() {
        List<ReflexV2Scoring.CriterionScore> all = List.of(
                new ReflexV2Scoring.CriterionScore("GV", 30, false),
                new ReflexV2Scoring.CriterionScore("DM", 50, false),
                new ReflexV2Scoring.CriterionScore("P", 50, false));
        // (30+50+50)/3 = 43.33 -> làm tròn XUỐNG bội 5 = 40
        assertThat(ReflexV2Scoring.average(all)).isEqualTo(40);
    }

    @Test
    void applyRedCap_capsGrammarOnlyFromTwoRedErrorsAndNeverRaises() {
        ReflexV2Scoring.ScoreSet high = new ReflexV2Scoring.ScoreSet(
                List.of(new ReflexV2Scoring.CriterionScore("LR", 100, false), new ReflexV2Scoring.CriterionScore("GRA", 100, false)),
                100, List.of());
        assertThat(ReflexV2Scoring.applyRedCap("GRA", high, 1)).isSameAs(high);
        ReflexV2Scoring.ScoreSet capped = ReflexV2Scoring.applyRedCap("GRA", high, 2);
        assertThat(capped.criteria().get(1).percent()).isEqualTo(60);
        assertThat(capped.criteria().get(1).cappedByRedErrors()).isTrue();
        assertThat(capped.criteria().get(0).percent()).isEqualTo(100);
        assertThat(capped.finalPercent()).isEqualTo(80);

        ReflexV2Scoring.ScoreSet low = new ReflexV2Scoring.ScoreSet(
                List.of(new ReflexV2Scoring.CriterionScore("GV", 30, false)), 30, List.of());
        assertThat(ReflexV2Scoring.applyRedCap("GV", low, 5).criteria().get(0).percent()).isEqualTo(30);
    }

    @Test
    void locateHighlights_levelComesFromTag_andDropsUnknownOrMissing() throws Exception {
        String text = "At break time I usually eat snack with my friend. Sometime we play đá cầu in the yard. I don't like stay in class because it hot.";
        JsonNode hs = json("[{\"quote\":\"đá cầu\",\"occurrence\":1,\"level\":\"yellow\",\"tag\":\"tieng_viet\"},"
                + "{\"quote\":\"it hot\",\"occurrence\":1,\"level\":\"yellow\",\"tag\":\"thieu_thanh_phan\"},"
                + "{\"quote\":\"Sometime\",\"occurrence\":1,\"level\":\"red\",\"tag\":\"chinh_ta\"},"
                + "{\"quote\":\"because\",\"occurrence\":1,\"level\":\"red\",\"tag\":\"tu_noi\"},"
                + "{\"quote\":\"friend\",\"occurrence\":1,\"level\":\"red\",\"tag\":\"khong_co_tag_nay\"},"
                + "{\"quote\":\"khong co trong bai\",\"occurrence\":1,\"level\":\"red\",\"tag\":\"dung_tu\"}]");
        List<ReflexV2Scoring.Highlight> found = ReflexV2Scoring.locateHighlights(text, hs);
        // Theo thứ tự vị trí trong bài: Sometime (vàng, chinh_ta), đá cầu (đỏ, tieng_viet), because (xanh), it hot (đỏ)
        assertThat(found).extracting(ReflexV2Scoring.Highlight::level).containsExactly("yellow", "red", "green", "red");
        assertThat(ReflexV2Scoring.countRed(found)).isEqualTo(2);
        String marked = ReflexV2Scoring.toErrMarkup(text, found);
        assertThat(marked).contains("{{err}}đá cầu{{/err}}").contains("{{err}}it hot{{/err}}").contains("{{err}}Sometime{{/err}}");
        assertThat(marked).doesNotContain("{{err}}because");
        // bỏ hết markup thì phải ra đúng bài gốc — không được sửa/thêm/bớt chữ nào
        assertThat(marked.replace("{{err}}", "").replace("{{/err}}", "")).isEqualTo(text);
    }

    @Test
    void locateHighlights_overlappingIsDropped() throws Exception {
        String text = "I want buy a book";
        JsonNode hs = json("[{\"quote\":\"want buy\",\"occurrence\":1,\"level\":\"red\",\"tag\":\"cau_truc_cau\"},"
                + "{\"quote\":\"buy a\",\"occurrence\":1,\"level\":\"red\",\"tag\":\"mao_tu\"}]");
        assertThat(ReflexV2Scoring.locateHighlights(text, hs)).hasSize(1);
    }

    @Test
    void locateHighlights_usesOccurrence() throws Exception {
        String text = "I go go home";
        JsonNode hs = json("[{\"quote\":\"go\",\"occurrence\":2,\"level\":\"yellow\",\"tag\":\"lap_lai\"}]");
        List<ReflexV2Scoring.Highlight> found = ReflexV2Scoring.locateHighlights(text, hs);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).start()).isEqualTo(5);
    }

    @Test
    void trimFeedback_stripsMarkdownAndCapsAt50Words() {
        assertThat(ReflexV2Scoring.trimFeedback("**Em** nói tốt. Lỗi nặng nhất: thiếu động từ.")).isEqualTo("Em nói tốt. Lỗi nặng nhất: thiếu động từ.");
        String longText = "từ ".repeat(60).trim();
        String trimmed = ReflexV2Scoring.trimFeedback(longText);
        assertThat(trimmed).endsWith("…");
        assertThat(trimmed.split("\\s+")).hasSize(50);
    }

    @Test
    void buildGateNote_tooShort_mentionsThresholdAndCount() {
        ReflexV2Task task = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS, 25).orElseThrow();
        String note = ReflexV2Scoring.buildGateNote(task, List.of("C3"), 6);
        assertThat(note).startsWith("Yêu cầu:").contains("15 từ").contains("hiện có 6 từ");
        assertThat(ReflexV2Scoring.buildGateNote(task, List.of(), 6)).isEmpty();
        assertThat(ReflexV2Scoring.buildGateNote(task, List.of("C2"), 6)).isEmpty();
    }

    @Test
    void buildGateNote_spokenSaysSpeakMore_writtenSaysWriteMore() {
        ReflexV2Task task = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS, 25).orElseThrow();
        String spoken = ReflexV2Scoring.buildGateNote(task, List.of("C3"), 2, true);
        assertThat(spoken).contains("Bài nói cần tối thiểu 15 từ").contains("hiện có 2 từ").contains("hãy nói thêm").doesNotContain("viết");
        assertThat(ReflexV2Scoring.buildGateNote(task, List.of("C3"), 2, false)).contains("hãy viết thêm");
    }

    @Test
    void task_routingByGradeAndTrack() {
        assertThat(ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_6, null, 25)).isPresent();
        assertThat(ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_7, null, 25)).isEmpty();
        assertThat(ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.CAMBRIDGE, 25).orElseThrow().criteria())
                .containsExactly("GV", "DM", "P");
        assertThat(ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.CAMBRIDGE, 25)).isEmpty();
        assertThat(ReflexV2Task.forGradeTrack(null, null, 25)).isEmpty();
    }

    // ---------- Khối 8-9 (bộ v2 mở rộng) ----------

    @Test
    void task_grade8And9_routingAndShortVsPart2ByRecordingSeconds() {
        ReflexV2Task g8Short = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.IELTS, 30).orElseThrow();
        ReflexV2Task g8Part2 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.IELTS, 90).orElseThrow();
        assertThat(g8Short.id()).isEqualTo("g8-ielts-short");
        assertThat(g8Short.rubricFormat()).isEqualTo("SHORT");
        assertThat(g8Part2.id()).isEqualTo("g8-ielts-part2");
        assertThat(g8Part2.rubricFormat()).isEqualTo("PART2");
        assertThat(g8Part2.seconds()).isEqualTo(90);
        // cùng cặp file rubric cho SHORT và PART2
        assertThat(g8Short.speakingRubricFile()).isEqualTo(g8Part2.speakingRubricFile());

        ReflexV2Task g8Cam = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.CAMBRIDGE, 60).orElseThrow();
        assertThat(g8Cam.id()).isEqualTo("g8-cam-pet4");
        assertThat(g8Cam.rubricFormat()).isNull();
        assertThat(g8Cam.criteria()).containsExactly("GV", "DM", "P");

        assertThat(ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 90).orElseThrow().id())
                .isEqualTo("g9-ielts-part2");
        assertThat(ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 30).orElseThrow().minWords()).isEqualTo(18);
        // Khối 9 CAMBRIDGE chưa có bộ v2 → luồng cũ
        assertThat(ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.CAMBRIDGE, 60)).isEmpty();
        assertThat(ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_8, null, 30)).isEmpty();
    }

    @Test
    void gateScheme_v3_ignoresC3_andC1MeansTooShortC2MeansOffTopic() throws Exception {
        ReflexV2Task g8 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.IELTS, 30).orElseThrow();
        JsonNode data = json("{\"criteria\":[{\"code\":\"LR\",\"checkpoints\":[1,1,1,1,1],\"cap_percent\":40}],"
                + "\"gates_triggered\":[\"C1\",\"C3\"]}");
        ReflexV2Scoring.ScoreSet set = ReflexV2Scoring.computeScores(g8, List.of("LR"), data);
        assertThat(set.gates()).containsExactly("C1");

        String tooShort = ReflexV2Scoring.buildGateNote(g8, List.of("C1"), 9);
        assertThat(tooShort).startsWith("Yêu cầu:").contains("15 từ").contains("hiện có 9 từ");
        String offTopic = ReflexV2Scoring.buildGateNote(g8, List.of("C2"), 30);
        assertThat(offTopic).contains("đúng trọng tâm");
        assertThat(offTopic).doesNotContain("tối thiểu");

        // STANDARD (Khối 7): C3 = quá ngắn, C1 = lạc đề — ngược với V3
        ReflexV2Task g7 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS, 25).orElseThrow();
        assertThat(ReflexV2Scoring.buildGateNote(g7, List.of("C1"), 9)).contains("đúng trọng tâm");
        assertThat(ReflexV2Scoring.buildGateNote(g7, List.of("C3"), 9)).contains("15 từ");
        // PART2 yêu cầu dài hơn
        ReflexV2Task part2 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.IELTS, 90).orElseThrow();
        assertThat(ReflexV2Scoring.buildGateNote(part2, List.of("C1"), 20)).contains("30 từ");
    }
}
