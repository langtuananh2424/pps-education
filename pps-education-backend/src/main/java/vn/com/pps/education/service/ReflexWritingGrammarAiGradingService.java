package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.com.pps.education.domain.Curriculum;

import java.io.IOException;
import java.util.Map;

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
 * V145 (2026-08-25, xác nhận với người dùng trên nhánh spike/openrouter-ai-rotation) — gọi AI chấm qua
 * {@link NineRouterAiClient#chat} (combo cấu hình sẵn trong Dashboard 9Router → Combo & Vision Adapter,
 * VD "teacher-models") thay vì gọi thẳng Gemini/Claude như trước — đồng bộ với
 * {@link WritingAiGradingService} và {@link ReflexSpeakingContentAiGradingService}. Bỏ luôn logic tự
 * chọn Gemini/Claude theo key nào có sẵn, vì combo trong Dashboard 9Router đã tự làm fallback giữa
 * nhiều provider. LƯU Ý VẬN HÀNH: 9Router hiện CHỈ chạy local trên máy dev — CHƯA có kế hoạch tự host
 * cho staging/production.
 *
 * Lỗi gọi API HOẶC chưa xác định được đúng rubric (thiếu gradeLevel/track, hoặc giáo viên chưa cung cấp
 * bảng cho tổ hợp đó) trả về {@code null} — caller tự quyết định (KHÔNG tự cho qua, KHÔNG tự đoán dùng
 * rubric của Khối/track khác).
 *
 * V147 (2026-08-25, xác nhận với người dùng, cùng gốc bug với ReflexSpeakingContentAiGradingService) —
 * TRƯỚC ĐÂY chỉ gửi câu trả lời của học sinh, KHÔNG hề gửi câu hỏi gốc ({@link ReviewVideoQuestion#getPrompt})
 * — dù rubric Writing có cột CONTENT (kiểm tra liên quan nhiệm vụ) xuyên suốt mọi mức điểm, AI không có
 * căn cứ nào để biết "nhiệm vụ" là câu hỏi nào mà đối chiếu, nên câu trả lời lạc đề nhưng ngữ pháp đúng
 * vẫn được chấm cao. Thêm {@code questionPrompt} vào tham số + system prompt để AI đối chiếu đúng.
 */
@Service
public class ReflexWritingGrammarAiGradingService {

    private static final Logger log = LoggerFactory.getLogger(ReflexWritingGrammarAiGradingService.class);

    private static final String RUBRIC_FILE_PREFIX = "writing-rubric";
    private static final String SYSTEM_PROMPT_FILE = "reflex-writing-grammar-grading-system-prompt.txt";

    private final ObjectMapper objectMapper;
    private final RubricByGradeTrackLoader rubricLoader;
    private final NineRouterAiClient nineRouterAiClient;
    private final PromptTemplateLoader promptTemplateLoader;

    public ReflexWritingGrammarAiGradingService(ObjectMapper objectMapper,
                                                 RubricByGradeTrackLoader rubricLoader,
                                                 NineRouterAiClient nineRouterAiClient,
                                                 PromptTemplateLoader promptTemplateLoader) {
        this.objectMapper = objectMapper;
        this.rubricLoader = rubricLoader;
        this.nineRouterAiClient = nineRouterAiClient;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    /**
     * V141 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — correctedAnswer: câu trả
     * lời CHỈ sửa lỗi ngữ pháp trong chính bài học sinh viết (giữ nguyên cấu trúc/ý gốc), KHÔNG phải
     * câu mẫu tự bịa — luôn yêu cầu AI trả về cùng lúc chấm (không gọi thêm lần API riêng), FE tự quyết
     * định khi nào hiện ra (chỉ hiện từ lần nộp thứ 3 trở đi mà vẫn chưa đạt, xem ReflexVideoTaskPage.tsx).
     */
    public record GradeResult(int scorePercent, String feedback, String correctedAnswer) {
    }

    /**
     * Trả null nếu chưa xác định được rubric (xem {@link RubricByGradeTrackLoader}) HOẶC 9Router chấm
     * thất bại (kể cả sau khi tự retry/fallback nội bộ giữa các provider trong combo) — caller tự quyết
     * định (không tự cho qua).
     */
    public GradeResult grade(String answerText, String questionPrompt, Curriculum curriculum) {
        if (answerText == null || answerText.isBlank()) {
            return null;
        }
        String rubric = rubricLoader.load(RUBRIC_FILE_PREFIX, curriculum.getGradeLevel(), curriculum.getTrack());
        if (rubric == null) {
            return null;
        }
        String rawText = nineRouterAiClient.chat(
                systemPrompt(rubric, questionPrompt),
                "Câu hỏi: \"" + questionPrompt + "\"\nCâu trả lời của học sinh: \"" + answerText + "\"",
                null);
        if (rawText == null) {
            log.warn("ReflexWritingGrammarAiGradingService: 9Router chấm thất bại.");
            return null;
        }
        try {
            return parseResult(rawText);
        } catch (IOException e) {
            log.warn("ReflexWritingGrammarAiGradingService: parse kết quả chấm thất bại. {}", e.getMessage());
            return null;
        }
    }

    private String systemPrompt(String rubric, String questionPrompt) {
        return promptTemplateLoader.load(SYSTEM_PROMPT_FILE, Map.of(
                "QUESTION_PROMPT", questionPrompt,
                "RUBRIC", rubric));
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
