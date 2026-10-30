package vn.com.pps.education.common.pronunciation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.common.pronunciation.PronunciationInput.Gopt;
import vn.com.pps.education.common.pronunciation.PronunciationInput.GoptPhone;
import vn.com.pps.education.common.pronunciation.PronunciationInput.GoptUtterance;
import vn.com.pps.education.common.pronunciation.PronunciationInput.GoptWord;
import vn.com.pps.education.common.pronunciation.PronunciationInput.Scores;
import vn.com.pps.education.common.pronunciation.PronunciationInput.Timestamp;
import vn.com.pps.education.common.pronunciation.PronunciationInput.Word;
import vn.com.pps.education.common.pronunciation.SpontaneousScorer.Llm;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chấm nói tự do: các ca hành vi chính (đặc tả nói tự do) và khớp từng ca với {@code spontaneous_scorer.py} của lab qua
 * {@code spontaneous_golden.json} (220 ca: sai ngôn ngữ, quá ngắn, từ chối, thiếu Claude, Kaldi không gióng được, cấu hình khác mặc định).
 */
class SpontaneousScorerTest {

    private static final String GOOD18 = "I like my school because it has a big library and the teachers are very kind to every student in my class";
    private static final Llm LLM_GOOD = new Llm(9.0, 8.5, List.of(), List.of("dùng because"), "Em nói rõ ý.", "m", null);
    private final ObjectMapper mapper = new ObjectMapper();

    /** Bài nói tự do giả: n từ (3 âm vị mỗi từ), tốc độ wpm, độ dài file = n·60/110 + 1.5 giây. */
    private static PronunciationInput spont(String text, Integer nWords, double wpm, double[] gopt) {
        int n = nWords != null ? nWords : text.split("\\s+").length;
        List<Word> words = new ArrayList<>();
        List<GoptWord> gws = new ArrayList<>();
        List<Timestamp> ts = new ArrayList<>();
        double t = 0;
        double dur = 60.0 / wpm;
        for (int i = 0; i < n; i++) {
            words.add(new Word("w" + i, "kæt", "kæt", List.of(), 90, "ok"));
            gws.add(new GoptWord(9.5, List.of(new GoptPhone("K", 1.9), new GoptPhone("AE", 1.9), new GoptPhone("T", 1.9))));
            ts.add(new Timestamp("w" + i, t, t + dur));
            t += dur + 0.05;
        }
        return new PronunciationInput(words, ts, null, new Scores(60.0), new Gopt(null, new GoptUtterance(gopt[0], gopt[1], gopt[2]), gws), false,
                PronunciationInput.MODE_UNSCRIPTED, text, Math.round((n * 60 / 110.0 + 1.5) * 100) / 100.0);
    }

    private static PronunciationInput spont(String text) {
        return spont(text, null, 110.0, new double[]{6.0, 6.5, 6.3});
    }

    private static SpontaneousScorer.Result score(PronunciationInput in, Object grade, Llm llm) {
        return SpontaneousScorer.score(in, grade, llm, PpsConfig.defaults()).orElseThrow();
    }

    @Test
    void twoWords_isTooShortBetweenOnePointFiveAndTwo() {
        SpontaneousScorer.Result r = score(spont("I like", 2, 110.0, new double[]{6, 6.5, 6.3}), 7, LLM_GOOD);
        assertThat(r.status()).isEqualTo("too_short");
        assertThat(r.total()).isLessThan(2.0).isGreaterThanOrEqualTo(1.5);
        assertThat(r.taskResponse()).isEqualTo(1.0);
    }

    @Test
    void shortSpeakingTime_isTooShort() {
        assertThat(score(spont("I like my school very much indeed", 7, 400.0, new double[]{6, 6.5, 6.3}), 7, LLM_GOOD).status()).isEqualTo("too_short");
    }

    @Test
    void vietnamese_getsOnePoint() {
        SpontaneousScorer.Result r = score(spont("Em thích trường của em vì có nhiều bạn tốt"), 7, LLM_GOOD);
        assertThat(r.status()).isEqualTo("wrong_language");
        assertThat(r.total()).isEqualTo(1.0);
        assertThat(r.taskResponse()).isZero();
        assertThat(r.feedback()).contains("tiếng Anh");
    }

