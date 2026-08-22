package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.dto.SpeakingAiGradingTestResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * SPIKE/TEST riêng — KHÔNG phải business logic của 1 UC đã đặc tả trong docs/uc/. Dựng theo yêu cầu
 * người dùng (2026-08-22) để đánh giá khả thi kỹ thuật + chi phí thật cho hướng "AI chấm Speaking"
 * (dự kiến sau này thay luồng chấm thủ công Video phản xạ hiện có — UC-23b, ReviewVideoQuestion/
 * ReviewVideoQuestionSubmission) — KHÔNG đọc/ghi bảng nghiệp vụ nào, không ảnh hưởng luồng thật.
 *
 * Rubric chấm hiện là PLACEHOLDER (xem {@link #PLACEHOLDER_RUBRIC}) — người dùng sẽ cung cấp file
 * .md thật sau khi chốt tiêu chí với người chấm; PHẢI thay hằng số này trước khi coi kết quả chấm
 * ở đây là đáng tin cho quyết định nghiệp vụ thật (xem .claude/rules/business-fidelity.md).
 *
 * 2 nhà cung cấp AI (chọn qua tham số "provider" từ FE, xem {@link Provider} — bổ sung 2026-08-22, đã
 * xác nhận với người dùng: OpenAI yêu cầu billing dù key hợp lệ (insufficient_quota 429 khi tài khoản
 * chưa nạp tiền), Anthropic yêu cầu nạp tiền trước khi gọi được API — cả 2 chặn test ngay lúc chưa
 * quyết định dùng thật hay không):
 * - OPENAI_CLAUDE: audio -> lưu R2 -> OpenAI gpt-4o-mini-transcribe (Speech-to-Text, Claude KHÔNG
 *   nhận input audio trực tiếp) -> Claude Haiku 4.5 chấm nội dung/ngữ pháp.
 * - GEMINI: audio -> lưu R2 -> Gemini Flash (nhận audio đa phương thức trực tiếp, không cần bước STT
 *   riêng) -> CÙNG Gemini Flash chấm nội dung/ngữ pháp. Free tier, KHÔNG cần thẻ thanh toán (xem
 *   ai.google.dev) — LƯU Ý: dữ liệu gọi qua free tier CÓ THỂ được Google dùng để cải thiện sản phẩm
 *   (khác free tier trả phí) — chỉ chấp nhận được cho test kỹ thuật (voice tự ghi thử), KHÔNG dùng
 *   free tier cho dữ liệu học sinh thật khi lên production.
 * Không truyền "provider" (hoặc "AUTO") thì tự chọn theo key nào đã cấu hình, ưu tiên OPENAI_CLAUDE.
 *
 * Gọi thẳng REST API bằng java.net.http.HttpClient (spike, chưa có dấu hiệu cần trừu tượng hoá
 * interface theo OCP như AttendanceMethod — xem .claude/rules/solid.md — cho tới khi hướng này thật
 * sự lên production, lúc đó 2 nhánh Provider ở đây chính là ứng viên tự nhiên cho pattern đó).
 */
@Service
public class SpeakingAiGradingTestService {

    public enum Provider { AUTO, OPENAI_CLAUDE, GEMINI }

    private static final String PLACEHOLDER_RUBRIC = """
            [PLACEHOLDER - CHUA PHAI RUBRIC THAT, nguoi dung se cung cap file .md sau khi chot voi nguoi cham]
            Cham cau tra loi speaking cua hoc sinh tieng Anh theo thang % (0-100), tham khao khung CEFR:
            - 90-100%: trinh do B2+ - cau phuc, tu vung da dang dung ngu canh.
            - 70-89%: trinh do B1-B2 - tra loi dung trong tam cau hoi, ngu phap co ban dung.
            - 50-69%: trinh do A2-B1 - tra loi duoc nhung cau don gian, co loi ngu phap/tu vung ro.
            - Duoi 50%: chua tra loi dung trong tam cau hoi hoac cau qua ngan/khong ro nghia.
            """;

    private static final String OPENAI_TRANSCRIBE_MODEL = "gpt-4o-mini-transcribe";
    private static final String CLAUDE_GRADING_MODEL = "claude-haiku-4-5-20251001";
    private static final String GEMINI_MODEL = "gemini-flash-latest";

    private final MediaStorageService mediaStorageService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${app.ai-grading.openai-api-key:}")
    private String openAiApiKey;

    @Value("${app.ai-grading.anthropic-api-key:}")
    private String anthropicApiKey;

    @Value("${app.ai-grading.gemini-api-key:}")
    private String geminiApiKey;

    public SpeakingAiGradingTestService(MediaStorageService mediaStorageService, ObjectMapper objectMapper) {
        this.mediaStorageService = mediaStorageService;
        this.objectMapper = objectMapper;
    }

    public SpeakingAiGradingTestResponse grade(MultipartFile audio, String writingText, String providerParam) {
        Provider provider = resolveProvider(providerParam);
        String audioUrl = mediaStorageService.store(audio, "REVIEW_VIDEO_SUBMISSION");
        String transcript = provider == Provider.GEMINI ? transcribeWithGemini(audio) : transcribeWithOpenAi(audio);
        GradeResult content = gradeContent(transcript, provider);
        GradeResult grammar = (writingText == null || writingText.isBlank()) ? null : gradeGrammar(writingText, provider);
        return new SpeakingAiGradingTestResponse(
                audioUrl, transcript,
                content.scorePercent(), content.feedback(),
                grammar == null ? null : grammar.scorePercent(),
                grammar == null ? null : grammar.feedback());
    }

    private Provider resolveProvider(String providerParam) {
        Provider requested;
        try {
            requested = providerParam == null || providerParam.isBlank() ? Provider.AUTO : Provider.valueOf(providerParam);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("provider không hợp lệ: " + providerParam + " (chỉ nhận AUTO/OPENAI_CLAUDE/GEMINI)");
        }
        if (requested == Provider.OPENAI_CLAUDE) {
            if (openAiApiKey.isBlank()) {
                throw new IllegalArgumentException("Chưa cấu hình OPENAI_API_KEY (xem .env.example) — cần key thật để gọi Speech-to-Text.");
            }
            if (anthropicApiKey.isBlank()) {
                throw new IllegalArgumentException("Chưa cấu hình ANTHROPIC_API_KEY (xem .env.example) — cần key thật để chấm bài với Claude.");
            }
            return requested;
        }
        if (requested == Provider.GEMINI) {
            if (geminiApiKey.isBlank()) {
                throw new IllegalArgumentException("Chưa cấu hình GEMINI_API_KEY (xem .env.example) — cần key thật để dùng Gemini.");
            }
            return requested;
        }
        // AUTO: ưu tiên OPENAI_CLAUDE nếu đủ cả 2 key, không thì dùng GEMINI nếu có, không thì báo thiếu cấu hình.
        if (!openAiApiKey.isBlank() && !anthropicApiKey.isBlank()) {
            return Provider.OPENAI_CLAUDE;
        }
        if (!geminiApiKey.isBlank()) {
            return Provider.GEMINI;
        }
        throw new IllegalArgumentException("Chưa cấu hình đủ key cho nhà cung cấp AI nào (xem .env.example) — "
                + "cần OPENAI_API_KEY+ANTHROPIC_API_KEY, hoặc GEMINI_API_KEY.");
    }

    private String transcribeWithOpenAi(MultipartFile audio) {
        try {
            String boundary = "----ppsSpike" + UUID.randomUUID();
            byte[] body = buildMultipartBody(boundary, audio);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/audio/transcriptions"))
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalArgumentException("OpenAI transcription lỗi (HTTP " + response.statusCode() + "): " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            return json.path("text").asText("");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalArgumentException("Không gọi được OpenAI transcription: " + e.getMessage(), e);
        }
    }

    private byte[] buildMultipartBody(String boundary, MultipartFile audio) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String prefix = "--" + boundary + "\r\n";
        out.write((prefix + "Content-Disposition: form-data; name=\"model\"\r\n\r\n" + OPENAI_TRANSCRIBE_MODEL + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
        String filename = audio.getOriginalFilename() == null ? "audio.webm" : audio.getOriginalFilename();
        String contentType = audio.getContentType() == null ? "application/octet-stream" : audio.getContentType();
        out.write((prefix + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(audio.getBytes());
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** Gemini nhận audio trực tiếp (đa phương thức, khác Claude/GPT text-only) — không cần bước STT riêng như nhánh OpenAI. */
    private String transcribeWithGemini(MultipartFile audio) {
        try {
            String base64Audio = Base64.getEncoder().encodeToString(audio.getBytes());
            String mimeType = audio.getContentType() == null ? "audio/webm" : audio.getContentType();
            ObjectNode payload = objectMapper.createObjectNode();
            ArrayNode contents = payload.putArray("contents");
            ObjectNode contentEntry = contents.addObject();
            ArrayNode parts = contentEntry.putArray("parts");
            parts.addObject().put("text",
                    "Transcribe chính xác đoạn audio sau sang chữ (tiếng Anh nếu học sinh nói tiếng Anh). "
                            + "Chỉ trả về DUY NHẤT phần chữ đã transcribe, không thêm chú thích/nhận xét gì khác.");
            ObjectNode inlineData = parts.addObject().putObject("inline_data");
            inlineData.put("mime_type", mimeType);
            inlineData.put("data", base64Audio);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent"))
                    .header("x-goog-api-key", geminiApiKey)
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalArgumentException("Gemini transcription lỗi (HTTP " + response.statusCode() + "): " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            return json.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("").trim();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalArgumentException("Không gọi được Gemini transcription: " + e.getMessage(), e);
        }
    }

    private GradeResult gradeContent(String transcript, Provider provider) {
        String systemPrompt = "Bạn là giám khảo chấm Speaking tiếng Anh cho học sinh trung tâm Anh ngữ. Tiêu chí chấm:\n"
                + PLACEHOLDER_RUBRIC
                + "\nChỉ trả lời DUY NHẤT 1 JSON hợp lệ, không thêm chữ nào khác, đúng format: "
                + "{\"scorePercent\": <số nguyên 0-100>, \"feedback\": \"<nhận xét ngắn gọn tiếng Việt>\"}";
        String userContent = "Câu trả lời của học sinh (đã chuyển từ voice sang chữ, có thể có lỗi nhận dạng giọng nói): \""
                + transcript + "\"";
        return callGradingModel(systemPrompt, userContent, provider);
    }

    private GradeResult gradeGrammar(String writingText, Provider provider) {
        String systemPrompt = "Bạn là giáo viên chấm ngữ pháp tiếng Anh cho học sinh trung tâm Anh ngữ. "
                + "Chấm % dựa trên số lỗi ngữ pháp/chính tả so với độ dài bài viết (nhiều lỗi = điểm thấp). "
                + "Chỉ trả lời DUY NHẤT 1 JSON hợp lệ, không thêm chữ nào khác, đúng format: "
                + "{\"scorePercent\": <số nguyên 0-100>, \"feedback\": \"<liệt kê lỗi chính, tiếng Việt>\"}";
        String userContent = "Bài viết của học sinh: \"" + writingText + "\"";
        return callGradingModel(systemPrompt, userContent, provider);
    }

    private GradeResult callGradingModel(String systemPrompt, String userContent, Provider provider) {
        return provider == Provider.GEMINI ? callGeminiForGrade(systemPrompt, userContent) : callClaudeForGrade(systemPrompt, userContent);
    }

    private GradeResult callClaudeForGrade(String systemPrompt, String userContent) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", CLAUDE_GRADING_MODEL);
            payload.put("max_tokens", 512);
            payload.put("system", systemPrompt);
            ArrayNode messages = payload.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userContent);

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
                throw new IllegalArgumentException("Claude API lỗi (HTTP " + response.statusCode() + "): " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            String rawText = json.path("content").path(0).path("text").asText("");
            JsonNode parsed = objectMapper.readTree(extractJson(rawText));
            return new GradeResult(parsed.path("scorePercent").asInt(0), parsed.path("feedback").asText(""));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalArgumentException("Không gọi được Claude API: " + e.getMessage(), e);
        }
    }

    /** Gemini API (generativelanguage.googleapis.com) — free tier, không cần thẻ thanh toán. */
    private GradeResult callGeminiForGrade(String systemPrompt, String userContent) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            ObjectNode systemInstruction = payload.putObject("system_instruction");
            systemInstruction.putArray("parts").addObject().put("text", systemPrompt);
            ArrayNode contents = payload.putArray("contents");
            ObjectNode contentEntry = contents.addObject();
            contentEntry.putArray("parts").addObject().put("text", userContent);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent"))
                    .header("x-goog-api-key", geminiApiKey)
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalArgumentException("Gemini API lỗi (HTTP " + response.statusCode() + "): " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            String rawText = json.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            JsonNode parsed = objectMapper.readTree(extractJson(rawText));
            return new GradeResult(parsed.path("scorePercent").asInt(0), parsed.path("feedback").asText(""));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalArgumentException("Không gọi được Gemini API: " + e.getMessage(), e);
        }
    }

    /** LLM đôi khi bọc thêm text/markdown quanh JSON dù đã dặn "chỉ trả JSON" — cắt từ '{' đầu tới '}' cuối cho an toàn. */
    private String extractJson(String rawText) {
        int start = rawText.indexOf('{');
        int end = rawText.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Model chấm bài không trả về JSON hợp lệ: " + rawText);
        }
        return rawText.substring(start, end + 1);
    }

    private record GradeResult(int scorePercent, String feedback) {
    }
}
