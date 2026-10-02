package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-74 — request STT gửi kèm {@code language} (và {@code prompt} gợi ý chính tả) CHỈ khi caller truyền vào:
 * trợ lý nhận xét ép "vi", còn luồng phiên âm bài nói tiếng Anh (UC-23b) không được bị ép sang tiếng Việt.
 * Kiểm thẳng body multipart, không gọi mạng.
 */
class NineRouterAiClientTranscribeTest {

    private final NineRouterAiClient client = new NineRouterAiClient(new ObjectMapper(), 5, AiUsageSink.NO_OP);

    private String body(String spellingHint, String language) throws Exception {
        return new String(client.buildMultipartBody("b", "groq/whisper-large-v3-turbo", new byte[]{1}, "audio/webm",
                spellingHint, language), StandardCharsets.UTF_8);
    }

    @Test
    void buildMultipartBody_UC74_sendsLanguageAndPromptWhenProvided() throws Exception {
        String body = body("Học sinh: Nguyễn Văn An.", "vi");

        assertThat(body).contains("name=\"language\"\r\n\r\nvi\r\n");
        assertThat(body).contains("name=\"prompt\"\r\n\r\nHọc sinh: Nguyễn Văn An.\r\n");
    }

    @Test
    void buildMultipartBody_UC23b_omitsLanguageAndPromptForLegacyCallers() throws Exception {
        String body = body(null, null);

        assertThat(body).doesNotContain("name=\"language\"").doesNotContain("name=\"prompt\"");
        assertThat(body).contains("name=\"model\"");
    }

    /** Lớp sĩ số đông: ~45 họ tên tiếng Việt có dấu — dài hơn ngưỡng prompt của Groq (lỗi staging 2026-10-02, lớp 8D). */
    private static String largeClassHint() {
        String[] last = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Đặng"};
        String[] middle = {"Thị", "Văn", "Ngọc", "Đức", "Thùy"};
        String[] first = {"Ánh", "Hưởng", "Nguyệt", "Khuê", "Phượng", "Quỳnh", "Đạt", "Thư", "Việt"};
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            names.add(last[i % last.length] + " " + middle[i % middle.length] + " " + first[(i * 7) % first.length]);
        }
        return "Nhận xét lớp 8D. Học sinh: " + String.join(", ", names) + ".";
    }

    @Test
    void fitSttPrompt_largeClass_cutsAtNameBoundaryWithinByteLimit() {
        String hint = largeClassHint();
        assertThat(hint.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(896);

        String fitted = NineRouterAiClient.fitSttPrompt(hint);

        assertThat(fitted.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(NineRouterAiClient.MAX_STT_PROMPT_BYTES);
        assertThat(fitted).startsWith("Nhận xét lớp 8D. Học sinh: ").endsWith(".");
        // Cắt đúng ranh giới tên: phần đã giữ là tiền tố của chuỗi gốc tới ngay trước 1 dấu ", ".
        String kept = fitted.substring(0, fitted.length() - 1);
        assertThat(hint).startsWith(kept + ", ");
    }

    @Test
    void fitSttPrompt_shortHintUnchanged_blankIsNull() {
        assertThat(NineRouterAiClient.fitSttPrompt("Học sinh: Nguyễn Văn An.")).isEqualTo("Học sinh: Nguyễn Văn An.");
        assertThat(NineRouterAiClient.fitSttPrompt("  ")).isNull();
        assertThat(NineRouterAiClient.fitSttPrompt(null)).isNull();
    }

    @Test
    void fitSttPrompt_noNameBoundary_truncatesWithoutSplittingCharacters() {
        String fitted = NineRouterAiClient.fitSttPrompt("ệ".repeat(400));

        assertThat(fitted.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(NineRouterAiClient.MAX_STT_PROMPT_BYTES);
        assertThat(fitted).matches("ệ+");
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void transcribe_providerRejectsPrompt_retriesOnceWithoutPrompt() throws Exception {
        NineRouterAiClient spyClient = spy(client);
        doReturn(response(400, "{\"error\":{\"message\":\"prompt length must be 896 characters or fewer\"}}"))
                .when(spyClient).postTranscription(any(), any(), any(), anyString(), any());
        doReturn(response(200, "{\"text\":\" Cả lớp hôm nay rất ngoan. \"}"))
                .when(spyClient).postTranscription(any(), any(), any(), isNull(), any());

        String text = spyClient.transcribe(new byte[]{1}, "audio/mp4", "groq/whisper-large-v3-turbo", "Học sinh: An.", "vi");

        assertThat(text).isEqualTo("Cả lớp hôm nay rất ngoan.");
        verify(spyClient).postTranscription(any(), eq("audio/mp4"), any(), eq("Học sinh: An."), eq("vi"));
        verify(spyClient).postTranscription(any(), eq("audio/mp4"), any(), isNull(), eq("vi"));
    }

    @Test
    void transcribe_errorWithoutPrompt_doesNotRetry() throws Exception {
        NineRouterAiClient spyClient = spy(client);
        doReturn(response(400, "bad audio")).when(spyClient).postTranscription(any(), any(), any(), any(), any());

        assertThat(spyClient.transcribe(new byte[]{1}, "audio/mp4", "groq/whisper-large-v3-turbo", null, null)).isNull();
        verify(spyClient).postTranscription(any(), any(), any(), isNull(), isNull());
        verify(spyClient, never()).postTranscription(any(), any(), any(), anyString(), any());
    }
}
