package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

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
}
