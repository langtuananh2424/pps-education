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

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22/2026-08-23 — UC-23b (Video phản xạ) V2,
 * bước 1 của mỗi câu hỏi: chấm ngữ pháp phần "viết trước" (thời thì, chia động từ...) — đạt ngưỡng mới
 * mở khoá phần ghi âm (xem {@link ReflexSequentialGradingService}). Mirror cấu trúc
 * {@link WritingAiGradingService} (dùng CHUNG rubric writing theo Khối/track — cùng 1 chuẩn chấm
 * writing giáo viên cung cấp, xem {@link RubricByGradeTrackLoader}).
 *
 * V140 (2026-08-23) — rubric giờ chọn theo Khối (6/7/8/9) + chương trình (IELTS/CAMBRIDGE) của
 * Curriculum chứa video, KHÔNG còn 1 rubric tĩnh cho mọi học sinh (rubric cũ band 0-9 tổng quát đã bị
 * thay — xem RubricByGradeTrackLoader). AI trả thẳng % theo đúng thang "Mức điểm (%)" của bảng, không
 * còn quy đổi band 0-9 → %.
 *
 * Ưu tiên Gemini Flash (GEMINI_API_KEY), fallback Claude (ANTHROPIC_API_KEY) — cùng key dùng chung
 * {@code app.ai-grading.*}. Lỗi gọi API HOẶC chưa xác định được đúng rubric (thiếu gradeLevel/track,
 * hoặc giáo viên chưa cung cấp bảng cho tổ hợp đó) trả về {@code null} — caller tự quyết định (KHÔNG
 * tự cho qua, KHÔNG tự đoán dùng rubric của Khối/track khác).
 */
@Service
public class ReflexWritingGrammarAiGradingService {

    private static final Logger log = LoggerFactory.getLogger(ReflexWritingGrammarAiGradingService.class);

    private static final String RUBRIC_FILE_PREFIX = "writing-rubric";
    private static final String CLAUDE_GRADING_MODEL = "claude-haiku-4-5-20251001";
    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — fix bug thật: "gemini-flash-latest"
     * là ALIAS, đã âm thầm trỏ sang "gemini-3.7-flash" (bản preview mới, quota free tier chỉ 20
     * request/NGÀY — phát hiện qua log RESOURCE_EXHAUSTED thực tế lúc test). Đổi sang tên model CỤ THỂ
     * (không dùng alias "-latest" nữa) để tránh Google tự đổi ngầm sang model mới/quota thấp hơn lần nữa.
     */
    private static final String GEMINI_MODEL = "gemini-3.5-flash-lite";

    private final ObjectMapper objectMapper;
    private final RubricByGradeTrackLoader rubricLoader;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${app.ai-grading.anthropic-api-key:}")
    private String anthropicApiKey;

    @Value("${app.ai-grading.gemini-api-key:}")
    private String geminiApiKey;

    public ReflexWritingGrammarAiGradingService(ObjectMapper objectMapper, RubricByGradeTrackLoader rubricLoader) {
        this.objectMapper = objectMapper;
        this.rubricLoader = rubricLoader;
    }

    /**
     * V141 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — correctedAnswer: câu trả
     * lời CHỈ sửa lỗi ngữ pháp trong chính bài học sinh viết (giữ nguyên cấu trúc/ý gốc), KHÔNG phải
     * câu mẫu tự bịa — luôn yêu cầu AI trả về cùng lúc chấm (không gọi thêm lần API riêng), FE tự quyết
     * định khi nào hiện ra (chỉ hiện từ lần nộp thứ 3 trở đi mà vẫn chưa đạt, xem ReflexVideoTaskPage.tsx).
     */
    public record GradeResult(int scorePercent, String feedback, String correctedAnswer) {
    }

    /** Số lần thử tối đa (1 lần đầu + tối đa RETRY lần) khi gặp lỗi tạm thời (503/5xx, KHÔNG gồm 429) — xem {@link #isRetryable}. */
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1500;

