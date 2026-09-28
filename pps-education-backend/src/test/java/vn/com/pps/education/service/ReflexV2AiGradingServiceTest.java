package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.com.pps.education.common.AiTokenUsage;
import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.common.ReflexV2Task;
import vn.com.pps.education.domain.AiGradingTokenUsage;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.exception.ReflexAudioRejectedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
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
    private final ReflexV2AiGradingService.Step1Anchor locked = new ReflexV2AiGradingService.Step1Anchor(80, WRITTEN);

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

    /**
     * Từ 23/9 Ngữ pháp KHÔNG còn khoá: chấm lại từ transcript, nhưng không tụt dưới NỬA điểm Bước 1
     * (80% → sàn 40%). Phát âm chỉ để tham khảo nên không tính vào điểm mở khoá câu tiếp theo.
     */
    @Test
    void gradeSpeaking_UC23b_MainFlow_regradesGrammarFromTranscript_floorsAtHalfOfStep1_pronunciationIsReferenceOnly() {
        when(aiClient.chatWithAudioJson(any(), any(), any(), any(), any(), any()))
                .thenReturn(transcriptionResponse(WRITTEN))
                .thenReturn(gradingResponse("[]", 0, 1));

        ReflexV2AiGradingService.SpeakingResult result = grade();

        assertThat(result.criteria()).extracting(CriteriaScoreItem::criterion)
                .containsExactly("Grammar and Vocabulary", "Pronunciation (tham khảo)");
        assertThat(result.criteria()).extracting(CriteriaScoreItem::percent).containsExactly(40, 90);
        assertThat(result.unlockPercent()).isEqualTo(40);
        assertThat(result.finalPercent()).isEqualTo(65);
        assertThat(result.audit()).containsEntry("grammarStep1Percent", 80).containsEntry("grammarRegradedPercent", 0);
        verify(sink).record(AiGradingTokenUsage.Step.TRANSCRIPTION, transcriptionUsage);
        verify(sink).record(AiGradingTokenUsage.Step.SPEAKING, gradingUsage);
    }

    /** Một lỗi không bị phạt hai lần: trần theo lỗi đã tô (10%) không được đạp xuyên sàn Ngữ pháp (40%). */
    @Test
    void gradeSpeaking_UC23b_A_manyErrorsCapDoesNotPushGrammarBelowStep1Floor() {
        String highlights = "[" + highlight("favourite", "thi_dong_tu") + "," + highlight("sport", "thi_dong_tu") + ","
                + highlight("football", "mao_tu") + "," + highlight("play", "gioi_tu") + "," + highlight("friends", "so_it_so_nhieu") + ","
                + highlight("weekend", "dung_tu") + "," + highlight("with", "tu_loai") + "]";
        when(aiClient.chatWithAudioJson(any(), any(), any(), any(), any(), any()))
                .thenReturn(transcriptionResponse(WRITTEN))
                .thenReturn(gradingResponse(highlights, 0, 1));

        ReflexV2AiGradingService.SpeakingResult result = grade();

        assertThat(result.criteria().get(0).percent()).isEqualTo(40);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> caps = (Map<String, List<String>>) result.audit().get("caps");
        assertThat(caps.get("GV")).anyMatch(n -> n.contains("lỗi đã tô")).anyMatch(n -> n.contains("sàn"));
        assertThat(result.audit()).containsEntry("spokenRedCount", 2);
    }

    /** Không nói được gì: không có sàn Ngữ pháp — điểm phải là thật (0%), dù bài viết được 80%. */
    @Test
    void gradeSpeaking_UC23b_A_silentRecording_hasNoGrammarFloor() {
        when(aiClient.chatWithAudioJson(any(), any(), any(), any(), any(), any()))
                .thenReturn(transcriptionResponse(""))
                .thenReturn(new NineRouterAiClient.AiJsonResponse(
                        "{\"counting_notes\":\"\",\"gates_triggered\":[\"C1\"],\"insufficient_data\":true,\"criteria\":[],\"highlights\":[],\"feedback\":\"\"}",
                        "gemini-3.6-flash-medium", gradingUsage));

        ReflexV2AiGradingService.SpeakingResult result = grade();

        assertThat(result.criteria()).extracting(CriteriaScoreItem::percent).containsExactly(0, 0);
        assertThat(result.unlockPercent()).isZero();
    }

    /** Học sinh đọc lệch so với bài viết Bước 1 → danh sách "đã viết→nghe được" được đưa vào lượt chấm để tô lỗi. */
    @Test
    @SuppressWarnings("unchecked")
    void gradeSpeaking_UC23b_MainFlow_passesDeviantWordsFromStep1ComparisonToTheGradingPrompt() {
        when(aiClient.chatWithAudioJson(any(), any(), any(), any(), any(), any()))
                .thenReturn(transcriptionResponse("My favurit sport is football because I play it with my friends every weekend."))
                .thenReturn(new NineRouterAiClient.AiJsonResponse("không phải JSON", "gemini-3.6-flash-medium", gradingUsage));

        assertThat(grade()).isNull();

        ArgumentCaptor<List<String>> deviant = ArgumentCaptor.forClass(List.class);
        verify(prompts).speakingUser(any(), any(), eq(WRITTEN), any(), any(), deviant.capture(), anyDouble(), anyDouble(), anyDouble());
        assertThat(deviant.getValue()).containsExactly("favourite→favurit");
    }

    private static String highlight(String quote, String tag) {
        return "{\"quote\":\"" + quote + "\",\"occurrence\":1,\"level\":\"yellow\",\"tag\":\"" + tag + "\"}";
    }

    private NineRouterAiClient.AiJsonResponse gradingResponse(String highlightsJson, int gvCheckpoint, int pCheckpoint) {
        String gv = "{\"code\":\"GV\",\"evidence\":\"\",\"checkpoints\":[" + String.join(",", java.util.Collections.nCopies(5, String.valueOf(gvCheckpoint))) + "],\"cap_percent\":100}";
        String p = "{\"code\":\"P\",\"evidence\":\"\",\"checkpoints\":[" + String.join(",", java.util.Collections.nCopies(5, String.valueOf(pCheckpoint))) + "],\"cap_percent\":100}";
        return new NineRouterAiClient.AiJsonResponse(
                "{\"counting_notes\":\"\",\"gates_triggered\":[],\"insufficient_data\":false,\"criteria\":[" + gv + "," + p + "],\"highlights\":"
                        + highlightsJson + ",\"feedback\":\"Em nói rõ ý.\"}",
                "gemini-3.6-flash-medium", gradingUsage);
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