    @Test
    void refusal_hasFixedScoresAndNeedsNoLlm() {
        SpontaneousScorer.Result r = score(spont("I don't know"), 7, null);
        assertThat(r.status()).isEqualTo("refused");
        assertThat(r.taskResponse()).isZero();
        assertThat(r.grammarVocab()).isEqualTo(2.0);
        assertThat(r.total()).isNotNull();
        assertThat(SpontaneousScorer.needsLlm(spont("No idea"), 7, null)).isFalse();
        assertThat(SpontaneousScorer.needsLlm(spont(GOOD18), 7, null)).isTrue();
    }

    @Test
    void notSpontaneousOrNoSpeech_returnsEmpty() {
        PronunciationInput in = spont(GOOD18);
        PronunciationInput scripted = new PronunciationInput(in.words(), in.wordTimestamps(), null, in.scores(), in.gopt(), false,
                "writing_merged", GOOD18, 9.0);
        assertThat(SpontaneousScorer.score(scripted, 7, LLM_GOOD, null)).isEmpty();
        PronunciationInput silent = new PronunciationInput(in.words(), in.wordTimestamps(), null, in.scores(), in.gopt(), true,
                in.mode(), GOOD18, 9.0);
        assertThat(SpontaneousScorer.score(silent, 7, LLM_GOOD, null)).isEmpty();
    }

    @Test
    void offTopic_pullsTotalDownByAtLeastOnePoint() {
        double on = score(spont(GOOD18), 7, LLM_GOOD).total();
        SpontaneousScorer.Result off = score(spont(GOOD18), 7, new Llm(2.0, 7.0, List.of(), List.of(), "", "m", null));
        assertThat(off.taskResponse()).isLessThan(4.0);
        assertThat(on - off.total()).isGreaterThanOrEqualTo(1.0);   // TR chỉ chiếm 20%: lạc đề kéo tổng xuống từ 1 điểm, không tự xuống dưới trung bình
    }

    @Test
    void total_isTheWeightedSum() {
        SpontaneousScorer.Result r = score(spont(GOOD18), 7, LLM_GOOD);
        double raw = 0.30 * r.pronunciation() + 0.25 * r.fluency() + 0.25 * 8.5 + 0.20 * 9.0;
        assertThat(r.total()).isCloseTo(PpsHybridScorer.round(raw, 2), Offset.offset(0.011));
        assertThat(r.total()).isBetween(0.0, 10.0);
        assertThat(r.weights()).containsExactly(0.30, 0.25, 0.25, 0.20);
    }

    @Test
    void lengthGate_belowMinimumWords() {
        assertThat(score(spont(GOOD18), 7, LLM_GOOD).lengthGate()).isEqualTo(1.0);
        assertThat(score(spont("I like my school very much", 6, 110.0, new double[]{6, 6.5, 6.3}), 7, LLM_GOOD).lengthGate()).isLessThan(1.0);
        SpontaneousScorer.Result g8 = score(spont("I like my school very much indeed it nice", 9, 110.0, new double[]{6, 6.5, 6.3}), 8, LLM_GOOD);
        assertThat(g8.lengthGate()).isCloseTo(0.40 + 0.60 * 9 / 12, Offset.offset(0.001));
    }

    @Test
    void withoutLlm_doesNotInventScores() {
        SpontaneousScorer.Result r = score(spont(GOOD18), 7, null);
        assertThat(r.status()).isEqualTo("llm_unavailable");
        assertThat(r.total()).isNull();
        assertThat(r.grammarVocab()).isNull();
        assertThat(r.pronunciation()).isPositive();
        SpontaneousScorer.Result failed = score(spont(GOOD18), 7, Llm.failed("Claude lỗi: x"));
        assertThat(failed.status()).isEqualTo("llm_unavailable");
        assertThat(failed.feedback()).contains("Claude lỗi");
    }

    @Test
    void kaldiFailure_stillScoresButIsMarked() {
        PronunciationInput in = spont(GOOD18);
        PronunciationInput bad = new PronunciationInput(in.words(), in.wordTimestamps(), null, in.scores(), new Gopt("Kaldi lỗi", null, null), false,
                in.mode(), in.whisperTranscript(), in.audioSeconds());
        SpontaneousScorer.Result r = score(bad, 7, LLM_GOOD);
        assertThat(r.status()).isEqualTo("no_alignment");
        assertThat(r.pronunciation()).isZero();
    }

    @Test
    void gradeKey_mapsEverythingButEightToSeven() {
        assertThat(SpontaneousScorer.gradeKey(8)).isEqualTo(8);
        assertThat(SpontaneousScorer.gradeKey("other")).isEqualTo(7);
        assertThat(SpontaneousScorer.gradeKey(6)).isEqualTo(7);
        assertThat(SpontaneousScorer.gradeKey(null)).isEqualTo(7);
    }