    /**
     * Trả null nếu chưa xác định được rubric (xem {@link RubricByGradeTrackLoader}), chưa cấu hình key
     * nào, HOẶC gọi AI thất bại (kể cả sau khi đã thử lại) — caller tự quyết định (không tự cho qua).
     */
    public GradeResult grade(String answerText, Curriculum curriculum) {
        if (answerText == null || answerText.isBlank()) {
            return null;
        }
        String rubric = rubricLoader.load(RUBRIC_FILE_PREFIX, curriculum.getGradeLevel(), curriculum.getTrack());
        if (rubric == null) {
            return null;
        }
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                if (!geminiApiKey.isBlank()) {
                    return callGemini(answerText, rubric);
                }
                if (!anthropicApiKey.isBlank()) {
                    return callClaude(answerText, rubric);
                }
                log.warn("ReflexWritingGrammarAiGradingService: chưa cấu hình GEMINI_API_KEY hoặc ANTHROPIC_API_KEY.");
                return null;
            } catch (Exception e) {
                if (attempt < MAX_ATTEMPTS && isRetryable(e)) {
                    log.warn("ReflexWritingGrammarAiGradingService: lỗi tạm thời, thử lại lần {}/{}. {}", attempt + 1, MAX_ATTEMPTS, e.getMessage());
                    sleepQuietly(RETRY_DELAY_MS);
                    continue;
                }
                log.warn("ReflexWritingGrammarAiGradingService: gọi AI chấm thất bại. {}", e.getMessage());
                return null;
            }
        }
        return null;
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
        return "Bạn là giám khảo chấm phần viết trả lời (trước khi nói lại) của học sinh trung tâm Anh ngữ. Bảng "
                + "tiêu chí chấm dưới đây liệt kê các mức điểm % (cột \"Mức điểm (%)\") kèm mô tả (descriptor) của "
                + "TỪNG tiêu chí ở mức đó — đọc kỹ mô tả ở TẤT CẢ các cột tiêu chí, xác định mức % phù hợp nhất với "
                + "bài làm dựa trên toàn bộ các tiêu chí (có thể nội suy giữa 2 mức liền kề nếu bài làm nằm giữa):\n"
                + rubric
                + "\nNgoài chấm điểm, hãy sửa lại CHÍNH câu trả lời của học sinh thành bản đúng ngữ pháp (correctedAnswer): "
                + "CHỈ sửa lỗi ngữ pháp/chính tả/từ vựng dùng sai trong câu học sinh đã viết, GIỮ NGUYÊN cấu trúc câu và "
                + "ý tưởng gốc của học sinh — TUYỆT ĐỐI KHÔNG tự viết lại thành 1 câu trả lời khác, KHÔNG thêm ý mới học "
                + "sinh chưa viết, KHÔNG nâng cấp từ vựng lên trình độ cao hơn nếu không phải lỗi sai."
                + "\nChỉ trả lời DUY NHẤT 1 JSON hợp lệ, không thêm chữ nào khác, đúng format: "
                + "{\"scorePercent\": <số nguyên 0-100, theo đúng thang % của bảng trên>, "
                + "\"feedback\": \"<nhận xét chi tiết tiếng Việt, đánh giá lần lượt theo TỪNG tiêu chí trong bảng, "
                + "nêu điểm mạnh/điểm yếu cụ thể và gợi ý cải thiện>\", "
                + "\"correctedAnswer\": \"<câu trả lời của học sinh sau khi CHỈ sửa lỗi, giữ nguyên cấu trúc/ý gốc>\"}";
    }

    private GradeResult callClaude(String answerText, String rubric) throws IOException, InterruptedException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", CLAUDE_GRADING_MODEL);
        payload.put("max_tokens", 512);
        payload.put("system", systemPrompt(rubric));
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

    private GradeResult callGemini(String answerText, String rubric) throws IOException, InterruptedException {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode systemInstruction = payload.putObject("system_instruction");
        systemInstruction.putArray("parts").addObject().put("text", systemPrompt(rubric));
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

    /** LLM đôi khi bọc thêm text/markdown quanh JSON dù đã dặn "chỉ trả JSON" — cắt từ '{' đầu tới '}' cuối cho an toàn. */
    private GradeResult parseResult(String rawText) throws IOException {
        int start = rawText.indexOf('{');
        int end = rawText.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IOException("Model chấm bài không trả về JSON hợp lệ: " + rawText);
        }
        JsonNode parsed = objectMapper.readTree(rawText.substring(start, end + 1));
        int scorePercent = Math.min(100, Math.max(0, parsed.path("scorePercent").asInt(0)));
        return new GradeResult(scorePercent, parsed.path("feedback").asText(""), parsed.path("correctedAnswer").asText(""));
    }
}
