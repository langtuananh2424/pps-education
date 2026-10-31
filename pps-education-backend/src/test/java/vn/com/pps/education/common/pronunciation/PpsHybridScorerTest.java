package vn.com.pps.education.common.pronunciation;

import org.junit.jupiter.api.Test;
import vn.com.pps.education.common.pronunciation.PronunciationInput.Gopt;
import vn.com.pps.education.common.pronunciation.PronunciationInput.GoptPhone;
import vn.com.pps.education.common.pronunciation.PronunciationInput.GoptUtterance;
import vn.com.pps.education.common.pronunciation.PronunciationInput.GoptWord;
import vn.com.pps.education.common.pronunciation.PronunciationInput.Scores;
import vn.com.pps.education.common.pronunciation.PronunciationInput.Timestamp;
import vn.com.pps.education.common.pronunciation.PronunciationInput.Word;
import vn.com.pps.education.common.pronunciation.PronunciationInput.Writing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Các ca hành vi chính của bộ chấm (đặc tả mục 5). Độ khớp số với lab nằm ở PpsHybridScorerGoldenTest. */
class PpsHybridScorerTest {

    private static Writing fullWriting(int n) {
        return new Writing(n, 0, 0, false);
    }

    /** Bài giả: n từ, mỗi từ 3 âm vị, tốc độ wpm, GOPT cho trước. */
    private static PronunciationInput make(int n, double phoneS, double wpm, double[] gopt, String whisper, String expIpa,
                                           String heardIpa, List<String> issues, Writing writing) {
        List<Word> words = new ArrayList<>();
        List<GoptWord> gws = new ArrayList<>();
        List<Timestamp> ts = new ArrayList<>();
        double t = 0;
        double dur = 60.0 / wpm;
        for (int i = 0; i < n; i++) {
            words.add(new Word("w" + i, expIpa, heardIpa, issues, 90, whisper));
            gws.add(new GoptWord(9.5, List.of(new GoptPhone("K", phoneS), new GoptPhone("AE", phoneS), new GoptPhone("T", phoneS))));
            ts.add(new Timestamp("w" + i, t, t + dur));
            t += dur + 0.05;
        }
        return new PronunciationInput(words, ts, writing, new Scores(60.0),
                new Gopt(null, new GoptUtterance(gopt[0], gopt[1], gopt[2]), gws), false);
    }

    private static PronunciationInput good(int n, Writing w) {
        return make(n, 1.9, 110.0, new double[]{6.0, 6.5, 6.3}, "ok", "kæt", "kæt", List.of(), w);
    }

    private static PpsV2Result score(PronunciationInput in, Object grade) {
        return PpsHybridScorer.score(in, grade, null, PpsConfig.defaults()).orElseThrow();
    }

    @Test
    void phoneScores_clipAndStretch() {
        assertThat(PpsHybridScorer.phoneScores(2.037, PpsConfig.defaults())[0]).isEqualTo(2.0);
        assertThat(PpsHybridScorer.phoneScores(0.5, PpsConfig.defaults())).containsExactly(0.0, 0.0);
        assertThat(PpsHybridScorer.phoneScores(1.5, PpsConfig.defaults())[0]).isLessThan(1.5);   // âm sai ở 1.1-1.7 bị hạ
    }

    @Test
    void longGoodUtterance_reachesSix() {
        assertThat(score(good(18, fullWriting(18)), 7).utterance().totalCalibrated()).isGreaterThanOrEqualTo(6.0);
    }

    @Test
    void fallback_isCappedAndWordsUnverified() {
        PronunciationInput in = make(16, 1.9, 110.0, new double[]{9, 9, 9}, "ok", "kæt", "kæt", List.of(), new Writing(16, 0, 0, true));
        PpsV2Result r = score(in, 7);
        assertThat(r.utterance().totalCalibrated()).isLessThanOrEqualTo(4.5);
        assertThat(r.utterance().accuracyCalibrated()).isLessThanOrEqualTo(4.5);
        assertThat(r.words()).allMatch(w -> w.whisperStatus().equals("unverified"));
    }

    @Test
    void lowCoverage_dragsTotalBelowFour() {
        PronunciationInput in = make(8, 1.9, 110.0, new double[]{8, 8, 8}, "ok", "kæt", "kæt", List.of(), new Writing(18, 10, 0, false));
        assertThat(score(in, 7).utterance().totalCalibrated()).isLessThan(4.0);
    }

    @Test
    void diffWords_areCapped_andMissedCountsAsDiff() {
        PpsV2Result r = score(make(5, 1.9, 110.0, new double[]{6, 6.5, 6.3}, "missed", "kæt", "kæt", List.of(), fullWriting(5)), 7);
        assertThat(r.words()).allMatch(w -> w.wordAccuracyCalibrated() <= 6.8 && w.whisperStatus().equals("diff"));
    }

    @Test
    void shortAnswer_isPenalised() {
        double full = score(good(16, fullWriting(16)), 7).utterance().totalCalibrated();
        double shortOne = score(good(3, fullWriting(3)), 7).utterance().totalCalibrated();
        assertThat(shortOne).isLessThan(full);
    }

    @Test
    void emptyWhenNothingToRescore() {
        PronunciationInput in = good(2, null);
        assertThat(PpsHybridScorer.score(new PronunciationInput(in.words(), in.wordTimestamps(), null, null, null, false), 7, null, null)).isEmpty();
        assertThat(PpsHybridScorer.score(new PronunciationInput(in.words(), in.wordTimestamps(), null, null, new Gopt(null, null, null), false), 7, null, null)).isEmpty();
        assertThat(PpsHybridScorer.score(new PronunciationInput(in.words(), in.wordTimestamps(), null, null, in.gopt(), true), 7, null, null)).isEmpty();
    }

