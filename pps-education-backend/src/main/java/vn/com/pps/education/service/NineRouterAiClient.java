package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.UUID;

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
 * Yêu cầu chạy 9Router local trước (`npm install -g 9router && 9router`, mặc định cổng 20128) và cấu
 * hình provider tương ứng trong Dashboard — client này KHÔNG tự khởi động 9Router. Được gọi thật từ
 * {@link WritingAiGradingService} (UC-40/41) và {@link ReflexSpeakingContentAiGradingService}/
 * {@link ReflexWritingGrammarAiGradingService} (UC-23b) — LƯU Ý VẬN HÀNH: 9Router hiện CHỈ chạy local
 * trên máy dev, CHƯA có kế hoạch tự host cho staging/production (xem Javadoc các service gọi client này).
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

    public NineRouterAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        String resolvedModel = (model == null || model.isBlank()) ? defaultModel : model;
        if (resolvedModel == null || resolvedModel.isBlank()) {
            log.warn("NineRouterAiClient: chưa cấu hình model (app.ai-grading.nine-router-model hoặc tham số model).");
            return null;
        }
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
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("NineRouterAiClient: gọi 9Router lỗi (HTTP {}): {}", response.statusCode(), response.body());
                return null;
            }
            JsonNode json = objectMapper.readTree(response.body());
            return json.path("choices").path(0).path("message").path("content").asText(null);
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
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("NineRouterAiClient: gọi 9Router (audio chat) lỗi (HTTP {}): {}", response.statusCode(), response.body());
                return null;
            }
            JsonNode json = objectMapper.readTree(response.body());
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
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("NineRouterAiClient: gọi 9Router (STT) lỗi (HTTP {}): {}", response.statusCode(), response.body());
                return null;
            }
            JsonNode json = objectMapper.readTree(response.body());
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
