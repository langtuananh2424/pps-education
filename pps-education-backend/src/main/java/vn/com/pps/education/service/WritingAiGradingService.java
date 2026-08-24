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
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22/2026-08-23 — UC-40/UC-41: chấm AI cho
 * câu ESSAY thuộc Bài {@code Exercise.skillCategory=WRITING}, thay luồng luôn chờ Giáo viên chấm thủ
 * công (mặc định cũ, xem Javadoc {@link ExerciseAttemptService}) CHỈ cho riêng nhóm Bài này. Gọi từ
 * {@link ExerciseAttemptService#gradeAndFinalize}.
 *
 * V140 (2026-08-23) — rubric giờ chọn theo Khối (6/7/8/9) + chương trình (IELTS/CAMBRIDGE) của
 * Curriculum chứa Đề (Exercise → Exam → Curriculum), KHÔNG còn 1 rubric tĩnh "Writing Scoring Standard"
 * chung cho mọi học sinh — xem {@link RubricByGradeTrackLoader}, dùng CHUNG rubric với
 * {@link ReflexWritingGrammarAiGradingService} (cùng 1 chuẩn chấm writing giáo viên cung cấp). AI trả
 * thẳng % theo đúng thang "Mức điểm (%)" của bảng, không còn quy đổi band 0-9 → %.
 *
 * Ưu tiên Gemini Flash (GEMINI_API_KEY, đã xác nhận với người dùng 2026-08-22 — free tier/rẻ hơn),
 * fallback Claude (ANTHROPIC_API_KEY) nếu chưa cấu hình Gemini — cùng 2 key dùng chung với
 * SpeakingAiGradingTestService (xem {@code app.ai-grading.*}). Lỗi gọi API (thiếu key/timeout/HTTP
 * lỗi) HOẶC chưa xác định được đúng rubric trả về {@code null} — KHÔNG throw, để câu trả lời rơi lại
 * đúng hàng chờ chấm tay UC-41 (ManualGradingService) thay vì làm hỏng cả giao dịch nộp bài của học sinh.
 */
@Service
public class WritingAiGradingService {

    private static final Logger log = LoggerFactory.getLogger(WritingAiGradingService.class);

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

    public WritingAiGradingService(ObjectMapper objectMapper, RubricByGradeTrackLoader rubricLoader) {
        this.objectMapper = objectMapper;
        this.rubricLoader = rubricLoader;
    }

    public record GradeResult(int scorePercent, String feedback) {
    }

    /** Số lần thử tối đa (1 lần đầu + tối đa RETRY lần) khi gặp lỗi tạm thời (503/5xx, KHÔNG gồm 429) — xem {@link #isRetryable}. */
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1500;

    /**
     * Trả null nếu chưa xác định được rubric (xem {@link RubricByGradeTrackLoader}), chưa cấu hình key
     * nào, HOẶC gọi AI thất bại (kể cả sau khi đã thử lại) — caller (ExerciseAttemptService) coi đây là
     * "chưa chấm được", câu trả lời tự động rơi lại hàng chờ Giáo viên chấm tay (UC-41), KHÔNG chặn
     * học sinh nộp bài.
     *
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — tự thử lại tối đa
     * {@link #MAX_ATTEMPTS} lần khi Gemini/Claude trả lỗi tạm thời (HTTP 503 "high demand"/5xx — đã
     * gặp thực tế 2 lần trong lúc verify rubric, retry thủ công là qua ngay). CHỈ retry cùng 1
     * provider đang dùng (không tự đổi sang provider khác giữa các lần thử — xem Javadoc lớp về thứ tự
     * ưu tiên Gemini/Claude).
     */
    public GradeResult grade(String essayText, Curriculum curriculum) {
        if (essayText == null || essayText.isBlank()) {
            return null;
        }
        String rubric = rubricLoader.load(RUBRIC_FILE_PREFIX, curriculum.getGradeLevel(), curriculum.getTrack());
        if (rubric == null) {
            return null;
        }
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                if (!geminiApiKey.isBlank()) {
                    return callGemini(essayText, rubric);
                }
                if (!anthropicApiKey.isBlank()) {
                    return callClaude(essayText, rubric);
                }
                log.warn("WritingAiGradingService: chưa cấu hình GEMINI_API_KEY hoặc ANTHROPIC_API_KEY — bỏ qua chấm AI, rơi lại hàng chờ chấm tay.");
                return null;
            } catch (Exception e) {
                if (attempt < MAX_ATTEMPTS && isRetryable(e)) {
                    log.warn("WritingAiGradingService: lỗi tạm thời, thử lại lần {}/{}. {}", attempt + 1, MAX_ATTEMPTS, e.getMessage());
                    sleepQuietly(RETRY_DELAY_MS);
                    continue;
                }
                log.warn("WritingAiGradingService: gọi AI chấm bài thất bại, rơi lại hàng chờ chấm tay. {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * CHỈ HTTP 503 (quá tải, "high demand")/5xx — đáng thử lại ngay (vài giây sau thường qua). KHÔNG
     * retry HTTP 429 — đã gặp thực tế 2026-08-22: 429 của Gemini free tier là RESOURCE_EXHAUSTED theo
     * quota NGÀY (generate_content_free_tier_requests, limit 20/ngày — xem "retry in Xs" thực tế lên
     * tới 50+s trong response, không phải rate-limit vài giây), retry ngay càng làm cạn quota nhanh hơn
     * (3 lần thử = tốn 3 lần quota cho 1 lần nộp bài) mà gần như chắc chắn vẫn thất bại. Lỗi khác (JSON
     * sai format, network...) cũng không retry, trả null ngay.
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
        return "Bạn là giám khảo chấm bài Writing tiếng Anh cho học sinh trung tâm Anh ngữ. Bảng tiêu chí chấm dưới "
                + "đây liệt kê các mức điểm % (cột \"Mức điểm (%)\"/\"Điểm\") kèm mô tả (descriptor) của TỪNG tiêu "
                + "chí ở mức đó — đọc kỹ mô tả ở TẤT CẢ các cột tiêu chí, xác định mức % phù hợp nhất với bài viết "
                + "dựa trên toàn bộ các tiêu chí (có thể nội suy giữa 2 mức liền kề nếu bài làm nằm giữa):\n"
                + rubric
                + "\nChỉ trả lời DUY NHẤT 1 JSON hợp lệ, không thêm chữ nào khác, đúng format: "
                + "{\"scorePercent\": <số nguyên 0-100, theo đúng thang % của bảng trên>, "
                + "\"feedback\": \"<nhận xét chi tiết tiếng Việt, đánh giá lần lượt theo TỪNG tiêu chí trong bảng, "
                + "nêu điểm mạnh/điểm yếu cụ thể và gợi ý cải thiện>\"}";
    }

    private GradeResult callClaude(String essayText, String rubric) throws IOException, InterruptedException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", CLAUDE_GRADING_MODEL);
        payload.put("max_tokens", 512);
        payload.put("system", systemPrompt(rubric));
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

    private GradeResult callGemini(String essayText, String rubric) throws IOException, InterruptedException {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode systemInstruction = payload.putObject("system_instruction");
        systemInstruction.putArray("parts").addObject().put("text", systemPrompt(rubric));
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

    /** LLM đôi khi bọc thêm text/markdown quanh JSON dù đã dặn "chỉ trả JSON" — cắt từ '{' đầu tới '}' cuối cho an toàn. */
    private GradeResult parseResult(String rawText) throws IOException {
        int start = rawText.indexOf('{');
        int end = rawText.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IOException("Model chấm bài không trả về JSON hợp lệ: " + rawText);
        }
        JsonNode parsed = objectMapper.readTree(rawText.substring(start, end + 1));
        int scorePercent = Math.min(100, Math.max(0, parsed.path("scorePercent").asInt(0)));
        return new GradeResult(scorePercent, parsed.path("feedback").asText(""));
    }
}
