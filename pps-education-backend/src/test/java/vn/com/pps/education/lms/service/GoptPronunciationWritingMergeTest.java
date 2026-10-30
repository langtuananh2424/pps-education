package vn.com.pps.education.lms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.com.pps.education.common.pronunciation.PronunciationInput;
import vn.com.pps.education.common.pronunciation.TargetMerger;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chế độ có bài viết Bước 1 (writing_merged của lab, đặc tả scoring v2 Module 1): câu mục tiêu là bài viết ghép với lời nói, KHÔNG phải
 * lời STT, để từ đọc lệch không bị STT "tự sửa" rồi chấm cao.
 */
class GoptPronunciationWritingMergeTest {

    /** Bốn từ của bài viết "I live in Hanoi", điểm IPA và GOPT đều tốt: không có trần trạng thái thì mọi từ đều trên 68. */
    private static String json(double snr, double activeSeconds) {
        return """
                {"no_signal":false,
                 "audio":{"peak":0.5,"noise_floor_db":-50,"snr_db":%s,"active_seconds":%s,"seconds":3.0},
                 "ipa":{"heard_ipa":"x","words":[
                   {"word":"I","expected_ipa":"aɪ","score":100,"issues":[],"heard_ipa":"aɪ"},
                   {"word":"live","expected_ipa":"lɪv","score":100,"issues":[],"heard_ipa":"lɪv"},
                   {"word":"in","expected_ipa":"ɪn","score":100,"issues":[],"heard_ipa":"ɪn"},
                   {"word":"Hanoi","expected_ipa":"hænɔɪ","score":100,"issues":[],"heard_ipa":"hænɔɪ"}]},
                 "gopt":{"utterance":{"accuracy":8.0,"completeness":9.8,"fluency":8.0,"prosodic":8.0,"total":8.0},
                   "words":[
                     {"word":"I","stress":9.5,"phones":[{"p":"AY","s":1.95}]},
                     {"word":"live","stress":9.5,"phones":[{"p":"L","s":1.95},{"p":"IH","s":1.95},{"p":"V","s":1.95}]},
                     {"word":"in","stress":9.5,"phones":[{"p":"IH","s":1.95},{"p":"N","s":1.95}]},
                     {"word":"Hanoi","stress":9.5,"phones":[{"p":"HH","s":1.95},{"p":"AE","s":1.95},{"p":"N","s":1.95},{"p":"OY","s":1.95}]}]},
                 "latency_seconds":{"total":2.1}}
                """.formatted(snr, activeSeconds);
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final PronunciationEngineClient engine = mock(PronunciationEngineClient.class);
    private final GoptPronunciationService service = new GoptPronunciationService(engine, new PpsConfigHolder(), mapper);

    private PronunciationEngineClient.Analysis analysis(String json) throws Exception {
        return mapper.readValue(json, PronunciationEngineClient.Analysis.class);
    }

    private static List<GroqSpeechClient.Word> heard(String... words) {
        java.util.List<GroqSpeechClient.Word> out = new java.util.ArrayList<>();
        double t = 0;
        for (String w : words) {
            out.add(new GroqSpeechClient.Word(w, t, t + 0.3));
            t += 0.4;
        }
        return out;
    }

    private List<String> sentToEngine() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> words = ArgumentCaptor.forClass(List.class);
        verify(engine).analyze(any(), any(), any(), words.capture());
        return words.getValue().stream().map(String::toLowerCase).toList();
    }

    @Test
    void misreadWordsKeepTheWrittenWordsAsTargetAndAreCappedAsDiff() throws Exception {
        when(engine.analyze(any(), any(), any(), anyList())).thenReturn(analysis(json(30, 2.0)));

        // đọc lệch cụm cuối: STT nghe ra "is it her eye" thay vì "in Hanoi"
        AzurePronunciationClient.Result r = service.assess(new byte[]{1}, "audio/webm", "I live is it her eye",
                heard("I", "live", "is", "it", "her", "eye"), 7, "I live in Hanoi");

        assertThat(sentToEngine()).as("câu mục tiêu là bài viết, không phải lời STT nghe nhầm").containsExactly("i", "live", "in", "hanoi");
        assertThat(r.hasScores()).isTrue();
        assertThat(r.words()).hasSize(4);
        double ok = r.words().get(0).accuracyScore();
        double diffIn = r.words().get(2).accuracyScore();
        double diffHanoi = r.words().get(3).accuracyScore();
        assertThat(ok).as("từ STT nghe đúng không bị chặn trần").isGreaterThan(68.0);
        assertThat(diffIn).as("từ đọc lệch bị chặn trần 6,8/10 của trạng thái diff").isLessThanOrEqualTo(68.0);
        assertThat(diffHanoi).isLessThanOrEqualTo(68.0);
    }