    @Test
    void configChangesWeightsAndThresholds() {
        PpsConfig gvHeavy = PpsConfig.validate(Map.of("spontaneous", Map.of("weights", Map.of(
                "pronunciation", 0.1, "fluency", 0.1, "grammar_vocab", 0.5, "task_response", 0.3))), null);
        SpontaneousScorer.Result r = SpontaneousScorer.score(spont(GOOD18), 7, LLM_GOOD, gvHeavy).orElseThrow();
        assertThat(r.weights()).containsExactly(0.1, 0.1, 0.5, 0.3);
        assertThat(r.total()).isNotEqualTo(score(spont(GOOD18), 7, LLM_GOOD).total());
        PpsConfig strict = PpsConfig.validate(Map.of("spontaneous", Map.of("min_words_hard", 20)), null);
        assertThat(SpontaneousScorer.score(spont("I like my school very much and it is nice", 10, 110.0, new double[]{6, 6.5, 6.3}), 7, LLM_GOOD, strict)
                .orElseThrow().status()).isEqualTo("too_short");
    }

    // ---------- khớp lab ----------

    @Test
    void matchesLabOnEveryGoldenCase() throws Exception {
        JsonNode cases;
        try (InputStream in = getClass().getResourceAsStream("/pronunciation/spontaneous_golden.json")) {
            assertThat(in).as("thiếu file golden").isNotNull();
            cases = mapper.readTree(in);
        }
        assertThat(cases.size()).isGreaterThan(200);
        for (int i = 0; i < cases.size(); i++) {
            JsonNode c = cases.get(i);
            PronunciationInput input = mapper.treeToValue(c.get("input"), PronunciationInput.class);
            Llm llm = c.get("llm").isNull() ? null : mapper.treeToValue(c.get("llm"), Llm.class);
            PpsConfig cfg = c.get("config").isNull() ? null : PpsConfig.validate(mapper.convertValue(c.get("config"), Map.class), null);
            String grade = c.get("grade").asText();

            assertThat(SpontaneousScorer.needsLlm(input, grade, cfg)).as("ca %d needs_llm", i).isEqualTo(c.get("needs_llm").asBoolean());
            var actual = SpontaneousScorer.score(input, grade, llm, cfg);
            JsonNode expected = c.get("expected");
            if (expected.isNull()) {
                assertThat(actual).as("ca %d phải rỗng", i).isEmpty();
            } else {
                assertThat(actual).as("ca %d phải có điểm", i).isPresent();
                assertSame("ca " + i, withoutNulls(expected), withoutNulls(mapper.valueToTree(actual.get())));
            }
        }
    }

    /** Bài thoát sớm của lab ghi khoá wpm/word_mean_accuracy/ending_sound_errors = null, bản Java bỏ hẳn khoá: coi hai cách là một. */
    private static JsonNode withoutNulls(JsonNode n) {
        if (n.isObject()) {
            ObjectNode out = new ObjectMapper().createObjectNode();
            n.fields().forEachRemaining(e -> {
                if (!e.getValue().isNull()) {
                    out.set(e.getKey(), withoutNulls(e.getValue()));
                }
            });
            return out;
        }
        return n;
    }

    private static void assertSame(String path, JsonNode expected, JsonNode actual) {
        if (expected.isNumber()) {
            assertThat(actual.isNumber()).as("%s phải là số", path).isTrue();
            assertThat(actual.asDouble()).as(path).isCloseTo(expected.asDouble(), Offset.offset(1e-9));
        } else if (expected.isObject()) {
            assertThat(actual.isObject()).as("%s phải là object", path).isTrue();
            assertThat(names(actual)).as("%s: tập khoá", path).isEqualTo(names(expected));
            expected.fieldNames().forEachRemaining(k -> assertSame(path + "." + k, expected.get(k), actual.get(k)));
        } else if (expected.isArray()) {
            assertThat(actual.isArray()).as("%s phải là mảng", path).isTrue();
            assertThat(actual.size()).as("%s: độ dài", path).isEqualTo(expected.size());
            for (int i = 0; i < expected.size(); i++) {
                assertSame(path + "[" + i + "]", expected.get(i), actual.get(i));
            }
        } else {
            assertThat(actual).as(path).isEqualTo(expected);
        }
    }

    private static Set<String> names(JsonNode o) {
        Set<String> out = new HashSet<>();
        for (Iterator<String> it = o.fieldNames(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }
}
