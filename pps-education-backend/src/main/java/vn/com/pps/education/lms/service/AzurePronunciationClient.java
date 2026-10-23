package vn.com.pps.education.lms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.com.pps.education.lms.dto.PronunciationPhonemeDetail;
import vn.com.pps.education.lms.dto.PronunciationWordDetail;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — client gọi Azure Speech Pronunciation Assessment,
 * dùng cho nhánh "chấm AI trực tiếp" (xem {@link AiGradingFallbackService}). Port từ spike
 * {@code SpeakingAiGradingTestService#callAzurePronunciation} sau khi đã sửa 2 lỗi phát hiện qua test
 * thật — xem Javadoc {@link #assess}.
 */
@Service
public class AzurePronunciationClient {

    private static final Logger log = LoggerFactory.getLogger(AzurePronunciationClient.class);
    private static final String LANGUAGE = "en-US";

    private final ObjectMapper objectMapper;
    private final AudioTranscoder audioTranscoder;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${app.ai-grading.azure-speech-key:}")
    private String apiKey;

    @Value("${app.ai-grading.azure-speech-region:}")
    private String region;

    @Value("${app.ai-grading.azure-direct.timeout-seconds:60}")
    private int requestTimeoutSeconds;

    @Value("${app.ai-grading.direct-max-attempts:3}")
    private int maxAttempts;

    public record Result(Integer accuracyScore, Integer fluencyScore, Integer completenessScore,
                          Integer prosodyScore, Integer overallScore, List<PronunciationWordDetail> words, String rawJson) {
        static Result error(String message) {
            return new Result(null, null, null, null, null, null, message);
        }

        public boolean hasScores() {
            return accuracyScore != null;
        }
    }

    public AzurePronunciationClient(ObjectMapper objectMapper, AudioTranscoder audioTranscoder) {
        this.objectMapper = objectMapper;
        this.audioTranscoder = audioTranscoder;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && region != null && !region.isBlank();
    }

    /**
     * Fix 2026-10-22 lần 1 (đã xác nhận với người dùng, test thật) — {@code ReferenceText} rỗng
     * (unscripted) khiến Azure tự do đoán cả câu, đã verify gọi tay 2 lần liên tiếp: Azure đoán sai hẳn
     * nguyên cụm dù phần đầu câu nghe đúng gần tuyệt đối. Bắt buộc truyền {@code referenceText} = transcript
     * đã có (Groq) để ép "forced-align" — KHÔNG dùng bài viết Bước 1 làm reference (học sinh có thể nói
     * khác bài viết, ép khớp bài viết sẽ chấm phát âm cho từ chưa chắc đã được nói).
     *
     * Fix 2026-10-22 lần 2 (đã xác nhận với người dùng, test thật với response đầy đủ) — field điểm từng
     * từ/âm vị ({@code AccuracyScore}/{@code ErrorType}) nằm PHẲNG trực tiếp trong object {@code Words[]}/
     * {@code Phonemes[]}, KHÔNG lồng qua {@code PronunciationAssessment} như tài liệu SDK mô tả (khác
     * phiên bản API) — đọc phẳng trực tiếp.
     */
    public Result assess(byte[] audioBytes, String mimeType, String referenceText) {
        if (!isConfigured()) {
            return Result.error("Chưa cấu hình AZURE_SPEECH_KEY/AZURE_SPEECH_REGION.");
        }
        try {
            java.util.Optional<byte[]> wav = audioTranscoder.toWav(audioBytes, mimeType);
            if (wav.isEmpty()) {
                return Result.error("Không convert được audio sang WAV 16kHz (ffmpeg lỗi/thiếu).");
            }

            ObjectNode config = objectMapper.createObjectNode();
            config.put("ReferenceText", referenceText == null ? "" : referenceText);
            config.put("GradingSystem", "HundredMark");
            config.put("Granularity", "Phoneme");
            config.put("Dimension", "Comprehensive");
            config.put("EnableMiscue", referenceText != null && !referenceText.isBlank());
            config.put("EnableProsodyAssessment", true);
            String pronunciationHeader = Base64.getEncoder()
                    .encodeToString(objectMapper.writeValueAsString(config).getBytes(StandardCharsets.UTF_8));

            String url = "https://" + region + ".stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1"
                    + "?language=" + LANGUAGE + "&format=detailed";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .header("Content-Type", "audio/wav; codecs=audio/pcm; samplerate=16000")
                    .header("Accept", "application/json")
                    .header("Pronunciation-Assessment", pronunciationHeader)
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(wav.get()))
                    .build();
            HttpResponse<String> response = DirectAiRetry.send("AzurePronunciationClient", maxAttempts, () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
            if (response.statusCode() >= 300) {
                return Result.error("Azure lỗi (HTTP " + response.statusCode() + "): " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode assessment = json.path("NBest").path(0);
            if (assessment.isMissingNode() || assessment.path("AccuracyScore").isMissingNode()) {
                return Result.error("Azure không có điểm phát âm ở NBest[0] — audio quá ngắn/im lặng: " + response.body());
            }
            return new Result(
                    assessment.path("AccuracyScore").isMissingNode() ? null : assessment.path("AccuracyScore").asInt(),
                    assessment.path("FluencyScore").isMissingNode() ? null : assessment.path("FluencyScore").asInt(),
                    assessment.path("CompletenessScore").isMissingNode() ? null : assessment.path("CompletenessScore").asInt(),
                    assessment.path("ProsodyScore").isMissingNode() ? null : assessment.path("ProsodyScore").asInt(),
                    assessment.path("PronScore").isMissingNode() ? null : assessment.path("PronScore").asInt(),
                    parseWords(assessment), response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Result.error("Không gọi được Azure: " + e.getMessage());
        }
    }

    private List<PronunciationWordDetail> parseWords(JsonNode assessment) {
        List<PronunciationWordDetail> words = new ArrayList<>();
        for (JsonNode w : assessment.path("Words")) {
            Double wordAccuracy = w.path("AccuracyScore").isMissingNode() ? null : w.path("AccuracyScore").asDouble();
            String errorType = w.path("ErrorType").isMissingNode() ? null : w.path("ErrorType").asText();
            List<PronunciationPhonemeDetail> phonemes = new ArrayList<>();
            for (JsonNode p : w.path("Phonemes")) {
                Double phAccuracy = p.path("AccuracyScore").isMissingNode() ? null : p.path("AccuracyScore").asDouble();
                phonemes.add(new PronunciationPhonemeDetail(p.path("Phoneme").asText(""), phAccuracy));
            }
            words.add(new PronunciationWordDetail(w.path("Word").asText(""), wordAccuracy, errorType, phonemes));
        }
        return words;
    }
}
