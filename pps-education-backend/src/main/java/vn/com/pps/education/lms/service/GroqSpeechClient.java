package vn.com.pps.education.lms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — client gọi THẲNG Groq Whisper (speech-to-text), thay
 * thế {@code NineRouterAiClient#transcribe} cho nhánh "chấm AI trực tiếp, không qua 9Router" (xem
 * {@link AiGradingFallbackService}). Port nguyên vẹn từ spike {@code SpeakingAiGradingTestService}
 * (đã verify gọi tay nhiều lần) — {@code response_format=verbose_json} để đọc độ tin cậy từng đoạn
 * ({@code avg_logprob}/{@code no_speech_prob}/{@code compression_ratio}), KHÔNG chặn cứng khi thấp (vẫn
 * trả transcript để chấm tiếp), chỉ đính kèm cảnh báo — giáo viên/hệ thống tự quyết có tin hay không.
 *
 * ĐÃ THỬ và BỎ (đã xác nhận với người dùng 2026-10-22): field {@code prompt} (gợi ý từ vựng từ bài viết
 * Bước 1) — test tay cho thấy KHÔNG nhất quán tốt lên, có lúc làm transcript lệch HƠN. Tham số
 * {@code promptHint} vẫn giữ trong chữ ký để dễ bật lại có kiểm soát sau này nếu có dữ liệu thuyết phục
 * hơn, nhưng mọi caller trong code thật PHẢI truyền {@code null}.
 */
@Service
public class GroqSpeechClient {

    private static final Logger log = LoggerFactory.getLogger(GroqSpeechClient.class);

    private static final String MODEL = "whisper-large-v3-turbo";
    private static final double LOW_CONFIDENCE_AVG_LOGPROB = -1.0;
    private static final double HIGH_NO_SPEECH_PROB = 0.5;
    private static final double HIGH_COMPRESSION_RATIO = 2.4;
    private static final int PROMPT_MAX_CHARS = 800;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${app.ai-grading.groq-api-key:}")
    private String apiKey;

    @Value("${app.ai-grading.groq-direct.timeout-seconds:60}")
    private int requestTimeoutSeconds;

    public record TranscriptionResult(String text, String lowConfidenceWarning) {
    }

    public GroqSpeechClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** @param promptHint PHẢI truyền {@code null} trong code thật — xem Javadoc lớp. */
    public TranscriptionResult transcribe(byte[] audioBytes, String mimeType, String filename, String promptHint) {
        if (!isConfigured()) {
            log.warn("GroqSpeechClient: chưa cấu hình GROQ_API_KEY.");
            return null;
        }
        try {
            String boundary = "----ppsGroqDirect" + UUID.randomUUID();
            byte[] body = buildMultipartBody(boundary, audioBytes, mimeType, filename, promptHint);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/audio/transcriptions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("GroqSpeechClient: HTTP {} — {}", response.statusCode(), response.body());
                return null;
            }
            JsonNode json = objectMapper.readTree(response.body());
            String text = json.path("text").asText("");
            List<String> warnings = new ArrayList<>();
            for (JsonNode seg : json.path("segments")) {
                double avgLogprob = seg.path("avg_logprob").asDouble(0);
                double noSpeechProb = seg.path("no_speech_prob").asDouble(0);
                double compressionRatio = seg.path("compression_ratio").asDouble(0);
                if (avgLogprob < LOW_CONFIDENCE_AVG_LOGPROB || noSpeechProb > HIGH_NO_SPEECH_PROB
                        || compressionRatio > HIGH_COMPRESSION_RATIO) {
                    warnings.add(String.format("\"%s\" (avg_logprob=%.2f, no_speech_prob=%.2f, compression_ratio=%.2f)",
                            seg.path("text").asText("").trim(), avgLogprob, noSpeechProb, compressionRatio));
                }
            }
            String warning = warnings.isEmpty() ? null
                    : "Groq không chắc chắn ở đoạn: " + String.join("; ", warnings) + " — nên nghe lại audio gốc để xác nhận.";
            return new TranscriptionResult(text, warning);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("GroqSpeechClient: gọi lỗi. {}", e.getMessage());
            return null;
        }
    }

    private byte[] buildMultipartBody(String boundary, byte[] audioBytes, String mimeType, String filename, String promptHint)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String prefix = "--" + boundary + "\r\n";
        out.write((prefix + "Content-Disposition: form-data; name=\"model\"\r\n\r\n" + MODEL + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((prefix + "Content-Disposition: form-data; name=\"response_format\"\r\n\r\nverbose_json\r\n").getBytes(StandardCharsets.UTF_8));
        if (promptHint != null && !promptHint.isBlank()) {
            String trimmed = promptHint.length() > PROMPT_MAX_CHARS ? promptHint.substring(0, PROMPT_MAX_CHARS) : promptHint;
            out.write((prefix + "Content-Disposition: form-data; name=\"prompt\"\r\n\r\n" + trimmed + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        String fname = filename == null ? "audio.webm" : filename;
        String ctype = mimeType == null ? "application/octet-stream" : mimeType;
        out.write((prefix + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fname + "\"\r\n"
                + "Content-Type: " + ctype + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(audioBytes);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
