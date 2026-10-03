package vn.com.pps.education.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.academic.domain.Curriculum;

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
        // Trần Ngữ pháp chỉ đếm lỗi đỏ NGỮ PHÁP: "it hot" (thieu_thanh_phan) có, "đá cầu" (tieng_viet) thì không
        assertThat(ReflexV2Scoring.countRed(found)).isEqualTo(1);
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
        assertThat(ReflexV2Scoring.trimFeedback("**Em** nói tốt. Lỗi nặng nhất: thiếu động từ.", null)).isEqualTo("Em nói tốt. Lỗi nặng nhất: thiếu động từ.");
        String longText = "từ ".repeat(60).trim();
        String trimmed = ReflexV2Scoring.trimFeedback(longText, null);
        assertThat(trimmed).endsWith("…");
        assertThat(trimmed.split("\\s+")).hasSize(50);
    }

    @Test
    void trimFeedback_dropsWorstErrorSentenceWhenNothingWasHighlighted() {
        String fb = "Em trả lời rõ ý. Lỗi nặng nhất: thiếu động từ.";
        assertThat(ReflexV2Scoring.trimFeedback(fb, List.of())).isEqualTo("Em trả lời rõ ý.");
        assertThat(ReflexV2Scoring.trimFeedback(fb, List.of(hl(0, 2, "yellow", "mao_tu")))).isEqualTo(fb);
        // "Không có" luôn bị bỏ, kể cả khi highlights không được truyền
        assertThat(ReflexV2Scoring.trimFeedback("Em nói tốt. Lỗi nặng nhất: Không có.", null)).isEqualTo("Em nói tốt.");
    }

    // ---------- §D.5 (bản 30/9) — hint tách khỏi feedback ----------

    @Test
    void trimHint_emptyWhenNoErrorHighlighted_evenIfAiFilledSomething() {
        assertThat(ReflexV2Scoring.trimHint("Đọc chậm từng từ.", List.of())).isEmpty();
        assertThat(ReflexV2Scoring.trimHint("Đọc chậm từng từ.", null)).isEmpty();
        assertThat(ReflexV2Scoring.trimHint("Đọc chậm từng từ.", List.of(hl(0, 2, "green", "tu_vung")))).isEmpty();
    }

    @Test
    void trimHint_stripsMarkdownAndCapsAt35Words_whenBagHasAnError() {
        List<ReflexV2Scoring.Highlight> errored = List.of(hl(0, 2, "yellow", "am_cuoi"));
        assertThat(ReflexV2Scoring.trimHint("**Đọc chậm** từng từ.", errored)).isEqualTo("Đọc chậm từng từ.");
        String longText = "từ ".repeat(50).trim();
        String trimmed = ReflexV2Scoring.trimHint(longText, errored);
        assertThat(trimmed).endsWith("…");
        assertThat(trimmed.split("\\s+")).hasSize(35);
    }

    @Test
    void locateHighlights_matchesWholeWordsOnly_notInsideAnotherWord() throws Exception {
        String text = "I am planning to go in the morning";
        JsonNode hs = json("[{\"quote\":\"in\",\"occurrence\":1,\"level\":\"yellow\",\"tag\":\"gioi_tu\"}]");
        List<ReflexV2Scoring.Highlight> found = ReflexV2Scoring.locateHighlights(text, hs);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).start()).isEqualTo(text.indexOf(" in ") + 1);
    }

    // ---------- Chấm lại Ngữ pháp từ transcript (23/9) ----------

    @Test
    void regradeGrammar_floorIsHalfOfStep1RoundedDownTo5_andCapRunsBeforeFloor() {
        ReflexV2Scoring.CriterionScore graded = new ReflexV2Scoring.CriterionScore("GV", 30, false);
        // Ví dụ của người training: bài viết 100%, nói tệ chấm lại 30% → sàn 50%
        assertThat(ReflexV2Scoring.regradeGrammar(graded, 0, 100, false).percent()).isEqualTo(50);
        assertThat(ReflexV2Scoring.regradeGrammar(graded, 0, 70, false).percent()).isEqualTo(35);
        assertThat(ReflexV2Scoring.regradeGrammar(graded, 0, 70, false).floorPercent()).isEqualTo(35);
        // Điểm chấm lại cao hơn sàn thì giữ nguyên
        assertThat(ReflexV2Scoring.regradeGrammar(new ReflexV2Scoring.CriterionScore("GV", 80, false), 0, 100, false).percent()).isEqualTo(80);
        // ≥2 lỗi đỏ khi nói → trần 60%
        ReflexV2Scoring.CriterionScore capped = ReflexV2Scoring.regradeGrammar(new ReflexV2Scoring.CriterionScore("GV", 90, false), 2, 100, false);
        assertThat(capped.percent()).isEqualTo(60);
        assertThat(capped.cappedByRedErrors()).isTrue();
        // Không nói được gì thì KHÔNG có sàn — điểm phải là thật
        ReflexV2Scoring.CriterionScore silent = ReflexV2Scoring.regradeGrammar(new ReflexV2Scoring.CriterionScore("GV", 0, false), 0, 100, true);
        assertThat(silent.percent()).isZero();
        assertThat(silent.floorPercent()).isNull();
    }

    // ---------- Trần theo lỗi đã tô và các trần đo từ transcript (24–26/9) ----------

    private static ReflexV2Scoring.Highlight hl(int start, int end, String level, String tag) {
        return new ReflexV2Scoring.Highlight(start, end, level, tag, tag);
    }

    private static ReflexV2Scoring.CriterionScore cs(String code, int percent) {
        return new ReflexV2Scoring.CriterionScore(code, percent, false);
    }

    private static int percentOf(List<ReflexV2Scoring.CriterionScore> list, String code) {
        return list.stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow().percent();
    }

    private static final ReflexV2Task G7_IELTS = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS, 25).orElseThrow();
    private static final ReflexV2Task G8_PART2 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.IELTS, 90).orElseThrow();

    @Test
    void applyErrorCaps_criterionCannotBe100WhileItOwnsErrors_yellowMinus10_redMinus20() {
        List<ReflexV2Scoring.Highlight> hs = List.of(
                hl(0, 1, "yellow", "thi_dong_tu"), hl(2, 3, "yellow", "mao_tu"), hl(4, 5, "red", "cau_truc_cau"));
        List<ReflexV2Scoring.CriterionScore> out = ReflexV2Scoring.applyErrorCaps(G7_IELTS,
                List.of(cs("FC", 60), cs("LR", 100), cs("GRA", 100), cs("P", 60)), hs, ReflexV2Scoring.ErrorEvidence.NONE);
        assertThat(percentOf(out, "GRA")).isEqualTo(60);   // 100 - 2*10 - 1*20
        assertThat(percentOf(out, "LR")).isEqualTo(90);    // chỉ bị trần 90%, không có lỗi từ vựng
    }

    @Test
    void applyErrorCaps_vocabErrorsGoToLr_pronunciationTagsGoToP() {
        List<ReflexV2Scoring.Highlight> hs = List.of(hl(0, 1, "red", "dung_tu"),
                hl(2, 3, "yellow", "am_cuoi"), hl(4, 5, "yellow", "am_cuoi"), hl(6, 7, "yellow", "phat_am"));
        List<ReflexV2Scoring.CriterionScore> out = ReflexV2Scoring.applyErrorCaps(G7_IELTS,
                List.of(cs("FC", 50), cs("LR", 90), cs("GRA", 90), cs("P", 80)), hs, ReflexV2Scoring.ErrorEvidence.NONE);
        assertThat(percentOf(out, "LR")).isEqualTo(80);    // 1 lỗi từ vựng nặng → −20
        assertThat(percentOf(out, "P")).isEqualTo(70);     // 3 lỗi phát âm nhẹ → −30
        assertThat(percentOf(out, "GRA")).isEqualTo(90);   // không có lỗi ngữ pháp
        // Khối 6 không có tiêu chí LR: lỗi từ vựng tính vào Ngữ pháp/Từ vựng (GV)
        ReflexV2Task g6 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_6, null, 20).orElseThrow();
        List<ReflexV2Scoring.CriterionScore> g6Out = ReflexV2Scoring.applyErrorCaps(g6, List.of(cs("GV", 100), cs("P", 60)),
                List.of(hl(0, 1, "red", "dung_tu")), ReflexV2Scoring.ErrorEvidence.NONE);
        assertThat(percentOf(g6Out, "GV")).isEqualTo(80);
    }

    @Test
    void applyErrorCaps_speakingCriteriaNeverExceed90_grammarIsNotCappedBy90() {
        List<ReflexV2Scoring.CriterionScore> out = ReflexV2Scoring.applyErrorCaps(G7_IELTS,
                List.of(cs("FC", 100), cs("LR", 100), cs("GRA", 100), cs("P", 100)), List.of(), ReflexV2Scoring.ErrorEvidence.NONE);
        assertThat(percentOf(out, "FC")).isEqualTo(90);
        assertThat(percentOf(out, "LR")).isEqualTo(90);
        assertThat(percentOf(out, "P")).isEqualTo(80);   // không có bằng chứng đọc lệch → thêm trần 80%
        assertThat(percentOf(out, "GRA")).isEqualTo(100);
    }

    @Test
    void applyErrorCaps_fluencyCannotExceedGrammarPlus40() {
        List<ReflexV2Scoring.CriterionScore> out = ReflexV2Scoring.applyErrorCaps(G7_IELTS,
                List.of(cs("FC", 90), cs("LR", 50), cs("GRA", 30), cs("P", 50)), List.of(), ReflexV2Scoring.ErrorEvidence.NONE);
        assertThat(percentOf(out, "FC")).isEqualTo(70);
    }

    @Test
    void applyErrorCaps_lexicalRangeAndFluencyEvidenceAreCeilings() {
        ReflexV2Scoring.ErrorEvidence ev = new ReflexV2Scoring.ErrorEvidence(
                new ReflexV2Scoring.LexicalEvidence(4, 20, 0.16, 60),
                new ReflexV2Scoring.FluencyEvidence(6, 20, 0.3, 4.0, 70), null, null);
        List<ReflexV2Scoring.CriterionScore> out = ReflexV2Scoring.applyErrorCaps(G7_IELTS,
                List.of(cs("FC", 90), cs("LR", 90), cs("GRA", 100), cs("P", 60)), List.of(), ev);
        assertThat(percentOf(out, "LR")).isEqualTo(60);
        assertThat(percentOf(out, "FC")).isEqualTo(70);
        assertThat(percentOf(out, "P")).isEqualTo(60);   // FC có trong bài → fluency không áp cho P
        // Khối 6 chỉ có GV + P: fluency áp thẳng cho P
        ReflexV2Task g6 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_6, null, 20).orElseThrow();
        assertThat(percentOf(ReflexV2Scoring.applyErrorCaps(g6, List.of(cs("GV", 90), cs("P", 90)), List.of(),
                new ReflexV2Scoring.ErrorEvidence(null, new ReflexV2Scoring.FluencyEvidence(6, 20, 0.3, 4.0, 70), null, null)), "P")).isEqualTo(70);
    }

    @Test
    void applyErrorCaps_part2TooShort_capsFcAt60AndLrAt80() {
        ReflexV2Scoring.ErrorEvidence ev = new ReflexV2Scoring.ErrorEvidence(null, null,
                new ReflexV2Scoring.LengthEvidence(20, 12.0, false, 60, 80), null);
        List<ReflexV2Scoring.CriterionScore> out = ReflexV2Scoring.applyErrorCaps(G8_PART2,
                List.of(cs("FC", 90), cs("LR", 90), cs("GRA", 90), cs("P", 60)), List.of(), ev);
        assertThat(percentOf(out, "FC")).isEqualTo(60);
        assertThat(percentOf(out, "LR")).isEqualTo(80);
        assertThat(percentOf(out, "GRA")).isEqualTo(90);
    }

    @Test
    void applyErrorCaps_pronunciationBandFollowsReadbackRatio_capsAndFloors() {
        List<String> none = List.of();
        // 50% từ đọc lệch → dải [40,60]: kéo P 90 xuống 60 — TRẦN áp bất kể mẫu lớn/nhỏ (ở đây chỉ 20 từ).
        ReflexV2Scoring.ErrorEvidence bad = new ReflexV2Scoring.ErrorEvidence(null, null, null, new ReflexV2Scoring.Readback(20, 10, 0.5, none));
        assertThat(percentOf(ReflexV2Scoring.applyErrorCaps(G7_IELTS, List.of(cs("FC", 50), cs("LR", 50), cs("GRA", 50), cs("P", 90)), List.of(), bad), "P")).isEqualTo(60);
        // đọc gần như hoàn hảo (ratio < 0.12), đủ ≥30 từ so được → sàn 85: kéo P 40 LÊN 85.
        // count=2 (không phải 0/1): tránh chạm luôn trần "không có bằng chứng" (weak = total≥30 && count≤1),
        // vốn cũng đòi ≥30 từ nên dễ vô tình trùng điều kiện với sàn nếu chỉ đổi total mà không đổi count.
        ReflexV2Scoring.ErrorEvidence good = new ReflexV2Scoring.ErrorEvidence(null, null, null, new ReflexV2Scoring.Readback(35, 2, 0.06, none));
        assertThat(percentOf(ReflexV2Scoring.applyErrorCaps(G7_IELTS, List.of(cs("FC", 50), cs("LR", 50), cs("GRA", 50), cs("P", 40)), List.of(), good), "P")).isEqualTo(85);
    }

    /**
     * Bản 1/10 — SÀN dải đọc lệch chỉ áp khi so được ≥30 từ. Bài thật: chỉ so được 9 từ, lệch 2 ("lai",
     * "fren") = 22% → rơi dải [75,90] → sàn CŨ kéo một bài rất yếu lên 75% dù chấm tay 20-40%. Mẫu quá nhỏ
     * không đủ tin để NÂNG điểm ai; TRẦN (hạ điểm) thì vẫn áp ở mọi độ dài vì đọc lệch nhiều là bằng chứng thật.
     */
    @Test
    void applyErrorCaps_pronunciationFloor_requiresAtLeast30WordsReadback_ceilingStillAppliesOnSmallSample() {
        ReflexV2Scoring.ErrorEvidence tinySample = new ReflexV2Scoring.ErrorEvidence(null, null, null,
                new ReflexV2Scoring.Readback(9, 2, 2 / 9.0, List.of("lai→fren")));
        // Checkpoint đã chấm P = 40%: dải [75,90] ứng với tỷ lệ 22% muốn NÂNG lên 75 nhưng mẫu <30 từ → giữ 40.
        assertThat(percentOf(ReflexV2Scoring.applyErrorCaps(G7_IELTS,
                List.of(cs("FC", 50), cs("LR", 50), cs("GRA", 50), cs("P", 40)), List.of(), tinySample), "P")).isEqualTo(40);
        // Cùng mẫu nhỏ nhưng checkpoint chấm CAO hơn trần của dải (90) thì vẫn bị hạ — trần không cần mẫu lớn.
        assertThat(percentOf(ReflexV2Scoring.applyErrorCaps(G7_IELTS,
                List.of(cs("FC", 50), cs("LR", 50), cs("GRA", 50), cs("P", 95)), List.of(), tinySample), "P")).isEqualTo(90);
    }

    @Test
    void applyErrorCaps_noPronunciationEvidence_capsAt80_butShortCleanReadbackKeeps90() {
        // bài dài (≥30 từ) mà gần như không lệch → transcript bị làm mượt → không có bằng chứng → 80
        ReflexV2Scoring.ErrorEvidence smooth = new ReflexV2Scoring.ErrorEvidence(null, null, null, new ReflexV2Scoring.Readback(45, 1, 0.02, List.of()));
        assertThat(percentOf(ReflexV2Scoring.applyErrorCaps(G7_IELTS, List.of(cs("FC", 50), cs("LR", 50), cs("GRA", 50), cs("P", 100)), List.of(), smooth), "P")).isEqualTo(80);
        // bài ngắn (<30 từ) đọc sạch → có bằng chứng thật → giữ 90
        ReflexV2Scoring.ErrorEvidence shortClean = new ReflexV2Scoring.ErrorEvidence(null, null, null, new ReflexV2Scoring.Readback(15, 0, 0.0, List.of()));
        assertThat(percentOf(ReflexV2Scoring.applyErrorCaps(G7_IELTS, List.of(cs("FC", 50), cs("LR", 50), cs("GRA", 50), cs("P", 100)), List.of(), shortClean), "P")).isEqualTo(90);
    }

    /**
     * Bản 1/10 (thầy cô chấm theo bội số của 10) — làm tròn XUỐNG, không làm tròn gần nhất: mọi giá trị lệch
     * lưới đều là kết quả của một trần hoặc sàn, nên tròn xuống là chiều dè dặt (trần cho ít hơn, sàn nâng
     * ít hơn). Giá trị đã trên lưới giữ nguyên nguyên vẹn, không bị gắn ghi chú "ép lưới" không cần thiết.
     */
    @Test
    void snapToGrid10_roundsDownOffGridValues_leavesOnGridValuesUntouched() {
        List<ReflexV2Scoring.CriterionScore> out = ReflexV2Scoring.snapToGrid10(
                List.of(cs("P", 85), cs("FC", 75), cs("GRA", 35), cs("LR", 80)));
        assertThat(percentOf(out, "P")).isEqualTo(80);
        assertThat(percentOf(out, "FC")).isEqualTo(70);
        assertThat(percentOf(out, "GRA")).isEqualTo(30);
        assertThat(percentOf(out, "LR")).isEqualTo(80);
        assertThat(out.stream().filter(c -> c.code().equals("LR")).findFirst().orElseThrow().caps()).isEmpty();
        assertThat(out.stream().filter(c -> c.code().equals("P")).findFirst().orElseThrow().caps())
                .anyMatch(n -> n.contains("ép lưới 10"));
    }

    @Test
    void applyGrammarFloor_restoresFloorWhenOwnErrorCapPushedBelowIt() {
        // Đo 26/9: Bước 1 = 60% → sàn 30%, nhưng 5 lỗi vàng + 2 lỗi đỏ cho trần 10% → điểm ra 0%: một lỗi bị phạt hai lần
        ReflexV2Task g6 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_6, null, 20).orElseThrow();
        List<ReflexV2Scoring.Highlight> hs = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            hs.add(hl(i * 2, i * 2 + 1, "yellow", "mao_tu"));
        }
        hs.add(hl(20, 21, "red", "cau_truc_cau"));
        hs.add(hl(22, 23, "red", "trat_tu_tu"));
        ReflexV2Scoring.CriterionScore regraded = ReflexV2Scoring.regradeGrammar(cs("GV", 20), 2, 60, false);
        List<ReflexV2Scoring.CriterionScore> capped = ReflexV2Scoring.applyErrorCaps(g6, List.of(regraded, cs("P", 60)), hs, ReflexV2Scoring.ErrorEvidence.NONE);
        assertThat(percentOf(capped, "GV")).isEqualTo(10);
        assertThat(percentOf(ReflexV2Scoring.applyGrammarFloor("GV", capped), "GV")).isEqualTo(30);
    }

    // ---------- Bằng chứng đo tất định từ transcript ----------

    @Test
    void writtenVsSpoken_findsDeviantWordsByConsonantSkeleton_ignoresExtraSpokenWords() {
        // Ví dụ trong DOI-MOI-26-9: chips→chip, fried→fry, friends→fren
        ReflexV2Scoring.Readback rb = ReflexV2Scoring.writtenVsSpoken(
                "I like chips and fried fish with my friends", "I like chip and fry fish with my fren");
        assertThat(rb.words()).containsExactly("chips→chip", "fried→fry", "friends→fren");
        assertThat(rb.total()).isEqualTo(8);
        assertThat(rb.ratio()).isEqualTo(3 / 8.0);

        // Từ nói thêm ngoài bài viết + từ đệm KHÔNG bị tính là đọc lệch
        ReflexV2Scoring.Readback extra = ReflexV2Scoring.writtenVsSpoken(
                "I like chips and fish", "um I like chips and fish and also basketball");
        assertThat(extra.count()).isZero();

        // Quá ngắn → không đủ bằng chứng
        assertThat(ReflexV2Scoring.writtenVsSpoken("I like fish", "I like fish")).isNull();
    }

    @Test
    void lexicalCeiling_countsDistinctContentWordsPerSecondOfTheTask() {
        assertThat(ReflexV2Scoring.lexicalCeiling("playing football basketball swimming reading drawing cooking dancing", 20).ceil()).isEqualTo(80);
        // 8 từ nội dung nhưng chỉ 1 từ khác nhau → mật độ 0,05 → trần 60
        ReflexV2Scoring.LexicalEvidence repeated = ReflexV2Scoring.lexicalCeiling("bottle bottle bottle bottle bottle bottle bottle bottle", 20);
        assertThat(repeated.distinct()).isEqualTo(1);
        assertThat(repeated.ceil()).isEqualTo(60);
        // từ chức năng và khoảng dừng (...3s) không tính; dưới 8 từ nội dung → không xét
        assertThat(ReflexV2Scoring.lexicalCeiling("I like it and (...3s) the football is good", 20)).isNull();
        assertThat(ReflexV2Scoring.lexicalCeiling("playing football basketball swimming reading drawing cooking dancing", 0)).isNull();
    }

    @Test
    void fluencyCeiling_countsVietnameseFillersAndSelfRepairsAndLongPause() {
        // "à" tiếng Việt đứng giữa hai dấu cách từng bị bỏ sót vì \b chỉ hiểu ASCII
        ReflexV2Scoring.FluencyEvidence filled = ReflexV2Scoring.fluencyCeiling("I à like football à and à basketball à with my friends", 0.5);
        assertThat(filled.fillers()).isGreaterThanOrEqualTo(4);
        assertThat(filled.ceil()).isEqualTo(50);

        String clean = "I like playing football with my friends every weekend at the park";
        assertThat(ReflexV2Scoring.fluencyCeiling(clean, 0.5).ceil()).isEqualTo(100);
        assertThat(ReflexV2Scoring.fluencyCeiling(clean, 3.0).ceil()).isEqualTo(70);
        assertThat(ReflexV2Scoring.fluencyCeiling(clean, 5.2).ceil()).isEqualTo(50);
        // nói vấp / tự sửa: cùng một từ lặp lại trong vòng 3 tiếng
        assertThat(ReflexV2Scoring.fluencyCeiling("I I go to school because I go to school every day", 0.5).fillers()).isGreaterThanOrEqualTo(1);
        assertThat(ReflexV2Scoring.fluencyCeiling("too short", 9)).isNull();
    }

    @Test
    void lengthGate_onlyForPart2OrShort_enoughByWordsOrBySpokenSeconds() {
        String tenWords = "I like my town because it is quiet and green";
        // G7_IELTS có rubricFormat="SHORT" nên từ bản 1/10 KHÔNG còn null — xem test riêng dưới.
        // Dạng không có cột ngưỡng (G6) mới thật sự không áp cổng này.
        ReflexV2Task g6 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_6, null, 20).orElseThrow();
        assertThat(ReflexV2Scoring.lengthGate(g6, tenWords, 10)).isNull();
        ReflexV2Scoring.LengthEvidence shortTalk = ReflexV2Scoring.lengthGate(G8_PART2, tenWords, 20);
        assertThat(shortTalk.enough()).isFalse();
        assertThat(shortTalk.fcCeil()).isEqualTo(60);
        assertThat(shortTalk.lrCeil()).isEqualTo(80);
        // đủ 45 giây nói dù ít từ (bài nói 73 giây với 85 từ từng bị model gắn nhầm "chưa đủ độ dài")
        assertThat(ReflexV2Scoring.lengthGate(G8_PART2, tenWords, 46).enough()).isTrue();
        assertThat(ReflexV2Scoring.lengthGate(G8_PART2, "word ".repeat(60), 5).enough()).isTrue();
        // dấu khoảng dừng (...3s) không tính là từ
        assertThat(ReflexV2Scoring.lengthGate(G8_PART2, "hello (...3s) world", 0).words()).isEqualTo(2);
    }

    /**
     * Bản 1/10 — cổng độ dài mới cho đề NGẮN (SHORT): mốc 25 từ, KHÔNG có nhánh "nói đủ lâu thì cứu" như
     * Part 2 (bài đã ngắn sẵn, nói hết giờ mà vẫn ít từ chính là rề rà — xem DOI-MOI-1-10.md mục 1).
     */
    @Test
    void lengthGate_shortForm_25WordMinimum_timeDoesNotRescue() {
        String twentyFourWords = "word ".repeat(24).trim();
        ReflexV2Scoring.LengthEvidence tooShort = ReflexV2Scoring.lengthGate(G7_IELTS, twentyFourWords, 30);
        assertThat(tooShort.enough()).isFalse();
        assertThat(tooShort.fcCeil()).isEqualTo(60);
        assertThat(tooShort.lrCeil()).isEqualTo(80);
        assertThat(ReflexV2Scoring.lengthGate(G7_IELTS, "word ".repeat(25).trim(), 1).enough()).isTrue();
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
        assertThat(g8Cam.rubricFormat()).isEqualTo("PET4");
        assertThat(g8Cam.criteria()).containsExactly("GV", "DM", "P");

        ReflexV2Task g9Part2 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 90).orElseThrow();
        assertThat(g9Part2.id()).isEqualTo("g9-ielts-part2");
        // Bản 1/10 — hạn ghi âm 120s (nới 28/9 để học sinh có thêm thời gian) KHÔNG phải mẫu số Từ vựng:
        // kỳ vọng lượng nói vẫn 90s như trước, tách khỏi seconds() để không tự siết trần Từ vựng oan.
        assertThat(g9Part2.seconds()).isEqualTo(120);
        assertThat(g9Part2.lexicalSeconds()).isEqualTo(90);
        assertThat(ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 30).orElseThrow().minWords()).isEqualTo(18);
        assertThat(ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 30).orElseThrow().lexicalSeconds()).isEqualTo(30);
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

    // ---------- 29/9: trần chỉ đếm lỗi đỏ ngữ pháp, cách B, rubric v3 ----------

    private static ReflexV2Scoring.Highlight red(String tag) {
        return new ReflexV2Scoring.Highlight(0, 1, "red", tag, tag);
    }

    @Test
    void countRed_countsOnlyGrammarRedErrors_forTheGrammarCap() {
        // Hai từ nghe không ra + chêm tiếng Việt + dùng sai từ: vẫn đỏ nhưng KHÔNG kéo trần Ngữ pháp
        List<ReflexV2Scoring.Highlight> nonGrammar = List.of(red("khong_ro"), red("khong_ro"), red("tieng_viet"), red("dung_tu"), red("lac_y"));
        assertThat(ReflexV2Scoring.countRed(nonGrammar)).isZero();
        assertThat(ReflexV2Scoring.regradeGrammar(cs("GV", 90), ReflexV2Scoring.countRed(nonGrammar), 80, false).percent()).isEqualTo(90);

        List<ReflexV2Scoring.Highlight> grammar = List.of(red("thieu_thanh_phan"), red("trat_tu_tu"), red("khong_ro"));
        assertThat(ReflexV2Scoring.countRed(grammar)).isEqualTo(2);
        assertThat(ReflexV2Scoring.regradeGrammar(cs("GV", 90), ReflexV2Scoring.countRed(grammar), 80, false).percent()).isEqualTo(60);
        // Lỗi vàng cùng loại ngữ pháp không bao giờ đếm
        assertThat(ReflexV2Scoring.countRed(List.of(new ReflexV2Scoring.Highlight(0, 1, "yellow", "x", "thieu_thanh_phan")))).isZero();
    }

    @Test
    void tenseFixedByQuestion_isAlwaysRedGrammar_prepositionBreakingPhraseIsRedOnlyFromGrade8() throws Exception {
        String text = "Yesterday I go to home and discuss about it.";
        JsonNode hs = json("[{\"quote\":\"go\",\"occurrence\":1,\"level\":\"yellow\",\"tag\":\"thi_de_an_dinh\"},"
                + "{\"quote\":\"to home\",\"occurrence\":1,\"level\":\"red\",\"tag\":\"gioi_tu_pha_cum\"},"
                + "{\"quote\":\"discuss about\",\"occurrence\":1,\"level\":\"red\",\"tag\":\"gioi_tu_pha_cum\"}]");
        // Khối 7: sai thì đề ấn định luôn đỏ; giới từ phá cụm còn là lỗi vàng → chỉ 1 lỗi đỏ ngữ pháp, chưa chạm trần
        List<ReflexV2Scoring.Highlight> g7 = ReflexV2Scoring.locateHighlights(text, hs, 7);
        assertThat(g7).extracting(ReflexV2Scoring.Highlight::level).containsExactly("red", "yellow", "yellow");
        assertThat(ReflexV2Scoring.countRed(g7)).isEqualTo(1);
        // Khối 8: cả ba đều đỏ ngữ pháp → tính vào trần 60%
        List<ReflexV2Scoring.Highlight> g8 = ReflexV2Scoring.locateHighlights(text, hs, 8);
        assertThat(g8).extracting(ReflexV2Scoring.Highlight::level).containsExactly("red", "red", "red");
        assertThat(ReflexV2Scoring.countRed(g8)).isEqualTo(3);
        assertThat(ReflexV2Scoring.grammarRedQuotes(text, g8)).containsExactly("go", "to home", "discuss about");
        assertThat(ReflexV2Scoring.grammarRedQuotes(text, g7)).containsExactly("go");
    }

    @Test
    void sameAsWritten_ignoresFillersRepeatsPausesAndPronunciationSlips() {
        String written = "My favourite food is fried chicken because it is very tasty.";
        ReflexV2Scoring.SpokenMatch clean = ReflexV2Scoring.sameAsWritten(written,
                "um My my favourite food is fried chicken (...3s) because it is very tasty.");
        assertThat(clean.same()).isTrue();
        assertThat(clean.diff()).isZero();
        // mất âm cuối / đọc lệch là lỗi Phát âm (đã trừ ở P), không phải đổi câu
        ReflexV2Scoring.SpokenMatch slips = ReflexV2Scoring.sameAsWritten(written,
                "My favourite foo is fry chicken becau it is very tasty.");
        assertThat(slips.same()).isTrue();
        assertThat(slips.diff()).isZero();
    }

    @Test
    void sameAsWritten_toleratesOneOrTwoMisheardWords() {
        String written = "On Sunday I usually go to the park with my family and we play badminton.";
        // lượt phiên âm nghe nhầm 1 từ ngắn ("the" → "a") và 1 từ nội dung → vẫn là cùng một câu
        ReflexV2Scoring.SpokenMatch m = ReflexV2Scoring.sameAsWritten(written,
                "On Sunday I usually go to a park with my family and we play tennis.");
        assertThat(m.diff()).isEqualTo(4);
        assertThat(m.ratio()).isGreaterThanOrEqualTo(ReflexV2Scoring.SAME_AS_WRITTEN_MIN_RATIO);
        assertThat(m.same()).isTrue();
        // Căn cứ duy nhất là tỷ lệ ≥85%: bài 3 từ lệch 1 từ chỉ khớp 67% → coi là nói khác, chấm lại
        ReflexV2Scoring.SpokenMatch tiny = ReflexV2Scoring.sameAsWritten("I like cats.", "I like dogs.");
        assertThat(tiny.diff()).isEqualTo(2);
        assertThat(tiny.same()).isFalse();
    }

    @Test
    void sameAsWritten_isFalse_whenTheStudentReshapesOrExtendsTheSentence() {
        ReflexV2Scoring.SpokenMatch extended = ReflexV2Scoring.sameAsWritten("I like play football.",
                "I like to play football with my friends after school.");
        assertThat(extended.same()).isFalse();
        assertThat(extended.diff()).isEqualTo(6);
        // Rủi ro đã biết (TRANG-THAI-BAN-GIAO 29/9): nghe nhầm 3 từ đầu câu → coi là nói khác → chấm lại từ
        // transcript. Chỉ giáo viên soát lại mới phát hiện được.
        assertThat(ReflexV2Scoring.sameAsWritten("Like I said, I used to play Minecraft a lot as a kid.",
                "A game that I used to play, Minecraft, a lot as a kid.").same()).isFalse();
        assertThat(ReflexV2Scoring.sameAsWritten(null, "I like cats.")).isNull();
        assertThat(ReflexV2Scoring.sameAsWritten("I like cats.", "um (...4s)")).isNull();
    }

    @Test
    void keepGrammarFromStep1_overridesEveryCapOnGrammarOnly() {
        List<ReflexV2Scoring.CriterionScore> capped = List.of(cs("GV", 40), cs("DM", 70), cs("P", 60));
        List<ReflexV2Scoring.CriterionScore> kept = ReflexV2Scoring.keepGrammarFromStep1("GV", capped, 80);
        assertThat(kept).extracting(ReflexV2Scoring.CriterionScore::percent).containsExactly(80, 70, 60);
        assertThat(kept.get(0).caps()).containsExactly("giữ điểm Bước 1 (nói giống bài viết)");
        assertThat(kept.get(0).cappedByRedErrors()).isFalse();
    }

    @Test
    void task_v3IsCurrent_andV2KeepsItsOwnConfigForUnfinishedQuestions() {
        assertThat(ReflexV2Task.CURRENT_RUBRIC_VERSION).isEqualTo(ReflexV2Task.RUBRIC_V3);
        ReflexV2Task g9v3 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 120).orElseThrow();
        assertThat(g9v3.seconds()).isEqualTo(120);
        assertThat(g9v3.rubricDir()).isEqualTo("rubrics-v3/");

        ReflexV2Task g9v2 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 90, ReflexV2Task.RUBRIC_V2).orElseThrow();
        assertThat(g9v2.id()).isEqualTo("g9-ielts-part2");
        assertThat(g9v2.seconds()).isEqualTo(90);
        assertThat(g9v2.rubricDir()).isEqualTo("rubrics-v2/");
        assertThat(ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.CAMBRIDGE, 60, ReflexV2Task.RUBRIC_V2)
                .orElseThrow().rubricFormat()).isNull();
        // Dòng luồng cũ (rubric_version NULL) hoặc version lạ → không vào luồng v2/v3
        assertThat(ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_6, null, 20, null)).isEmpty();
        assertThat(ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_6, null, 20, "v9")).isEmpty();
        assertThat(g9v3.grade()).isEqualTo(9);
        assertThat(ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_6, null, 20).orElseThrow().grade()).isEqualTo(6);
    }

    // ---------- V200: dạng đề tường minh ----------

    @Test
    void allowedFormats_matchRubricV3PerGradeAndTrack() {
        assertThat(ReflexV2Task.allowedFormats(Curriculum.GradeLevel.GRADE_6, null)).containsOnlyKeys(ReflexQuestionFormat.SHORT);
        assertThat(ReflexV2Task.allowedFormats(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS).keySet())
                .containsExactly(ReflexQuestionFormat.SHORT, ReflexQuestionFormat.PART2);
        assertThat(ReflexV2Task.allowedFormats(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.CAMBRIDGE).keySet())
                .containsExactly(ReflexQuestionFormat.SHORT, ReflexQuestionFormat.PICTURE);
        assertThat(ReflexV2Task.allowedFormats(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.IELTS).keySet())
                .containsExactly(ReflexQuestionFormat.SHORT, ReflexQuestionFormat.PART2);
        assertThat(ReflexV2Task.allowedFormats(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.CAMBRIDGE).keySet())
                .containsExactly(ReflexQuestionFormat.PET4, ReflexQuestionFormat.PICTURE);
        assertThat(ReflexV2Task.allowedFormats(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS).keySet())
                .containsExactly(ReflexQuestionFormat.SHORT, ReflexQuestionFormat.PART2);
        // Chưa có bộ tiêu chí → không chọn được dạng đề (luồng cũ)
        assertThat(ReflexV2Task.allowedFormats(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.CAMBRIDGE)).isEmpty();
        assertThat(ReflexV2Task.allowedFormats(Curriculum.GradeLevel.GRADE_7, null)).isEmpty();
        assertThat(ReflexV2Task.allowedFormats(null, null)).isEmpty();
    }

    @Test
    void forQuestion_explicitFormatWins_evenWhenDurationWouldSaySomethingElse() {
        // Khối 8 Cambridge: PET4 và tả tranh cùng 60 giây — chỉ phân biệt được bằng dạng đề
        ReflexV2Task picture = ReflexV2Task.forQuestion(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.CAMBRIDGE, 60,
                ReflexQuestionFormat.PICTURE, ReflexV2Task.RUBRIC_V3).orElseThrow();
        assertThat(picture.id()).isEqualTo("g8-cam-pet2");
        assertThat(picture.rubricFormat()).isEqualTo("PICTURE");
        assertThat(picture.minWords()).isEqualTo(18);
        // Khối 7 IELTS Part 2 — trước đây câu 60 giây bị chấm như câu ngắn 25 giây
        ReflexV2Task g7Part2 = ReflexV2Task.forQuestion(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS, 60,
                ReflexQuestionFormat.PART2, ReflexV2Task.RUBRIC_V3).orElseThrow();
        assertThat(g7Part2.id()).isEqualTo("g7-ielts-part2");
        assertThat(g7Part2.seconds()).isEqualTo(60);
        assertThat(ReflexV2Task.forQuestion(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.CAMBRIDGE, 60,
                ReflexQuestionFormat.PICTURE, ReflexV2Task.RUBRIC_V3).orElseThrow().id()).isEqualTo("g7-cam-pet2");
        // Dạng đề thắng thời lượng: Khối 9 đặt SHORT dù ghi âm 90 giây
        assertThat(ReflexV2Task.forQuestion(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 90,
                ReflexQuestionFormat.SHORT, ReflexV2Task.RUBRIC_V3).orElseThrow().id()).isEqualTo("g9-ielts-short");
    }

    @Test
    void forQuestion_fallsBackToDurationInference_forOldQuestionsInvalidFormatsAndV2Rows() {
        // Câu hỏi cũ (chưa chọn dạng đề)
        assertThat(ReflexV2Task.forQuestion(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.IELTS, 90, null,
                ReflexV2Task.RUBRIC_V3).orElseThrow().id()).isEqualTo("g8-ielts-part2");
        // Dạng đề không hợp lệ với khối/tuyến (lưu được là do dữ liệu cũ) → suy theo thời lượng
        assertThat(ReflexV2Task.forQuestion(Curriculum.GradeLevel.GRADE_6, null, 20, ReflexQuestionFormat.PICTURE,
                ReflexV2Task.RUBRIC_V3).orElseThrow().id()).isEqualTo("g6-short");
        // Câu dở dang bằng v2: bộ v2 không có dạng đề mới → giữ cấu hình v2
        ReflexV2Task v2 = ReflexV2Task.forQuestion(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.CAMBRIDGE, 60,
                ReflexQuestionFormat.PICTURE, ReflexV2Task.RUBRIC_V2).orElseThrow();
        assertThat(v2.id()).isEqualTo("g8-cam-pet4");
        assertThat(v2.rubricVersion()).isEqualTo(ReflexV2Task.RUBRIC_V2);
    }

    @Test
    void lengthGate_part2Grade7_needs30SecondsOr40Words_grade8Needs45SecondsOr60Words() {
        ReflexV2Task g7Part2 = ReflexV2Task.forQuestion(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS, 60,
                ReflexQuestionFormat.PART2, ReflexV2Task.RUBRIC_V3).orElseThrow();
        String fortyWords = String.join(" ", java.util.Collections.nCopies(40, "word"));
        assertThat(ReflexV2Scoring.lengthGate(g7Part2, fortyWords, 10).enough()).isTrue();
        assertThat(ReflexV2Scoring.lengthGate(g7Part2, "short answer", 30).enough()).isTrue();
        assertThat(ReflexV2Scoring.lengthGate(g7Part2, "short answer", 29).enough()).isFalse();
        // Khối 8: 40 từ / 30 giây chưa đủ
        assertThat(ReflexV2Scoring.lengthGate(G8_PART2, fortyWords, 30).enough()).isFalse();
    }
}
