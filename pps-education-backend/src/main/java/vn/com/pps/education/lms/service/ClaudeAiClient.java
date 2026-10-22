package vn.com.pps.education.lms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.com.pps.education.common.AiTokenUsage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — client gọi THẲNG Anthropic Messages API, thay thế
 * {@link NineRouterAiClient} cho nhánh "chấm AI trực tiếp, không qua 9Router" (xem
 * {@link AiGradingFallbackService}). Trả đúng kiểu {@link NineRouterAiClient.AiJsonResponse}/
 * {@link NineRouterAiClient.ChatResult} để khớp chữ ký {@code aiCallerOverride} đã có sẵn ở
 * {@link WritingAiGradingService#grade(String, String, vn.com.pps.education.academic.domain.Curriculum, java.util.List, java.util.function.BiFunction)}
 * và {@link ReflexV2AiGradingService#gradeWriting(vn.com.pps.education.common.ReflexV2Task, String, String, java.util.function.BiFunction)}
 * — không phải trùng hợp, 2 overload đó được thiết kế riêng để nhận client thay thế kiểu này.
 *
 * 2 bài học rút ra từ phiên test thật (spike {@code SpeakingAiGradingTestService}), áp dụng CỐ ĐỊNH ở
 * đây, không để caller tự nhớ:
 * <ol>
 *   <li>{@code thinking: {type: "disabled"}} — Claude Haiku 5.5 mặc định tự bật "suy nghĩ" ẩn, có lúc ăn
 *       hết ngân sách {@code max_tokens} với prompt phức tạp (rubric v3), khiến content rỗng dù HTTP 200.
 *       Rubric đã liệt kê sẵn checklist cụ thể, không cần model tự suy luận thêm.</li>
 *   <li>{@code cache_control: {type: "ephemeral"}} trên block system — bật prompt caching thật (đã verify
 *       {@code cache_read_input_tokens} khớp {@code cache_creation_input_tokens} lượt trước), có lợi vì
 *       rubric theo Khối/Track lặp lại giống hệt giữa nhiều học sinh cùng lớp.</li>
 * </ol>
 */
@Service
public class ClaudeAiClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAiClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${app.ai-grading.anthropic-api-key:}")
    private String apiKey;

    @Value("${app.ai-grading.claude-direct.timeout-seconds:150}")
    private int requestTimeoutSeconds;

    /** Ngân sách cho JSON ngắn (điểm + feedback 1-2 câu) — xem {@link #chatJson(String, String, String)}. */
    public static final int MAX_TOKENS_SHORT = 2048;
    /** Ngân sách cho output dài (markdown đánh dấu lỗi + bảng điểm + mục kiểm đếm của rubric v3) — xem Javadoc lớp. */
    public static final int MAX_TOKENS_LONG_RUBRIC = 16384;

    public ClaudeAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Dùng cho {@code ReflexV2AiGradingService#gradeWriting} override — trả {@link NineRouterAiClient.AiJsonResponse}. */
    public NineRouterAiClient.AiJsonResponse chatJson(String systemPrompt, String userMessage, String model) {
        return chatJson(systemPrompt, userMessage, model, MAX_TOKENS_SHORT);
    }

    public NineRouterAiClient.AiJsonResponse chatJson(String systemPrompt, String userMessage, String model, int maxTokens) {
        if (!isConfigured()) {
            log.warn("ClaudeAiClient: chưa cấu hình ANTHROPIC_API_KEY.");
            return null;
        }
        try {
            long startedAt = System.currentTimeMillis();
            HttpResponse<String> response = send(systemPrompt, userMessage, model, maxTokens);
            if (response.statusCode() >= 300) {
                log.warn("ClaudeAiClient: HTTP {} — {}", response.statusCode(), response.body());
                return null;
            }
            JsonNode json = objectMapper.readTree(response.body());
            logCacheUsage(json);
            String content = extractTextBlock(json);
            if (content == null || content.isBlank()) {
                return null;
            }
            return new NineRouterAiClient.AiJsonResponse(content, json.path("model").asText(model),
                    buildUsage(json, model, System.currentTimeMillis() - startedAt));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("ClaudeAiClient: gọi lỗi. {}", e.getMessage());
            return null;
        }
    }

    /** Dùng cho {@code WritingAiGradingService#grade} override — trả {@link NineRouterAiClient.ChatResult}. finishReason luôn "stop" (xem Javadoc {@code gradeWritingRealRubric} trong spike — hậu kiểm tự phát hiện dở dang qua nội dung thiếu mục, không bắt buộc finish_reason thật). */
    public NineRouterAiClient.ChatResult chatWithFinishReason(String systemPrompt, String userMessage, String model) {
        NineRouterAiClient.AiJsonResponse r = chatJson(systemPrompt, userMessage, model, MAX_TOKENS_LONG_RUBRIC);
        return r == null ? null : new NineRouterAiClient.ChatResult(r.content(), "stop", r.usage());
    }

    private HttpResponse<String> send(String systemPrompt, String userMessage, String model, int maxTokens)
            throws IOException, InterruptedException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("max_tokens", maxTokens);
        payload.putObject("thinking").put("type", "disabled");
        ArrayNode systemBlocks = payload.putArray("system");
        ObjectNode systemBlock = systemBlocks.addObject();
        systemBlock.put("type", "text");
        systemBlock.put("text", systemPrompt);
        systemBlock.putObject("cache_control").put("type", "ephemeral");
        ArrayNode messages = payload.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Claude trả {@code content} dạng mảng block — tìm đúng block {@code type=="text"} (bỏ qua block "thinking" dù đã tắt, phòng model vẫn trả rỗng). */
    private String extractTextBlock(JsonNode json) {
        for (JsonNode block : json.path("content")) {
            if ("text".equals(block.path("type").asText(""))) {
                return block.path("text").asText("");
            }
        }
        return null;
    }

    private AiTokenUsage buildUsage(JsonNode json, String requestedModel, long elapsedMs) {
        JsonNode usage = json.path("usage");
        String servedModel = json.path("model").asText(requestedModel);
        if (usage.isMissingNode()) {
            return AiTokenUsage.unknown(servedModel, false, elapsedMs);
        }
        return new AiTokenUsage(servedModel, false,
                usage.path("input_tokens").asInt(0),
                usage.path("cache_read_input_tokens").asInt(0),
                usage.path("output_tokens").asInt(0),
                0, elapsedMs);
    }

    private void logCacheUsage(JsonNode json) {
        JsonNode usage = json.path("usage");
        if (usage.isMissingNode()) {
            return;
        }
        log.info("ClaudeAiClient usage: model={} cacheCreationTokens={} cacheReadTokens={} inputTokens={} outputTokens={}",
                json.path("model").asText("?"),
                usage.path("cache_creation_input_tokens").asInt(0),
                usage.path("cache_read_input_tokens").asInt(0),
                usage.path("input_tokens").asInt(0),
                usage.path("output_tokens").asInt(0));
    }
}
