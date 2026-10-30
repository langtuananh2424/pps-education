package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.com.pps.education.academic.domain.Curriculum;
import vn.com.pps.education.common.ReflexV2Task;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Phiên âm: Groq lỗi thì thử Whisper cục bộ của engine, rồi mới tới 9Router. */
class AiGradingFallbackServiceWhisperFallbackTest {

    private static final ReflexV2Task TASK = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_6, null, 20).orElseThrow();
    private static final ReflexV2AiGradingService.Step1Anchor ANCHOR = new ReflexV2AiGradingService.Step1Anchor(80, "I like my school.");
    private static final ReflexV2AiGradingService.SpeakingUsageSink SINK = (step, usage) -> { };

    private final ReflexV2AiGradingService v2 = mock(ReflexV2AiGradingService.class);
    private final ReflexV2Prompts prompts = mock(ReflexV2Prompts.class);
    private final GroqSpeechClient groq = mock(GroqSpeechClient.class);
    private final PronunciationEngineClient engine = mock(PronunciationEngineClient.class);
    private final AiGradingFallbackService service = new AiGradingFallbackService(
            mock(WritingAiGradingService.class), v2, prompts, mock(ClaudeAiClient.class), groq, mock(AzurePronunciationClient.class),
            mock(AudioTranscoder.class), null, engine);

    AiGradingFallbackServiceWhisperFallbackTest() {
        ReflectionTestUtils.setField(service, "directPipelineEnabled", true);
        ReflectionTestUtils.setField(service, "maxAttempts", 1);
        when(groq.isConfigured()).thenReturn(true);
        when(groq.transcribe(any(), any(), any(), any())).thenReturn(null);   // Groq lỗi
        when(prompts.speakingSystem(any())).thenReturn("sys");
        when(prompts.speakingUser(any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyDouble())).thenReturn("user");
    }

    private static PronunciationEngineClient.Transcription whisperResult() {
        return new PronunciationEngineClient.Transcription("I like my school.", "en", 2.0,
                List.of(new PronunciationEngineClient.TranscribedSegment(0.0, 2.0, "I like my school.")),
                List.of(new PronunciationEngineClient.TranscribedWord("I", 0.0, 0.2, 0.9)), "whisper-large-v3-turbo");
    }

    @Test
    void groqFails_engineWhisperTranscribes_nineRouterTranscriptionIsNotUsed() {
        when(engine.canTranscribe()).thenReturn(true);
        when(engine.transcribe(any(), any(), any())).thenReturn(whisperResult());

        service.gradeReflexSpeakingWithFallback(TASK, "q", new byte[]{1}, "audio/webm", ANCHOR, SINK);

        verify(engine).transcribe(any(), any(), any());
        verify(v2, never()).transcribeBlind(any(), any(), anyDouble(), any());
    }

    @Test
    void engineWhisperAlsoFails_fallsBackToNineRouterTranscription() {
        when(engine.canTranscribe()).thenReturn(true);
        when(engine.transcribe(any(), any(), any())).thenReturn(null);
        when(v2.transcribeBlind(any(), any(), anyDouble(), any())).thenReturn(null);

        assertThat(service.gradeReflexSpeakingWithFallback(TASK, "q", new byte[]{1}, "audio/webm", ANCHOR, SINK)).isNull();

        verify(engine).transcribe(any(), any(), any());
        verify(v2).transcribeBlind(any(), any(), anyDouble(), any());
    }

    @Test
    void whisperFallbackDisabledOrEngineNotConfigured_goesStraightToNineRouter() {
        when(engine.canTranscribe()).thenReturn(false);
        when(v2.transcribeBlind(any(), any(), anyDouble(), any())).thenReturn(null);

        service.gradeReflexSpeakingWithFallback(TASK, "q", new byte[]{1}, "audio/webm", ANCHOR, SINK);

        verify(engine, never()).transcribe(any(), any(), any());
        verify(v2).transcribeBlind(any(), any(), anyDouble(), any());
    }

    @Test
    void groqWorks_engineWhisperIsNeverCalled() {
        when(groq.transcribe(any(), any(), any(), any())).thenReturn(new GroqSpeechClient.TranscriptionResult("I like my school.", null));
        when(engine.canTranscribe()).thenReturn(true);

        service.gradeReflexSpeakingWithFallback(TASK, "q", new byte[]{1}, "audio/webm", ANCHOR, SINK);

        verify(engine, never()).transcribe(any(), any(), any());
    }

    @Test
    void engineTranscriptionIsMappedToGroqShape() {
        when(engine.canTranscribe()).thenReturn(true);
        when(engine.transcribe(any(), any(), any())).thenReturn(whisperResult());

        GroqSpeechClient.TranscriptionResult r = ReflectionTestUtils.invokeMethod(service, "transcribeWithEngineWhisper", new byte[]{1}, "audio/webm");

        assertThat(r.text()).isEqualTo("I like my school.");
        assertThat(r.segments()).containsExactly(new GroqSpeechClient.Segment(0.0, 2.0, "I like my school."));
        assertThat(r.words()).containsExactly(new GroqSpeechClient.Word("I", 0.0, 0.2));
        assertThat(r.lowConfidenceWarning()).contains("Whisper cục bộ").contains("whisper-large-v3-turbo");
    }
}
