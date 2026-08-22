package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — UC-23b (Video phản xạ) V2, bước 2
 * của mỗi câu hỏi (SAU khi đạt phần viết trước — xem {@link ReflexWritingGrammarAiGradingService}):
 * chuyển giọng nói (audio đã ghi) sang chữ RỒI chấm nội dung theo rubric — đạt ngưỡng mới mở khoá câu
 * tiếp theo (xem {@link ReflexSequentialGradingService}).
 *
 * CHỈ dùng Gemini (GEMINI_API_KEY) — Gemini nhận audio đa phương thức trực tiếp trong 1 lệnh gọi
 * (transcribe + chấm luôn), khác Claude (không nhận input audio) và OpenAI (STT tốt nhưng cần
 * billing, xem SpeakingAiGradingTestService — Gemini là lựa chọn người dùng đã chốt cho luồng này).
 *
 * Rubric đọc 1 lần lúc khởi động từ {@code resources/rubrics/reflex-speaking-content-rubric.md} —
 * rubric thật do người dùng cung cấp 2026-08-22 (band 0-9 theo 4 tiêu chí: Fluency & Coherence,
 * Lexical Resource, Grammatical Range & Accuracy, Pronunciation). {@link #parseResult} quy đổi
 * overallBand (0-9) sang % (overallBand/9*100, làm tròn) để khớp ngưỡng đạt 70% của
 * {@link ReflexSequentialGradingService}. Lỗi gọi API trả về {@code null} — caller tự quyết định.
 */
@Service
public class ReflexSpeakingContentAiGradingService {

    private static final Logger log = LoggerFactory.getLogger(ReflexSpeakingContentAiGradingService.class);

    private static final String RUBRIC_CLASSPATH = "rubrics/reflex-speaking-content-rubric.md";
    private static final String GEMINI_MODEL = "gemini-flash-latest";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final String rubric;

    @Value("${app.ai-grading.gemini-api-key:}")
    private String geminiApiKey;

    public ReflexSpeakingContentAiGradingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.rubric = readRubric();
    }

    public record GradeResult(String transcript, int scorePercent, String feedback) {
    }

    /** Số lần thử tối đa (1 lần đầu + tối đa RETRY lần) khi gặp lỗi tạm thời (503/5xx, KHÔNG gồm 429) — xem {@link #isRetryable}. */
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1500;

    /**
     * audioBytes: tải trực tiếp từ audioUrl đã lưu R2 (caller tự tải, xem
     * {@link ReflexSequentialGradingService}) — tránh phụ thuộc MultipartFile ở service này.
     * Trả null nếu chưa cấu hình GEMINI_API_KEY HOẶC gọi AI thất bại (kể cả sau khi đã thử lại) —
     * caller tự quyết định.
     *
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — tự thử lại tối đa
     * {@link #MAX_ATTEMPTS} lần khi gặp lỗi tạm thời (mirror {@link WritingAiGradingService}) — audio
     * base64 đã encode 1 lần dùng lại cho mọi lần thử, không encode lại mỗi vòng lặp.
     */
    public GradeResult grade(byte[] audioBytes, String mimeType) {
        if (audioBytes == null || audioBytes.length == 0 || geminiApiKey.isBlank()) {
            if (geminiApiKey.isBlank()) {
                log.warn("ReflexSpeakingContentAiGradingService: chưa cấu hình GEMINI_API_KEY.");
            }
            return null;
        }
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return callGemini(audioBytes, mimeType);
            } catch (Exception e) {
                if (attempt < MAX_ATTEMPTS && isRetryable(e)) {
                    log.warn("ReflexSpeakingContentAiGradingService: lỗi tạm thời, thử lại lần {}/{}. {}", attempt + 1, MAX_ATTEMPTS, e.getMessage());
                    sleepQuietly(RETRY_DELAY_MS);
                    continue;
                }
                log.warn("ReflexSpeakingContentAiGradingService: gọi AI chấm thất bại. {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    private GradeResult callGemini(byte[] audioBytes, String mimeType) throws IOException, InterruptedException {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode systemInstruction = payload.putObject("system_instruction");
        systemInstruction.putArray("parts").addObject().put("text", systemPrompt());
        ArrayNode contents = payload.putArray("contents");
        ArrayNode parts = contents.addObject().putArray("parts");
        parts.addObject().put("text", "Đây là audio câu trả lời speaking của học sinh. Hãy transcribe rồi chấm theo tiêu chí đã cho.");
        ObjectNode inlineData = parts.addObject().putObject("inline_data");
        inlineData.put("mime_type", mimeType == null ? "audio/webm" : mimeType);
        inlineData.put("data", Base64.getEncoder().encodeToString(audioBytes));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent"))
                .header("x-goog-api-key", geminiApiKey)
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("Gemini API lỗi (HTTP " + response.statusCode() + "): " + response.body());
        }
        JsonNode json = objectMapper.readTree(response.body());
        String rawText = json.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        return parseResult(rawText);
    }

    /**
     * CHỈ HTTP 503/5xx — đáng thử lại ngay. KHÔNG retry 429 — đã gặp thực tế 2026-08-22: 429 của
     * Gemini free tier là RESOURCE_EXHAUSTED theo quota NGÀY (limit 20/ngày, "retry in Xs" thực tế lên
     * tới 50+s), retry ngay càng làm cạn quota nhanh hơn mà gần như chắc chắn vẫn thất bại — xem
     * {@link WritingAiGradingService#isRetryable}.
     */
    private boolean isRetryable(Exception e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("HTTP 503") || msg.contains("HTTP 500") || msg.contains("HTTP 502") || msg.contains("HTTP 504"));
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String readRubric() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RUBRIC_CLASSPATH)) {
            if (in == null) {
                log.warn("ReflexSpeakingContentAiGradingService: không tìm thấy {} trên classpath.", RUBRIC_CLASSPATH);
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("ReflexSpeakingContentAiGradingService: đọc rubric thất bại. {}", e.getMessage());
            return "";
        }
    }

    private String systemPrompt() {
        return "Bạn là giám khảo chấm Speaking tiếng Anh cho học sinh trung tâm Anh ngữ. Trước tiên transcribe chính "
                + "xác audio thành chữ, sau đó áp dụng ĐÚNG tiêu chí chấm sau đây — chấm lần lượt 4 tiêu chí "
                + "(Fluency & Coherence, Lexical Resource, Grammatical Range & Accuracy, Pronunciation), mỗi tiêu chí "
                + "thang band 0-9, rồi tính overallBand = trung bình cộng 4 band đó:\n"
                + rubric
                + "\nChỉ trả lời DUY NHẤT 1 JSON hợp lệ, không thêm chữ nào khác, đúng format: "
                + "{\"transcript\": \"<chữ đã transcribe từ audio>\", \"fluencyCoherence\": <band 0-9>, "
                + "\"lexicalResource\": <band 0-9>, \"grammar\": <band 0-9>, \"pronunciation\": <band 0-9>, "
                + "\"overallBand\": <band 0-9, trung bình cộng 4 band trên>, \"feedback\": \"<nhận xét tiếng Việt, "
                + "theo đúng cấu trúc mục 8 Standard Feedback Format của tiêu chí chấm: điểm từng tiêu chí, "
                + "strongest area, weakest area, what you did well, what is limiting your score, top 3 priorities, "
                + "target for next test>\"}";
    }

    /**
     * LLM đôi khi bọc thêm text/markdown quanh JSON dù đã dặn "chỉ trả JSON" — cắt từ '{' đầu tới '}'
     * cuối cho an toàn. Rubric chấm theo band 0-9 (mục 1 "SPEAKING SCORING RUBRIC") — quy đổi
     * overallBand sang % (overallBand/9*100, làm tròn) để khớp ngưỡng đạt 70% dùng chung cho Video
     * phản xạ.
     */
    private GradeResult parseResult(String rawText) throws IOException {
        int start = rawText.indexOf('{');
        int end = rawText.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IOException("Model chấm bài không trả về JSON hợp lệ: " + rawText);
        }
        JsonNode parsed = objectMapper.readTree(rawText.substring(start, end + 1));
        double overallBand = parsed.path("overallBand").asDouble(0);
        int scorePercent = (int) Math.round(Math.min(9, Math.max(0, overallBand)) / 9.0 * 100);
        return new GradeResult(parsed.path("transcript").asText(""), scorePercent, parsed.path("feedback").asText(""));
    }
}
