package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.com.pps.education.academic.domain.Curriculum;
import vn.com.pps.education.common.ReflexV2Task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Fallback TỪNG BƯỚC của Speaking: dự phòng 9Router của 1 bước lỗi thì dừng, không chạy lại cả luồng 9Router. */
class AiGradingFallbackServiceSpeakingTest {

    private static final ReflexV2Task TASK = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_6, null, 20).orElseThrow();
    private static final ReflexV2AiGradingService.Step1Anchor ANCHOR = new ReflexV2AiGradingService.Step1Anchor(80, "I like my school.");
    private static final ReflexV2AiGradingService.SpeakingUsageSink SINK = (step, usage) -> { };

    private final ReflexV2AiGradingService v2 = mock(ReflexV2AiGradingService.class);
    private final ReflexV2Prompts prompts = mock(ReflexV2Prompts.class);
    private final ClaudeAiClient claude = mock(ClaudeAiClient.class);
    private final GroqSpeechClient groq = mock(GroqSpeechClient.class);
    private final AzurePronunciationClient azure = mock(AzurePronunciationClient.class);
    private final AiGradingFallbackService service = new AiGradingFallbackService(
            mock(WritingAiGradingService.class), v2, prompts, claude, groq, azure, mock(AudioTranscoder.class));

    AiGradingFallbackServiceSpeakingTest() {
        ReflectionTestUtils.setField(service, "directPipelineEnabled", true);
        ReflectionTestUtils.setField(service, "maxAttempts", 1);
    }

    @Test
    void groqAndNineRouterTranscriptionBothFail_stopsWithoutRerunningWholeNineRouterFlow() {
        when(groq.isConfigured()).thenReturn(true);
        when(groq.transcribe(any(), any(), any(), any())).thenReturn(null);
        when(v2.transcribeBlind(any(), any(), anyDouble(), any())).thenReturn(null);

        assertThat(service.gradeReflexSpeakingWithFallback(TASK, "q", new byte[]{1}, "audio/webm", ANCHOR, SINK)).isNull();
        verify(v2, never()).gradeSpeaking(any(), any(), any(), any(), any(), any());
    }

    @Test
    void contentAndPronunciationFallbackFails_stopsWithoutRerunningWholeNineRouterFlow() {
        when(groq.isConfigured()).thenReturn(true);
        when(groq.transcribe(any(), any(), any(), any())).thenReturn(new GroqSpeechClient.TranscriptionResult("I like my school.", null));
        when(prompts.speakingSystem(any())).thenReturn("sys");
        when(prompts.speakingUser(any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyDouble())).thenReturn("user");
        when(v2.gradeSpeakingOnAudio(any(), any(), any(), any(), any(), any())).thenReturn(null);

        assertThat(service.gradeReflexSpeakingWithFallback(TASK, "q", new byte[]{1}, "audio/webm", ANCHOR, SINK)).isNull();
        verify(v2, never()).gradeSpeaking(any(), any(), any(), any(), any(), any());
    }

    @Test
    void noDirectProviderConfigured_usesNineRouterWholeFlow() {
        service.gradeReflexSpeakingWithFallback(TASK, "q", new byte[]{1}, "audio/webm", ANCHOR, SINK);
        verify(v2).gradeSpeaking(any(), any(), any(), any(), any(), any());
    }
}