    @Test
    void kaldiFailure_givesZerosAndNoAlignmentNotNan() {
        PronunciationInput in = good(1, fullWriting(1));
        PronunciationInput bad = new PronunciationInput(in.words(), in.wordTimestamps(), in.writing(), in.scores(),
                new Gopt("Kaldi lỗi (mã 1): quá ngắn", null, null), false);
        PpsV2Result r = score(bad, 7);
        assertThat(r.status()).isEqualTo("no_alignment");
        assertThat(r.reason()).contains("quá ngắn");
        assertThat(r.utterance()).isEqualTo(new PpsV2Result.Utterance(0, 0, 0, 0, 0, 0));
        assertThat(r.goptTotalCalibrated()).isZero();
        assertThat(r.words().get(0).wordAccuracyCalibrated()).isZero();
        assertThat(score(good(4, fullWriting(4)), 7).status()).isEqualTo("ok");
    }

    @Test
    void grade_defaultsToSevenAndRaisesLowerGrades() {
        assertThat(PpsHybridScorer.gradeOf("other")).isEqualTo(7);
        assertThat(PpsHybridScorer.gradeOf("")).isEqualTo(7);
        assertThat(PpsHybridScorer.gradeOf(null)).isEqualTo(7);
        assertThat(PpsHybridScorer.gradeOf("8")).isEqualTo(8);
        assertThat(PpsHybridScorer.gradeOf(6)).isEqualTo(6);
        PronunciationInput in = good(16, fullWriting(16));
        double t6 = score(in, 6).utterance().totalCalibrated();
        double t7 = score(in, 7).utterance().totalCalibrated();
        double t8 = score(in, 8).utterance().totalCalibrated();
        assertThat(t6).isGreaterThanOrEqualTo(t7);
        assertThat(t7).isGreaterThanOrEqualTo(t8);
    }

    @Test
    void endingSoundError_detectsMissingOrReplacedFinalConsonant() {
        assertThat(PpsHybridScorer.endingSoundError("mæθ", "mæ·", List.of("/θ/ → thiếu"))).isTrue();
        assertThat(PpsHybridScorer.endingSoundError("ˈkæt", "kæd", List.of("/t/ → /d/"))).isTrue();
        assertThat(PpsHybridScorer.endingSoundError("ˈkæt", "kæt", List.of())).isFalse();
        assertThat(PpsHybridScorer.endingSoundError("maɪ", "maɪ", List.of())).isFalse();                     // nguyên âm cuối
        assertThat(PpsHybridScorer.endingSoundError("ˈkæt", "kæd", List.of("/æ/ → /e/"))).isFalse();       // lỗi ở âm khác
    }

    @Test
    void endingPenalty_lowersWordScore_andConfigChangesIt() {
        PronunciationInput ok = good(4, fullWriting(4));
        PronunciationInput bad = make(4, 1.9, 110.0, new double[]{6, 6.5, 6.3}, "ok", "kæt", "kæ·", List.of("/t/ → thiếu"), fullWriting(4));
        double okAcc = score(ok, 7).words().get(0).wordAccuracyCalibrated();
        PpsV2Result badR = score(bad, 7);
        assertThat(badR.words()).allMatch(PpsV2Result.Word::endingSoundError);
        assertThat(badR.words().get(0).wordAccuracyCalibrated()).isLessThan(okAcc);

        PpsConfig heavy = PpsConfig.validate(Map.of("word", Map.of("ending_penalty", 4.0)), null);
        PpsV2Result heavyR = PpsHybridScorer.score(bad, 7, null, heavy).orElseThrow();
        assertThat(heavyR.words().get(0).wordAccuracyCalibrated()).isLessThan(badR.words().get(0).wordAccuracyCalibrated());
        assertThat(heavyR.calibrationConfig().changed()).containsExactly(Map.entry("word.ending_penalty", 4.0));
        assertThat(score(bad, 7).calibrationConfig().changed()).isEmpty();   // cấu hình mặc định không đổi sau khi tính với cấu hình khác
    }

    @Test
    void outputsStayInRangeOnExtremeInputs() {
        for (double s : new double[]{-1.0, 0.0, 0.8, 1.5, 1.95, 2.0, 2.037, 3.0}) {
            for (double[] g : new double[][]{{0, 0, 0}, {10.29, 10, 10}, {5, 5, 5}}) {
                PpsV2Result r = score(make(7, s, 110.0, g, "ok", "kæt", "kæt", List.of(), fullWriting(7)), 7);
                PpsV2Result.Utterance u = r.utterance();
                for (double x : new double[]{u.accuracyCalibrated(), u.fluencyCalibrated(), u.prosodicCalibrated(), u.totalCalibrated(),
                        u.completenessCalibrated(), u.wordMeanAccuracyCalibrated()}) {
                    assertThat(x).isBetween(0.0, 10.0);
                }
                r.words().forEach(w -> w.phones().forEach(p -> assertThat(p.phoneScoreCalibrated()).isBetween(0.0, 2.0)));
            }
        }
    }

    @Test
    void resultJson_hasSpecFieldNames() throws Exception {
        var node = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(score(good(4, fullWriting(4)), 7));
        for (String k : List.of("gopt_accuracy_calibrated", "gopt_fluency_calibrated", "gopt_prosodic_calibrated", "gopt_total_calibrated",
                "gopt_completeness_calibrated", "gopt_word_mean_accuracy_calibrated", "status", "calibration_config")) {
            assertThat(node.has(k)).as(k).isTrue();
        }
        assertThat(node.get("calibration_config").get("mode").asText()).isEqualTo("pps_hybrid_v2");
    }
}
