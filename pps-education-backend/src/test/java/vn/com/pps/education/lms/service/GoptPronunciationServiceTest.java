package vn.com.pps.education.lms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Chấm phát âm bằng GOPT: ánh xạ kết quả service suy luận + PPS Hybrid v2 sang khung điểm 0-100 của Azure, và các đường lỗi. */
class GoptPronunciationServiceTest {

    private static final String OK_JSON = """
            {"no_signal":false,
             "audio":{"peak":0.5,"noise_floor_db":-50,"snr_db":30,"active_seconds":2.0,"seconds":2.4},
             "ipa":{"heard_ipa":"aɪ laɪk skul","words":[
               {"word":"I","expected_ipa":"aɪ","score":100,"issues":[],"heard_ipa":"aɪ"},
               {"word":"like","expected_ipa":"laɪk","score":100,"issues":[],"heard_ipa":"laɪk"},
               {"word":"school","expected_ipa":"skul","score":75,"issues":["/l/ → thiếu"],"heard_ipa":"sku·"}]},
             "gopt":{"utterance":{"accuracy":7.0,"completeness":9.8,"fluency":7.0,"prosodic":7.0,"total":7.0},
               "words":[
                 {"word":"I","accuracy":8,"stress":9.5,"total":8,"phones":[{"p":"AY","s":1.9}],"guessed_pron":false},
                 {"word":"like","accuracy":8,"stress":9.5,"total":8,"phones":[{"p":"L","s":1.9},{"p":"AY","s":1.9},{"p":"K","s":1.9}],"guessed_pron":false},
                 {"word":"school","accuracy":6,"stress":9.0,"total":6,"phones":[{"p":"S","s":1.9},{"p":"K","s":1.8},{"p":"UW","s":1.7},{"p":"L","s":1.0}],"guessed_pron":false}]},
             "latency_seconds":{"total":2.1}}
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final PronunciationEngineClient engine = mock(PronunciationEngineClient.class);
    private final GoptPronunciationService service = new GoptPronunciationService(engine, new PpsConfigHolder(), mapper);

    private PronunciationEngineClient.Analysis analysis(String json) throws Exception {
        return mapper.readValue(json, PronunciationEngineClient.Analysis.class);
    }

    private static List<GroqSpeechClient.Word> stamps() {
        return List.of(new GroqSpeechClient.Word("I", 0.0, 0.3), new GroqSpeechClient.Word("like", 0.4, 0.8),
                new GroqSpeechClient.Word("school", 0.9, 1.5));
    }

    @Test
    void happyPath_mapsToAzureShapedResultOnPercentScale() throws Exception {
        when(engine.analyze(any(), any(), any(), anyList())).thenReturn(analysis(OK_JSON));

        AzurePronunciationClient.Result r = service.assess(new byte[]{1}, "audio/webm", "I like school", stamps(), 7);

        assertThat(r.hasScores()).isTrue();
        assertThat(r.engine()).isEqualTo(AzurePronunciationClient.Result.ENGINE_GOPT);
        for (Integer score : List.of(r.accuracyScore(), r.fluencyScore(), r.completenessScore(), r.prosodyScore(), r.overallScore())) {
            assertThat(score).isBetween(0, 100);
        }
        assertThat(r.words()).hasSize(3);
        assertThat(r.words().get(0).phonemes().get(0).phoneme()).isEqualTo("aɪ");           // ARPAbet AY thành IPA
        assertThat(r.words().get(2).phonemes()).extracting(p -> p.phoneme()).containsExactly("s", "k", "u", "l");
        assertThat(r.words().get(2).errorType()).isEqualTo("Mispronunciation");                // thiếu /l/ cuối, điểm từ thấp
        assertThat(r.words().get(0).errorType()).isEqualTo("None");
        assertThat(r.rawJson()).contains("gopt-pps-v2").contains("pps_hybrid_v2");
    }

    @Test
    void numbersInTranscriptAreSentAsWords() throws Exception {
        when(engine.analyze(any(), any(), any(), anyList())).thenReturn(null);

        service.assess(new byte[]{1}, "audio/webm", "I have 30 books", List.of(), 7);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> words = ArgumentCaptor.forClass(List.class);
        verify(engine).analyze(any(), eq("audio/webm"), any(), words.capture());
        assertThat(words.getValue()).containsExactly("I", "have", "thirty", "books");
    }

    @Test
    void emptyTranscript_doesNotCallEngine() {
        AzurePronunciationClient.Result r = service.assess(new byte[]{1}, "audio/webm", "   ", List.of(), 7);

        assertThat(r.hasScores()).isFalse();
        verify(engine, never()).analyze(any(), any(), any(), anyList());
    }

    @Test
    void engineDown_givesNoScoresSoThePipelineFallsBack() {
        when(engine.analyze(any(), any(), any(), anyList())).thenReturn(null);

        assertThat(service.assess(new byte[]{1}, "audio/webm", "I like school", stamps(), 7).hasScores()).isFalse();
    }

    @Test
    void noSignal_givesNoScores() throws Exception {
        when(engine.analyze(any(), any(), any(), anyList())).thenReturn(analysis("{\"no_signal\":true,\"audio\":{\"peak\":0.0}}"));

        assertThat(service.assess(new byte[]{1}, "audio/webm", "I like school", stamps(), 7).hasScores()).isFalse();
    }

    @Test
    void kaldiCouldNotAlign_givesNoScoresNotZero() throws Exception {
        String json = OK_JSON.replaceAll("(?s)\"gopt\":\\{.*\"latency_seconds\"", "\"gopt\":{\"error\":\"Kaldi lỗi (mã 1): quá ngắn\"},\"latency_seconds\"");
        when(engine.analyze(any(), any(), any(), anyList())).thenReturn(analysis(json));

        AzurePronunciationClient.Result r = service.assess(new byte[]{1}, "audio/webm", "I like school", stamps(), 7);

        assertThat(r.hasScores()).isFalse();
        assertThat(r.rawJson()).contains("không gióng hàng");
    }

    @Test
    void ipaWordCountMismatch_givesNoScores() throws Exception {
        when(engine.analyze(any(), any(), any(), anyList())).thenReturn(analysis(OK_JSON));

        assertThat(service.assess(new byte[]{1}, "audio/webm", "I like my school", stamps(), 7).hasScores()).isFalse();
    }

    @Test
    void withoutWordTimestamps_fluencyStillComesFromGopt() throws Exception {
        when(engine.analyze(any(), any(), any(), anyList())).thenReturn(analysis(OK_JSON));

        AzurePronunciationClient.Result r = service.assess(new byte[]{1}, "audio/webm", "I like school", List.of(), 7);

        assertThat(r.hasScores()).isTrue();
        assertThat(r.fluencyScore()).isBetween(0, 100);
    }

    @Test
    void helpers_convertScalesAndFluencyHeuristic() {
        assertThat(GoptPronunciationService.toPercent(7.349)).isEqualTo(73);
        assertThat(GoptPronunciationService.toPercent(-1)).isZero();
        assertThat(GoptPronunciationService.toPercent(11)).isEqualTo(100);
        assertThat(GoptPronunciationService.fluencyHeuristic(List.of(), 3.0)).isZero();
        var steady = GoptPronunciationService.timestamps(List.of(new GroqSpeechClient.Word("a", 0, 0.5), new GroqSpeechClient.Word("b", 0.5, 1.0),
                new GroqSpeechClient.Word("...", 1.0, 1.1)));
        assertThat(steady).hasSize(2);   // bỏ mốc không có chữ
    }
}
