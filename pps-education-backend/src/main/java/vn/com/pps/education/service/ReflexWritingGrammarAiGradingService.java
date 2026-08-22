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

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — UC-23b (Video phản xạ) V2, bước 1
 * của mỗi câu hỏi: chấm ngữ pháp phần "viết trước" (thời thì, chia động từ...) — đạt ngưỡng mới mở
 * khoá phần ghi âm (xem {@link ReflexSequentialGradingService}). Mirror cấu trúc
 * {@link WritingAiGradingService} (khác rubric/mục đích — grammar phần viết trước Speaking, không
 * phải chấm cả bài Writing hoàn chỉnh).
 *
 * Rubric đọc 1 lần lúc khởi động từ {@code resources/rubrics/reflex-writing-grammar-rubric.md} — rubric
 * thật do người dùng cung cấp 2026-08-22 (band 0-9 theo 4 tiêu chí, giống {@link WritingAiGradingService}
 * nhưng khác nội dung/mục đích — chấm phần viết trước Speaking, không phải bài Writing hoàn chỉnh).
 * {@link #parseResult} quy đổi overallBand (0-9) sang % (overallBand/9*100, làm tròn) để khớp ngưỡng
 * đạt 70% của {@link ReflexSequentialGradingService}.
 *
 * Ưu tiên Gemini Flash (GEMINI_API_KEY), fallback Claude (ANTHROPIC_API_KEY) — cùng key dùng chung
 * {@code app.ai-grading.*}. Lỗi gọi API trả về {@code null} — caller tự quyết định (KHÔNG tự cho qua).
 */
@Service
public class ReflexWritingGrammarAiGradingService {

    private static final Logger log = LoggerFactory.getLogger(ReflexWritingGrammarAiGradingService.class);

    private static final String RUBRIC_CLASSPATH = "rubrics/reflex-writing-grammar-rubric.md";
    private static final String CLAUDE_GRADING_MODEL = "claude-haiku-4-5-20251001";
    private static final String GEMINI_MODEL = "gemini-flash-latest";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final String rubric;

    @Value("${app.ai-grading.anthropic-api-key:}")
    private String anthropicApiKey;

    @Value("${app.ai-grading.gemini-api-key:}")
    private String geminiApiKey;

    public ReflexWritingGrammarAiGradingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.rubric = readRubric();
    }

    public record GradeResult(int scorePercent, String feedback) {
    }

    /** Trả null nếu chưa cấu hình key nào HOẶC gọi AI thất bại — caller tự quyết định (không tự cho qua). */
    public GradeResult grade(String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return null;
        }
        try {
            if (!geminiApiKey.isBlank()) {
                return callGemini(answerText);
            }
            if (!anthropicApiKey.isBlank()) {
                return callClaude(answerText);
            }
            log.warn("ReflexWritingGrammarAiGradingService: chưa cấu hình GEMINI_API_KEY hoặc ANTHROPIC_API_KEY.");
            return null;
        } catch (Exception e) {
            log.warn("ReflexWritingGrammarAiGradingService: gọi AI chấm thất bại. {}", e.getMessage());
            return null;
        }
    }

    private String readRubric() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RUBRIC_CLASSPATH)) {
            if (in == null) {
                log.warn("ReflexWritingGrammarAiGradingService: không tìm thấy {} trên classpath.", RUBRIC_CLASSPATH);
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("ReflexWritingGrammarAiGradingService: đọc rubric thất bại. {}", e.getMessage());
            return "";
        }
    }

    private String systemPrompt() {
        return "Bạn là giám khảo chấm phần viết trả lời (trước khi nói lại) của học sinh trung tâm Anh ngữ. Áp dụng "
                + "ĐÚNG tiêu chí chấm sau đây — chấm lần lượt 4 tiêu chí (Task Response/Task Achievement, "
                + "Coherence & Cohesion, Lexical Resource, Grammatical Range & Accuracy), mỗi tiêu chí thang band 0-9, "
                + "rồi tính overallBand = trung bình cộng 4 band đó:\n"
                + rubric
                + "\nChỉ trả lời DUY NHẤT 1 JSON hợp lệ, không thêm chữ nào khác, đúng format: "
                + "{\"taskResponse\": <band 0-9>, \"coherenceCohesion\": <band 0-9>, \"lexicalResource\": <band 0-9>, "
                + "\"grammar\": <band 0-9>, \"overallBand\": <band 0-9, trung bình cộng 4 band trên>, "
                + "\"feedback\": \"<nhận xét tiếng Việt, theo đúng cấu trúc mục 8 Standard Feedback Format của tiêu chí "
                + "chấm: điểm từng tiêu chí, strongest area, weakest area, what you did well, what is limiting your "
                + "score, top 3 priorities, target for next submission>\"}";
    }

    private GradeResult callClaude(String answerText) throws IOException, InterruptedException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", CLAUDE_GRADING_MODEL);
        payload.put("max_tokens", 512);
        payload.put("system", systemPrompt());
        ArrayNode messages = payload.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "Câu trả lời của học sinh: \"" + answerText + "\"");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("x-api-key", anthropicApiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("Claude API lỗi (HTTP " + response.statusCode() + "): " + response.body());
        }
        JsonNode json = objectMapper.readTree(response.body());
        return parseResult(json.path("content").path(0).path("text").asText(""));
    }

    private GradeResult callGemini(String answerText) throws IOException, InterruptedException {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode systemInstruction = payload.putObject("system_instruction");
        systemInstruction.putArray("parts").addObject().put("text", systemPrompt());
        ArrayNode contents = payload.putArray("contents");
        contents.addObject().putArray("parts").addObject().put("text", "Câu trả lời của học sinh: \"" + answerText + "\"");

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
        return parseResult(json.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(""));
    }

    /**
     * LLM đôi khi bọc thêm text/markdown quanh JSON dù đã dặn "chỉ trả JSON" — cắt từ '{' đầu tới '}'
     * cuối cho an toàn. Rubric chấm theo band 0-9 (mục 3 "WRITING SCORING RUBRIC") — quy đổi overallBand
     * sang % (overallBand/9*100, làm tròn) để khớp ngưỡng đạt 70% dùng chung cho Video phản xạ.
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
        return new GradeResult(scorePercent, parsed.path("feedback").asText(""));
    }
}
