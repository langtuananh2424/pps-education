package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.domain.Curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UC-23b (Video phản xạ), bước Speaking — V178 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-09-16): transcript đánh dấu lỗi {@code {{err}}...{{/err}}} + criteriaScores tách riêng khỏi
 * feedback (rút gọn feedback prose 7 mục cũ xuống 1 đoạn ngắn). Test thuần Service logic, mock hết các
 * dependency gọi mạng/đọc file — không chạm DB nên không cần Testcontainers (xem
 * .claude/rules/testing.md, mirror MediaStorageServiceTest).
 */
class ReflexSpeakingContentAiGradingServiceTest {

    private final RubricByGradeTrackLoader rubricLoader = mock(RubricByGradeTrackLoader.class);
    private final NineRouterAiClient nineRouterAiClient = mock(NineRouterAiClient.class);
    private final PromptTemplateLoader promptTemplateLoader = mock(PromptTemplateLoader.class);
    private final ReflexSpeakingContentAiGradingService service = new ReflexSpeakingContentAiGradingService(
            new ObjectMapper(), rubricLoader, nineRouterAiClient, promptTemplateLoader);

    private final Curriculum curriculum = mock(Curriculum.class);

    private void stubRubricAndPrompt() {
        when(rubricLoader.load(anyString(), any(), any())).thenReturn("| Ngữ pháp | Phát âm |\n| --- | --- |");
        when(promptTemplateLoader.load(anyString(), anyMap())).thenReturn("system prompt");
    }

    @Test
    void grade_UC23b_MainFlow_parsesTranscriptCriteriaScoresAndShortFeedback() {
        stubRubricAndPrompt();
        String rawText = "{"
                + "\"transcript\": \"I go {{err}}to school yesterday{{/err}}.\","
                + "\"criteriaScores\": [{\"criterion\": \"Ngữ pháp\", \"percent\": 70}, {\"criterion\": \"Phát âm\", \"percent\": 80}],"
                + "\"scorePercent\": 75,"
                + "\"feedback\": \"Phát âm rõ ràng, nhưng cần chú ý thì quá khứ.\"}";
        when(nineRouterAiClient.chatWithAudioWithUsage(anyString(), anyString(), any(byte[].class), anyString(), any()))
                .thenReturn(new NineRouterAiClient.AiTextResponse(rawText, null));

        ReflexSpeakingContentAiGradingService.GradeResult result =
                service.grade("audio-bytes".getBytes(), "audio/webm", "What did you do yesterday?", curriculum);

        assertThat(result).isNotNull();
        assertThat(result.transcript()).isEqualTo("I go {{err}}to school yesterday{{/err}}.");
        assertThat(result.criteriaScores()).containsExactly(
                new CriteriaScoreItem("Ngữ pháp", 70),
                new CriteriaScoreItem("Phát âm", 80));
        assertThat(result.scorePercent()).isEqualTo(75);
        assertThat(result.feedback()).isEqualTo("Phát âm rõ ràng, nhưng cần chú ý thì quá khứ.");
    }

    @Test
    void grade_UC23b_A_offTopic_returnsEmptyCriteriaScoresAndZeroScore() {
        stubRubricAndPrompt();
        String rawText = "{"
                + "\"transcript\": \"I like sleeping.\","
                + "\"criteriaScores\": [],"
                + "\"scorePercent\": 0,"
                + "\"feedback\": \"LẠC ĐỀ: câu trả lời không liên quan tới câu hỏi.\"}";
        when(nineRouterAiClient.chatWithAudioWithUsage(anyString(), anyString(), any(byte[].class), anyString(), any()))
                .thenReturn(new NineRouterAiClient.AiTextResponse(rawText, null));

        ReflexSpeakingContentAiGradingService.GradeResult result =
                service.grade("audio-bytes".getBytes(), "audio/webm", "What subject do you like most?", curriculum);

        assertThat(result).isNotNull();
        assertThat(result.criteriaScores()).isEmpty();
        assertThat(result.scorePercent()).isZero();
        assertThat(result.feedback()).startsWith("LẠC ĐỀ:");
    }

    @Test
    void grade_UC23b_A_missingRubric_returnsNull() {
        when(rubricLoader.load(anyString(), any(), any())).thenReturn(null);

        ReflexSpeakingContentAiGradingService.GradeResult result =
                service.grade("audio-bytes".getBytes(), "audio/webm", "What subject do you like most?", curriculum);

        assertThat(result).isNull();
    }
}
