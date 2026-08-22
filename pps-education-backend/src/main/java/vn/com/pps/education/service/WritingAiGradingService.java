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
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — UC-40/UC-41: chấm AI cho câu ESSAY
 * thuộc Bài {@code Exercise.skillCategory=WRITING}, thay luồng luôn chờ Giáo viên chấm thủ công
 * (mặc định cũ, xem Javadoc {@link ExerciseAttemptService}) CHỈ cho riêng nhóm Bài này. Gọi từ
 * {@link ExerciseAttemptService#gradeAndFinalize}.
 *
 * Rubric đọc 1 lần lúc khởi động từ {@code resources/rubrics/writing-exercise-rubric.md} — rubric
 * thật do người dùng cung cấp (2026-08-22, "Writing Scoring Standard"): 4 tiêu chí Task Response/
 * Achievement, Coherence & Cohesion, Lexical Resource, Grammatical Range & Accuracy, mỗi tiêu chí
 * chấm band 0-9, Overall = trung bình cộng 4 tiêu chí. AI được yêu cầu trả về đủ 4 band + overall +
 * feedback theo đúng format "Standard Feedback" của rubric — {@link #parseResult} quy đổi
 * overallBand (0-9) sang % (overallBand/9*100, làm tròn) để khớp {@code exercises.pass_threshold_percent}
 * (thang % có sẵn, dùng chung cho mọi loại đề).
 *
 * Ưu tiên Gemini Flash (GEMINI_API_KEY, đã xác nhận với người dùng 2026-08-22 — free tier/rẻ hơn),
 * fallback Claude (ANTHROPIC_API_KEY) nếu chưa cấu hình Gemini — cùng 2 key dùng chung với
 * SpeakingAiGradingTestService (xem {@code app.ai-grading.*}). Lỗi gọi API (thiếu key/timeout/HTTP
 * lỗi) trả về {@code null} — KHÔNG throw, để câu trả lời rơi lại đúng hàng chờ chấm tay UC-41
 * (ManualGradingService) thay vì làm hỏng cả giao dịch nộp bài của học sinh.
 */
@Service
public class WritingAiGradingService {

    private static final Logger log = LoggerFactory.getLogger(WritingAiGradingService.class);

    private static final String RUBRIC_CLASSPATH = "rubrics/writing-exercise-rubric.md";
    private static final String CLAUDE_GRADING_MODEL = "claude-haiku-4-5-20251001";
    private static final String GEMINI_MODEL = "gemini-flash-latest";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final String rubric;

    @Value("${app.ai-grading.anthropic-api-key:}")
    private String anthropicApiKey;

    @Value("${app.ai-grading.gemini-api-key:}")
    private String geminiApiKey;

    public WritingAiGradingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.rubric = readRubric();
    }

    public record GradeResult(int scorePercent, String feedback) {
    }

    /**
     * Trả null nếu chưa cấu hình key nào HOẶC gọi AI thất bại — caller (ExerciseAttemptService) coi
     * đây là "chưa chấm được", câu trả lời tự động rơi lại hàng chờ Giáo viên chấm tay (UC-41), KHÔNG
     * chặn học sinh nộp bài.
     */
    public GradeResult grade(String essayText) {
        if (essayText == null || essayText.isBlank()) {
            return null;
        }
        try {
            if (!geminiApiKey.isBlank()) {
                return callGemini(essayText);
            }
            if (!anthropicApiKey.isBlank()) {
                return callClaude(essayText);
            }
            log.warn("WritingAiGradingService: chưa cấu hình GEMINI_API_KEY hoặc ANTHROPIC_API_KEY — bỏ qua chấm AI, rơi lại hàng chờ chấm tay.");
            return null;
        } catch (Exception e) {
            log.warn("WritingAiGradingService: gọi AI chấm bài thất bại, rơi lại hàng chờ chấm tay. {}", e.getMessage());
            return null;
        }
    }

    private String readRubric() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RUBRIC_CLASSPATH)) {
            if (in == null) {
                log.warn("WritingAiGradingService: không tìm thấy {} trên classpath.", RUBRIC_CLASSPATH);
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("WritingAiGradingService: đọc rubric thất bại. {}", e.getMessage());
            return "";
        }
    }

    private String systemPrompt() {
        return "Bạn là giám khảo chấm bài Writing tiếng Anh cho học sinh trung tâm Anh ngữ. Áp dụng ĐÚNG quy trình "
                + "\"Examiner Procedure\" và \"Scoring Principles\" trong tiêu chí chấm sau đây — chấm lần lượt 4 tiêu chí "
                + "(Task Response/Achievement, Coherence & Cohesion, Lexical Resource, Grammatical Range & Accuracy), mỗi "
                + "tiêu chí thang band 0-9, rồi tính overallBand = trung bình cộng 4 band đó:\n"
                + rubric
                + "\nChỉ trả lời DUY NHẤT 1 JSON hợp lệ, không thêm chữ nào khác, đúng format: "
                + "{\"taskResponse\": <band 0-9>, \"coherenceCohesion\": <band 0-9>, \"lexicalResource\": <band 0-9>, "
                + "\"grammar\": <band 0-9>, \"overallBand\": <band 0-9, trung bình cộng 4 band trên>, "
                + "\"feedback\": \"<nhận xét tiếng Việt, theo đúng cấu trúc mục 11 Standard Feedback của tiêu chí chấm: "
                + "điểm từng tiêu chí, strongest area, main limitation, what you did well, what is limiting your score, "
                + "top 3 priorities, target for next submission>\"}";
    }

    private GradeResult callClaude(String essayText) throws IOException, InterruptedException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", CLAUDE_GRADING_MODEL);
        payload.put("max_tokens", 512);
        payload.put("system", systemPrompt());
        ArrayNode messages = payload.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "Bài viết của học sinh: \"" + essayText + "\"");

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
        String rawText = json.path("content").path(0).path("text").asText("");
        return parseResult(rawText);
    }

    private GradeResult callGemini(String essayText) throws IOException, InterruptedException {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode systemInstruction = payload.putObject("system_instruction");
        systemInstruction.putArray("parts").addObject().put("text", systemPrompt());
        ArrayNode contents = payload.putArray("contents");
        ObjectNode contentEntry = contents.addObject();
        contentEntry.putArray("parts").addObject().put("text", "Bài viết của học sinh: \"" + essayText + "\"");

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
     * LLM đôi khi bọc thêm text/markdown quanh JSON dù đã dặn "chỉ trả JSON" — cắt từ '{' đầu tới '}'
     * cuối cho an toàn. Rubric chấm theo band 0-9 (mục 2 "Cấu trúc chấm điểm") — quy đổi overallBand
     * sang % (overallBand/9*100, làm tròn) để khớp thang % dùng chung cho pass_threshold_percent.
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
