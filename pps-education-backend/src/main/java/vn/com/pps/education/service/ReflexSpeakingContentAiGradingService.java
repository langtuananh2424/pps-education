package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.com.pps.education.domain.Curriculum;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22/2026-08-23 — UC-23b (Video phản xạ) V2,
 * bước 2 của mỗi câu hỏi (SAU khi đạt phần viết trước — xem {@link ReflexWritingGrammarAiGradingService}):
 * chuyển giọng nói (audio đã ghi) sang chữ RỒI chấm nội dung theo rubric — đạt ngưỡng mới mở khoá câu
 * tiếp theo (xem {@link ReflexSequentialGradingService}).
 *
 * CHỈ dùng Gemini (GEMINI_API_KEY) — Gemini nhận audio đa phương thức trực tiếp trong 1 lệnh gọi
 * (transcribe + chấm luôn), khác Claude (không nhận input audio) và OpenAI (STT tốt nhưng cần
 * billing, xem SpeakingAiGradingTestService — Gemini là lựa chọn người dùng đã chốt cho luồng này).
 *
 * V140 (2026-08-23) — rubric giờ chọn theo Khối (6/7/8/9) + chương trình (IELTS/CAMBRIDGE) của
 * Curriculum chứa video (xem {@link RubricByGradeTrackLoader}), KHÔNG còn 1 rubric tĩnh cho mọi học
 * sinh. AI trả thẳng % theo đúng thang "Mức điểm (%)" của bảng, không còn quy đổi band 0-9 → %.
 * Lỗi gọi API HOẶC chưa xác định được đúng rubric trả về {@code null} — caller tự quyết định.
 */
@Service
public class ReflexSpeakingContentAiGradingService {

    private static final Logger log = LoggerFactory.getLogger(ReflexSpeakingContentAiGradingService.class);

    private static final String RUBRIC_FILE_PREFIX = "speaking-rubric";
    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — fix bug thật: "gemini-flash-latest"
     * là ALIAS, đã âm thầm trỏ sang "gemini-3.7-flash" (bản preview mới, quota free tier chỉ 20
     * request/NGÀY — phát hiện qua log RESOURCE_EXHAUSTED thực tế lúc test). Đổi sang tên model CỤ THỂ
     * (không dùng alias "-latest" nữa) để tránh Google tự đổi ngầm sang model mới/quota thấp hơn lần
     * nữa mà không báo trước. "flash-lite" đã verify chấp nhận audio inline_data bình thường (test
     * thực tế qua curl).
     */
    private static final String GEMINI_MODEL = "gemini-3.5-flash-lite";

    private final ObjectMapper objectMapper;
    private final RubricByGradeTrackLoader rubricLoader;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${app.ai-grading.gemini-api-key:}")
    private String geminiApiKey;

    public ReflexSpeakingContentAiGradingService(ObjectMapper objectMapper, RubricByGradeTrackLoader rubricLoader) {
        this.objectMapper = objectMapper;
        this.rubricLoader = rubricLoader;
    }

    public record GradeResult(String transcript, int scorePercent, String feedback) {
    }

    /** Số lần thử tối đa (1 lần đầu + tối đa RETRY lần) khi gặp lỗi tạm thời (503/5xx, KHÔNG gồm 429) — xem {@link #isRetryable}. */
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1500;

    /**
     * audioBytes: tải trực tiếp từ audioUrl đã lưu R2 (caller tự tải, xem
     * {@link ReflexSequentialGradingService}) — tránh phụ thuộc MultipartFile ở service này.
     * Trả null nếu chưa xác định được rubric (xem {@link RubricByGradeTrackLoader}), chưa cấu hình
     * GEMINI_API_KEY, HOẶC gọi AI thất bại (kể cả sau khi đã thử lại) — caller tự quyết định.
     */
    public GradeResult grade(byte[] audioBytes, String mimeType, Curriculum curriculum) {
        if (audioBytes == null || audioBytes.length == 0 || geminiApiKey.isBlank()) {
            if (geminiApiKey.isBlank()) {
                log.warn("ReflexSpeakingContentAiGradingService: chưa cấu hình GEMINI_API_KEY.");
            }
            return null;
        }
        String rubric = rubricLoader.load(RUBRIC_FILE_PREFIX, curriculum.getGradeLevel(), curriculum.getTrack());
        if (rubric == null) {
            return null;
        }
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return callGemini(audioBytes, mimeType, rubric);
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

    private GradeResult callGemini(byte[] audioBytes, String mimeType, String rubric) throws IOException, InterruptedException {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode systemInstruction = payload.putObject("system_instruction");
        systemInstruction.putArray("parts").addObject().put("text", systemPrompt(rubric));
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

    private String systemPrompt(String rubric) {
        return "Bạn là giám khảo chấm Speaking tiếng Anh cho học sinh trung tâm Anh ngữ. Trước tiên transcribe chính "
                + "xác audio thành chữ. Bảng tiêu chí chấm dưới đây liệt kê các mức điểm % (cột \"Mức điểm (%)\") kèm "
                + "mô tả (descriptor) của TỪNG tiêu chí ở mức đó — đọc kỹ mô tả ở TẤT CẢ các cột tiêu chí, xác định "
                + "mức % phù hợp nhất với bài nói dựa trên toàn bộ các tiêu chí (có thể nội suy giữa 2 mức liền kề "
                + "nếu bài làm nằm giữa):\n"
                + rubric
                + "\nChỉ trả lời DUY NHẤT 1 JSON hợp lệ, không thêm chữ nào khác, đúng format: "
                + "{\"transcript\": \"<chữ đã transcribe từ audio>\", \"scorePercent\": <số nguyên 0-100, theo đúng "
                + "thang % của bảng trên>, \"feedback\": \"<nhận xét chi tiết tiếng Việt, đánh giá lần lượt theo "
                + "TỪNG tiêu chí trong bảng, nêu điểm mạnh/điểm yếu cụ thể và gợi ý cải thiện>\"}";
    }

    /** LLM đôi khi bọc thêm text/markdown quanh JSON dù đã dặn "chỉ trả JSON" — cắt từ '{' đầu tới '}' cuối cho an toàn. */
    private GradeResult parseResult(String rawText) throws IOException {
        int start = rawText.indexOf('{');
        int end = rawText.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IOException("Model chấm bài không trả về JSON hợp lệ: " + rawText);
        }
        JsonNode parsed = objectMapper.readTree(rawText.substring(start, end + 1));
        int scorePercent = Math.min(100, Math.max(0, parsed.path("scorePercent").asInt(0)));
        return new GradeResult(parsed.path("transcript").asText(""), scorePercent, parsed.path("feedback").asText(""));
    }
}