    @Test
    void withoutWrittenTextTheTranscriptIsTheTargetAsBefore() throws Exception {
        when(engine.analyze(any(), any(), any(), anyList())).thenReturn(analysis(json(30, 2.0)));

        service.assess(new byte[]{1}, "audio/webm", "I live in Hanoi", heard("I", "live", "in", "Hanoi"), 7, "  ");

        assertThat(sentToEngine()).containsExactly("i", "live", "in", "hanoi");
        verify(engine).analyze(any(), any(), any(), anyList());
    }

    @Test
    void usesTranscriptWordsWhenTheSttGaveNoTimestamps() throws Exception {
        when(engine.analyze(any(), any(), any(), anyList())).thenReturn(analysis(json(30, 2.0)));

        service.assess(new byte[]{1}, "audio/webm", "I live in Hanoi", List.of(), 7, "I live in Hanoi");

        assertThat(sentToEngine()).containsExactly("i", "live", "in", "hanoi");
    }

    @Test
    void garbledSttFallsBackToTheWrittenTextWhenTheAudioIsClear() throws Exception {
        when(engine.analyze(any(), any(), any(), anyList())).thenReturn(analysis(json(28, 2.5)));

        AzurePronunciationClient.Result r = service.assess(new byte[]{1}, "audio/webm", "Mæsa Góhá þig",
                heard("Mæsa", "Góhá", "þig"), 7, "I live in Hanoi");

        assertThat(sentToEngine()).as("dùng bài viết làm câu mẫu").containsExactly("i", "live", "in", "hanoi");
        assertThat(r.hasScores()).isTrue();
        assertThat(r.words()).allSatisfy(w -> assertThat(w.accuracyScore()).as("unverified bị chặn trần 5/10").isLessThanOrEqualTo(50.0));
    }

    @Test
    void garbledSttWithUnclearAudioGivesNoScoreInsteadOfGuessing() throws Exception {
        when(engine.analyze(any(), any(), any(), anyList())).thenReturn(analysis(json(10, 0.5)));

        AzurePronunciationClient.Result r = service.assess(new byte[]{1}, "audio/webm", "Mæsa Góhá þig",
                heard("Mæsa", "Góhá", "þig"), 7, "I live in Hanoi");

        assertThat(r.hasScores()).isFalse();
        assertThat(r.rawJson()).contains("không đủ rõ");
    }

    @Test
    void wordStatusFollowsTheLab() {
        var ipa = List.of(new PronunciationEngineClient.IpaWord("a", "a", 100, List.of(), "a"),
                new PronunciationEngineClient.IpaWord("b", "b", 100, List.of(), "b"),
                new PronunciationEngineClient.IpaWord("c", "c", 100, List.of(), "c"));
        var merged = List.of(new TargetMerger.MergedWord("a", "writing", null, 0),
                new TargetMerger.MergedWord("b", "writing", "bee", 1),
                new TargetMerger.MergedWord("c", "expanded", null, null));

        List<PronunciationInput.Word> normal = GoptPronunciationService.toGuidedInputWords(ipa, merged, false);
        List<PronunciationInput.Word> unverified = GoptPronunciationService.toGuidedInputWords(ipa, merged, true);

        assertThat(normal).extracting(PronunciationInput.Word::whisper).containsExactly("ok", "diff", "extra");
        assertThat(unverified).extracting(PronunciationInput.Word::whisper).containsOnly("unverified");
    }

    @Test
    void garbledDetectionMirrorsTheLab() {
        TargetMerger.Result noneMatched = TargetMerger.merge("I live in Hanoi", List.of("Mæsa", "Góhá"));
        assertThat(GoptPronunciationService.garbled(List.of("Mæsa", "Góhá"), noneMatched)).isTrue();

        List<String> ok = List.of("I", "live", "in", "Hanoi");
        assertThat(GoptPronunciationService.garbled(ok, TargetMerger.merge("I live in Hanoi", ok))).isFalse();

        List<String> misread = List.of("I", "live", "is", "it", "her", "eye");
        assertThat(GoptPronunciationService.garbled(misread, TargetMerger.merge("I live in Hanoi", misread))).isFalse();
    }

    @Test
    void fallbackNeedsClearAudioWithEnoughSpeech() {
        var clear = new PronunciationEngineClient.AudioInfo(0.5, -50, 20.0, 1.5, 3.0);
        assertThat(GoptPronunciationService.clearEnoughForFallback(clear)).isTrue();
        assertThat(GoptPronunciationService.clearEnoughForFallback(new PronunciationEngineClient.AudioInfo(0.5, -50, 19.9, 3.0, 3.0))).isFalse();
        assertThat(GoptPronunciationService.clearEnoughForFallback(new PronunciationEngineClient.AudioInfo(0.5, -50, 30.0, 1.4, 3.0))).isFalse();
        assertThat(GoptPronunciationService.clearEnoughForFallback(null)).isFalse();
    }
}
