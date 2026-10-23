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

    /** Azure short-audio chỉ nhận audio ngắn (~60s): bài dài hơn mức này được cắt theo câu, xem {@link AzureAudioChunker}. */
    @Value("${app.ai-grading.azure-direct.max-chunk-seconds:50}")
    private double maxChunkSeconds;

    /** {@code chunkCount}: số đoạn audio đã gửi Azure (1 = bài ngắn gửi nguyên). */
    public record Result(Integer accuracyScore, Integer fluencyScore, Integer completenessScore,
                          Integer prosodyScore, Integer overallScore, List<PronunciationWordDetail> words, String rawJson,
                          int chunkCount) {
        Result(Integer accuracyScore, Integer fluencyScore, Integer completenessScore, Integer prosodyScore,
               Integer overallScore, List<PronunciationWordDetail> words, String rawJson) {
            this(accuracyScore, fluencyScore, completenessScore, prosodyScore, overallScore, words, rawJson, 1);
        }

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
        return assessSegmented(audioBytes, mimeType, referenceText, List.of());
    }

    /**
     * Bài ≤ {@code max-chunk-seconds} gửi nguyên như {@link #assess}. Bài dài hơn được cắt theo câu (mốc thời gian
     * {@code segments} của Groq), chấm từng đoạn với ReferenceText riêng rồi ghép: điểm trung bình theo thời lượng
     * đoạn, danh sách từ nối theo thứ tự. KHÔNG chia được (thiếu segments, hoặc 1 câu quá dài) hoặc BẤT KỲ đoạn nào
     * lỗi thì trả lỗi — caller rơi về điểm Phát âm của 9Router, không ghép điểm một phần.
     */
    public Result assessSegmented(byte[] audioBytes, String mimeType, String referenceText,
                                  List<GroqSpeechClient.Segment> segments) {
        if (!isConfigured()) {
            return Result.error("Chưa cấu hình AZURE_SPEECH_KEY/AZURE_SPEECH_REGION.");
        }
        java.util.Optional<byte[]> wav = audioTranscoder.toWav(audioBytes, mimeType);
        if (wav.isEmpty()) {
            return Result.error("Không convert được audio sang WAV 16kHz (ffmpeg lỗi/thiếu).");
        }
        java.util.Optional<AzureAudioChunker.Pcm> pcm = AzureAudioChunker.parse(wav.get());
        if (pcm.isEmpty() || pcm.get().durationSec() <= maxChunkSeconds) {
            return assessWav(wav.get(), referenceText);
        }
        List<AzureAudioChunker.Segment> segs = segments == null ? List.of() : segments.stream()
                .map(g -> new AzureAudioChunker.Segment(g.startSec(), g.endSec(), g.text())).toList();
        java.util.Optional<List<AzureAudioChunker.Chunk>> plan =
                AzureAudioChunker.plan(segs, pcm.get().durationSec(), maxChunkSeconds);
        if (plan.isEmpty()) {
            return Result.error("Bài nói dài " + Math.round(pcm.get().durationSec()) + "s nhưng không chia được theo câu (thiếu mốc thời gian Groq).");
        }
        List<Result> results = new java.util.ArrayList<>();
        for (AzureAudioChunker.Chunk chunk : plan.get()) {
            byte[] part = AzureAudioChunker.slice(wav.get(), pcm.get(), chunk.startSec(), chunk.endSec());
            Result r = assessWav(part, chunk.text());
            if (!r.hasScores()) {
                return Result.error("Đoạn " + (results.size() + 1) + "/" + plan.get().size() + " lỗi: " + r.rawJson());
            }
            results.add(r);
        }
        return merge(results, plan.get());
    }

    /** Ghép điểm các đoạn, trọng số = thời lượng đoạn. */
    static Result merge(List<Result> results, List<AzureAudioChunker.Chunk> chunks) {
        double total = chunks.stream().mapToDouble(AzureAudioChunker.Chunk::durationSec).sum();
        List<PronunciationWordDetail> words = new ArrayList<>();
        StringBuilder raw = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            words.addAll(results.get(i).words());
            raw.append(i == 0 ? "" : ",").append(results.get(i).rawJson());
        }
        raw.append("]");
        return new Result(weighted(results, chunks, total, Result::accuracyScore), weighted(results, chunks, total, Result::fluencyScore),
                weighted(results, chunks, total, Result::completenessScore), weighted(results, chunks, total, Result::prosodyScore),
                weighted(results, chunks, total, Result::overallScore), words, raw.toString(), results.size());
    }

    private static Integer weighted(List<Result> results, List<AzureAudioChunker.Chunk> chunks, double total,
                                    java.util.function.Function<Result, Integer> field) {
        double sum = 0;
        double weight = 0;
        for (int i = 0; i < results.size(); i++) {
            Integer v = field.apply(results.get(i));
            if (v != null) {
                sum += v * chunks.get(i).durationSec();
                weight += chunks.get(i).durationSec();
            }
        }
        return weight == 0 || total == 0 ? null : (int) Math.round(sum / weight);
    }

    /** Gọi Azure cho 1 WAV 16kHz mono (đã đủ ngắn). */
    private Result assessWav(byte[] wavBytes, String referenceText) {
        try {
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
                    .POST(HttpRequest.BodyPublishers.ofByteArray(wavBytes))
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
