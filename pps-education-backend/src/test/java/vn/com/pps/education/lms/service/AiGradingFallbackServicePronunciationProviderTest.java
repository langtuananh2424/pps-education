package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Chọn nguồn điểm Phát âm của Speaking: azure (mặc định) hoặc gopt, gopt lỗi thì thử Azure. */
class AiGradingFallbackServicePronunciationProviderTest {

    private static final AzurePronunciationClient.Result GOPT_OK = new AzurePronunciationClient.Result(
            80, 80, 100, 80, 80, List.of(), "{}", 1, AzurePronunciationClient.Result.ENGINE_GOPT);
    private static final AzurePronunciationClient.Result AZURE_OK = new AzurePronunciationClient.Result(
            70, 70, 100, 70, 70, List.of(), "{}");

    private final AzurePronunciationClient azure = mock(AzurePronunciationClient.class);
    private final GoptPronunciationService gopt = mock(GoptPronunciationService.class);
    private final AiGradingFallbackService service = new AiGradingFallbackService(
            mock(WritingAiGradingService.class), mock(ReflexV2AiGradingService.class), mock(ReflexV2Prompts.class),
            mock(ClaudeAiClient.class), mock(GroqSpeechClient.class), azure, mock(AudioTranscoder.class), gopt);

    private boolean usesGopt() {
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(service, "usesGopt"));
    }

    private AzurePronunciationClient.Result assess(boolean useGopt) {
        return ReflectionTestUtils.invokeMethod(service, "assessPronunciation", useGopt, new byte[]{1}, "audio/webm", "I like school", null, 7);
    }

    @Test
    void defaultProviderIsAzure_evenWhenEngineIsConfigured() {
        when(gopt.isConfigured()).thenReturn(true);
        ReflectionTestUtils.setField(service, "pronunciationProvider", "azure");
        assertThat(usesGopt()).isFalse();
    }

    @Test
    void goptProvider_needsEngineConfigured() {
        ReflectionTestUtils.setField(service, "pronunciationProvider", "gopt");
        when(gopt.isConfigured()).thenReturn(false);
        assertThat(usesGopt()).isFalse();
        when(gopt.isConfigured()).thenReturn(true);
        assertThat(usesGopt()).isTrue();
        ReflectionTestUtils.setField(service, "pronunciationProvider", "GOPT");
        assertThat(usesGopt()).isTrue();
    }

    @Test
    void sevenArgConstructor_hasNoGopt() {
        AiGradingFallbackService legacy = new AiGradingFallbackService(mock(WritingAiGradingService.class),
                mock(ReflexV2AiGradingService.class), mock(ReflexV2Prompts.class), mock(ClaudeAiClient.class),
                mock(GroqSpeechClient.class), azure, mock(AudioTranscoder.class));
        ReflectionTestUtils.setField(legacy, "pronunciationProvider", "gopt");
        assertThat(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(legacy, "usesGopt"))).isFalse();
    }

    @Test
    void goptScores_azureNotCalled() {
        when(gopt.assess(any(), any(), any(), anyList(), anyInt())).thenReturn(GOPT_OK);

        assertThat(assess(true)).isSameAs(GOPT_OK);
        verify(azure, never()).assessSegmented(any(), any(), any(), anyList());
    }

    @Test
    void goptFails_azureConfigured_usesAzure() {
        when(gopt.assess(any(), any(), any(), anyList(), anyInt())).thenReturn(AzurePronunciationClient.Result.error("GOPT: lỗi"));
        when(azure.isConfigured()).thenReturn(true);
        when(azure.assessSegmented(any(), any(), any(), anyList())).thenReturn(AZURE_OK);

        assertThat(assess(true)).isSameAs(AZURE_OK);
    }

    @Test
    void goptFails_azureNotConfigured_returnsNoScoresForNineRouterFallback() {
        when(gopt.assess(any(), any(), any(), anyList(), anyInt())).thenReturn(AzurePronunciationClient.Result.error("GOPT: lỗi"));
        when(azure.isConfigured()).thenReturn(false);

        assertThat(assess(true).hasScores()).isFalse();
        verify(azure, never()).assessSegmented(any(), any(), any(), anyList());
    }

    @Test
    void azureProvider_neverCallsGopt() {
        when(azure.assessSegmented(any(), any(), any(), anyList())).thenReturn(AZURE_OK);

        assertThat(assess(false)).isSameAs(AZURE_OK);
        verify(gopt, never()).assess(any(), any(), any(), anyList(), anyInt());
    }
}
