package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.common.AiTokenUsage;
import vn.com.pps.education.common.ReflexV2Task;
import vn.com.pps.education.domain.AiGradingTokenUsage;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.exception.ReflexAudioRejectedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * UC-23b V2 — chi phí từng lượt gọi AI của bước nói phải được đẩy ra {@link ReflexV2AiGradingService.SpeakingUsageSink}
 * NGAY khi AI trả về, kể cả khi sau đó bản ghi bị từ chối (HTTP 422) hoặc parse lỗi trả {@code null} — token đã
 * tốn thật. Test thuần Service logic, mock AI (xem .claude/rules/testing.md).
 */
class ReflexV2AiGradingServiceTest {

    private static final String WRITTEN =
            "My favourite sport is football because I play it with my friends every weekend.";

    private final NineRouterAiClient aiClient = mock(NineRouterAiClient.class);
    private final ReflexV2Prompts prompts = mock(ReflexV2Prompts.class);
    private final AudioTranscoder audioTranscoder = mock(AudioTranscoder.class);
    private final ReflexV2AiGradingService.SpeakingUsageSink sink = mock(ReflexV2AiGradingService.SpeakingUsageSink.class);
    private final ReflexV2AiGradingService service =
            new ReflexV2AiGradingService(aiClient, new ObjectMapper(), prompts, audioTranscoder);

    private final ReflexV2Task task = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_6, null, 20).orElseThrow();
    private final ReflexV2AiGradingService.LockedGrammar locked = new ReflexV2AiGradingService.LockedGrammar(80, 0, WRITTEN);

    private final AiTokenUsage transcriptionUsage =
            new AiTokenUsage("gemini-3.6-flash-medium", true, 5200, 0, 310, 900, 6100);
    private final AiTokenUsage gradingUsage =
            new AiTokenUsage("gemini-3.6-flash-medium", true, 7100, 0, 1800, 1100, 8400);

    @BeforeEach
    void khongChuyenDuocSangWav() {
        // Không chuyển được sang WAV → bộ đo tiếng nói không chạy, không cần audio thật.
        when(audioTranscoder.toWav(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void gradeSpeaking_UC23b_spokeDifferent_recordsTranscriptionUsageThenRejects() {
        when(aiClient.chatWithAudioJson(any(), any(), any(), any(), any(), any()))
                .thenReturn(transcriptionResponse("Yesterday elephants danced beautifully under purple mountains"));

        assertThatThrownBy(() -> grade())
                .isInstanceOf(ReflexAudioRejectedException.class)
                .hasMessage(ReflexV2AiGradingService.MSG_SPOKE_DIFFERENT);

        verify(sink, times(1)).record(AiGradingTokenUsage.Step.TRANSCRIPTION, transcriptionUsage);
        verifyNoMoreInteractions(sink);
        // Bị từ chối ngay sau Lượt A — KHÔNG được gọi tiếp Lượt B (chấm).
        verify(aiClient, times(1)).chatWithAudioJson(any(), any(), any(), any(), any(), any());
    }

    @Test
    void gradeSpeaking_UC23b_transcriptionUnparsable_recordsTranscriptionUsageAndReturnsNull() {
        when(aiClient.chatWithAudioJson(any(), any(), any(), any(), any(), any()))
                .thenReturn(new NineRouterAiClient.AiJsonResponse("không phải JSON", "gemini-3.6-flash-medium", transcriptionUsage));

        assertThat(grade()).isNull();

        verify(sink, times(1)).record(AiGradingTokenUsage.Step.TRANSCRIPTION, transcriptionUsage);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void gradeSpeaking_UC23b_gradingUnparsable_recordsBothUsagesAndReturnsNull() {
        when(aiClient.chatWithAudioJson(any(), any(), any(), any(), any(), any()))
                .thenReturn(transcriptionResponse(WRITTEN))
                .thenReturn(new NineRouterAiClient.AiJsonResponse("không phải JSON", "gemini-3.6-flash-medium", gradingUsage));

        assertThat(grade()).isNull();

        verify(sink, times(1)).record(AiGradingTokenUsage.Step.TRANSCRIPTION, transcriptionUsage);
        verify(sink, times(1)).record(AiGradingTokenUsage.Step.SPEAKING, gradingUsage);
        verifyNoMoreInteractions(sink);
    }

    private ReflexV2AiGradingService.SpeakingResult grade() {
        return service.gradeSpeaking(task, "What is your favourite sport?", new byte[]{1, 2, 3}, "audio/webm", locked, sink);
    }

    private NineRouterAiClient.AiJsonResponse transcriptionResponse(String transcript) {
        return new NineRouterAiClient.AiJsonResponse(
                "{\"transcript\":\"" + transcript + "\",\"speech_seconds\":6,\"longest_pause_seconds\":0.5}",
                "gemini-3.6-flash-medium", transcriptionUsage);
    }
}
