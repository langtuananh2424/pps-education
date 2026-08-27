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
 * V145 (2026-08-24, xác nhận với người dùng trên nhánh spike/openrouter-ai-rotation) — gọi AI chấm qua
 * {@link NineRouterAiClient} (proxy local xoay vòng nhiều provider/API key, xem Javadoc lớp đó) thay vì
 * gọi thẳng Gemini/Claude như trước — bỏ luôn logic tự chọn Gemini/Claude theo key nào có sẵn, vì
 * combo trong Dashboard 9Router đã tự làm fallback giữa nhiều provider. LƯU Ý VẬN HÀNH: 9Router hiện
 * CHỈ chạy local trên máy dev (xem app.ai-grading.nine-router-base-url) — CHƯA có kế hoạch tự host cho
 * staging/production.
 *
 * Lỗi gọi API (thiếu model/timeout/HTTP lỗi) HOẶC chưa xác định được đúng rubric trả về {@code null} —
 * KHÔNG throw, để câu trả lời rơi lại đúng hàng chờ chấm tay UC-41 (ManualGradingService) thay vì làm
 * hỏng cả giao dịch nộp bài của học sinh.
 *
 * V147 (2026-08-25, xác nhận với người dùng) — system prompt tách ra
 * {@code resources/prompts/writing-grading-system-prompt.txt} (xem {@link PromptTemplateLoader}), yêu
 * cầu feedback theo cấu trúc rõ ràng (điểm từng tiêu chí, Strongest area, Main limitation, What you did
 * well, What is limiting your score, Top 3 priorities, Target for next submission) thay vì 1 đoạn văn
 * liền mạch — đồng bộ với {@link ReflexWritingGrammarAiGradingService}/
 * {@link ReflexSpeakingContentAiGradingService}.
 */
@Service
public class WritingAiGradingService {

    private static final Logger log = LoggerFactory.getLogger(WritingAiGradingService.class);

    private static final String RUBRIC_FILE_PREFIX = "writing-rubric";
    private static final String SYSTEM_PROMPT_FILE = "writing-grading-system-prompt.txt";

    private final ObjectMapper objectMapper;
    private final RubricByGradeTrackLoader rubricLoader;
    private final NineRouterAiClient nineRouterAiClient;
    private final PromptTemplateLoader promptTemplateLoader;

    public WritingAiGradingService(ObjectMapper objectMapper, RubricByGradeTrackLoader rubricLoader,
                                    NineRouterAiClient nineRouterAiClient, PromptTemplateLoader promptTemplateLoader) {
        this.objectMapper = objectMapper;
        this.rubricLoader = rubricLoader;
        this.nineRouterAiClient = nineRouterAiClient;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    public record GradeResult(int scorePercent, String feedback) {
    }

    /**
     * Trả null nếu chưa xác định được rubric (xem {@link RubricByGradeTrackLoader}) HOẶC 9Router chấm
     * thất bại (kể cả sau khi tự retry/fallback nội bộ giữa các provider trong combo) — caller
     * (ExerciseAttemptService) coi đây là "chưa chấm được", câu trả lời tự động rơi lại hàng chờ Giáo
     * viên chấm tay (UC-41), KHÔNG chặn học sinh nộp bài.
     */
    public GradeResult grade(String essayText, Curriculum curriculum) {
        if (essayText == null || essayText.isBlank()) {
            return null;
        }
        String rubric = rubricLoader.load(RUBRIC_FILE_PREFIX, curriculum.getGradeLevel(), curriculum.getTrack());
        if (rubric == null) {
            return null;
        }
        String rawText = nineRouterAiClient.chat(systemPrompt(rubric), "Bài viết của học sinh: \"" + essayText + "\"", null);
        if (rawText == null) {
            log.warn("WritingAiGradingService: 9Router chấm thất bại, rơi lại hàng chờ chấm tay.");
            return null;
        }
        try {
            return parseResult(rawText);
        } catch (IOException e) {
            log.warn("WritingAiGradingService: parse kết quả chấm thất bại, rơi lại hàng chờ chấm tay. {}", e.getMessage());
            return null;
        }
    }

    private String systemPrompt(String rubric) {
        return promptTemplateLoader.load(SYSTEM_PROMPT_FILE, Map.of("RUBRIC", rubric));
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
