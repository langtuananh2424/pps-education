package vn.com.pps.education.lms.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.com.pps.education.common.pronunciation.PronunciationInput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gọi service suy luận phát âm {@code pps-speaking-engine} (Kaldi GOP + GOPT + wav2vec2 IPA, chạy riêng, chỉ nghe nội bộ).
 * Service chỉ trả SỐ LIỆU THÔ; mọi logic chấm điểm nằm ở backend ({@link GoptPronunciationService}).
 *
 * <p>Lỗi (không cấu hình, mạng, HTTP lỗi, JSON hỏng) đều trả {@code null} và ghi log — đúng cách các client AI trực tiếp khác
 * ({@link GroqSpeechClient}, {@link AzurePronunciationClient}) báo lỗi, để luồng chấm tự rơi về đường dự phòng.
 */
@Service
public class PronunciationEngineClient {

    private static final Logger log = LoggerFactory.getLogger(PronunciationEngineClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${app.ai-grading.pronunciation.engine-url:}")
    private String baseUrl;

    @Value("${app.ai-grading.pronunciation.engine-token:}")
    private String token;

    @Value("${app.ai-grading.pronunciation.engine-timeout-seconds:90}")
    private int timeoutSeconds;

    @Value("${app.ai-grading.direct-max-attempts:3}")
    private int maxAttempts;

    /** Chất lượng thu: biên độ đỉnh, nền ồn, chênh tiếng/ồn, số giây có tiếng và tổng số giây. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AudioInfo(double peak, double noiseFloorDb, double snrDb, double activeSeconds, double seconds) {
    }

    /** Điểm IPA từng từ (0-100) của wav2vec2 so với IPA chuẩn của espeak. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record IpaWord(String word, String expectedIpa, Integer score, List<String> issues, String heardIpa) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Ipa(String heardIpa, List<IpaWord> words) {
    }

    /** gopt.error khác null: Kaldi không gióng hàng được âm thanh này. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Analysis(Boolean noSignal, AudioInfo audio, Ipa ipa, PronunciationInput.Gopt gopt, Map<String, Double> latencySeconds) {
    }

    public PronunciationEngineClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && token != null && !token.isBlank();
    }

    /** @param words các từ mục tiêu theo thứ tự (backend đã tách từ và đổi số thành chữ). */
    public Analysis analyze(byte[] audioBytes, String mimeType, String filename, List<String> words) {
        if (!isConfigured()) {
            log.warn("PronunciationEngineClient: chưa cấu hình engine-url / engine-token.");
            return null;
        }
        try {
            String boundary = "----ppsEngine" + UUID.randomUUID();
            byte[] body = multipart(boundary, audioBytes, mimeType, filename, objectMapper.writeValueAsString(words));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/v1/analyze"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = DirectAiRetry.send("PronunciationEngineClient", maxAttempts,
                    () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
            if (response.statusCode() >= 300) {
                log.warn("PronunciationEngineClient: HTTP {} — {}", response.statusCode(), response.body());
                return null;
            }
            return objectMapper.readValue(response.body(), Analysis.class);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("PronunciationEngineClient: gọi lỗi. {}", e.getMessage());
            return null;
        }
    }

    private static byte[] multipart(String boundary, byte[] audio, String mimeType, String filename, String wordsJson) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String prefix = "--" + boundary + "\r\n";
        out.write((prefix + "Content-Disposition: form-data; name=\"words\"\r\n\r\n" + wordsJson + "\r\n").getBytes(StandardCharsets.UTF_8));
        String fname = filename == null ? "answer.webm" : filename;
        String ctype = mimeType == null ? "application/octet-stream" : mimeType;
        out.write((prefix + "Content-Disposition: form-data; name=\"audio\"; filename=\"" + fname + "\"\r\n"
                + "Content-Type: " + ctype + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(audio);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
