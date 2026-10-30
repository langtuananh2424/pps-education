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
    /**
     * Ép HTTP/1.1: service là uvicorn (h11) qua http thường. Mặc định HttpClient của Java gửi kèm yêu cầu nâng cấp lên HTTP/2
     * (Upgrade: h2c), uvicorn không hiểu, bỏ mất phần thân và trả 422 "thiếu audio/words" (phát hiện khi chạy thật 2026-10-30).
     */
    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${app.ai-grading.pronunciation.engine-url:}")
    private String baseUrl;

    @Value("${app.ai-grading.pronunciation.engine-token:}")
    private String token;

    @Value("${app.ai-grading.pronunciation.engine-timeout-seconds:90}")
    private int timeoutSeconds;

    @Value("${app.ai-grading.direct-max-attempts:3}")
    private int maxAttempts;

    /** Whisper cục bộ chậm hơn Groq nhiều (CPU), nên có hạn riêng. */
    @Value("${app.ai-grading.pronunciation.engine-transcribe-timeout-seconds:120}")
    private int transcribeTimeoutSeconds;

    /** Dùng Whisper của engine làm đường lui khi Groq lỗi hoặc chưa cấu hình (chỉ có tác dụng khi engine đã cấu hình). */
    @Value("${app.ai-grading.pronunciation.engine-whisper-fallback:true}")
    private boolean whisperFallbackEnabled;

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

    /** Một từ có mốc thời gian (giây) của Whisper cục bộ. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TranscribedWord(String word, double start, double end, double probability) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TranscribedSegment(double start, double end, String text) {
    }

    /** Kết quả nhận dạng của engine, cùng khung với verbose_json của Groq. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Transcription(String text, String language, double duration, List<TranscribedSegment> segments,
                                List<TranscribedWord> words, String model) {
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
            return post("/v1/analyze", audioBytes, mimeType, filename, Map.of("words", objectMapper.writeValueAsString(words)),
                    timeoutSeconds, Analysis.class);
        } catch (IOException | InterruptedException e) {
            return failed(e);
        }
    }

    /** Whisper cục bộ đang bật làm đường lui (đã cấu hình engine và không tắt bằng engine-whisper-fallback=false). */
    public boolean canTranscribe() {
        return whisperFallbackEnabled && isConfigured();
    }

    /**
     * Nhận dạng giọng nói bằng Whisper của engine. Dùng khi Groq lỗi: chậm hơn (CPU) nhưng không phụ thuộc dịch vụ ngoài và không đưa
     * âm thanh ra ngoài. Trả null khi lỗi (cùng quy ước với các client AI khác).
     */
    public Transcription transcribe(byte[] audioBytes, String mimeType, String filename) {
        if (!canTranscribe()) {
            return null;
        }
        try {
            return post("/v1/transcribe", audioBytes, mimeType, filename, Map.of("language", "en"), transcribeTimeoutSeconds,
                    Transcription.class);
        } catch (IOException | InterruptedException e) {
            return failed(e);
        }
    }

    private <T> T post(String path, byte[] audio, String mimeType, String filename, Map<String, String> fields, int timeoutSec,
                       Class<T> type) throws IOException, InterruptedException {
        String boundary = "----ppsEngine" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replaceAll("/+$", "") + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(timeoutSec))
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, audio, mimeType, filename, fields)))
                .build();
        HttpResponse<String> response = DirectAiRetry.send("PronunciationEngineClient", maxAttempts,
                () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
        if (response.statusCode() >= 300) {
            log.warn("PronunciationEngineClient: HTTP {} — {}", response.statusCode(), response.body());
            return null;
        }
        return objectMapper.readValue(response.body(), type);
    }

    private static <T> T failed(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        log.warn("PronunciationEngineClient: gọi lỗi. {}", e.getMessage());
        return null;
    }

    private static byte[] multipart(String boundary, byte[] audio, String mimeType, String filename, Map<String, String> fields)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String prefix = "--" + boundary + "\r\n";
        for (Map.Entry<String, String> f : fields.entrySet()) {
            out.write((prefix + "Content-Disposition: form-data; name=\"" + f.getKey() + "\"\r\n\r\n" + f.getValue() + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        String fname = filename == null ? "answer.webm" : filename;
        String ctype = mimeType == null ? "application/octet-stream" : mimeType;
        out.write((prefix + "Content-Disposition: form-data; name=\"audio\"; filename=\"" + fname + "\"\r\n"
                + "Content-Type: " + ctype + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(audio);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
