package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.com.pps.education.common.AiTokenUsage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * SPIKE — KHÔNG phải business logic của 1 UC đã đặc tả trong docs/uc/. Dựng theo yêu cầu người dùng
 * (2026-08-24) để dùng 9Router (https://github.com/decolua/9router) làm KÊNH ĐIỀU HƯỚNG DUY NHẤT cho
 * mọi request AI từ backend — proxy local expose endpoint OpenAI-compatible tại
 * {@code http://localhost:20128/v1}, xoay vòng nhiều provider/API key (fallback 3-tier, multi-account
 * round-robin) khi 1 key hết quota (xem sự cố Gemini free tier 20 request/ngày ở
 * ReflexWritingGrammarAiGradingService). Gồm 3 nhóm request:
 * - {@link #chat}: chấm nội dung (text) — {@code POST /chat/completions}, model theo combo đã tạo
 *   trong Dashboard 9Router → Combo & Vision Adapter (VD "teacher-models").
 * - {@link #chatWithAudio}: chấm dựa TRỰC TIẾP trên audio (multimodal, {@code input_audio} content
 *   part) — dùng khi cần đánh giá phát âm/ngữ điệu, KHÔNG dùng model có Claude (không nhận audio).
 * - {@link #transcribe}: speech-to-text THUẦN (chỉ ra chữ, không chấm) — {@code POST
 *   /audio/transcriptions}, model theo Dashboard 9Router → Media Providers → Speech To Text (VD
 *   "groq/whisper-large-v3-turbo").
 *
 * Yêu cầu chạy 9Router trước (`npm install -g 9router && 9router`, mặc định cổng 20128) và cấu hình
 * provider tương ứng trong Dashboard — client này KHÔNG tự khởi động 9Router. Được gọi thật từ
 * {@link WritingAiGradingService} (UC-40/41) và {@link ReflexSpeakingContentAiGradingService}/
 * {@link ReflexWritingGrammarAiGradingService} (UC-23b) — LƯU Ý VẬN HÀNH: 9Router chạy như 1 systemd
 * service DUY NHẤT trên server vật lý (không phải container, không scale nhiều instance), dùng CHUNG
 * cho cả staging lẫn production — backend mỗi stack gọi qua `host.docker.internal:20128` (Docker
 * container → host), ufw giới hạn theo subnet Docker riêng của từng stack (xem `deploy/README.md` mục
 * "9Router (chấm AI)"). Vì chỉ 1 process xử lý AI-grading cho CẢ 2 môi trường, traffic AI-grading của
 * staging và production CẠNH TRANH nhau tại cùng 1 điểm nghẽn — cân nhắc khi đánh giá khả năng chịu tải
 * giờ cao điểm. Client này gọi 9Router đồng bộ/blocking (request vẫn chờ và nhận kết quả chấm ngay
 * trong response, không đổi UX) — từ 2026-09-15 có giới hạn số cuộc gọi ĐỒNG THỜI phía backend qua
 * {@link #concurrencyLimiter} để tránh dội tải 1 process 9Router duy nhất, xem Javadoc field đó.
 */
@Service
public class NineRouterAiClient {

    private static final Logger log = LoggerFactory.getLogger(NineRouterAiClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${app.ai-grading.nine-router-base-url:http://localhost:20128/v1}")
    private String baseUrl;

    @Value("${app.ai-grading.nine-router-api-key:}")
    private String apiKey;

    @Value("${app.ai-grading.nine-router-model:}")
    private String defaultModel;

    @Value("${app.ai-grading.nine-router-stt-model:groq/whisper-large-v3-turbo}")
    private String defaultSttModel;

    /**
     * Model NHẬN AUDIO TRỰC TIẾP trong /chat/completions (xem {@link #chatWithAudio}) — PHẢI là model
     * đa phương thức (Gemini/GPT-4o-audio), KHÔNG dùng combo có Claude (Claude không nhận input audio,
     * request sẽ lỗi nếu combo fallback rơi vào nhánh Claude).
     */
    @Value("${app.ai-grading.nine-router-audio-model:ag/gemini-3.5-flash-low}")
    private String defaultAudioModel;

    /**
     * Bổ sung 2026-09-15 (đã xác nhận với người dùng) — giới hạn số request ĐỒNG THỜI mà backend gửi
     * sang 9Router. 9Router chạy như 1 systemd service DUY NHẤT trên server (xem
     * {@code app.ai-grading.nine-router-base-url}), không scale nhiều instance — nếu backend bắn thẳng
     * mọi request đồng thời (VD nhiều học sinh nộp bài Video phản xạ giờ cao điểm), 9Router hoặc AI
     * provider phía sau (rate-limit theo request/phút) có thể bị dội tải, gây lỗi hàng loạt thay vì chỉ
     * vài request. {@link Semaphore} này KHÔNG đổi UX — request vẫn được xử lý đồng bộ, học sinh vẫn
     * nhận kết quả chấm trong cùng 1 response; chỉ khác là khi vượt ngưỡng, request mới phải CHỜ trong
     * hàng đợi (tối đa {@code nineRouterAcquireTimeoutSeconds}) thay vì gọi thẳng 9Router ngay lập tức.
     * Ngưỡng mặc định (5) là ước lượng THẬN TRỌNG ban đầu, CHƯA qua load test thật — cần đo lại trên
     * staging rồi tinh chỉnh qua {@code app.ai-grading.nine-router-max-concurrent}.
     */
    private final Semaphore concurrencyLimiter;

    @Value("${app.ai-grading.nine-router-acquire-timeout-seconds:20}")
    private int acquireTimeoutSeconds;

    /**
     * Bổ sung 2026-09-21 (đã xác nhận với người dùng) — cho các lệnh gọi chấm Speaking v2 (bộ tiêu chí
     * Khối 6-7, xem {@code ReflexV2AiGradingService}): nếu true thì gửi kèm {@code response_format}
     * json_schema. Mặc định TẮT vì đã thử trên 9Router: field được chấp nhận nhưng kết quả vẫn bọc
     * trong khối ```json (không ép cứng) — parser phía backend tự bóc, prompt đã kèm schema dạng chữ.
     */
    @Value("${app.ai-grading.reflex-v2.json-schema-enabled:false}")
    private boolean jsonSchemaEnabled;

    /**
     * Chuỗi phải có mặt trong trường {@code model} 9Router trả về (VD "gemini-3.6-flash" khớp cả
     * "gemini-3.6-flash-tiered") — người training cảnh báo model dự phòng làm hỏng độ trung thực
     * transcript nên KHÔNG chấp nhận âm thầm chấm bằng model khác. Để trống = không kiểm tra.
     */
    @Value("${app.ai-grading.reflex-v2.expected-model-contains:gemini-3.6-flash}")
    private String expectedModelContains;

    /**
     * Kết quả gọi có cấu trúc: nội dung + tên model thực tế 9Router đã dùng (để kiểm chứng/ghi audit) +
     * mức tiêu thụ token của CHÍNH lệnh gọi này (V192 — service gọi mới biết ngữ cảnh học sinh/bài tập
     * để lưu lại, xem {@link vn.com.pps.education.common.AiTokenUsage}).
     */
    public record AiJsonResponse(String content, String model, AiTokenUsage usage) {
    }

    /** Như {@link AiJsonResponse} nhưng cho lệnh gọi text thuần trả chuỗi (xem {@link #chatWithUsage}). */
    public record AiTextResponse(String content, AiTokenUsage usage) {
    }

    private final AiUsageSink usageSink;

    public NineRouterAiClient(ObjectMapper objectMapper,
                               @Value("${app.ai-grading.nine-router-max-concurrent:5}") int maxConcurrentCalls,
                               AiUsageSink usageSink) {
        this.objectMapper = objectMapper;
        this.concurrencyLimiter = new Semaphore(maxConcurrentCalls);
        this.usageSink = usageSink;
    }

    /**
     * Gọi {@code POST /chat/completions} theo format OpenAI chuẩn (9Router tự dịch sang format của
     * provider thật đang được route tới phía sau). Trả {@code null} nếu chưa cấu hình model, gọi lỗi,
     * hoặc response không đúng shape mong đợi — caller (spike) tự quyết định, không tự đoán bừa.
     *
     * @param model tên model theo alias/combo đã tạo trong Dashboard 9Router (VD "kr/claude-sonnet-4.5");
     *              để trống thì dùng {@code app.ai-grading.nine-router-model}.
     */
    public String chat(String systemPrompt, String userMessage, String model) {
        AiTextResponse response = chatWithUsage(systemPrompt, userMessage, model);
        return response == null ? null : response.content();
    }

    /**
     * V192 — như {@link #chat} nhưng trả kèm mức tiêu thụ token, cho caller nào cần lưu chi phí gắn với
     * ngữ cảnh nghiệp vụ (VD bước sinh câu sửa mẫu của Video phản xạ). {@link #chat} giữ nguyên chữ ký
     * chuỗi cho các caller cũ không quan tâm chi phí, không phải sửa hàng loạt.
     */
    public AiTextResponse chatWithUsage(String systemPrompt, String userMessage, String model) {
        String resolvedModel = (model == null || model.isBlank()) ? defaultModel : model;
        if (resolvedModel == null || resolvedModel.isBlank()) {
            log.warn("NineRouterAiClient: chưa cấu hình model (app.ai-grading.nine-router-model hoặc tham số model).");
            return null;
        }
        return callWithConcurrencyLimit("chat", () -> doChat(systemPrompt, userMessage, resolvedModel));
    }

    private AiTextResponse doChat(String systemPrompt, String userMessage, String resolvedModel) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", resolvedModel);
            // 9Router mặc định trả SSE stream (nhiều dòng "data: {...}") nếu thiếu field này — HttpClient
            // đọc nguyên body như 1 JSON sẽ lỗi parse. Tắt stream để có 1 JSON response thường.
            payload.put("stream", false);
            ArrayNode messages = payload.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ObjectNode systemMsg = messages.addObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemPrompt);
            }
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            long startedAtMillis = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("NineRouterAiClient: gọi 9Router lỗi (HTTP {}): {}", response.statusCode(), response.body());
                return null;
            }
            JsonNode json = objectMapper.readTree(response.body());
            AiTokenUsage usage = logUsage("chat", resolvedModel, false, json, System.currentTimeMillis() - startedAtMillis);
            String content = json.path("choices").path(0).path("message").path("content").asText(null);
            return content == null ? null : new AiTextResponse(content, usage);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("NineRouterAiClient: gọi 9Router thất bại. {}", e.getMessage());
            return null;
        }
    }

    /**
     * V146 (2026-08-24, xác nhận với người dùng) — chấm Speaking dựa TRỰC TIẾP trên audio gốc (không
     * qua bước transcribe riêng như {@link #transcribe}) — cần thiết vì rubric Speaking có tiêu chí
     * "Phát âm" (âm đuôi, trọng âm, ngữ điệu) mà 1 bản transcript dạng chữ KHÔNG thể hiện được, bất kể
     * LLM nào chấm text đó giỏi tới đâu. Gửi audio inline theo content part {@code input_audio} (shape
     * OpenAI multimodal chuẩn) trong {@code POST /chat/completions} — đã verify hoạt động qua model
     * Gemini (route "ag/gemini-3.5-flash-low") bằng test thủ công.
     *
     * @param model tên model đa phương thức theo Dashboard 9Router; để trống thì dùng
     *              {@code app.ai-grading.nine-router-audio-model}. PHẢI là Gemini/GPT-4o-audio (model
     *              chấp nhận audio) — KHÔNG dùng model/combo Claude cho tham số này.
     */
    public String chatWithAudio(String systemPrompt, String userText, byte[] audioBytes, String mimeType, String model) {
        if (audioBytes == null || audioBytes.length == 0) {
            return null;
        }
        String resolvedModel = (model == null || model.isBlank()) ? defaultAudioModel : model;
        if (resolvedModel == null || resolvedModel.isBlank()) {
            log.warn("NineRouterAiClient: chưa cấu hình audio model (app.ai-grading.nine-router-audio-model hoặc tham số model).");
            return null;
        }
        return callWithConcurrencyLimit("chatWithAudio", () -> doChatWithAudio(systemPrompt, userText, audioBytes, mimeType, resolvedModel));
    }

    private String doChatWithAudio(String systemPrompt, String userText, byte[] audioBytes, String mimeType, String resolvedModel) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", resolvedModel);
            payload.put("stream", false);
            ArrayNode messages = payload.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ObjectNode systemMsg = messages.addObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemPrompt);
            }
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            ArrayNode contentParts = userMsg.putArray("content");
            contentParts.addObject().put("type", "text").put("text", userText);
            ObjectNode audioPart = contentParts.addObject();
            audioPart.put("type", "input_audio");
            ObjectNode inputAudio = audioPart.putObject("input_audio");
            inputAudio.put("data", java.util.Base64.getEncoder().encodeToString(audioBytes));
            inputAudio.put("format", extensionFor(mimeType == null ? "audio/webm" : mimeType));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            long startedAtMillis = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("NineRouterAiClient: gọi 9Router (audio chat) lỗi (HTTP {}): {}", response.statusCode(), response.body());
                return null;
            }
            JsonNode json = objectMapper.readTree(response.body());
            logUsage("chatWithAudio", resolvedModel, true, json, System.currentTimeMillis() - startedAtMillis);
            return json.path("choices").path(0).path("message").path("content").asText(null);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("NineRouterAiClient: gọi 9Router (audio chat) thất bại. {}", e.getMessage());
            return null;
        }
    }

    /**
     * Bổ sung 2026-09-21 (đã xác nhận với người dùng) — chấm Speaking v2 (bộ tiêu chí Khối 6-7): gọi text
     * với {@code temperature=0} (bắt buộc theo người training, KHÔNG đổi), tùy chọn ép JSON schema, thử
     * lại TỐI ĐA 1 lần khi 429/503 (lỗi 503 vẫn bị trừ quota ngày nên không thử nhiều hơn), và từ chối
     * kết quả nếu model thực tế khác model mong đợi. Trả {@code null} khi mọi lỗi — caller (service chấm)
     * coi là "chưa chấm được". Model do caller truyền (VD "ag/gemini-3.6-flash-medium").
     */
    public AiJsonResponse chatJson(String systemPrompt, String userMessage, String model, JsonNode schema) {
        if (model == null || model.isBlank()) {
            log.warn("NineRouterAiClient: chưa cấu hình model chấm Speaking v2.");
            return null;
        }
        return callWithConcurrencyLimit("chatJson", () -> doJsonCall("chatJson", systemPrompt, userMessage, null, null, model, schema));
    }

    /** Như {@link #chatJson} nhưng đính kèm audio (multimodal {@code input_audio}); WAV là định dạng đã kiểm chứng qua 9Router. */
    public AiJsonResponse chatWithAudioJson(String systemPrompt, String userText, byte[] audioBytes, String mimeType,
                                            String model, JsonNode schema) {
        if (audioBytes == null || audioBytes.length == 0 || model == null || model.isBlank()) {
            return null;
        }
        return callWithConcurrencyLimit("chatWithAudioJson",
                () -> doJsonCall("chatWithAudioJson", systemPrompt, userText, audioBytes, mimeType, model, schema));
    }

    private AiJsonResponse doJsonCall(String operation, String systemPrompt, String userText, byte[] audioBytes,
                                      String mimeType, String model, JsonNode schema) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", model);
            payload.put("stream", false);
            payload.put("temperature", 0);
            if (jsonSchemaEnabled && schema != null) {
                ObjectNode format = payload.putObject("response_format");
                format.put("type", "json_schema");
                ObjectNode js = format.putObject("json_schema");
                js.put("name", "grading");
                js.set("schema", schema);
                js.put("strict", false);
            }
            ArrayNode messages = payload.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ObjectNode systemMsg = messages.addObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemPrompt);
            }
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            if (audioBytes == null) {
                userMsg.put("content", userText);
            } else {
                ArrayNode parts = userMsg.putArray("content");
                parts.addObject().put("type", "text").put("text", userText);
                ObjectNode audioPart = parts.addObject();
                audioPart.put("type", "input_audio");
                ObjectNode inputAudio = audioPart.putObject("input_audio");
                inputAudio.put("data", java.util.Base64.getEncoder().encodeToString(audioBytes));
                inputAudio.put("format", extensionFor(mimeType == null ? "audio/wav" : mimeType));
            }
            String body = objectMapper.writeValueAsString(payload);
            long startedAtMillis = System.currentTimeMillis();
            HttpResponse<String> response = sendChatCompletions(body);
            if (response.statusCode() == 429 || response.statusCode() == 503) {
                log.warn("NineRouterAiClient: HTTP {} — thử lại 1 lần sau 2 giây.", response.statusCode());
                Thread.sleep(2000);
                response = sendChatCompletions(body);
            }
            if (response.statusCode() >= 300) {
                log.warn("NineRouterAiClient: gọi 9Router (JSON) lỗi (HTTP {}): {}", response.statusCode(), response.body());
                return null;
            }
            JsonNode json = objectMapper.readTree(response.body());
            // Đo TRƯỚC các nhánh trả null bên dưới: 1 lượt bị loại vì sai model hoặc content rỗng VẪN đã
            // tiêu thụ token thật và vẫn bị tính tiền — bỏ qua ở nhánh đó sẽ làm số liệu thấp hơn hoá đơn.
            AiTokenUsage usage = logUsage(operation, model, audioBytes != null, json,
                    System.currentTimeMillis() - startedAtMillis);
            String content = json.path("choices").path(0).path("message").path("content").asText(null);
            String actualModel = json.path("model").asText("");
            if (content == null || content.isBlank()) {
                usageSink.recordRejected(operation, model, usage);
                return null;
            }
            if (expectedModelContains != null && !expectedModelContains.isBlank() && !actualModel.isBlank()
                    && !actualModel.toLowerCase(java.util.Locale.ROOT).contains(expectedModelContains.toLowerCase(java.util.Locale.ROOT))) {
                log.warn("NineRouterAiClient: 9Router trả model '{}' KHÔNG chứa '{}' (đã yêu cầu '{}') — bỏ kết quả, không chấm bằng model khác.",
                        actualModel, expectedModelContains, model);
                usageSink.recordRejected(operation, model, usage);
                return null;
            }
            return new AiJsonResponse(content, actualModel, usage);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("NineRouterAiClient: gọi 9Router (JSON) thất bại. {}", e.getMessage());
            return null;
        }
    }

    private HttpResponse<String> sendChatCompletions(String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Gọi {@code POST /audio/transcriptions} (multipart/form-data, shape OpenAI chuẩn) — 9Router tự
     * route tới provider STT thật đã cấu hình trong Dashboard → Media Providers → Speech To Text (VD
     * Groq Whisper). Trả {@code null} nếu chưa cấu hình model, gọi lỗi, hoặc response rỗng.
     *
     * @param model tên model theo Dashboard 9Router (VD "groq/whisper-large-v3-turbo"); để trống thì
     *              dùng {@code app.ai-grading.nine-router-stt-model}.
     */
    public String transcribe(byte[] audioBytes, String mimeType, String model) {
        if (audioBytes == null || audioBytes.length == 0) {
            return null;
        }
        String resolvedModel = (model == null || model.isBlank()) ? defaultSttModel : model;
        if (resolvedModel == null || resolvedModel.isBlank()) {
            log.warn("NineRouterAiClient: chưa cấu hình STT model (app.ai-grading.nine-router-stt-model hoặc tham số model).");
            return null;
        }
        return callWithConcurrencyLimit("transcribe", () -> doTranscribe(audioBytes, mimeType, resolvedModel));
    }

    private String doTranscribe(byte[] audioBytes, String mimeType, String resolvedModel) {
        try {
            String boundary = "----ppsNineRouterBoundary" + UUID.randomUUID();
            byte[] body = buildMultipartBody(boundary, resolvedModel, audioBytes, mimeType == null ? "audio/webm" : mimeType);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/audio/transcriptions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("content-type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            long startedAtMillis = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("NineRouterAiClient: gọi 9Router (STT) lỗi (HTTP {}): {}", response.statusCode(), response.body());
                return null;
            }
            JsonNode json = objectMapper.readTree(response.body());
            logUsage("transcribe", resolvedModel, true, json, System.currentTimeMillis() - startedAtMillis);
            String text = json.path("text").asText(null);
            return (text == null || text.isBlank()) ? null : text.trim();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("NineRouterAiClient: gọi 9Router (STT) thất bại. {}", e.getMessage());
            return null;
        }
    }

    /**
     * V192 (2026-09-22, bước ĐO mở đầu cho hướng tối ưu chi phí AI-grading) — ghi log mức tiêu thụ token
     * của mỗi lệnh gọi. TRƯỚC ĐÂY client chỉ đọc {@code choices[0].message.content} + {@code model} và
     * VỨT BỎ field {@code usage} — không có số liệu nào về chi phí, nên mọi đề xuất tối ưu đều không
     * kiểm chứng được trước/sau, chỉ suy đoán từ số ký tự prompt. Thuần đo đạc: không sửa payload gửi
     * đi, không sửa cách parse, lỗi đọc usage bị nuốt để không bao giờ làm hỏng luồng chấm.
     *
     * Vì sao log ĐỦ 4 loại token chứ không chỉ tổng — mỗi loại trả lời 1 câu hỏi tối ưu khác nhau:
     * - {@code cachedTokens}: luồng Speaking v2 đã cố tình dựng prompt hệ thống GIỐNG HỆT NHAU giữa mọi
     *   học sinh cùng Khối/track để phần đầu ổn định cho cache nhà cung cấp (xem Javadoc
     *   {@code ReflexV2Prompts#speakingSystem}), nhưng thử tay 2026-09-21 CHƯA thấy cache qua 9Router.
     *   Số này bằng 0 kéo dài là bằng chứng cache thật sự không hoạt động, thay vì phỏng đoán.
     * - {@code reasoningTokens}: model chạy thinking mức medium và schema bắt model trả
     *   {@code counting_notes}/{@code evidence} (đánh dấu "Nội bộ" — không hiện cho học sinh nhưng VẪN
     *   bị tính tiền như output). Với model lớp Flash, output đắt hơn input nhiều lần, nên nếu số này
     *   lớn thì cắt prompt đầu vào là tối ưu nhầm vế.
     * - {@code promptTokens} tách khỏi {@code audioAttached}: lượt phiên âm và lượt chấm nói gửi CÙNG 1
     *   file audio 2 lần (thiết kế có chủ đích — rubric cần nghe thật mới chấm được phát âm), cần biết
     *   audio chiếm bao nhiêu phần input so với rubric dạng chữ trước khi bàn cắt chỗ nào.
     *
     * Mức INFO vì đây là số liệu vận hành cần có sẵn trên staging/production để đối chiếu hoá đơn, không
     * phải thông tin gỡ lỗi bật tạm khi nghi có sự cố.
     */
    private AiTokenUsage logUsage(String operation, String requestedModel, boolean audioAttached, JsonNode json, long elapsedMillis) {
        String servedModel = json.path("model").asText("?");
        try {
            JsonNode usage = json.path("usage");
            if (usage.isMissingNode() || usage.isNull()) {
                // Một số provider/route không trả usage — vẫn log route + latency để theo dõi xoay vòng model.
                log.info("NineRouterAiClient usage: op={} requestedModel={} servedModel={} audio={} usage=n/a elapsedMs={}",
                        operation, requestedModel, servedModel, audioAttached, elapsedMillis);
                return AiTokenUsage.unknown(servedModel, audioAttached, elapsedMillis);
            }
            AiTokenUsage parsed = new AiTokenUsage(servedModel, audioAttached,
                    usage.path("prompt_tokens").asInt(usage.path("input_tokens").asInt(0)),
                    extractCachedTokens(usage),
                    usage.path("completion_tokens").asInt(usage.path("output_tokens").asInt(0)),
                    extractReasoningTokens(usage), elapsedMillis);
            log.info("NineRouterAiClient usage: op={} requestedModel={} servedModel={} audio={} promptTokens={} "
                            + "cachedTokens={} completionTokens={} reasoningTokens={} elapsedMs={}",
                    operation, requestedModel, servedModel, audioAttached, parsed.promptTokens(), parsed.cachedTokens(),
                    parsed.completionTokens(), parsed.reasoningTokens(), elapsedMillis);
            return parsed;
        } catch (RuntimeException e) {
            log.debug("NineRouterAiClient: đọc usage thất bại ({}).", e.getMessage());
            return AiTokenUsage.unknown(servedModel, audioAttached, elapsedMillis);
        }
    }

    /**
     * Token input được tính giá cache. 9Router chuyển tiếp gần như nguyên vẹn usage của provider thật mà
     * mỗi provider lại đặt 1 tên khác nhau, nên phải dò lần lượt: OpenAI/Gemini qua lớp OpenAI-compatible
     * dùng {@code prompt_tokens_details.cached_tokens}, Anthropic dùng {@code cache_read_input_tokens},
     * Gemini gọi thẳng dùng {@code cachedContentTokenCount}. Không thấy đường dẫn nào thì trả 0 — nghĩa là
     * "không có bằng chứng cache hit", đúng với điều cần biết, KHÔNG phải lỗi.
     *
     * <p>Package-private (không private) để {@code NineRouterAiClientUsageTest} kiểm được từng shape
     * provider: dò sai tên field sẽ khiến số luôn ra 0 và dẫn tới kết luận ngược ("cache không chạy"),
     * mà lỗi kiểu này không hề lộ ra lúc chạy — log vẫn in đẹp, chỉ là in số sai.
     */
    int extractCachedTokens(JsonNode usage) {
        JsonNode openAiStyle = usage.path("prompt_tokens_details").path("cached_tokens");
        if (openAiStyle.isNumber()) {
            return openAiStyle.asInt();
        }
        JsonNode anthropicStyle = usage.path("cache_read_input_tokens");
        if (anthropicStyle.isNumber()) {
            return anthropicStyle.asInt();
        }
        return usage.path("cachedContentTokenCount").asInt(0);
    }

    /**
     * Token "suy nghĩ" (thinking/reasoning) — tính tiền như output nhưng KHÔNG nằm trong nội dung trả về,
     * nên nhìn {@code completion_tokens} không thấy hết. Dò theo tên của lớp OpenAI-compatible
     * ({@code completion_tokens_details.reasoning_tokens}) rồi tới tên gốc của Gemini
     * ({@code thoughts_token_count}/{@code thoughtsTokenCount}). Package-private vì lý do như
     * {@link #extractCachedTokens}.
     */
    int extractReasoningTokens(JsonNode usage) {
        JsonNode openAiStyle = usage.path("completion_tokens_details").path("reasoning_tokens");
        if (openAiStyle.isNumber()) {
            return openAiStyle.asInt();
        }
        JsonNode geminiSnake = usage.path("thoughts_token_count");
        if (geminiSnake.isNumber()) {
            return geminiSnake.asInt();
        }
        return usage.path("thoughtsTokenCount").asInt(0);
    }

    /**
     * Giữ chỗ (permit) trước khi thực sự gọi 9Router, nhả lại ngay sau khi xong (thành công hay lỗi đều
     * nhả) — xem Javadoc {@link #concurrencyLimiter}. Chờ quá {@code nineRouterAcquireTimeoutSeconds} mà
     * vẫn không có permit thì bỏ cuộc, trả {@code null} giống mọi lỗi gọi 9Router khác (KHÔNG tự gọi
     * thẳng bất chấp giới hạn, KHÔNG treo vô hạn).
     */
    private <T> T callWithConcurrencyLimit(String operationName, Supplier<T> call) {
        boolean acquired = false;
        try {
            acquired = concurrencyLimiter.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("NineRouterAiClient: {} bị bỏ qua - chờ quá {}s vẫn không có chỗ trống (đang giới hạn "
                                + "app.ai-grading.nine-router-max-concurrent cuộc gọi đồng thời tới 9Router).",
                        operationName, acquireTimeoutSeconds);
                return null;
            }
            return call.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (acquired) {
                concurrencyLimiter.release();
            }
        }
    }

    private byte[] buildMultipartBody(String boundary, String model, byte[] audioBytes, String mimeType) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeField(out, boundary, "model", model);
        writeField(out, boundary, "response_format", "json");

        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"answer." + extensionFor(mimeType) + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(audioBytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));

        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private void writeField(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private String extensionFor(String mimeType) {
        if (mimeType.contains("webm")) return "webm";
        if (mimeType.contains("mp3") || mimeType.contains("mpeg")) return "mp3";
        if (mimeType.contains("wav")) return "wav";
        if (mimeType.contains("m4a") || mimeType.contains("mp4")) return "m4a";
        if (mimeType.contains("ogg")) return "ogg";
        return "webm";
    }
}
