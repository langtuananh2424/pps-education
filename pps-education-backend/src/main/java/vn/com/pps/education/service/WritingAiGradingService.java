package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.domain.Curriculum;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *
 * V182 (2026-09-16, xác nhận với người dùng — PILOT chỉ Khối 7 IELTS) — rubric "v3" do giáo viên cung
 * cấp mới KHÔNG còn là 1 bảng mô tả % đơn thuần như {@code writing-rubric-*.md} cũ — mà tự chứa TOÀN BỘ
 * hướng dẫn chấm (cấu hình/cổng dữ liệu/checkpoint/quy đổi) VÀ tự quy định LUÔN định dạng output riêng
 * (markdown 3 mục, KHÔNG phải JSON): bài viết được đánh dấu lỗi ngay trong chữ bằng cú pháp
 * {@code {{mã|đoạn văn bản}}} (5 loại: ok/sp/gr/wd/pu, 2 mức độ nặng nhẹ mỗi loại lỗi — xem chi tiết
 * trong rubric) thay vì 1 đoạn feedback dài dòng như trước.
 *
 * Nhận diện rubric v3 qua tiêu đề {@code "# WRITING RUBRIC"} (khác hẳn tiêu đề
 * {@code "# Tiêu chí chấm Writing —"} của rubric cũ) — dùng system prompt riêng
 * ({@code writing-grading-system-prompt-v3.txt}, gần như rỗng vì rubric đã tự đủ hướng dẫn) và parser
 * riêng (markdown, không phải JSON). Rubric CŨ (Khối 6/8/9, chưa đổi trong đợt pilot này) vẫn đi qua
 * đường JSON như cũ — {@link GradeResult#markedAnswer}/{@link GradeResult#criteriaScores} là {@code null}
 * trong trường hợp đó, FE tự fallback hiện {@code feedback} dạng văn bản như trước.
 *
 * Cần thêm {@code taskPrompt} (đề bài — {@link vn.com.pps.education.domain.Question#getContent()}) vì
 * rubric v3 có cổng G2 "Off-topic" cần đối chiếu đúng đề mới chấm được — rubric cũ KHÔNG có cổng này nên
 * trước đây service này chưa hề nhận taskPrompt (lỗ hổng tương tự đã sửa cho luồng Reflex ở V147).
 */
@Service
public class WritingAiGradingService {

    private static final Logger log = LoggerFactory.getLogger(WritingAiGradingService.class);

    private static final String RUBRIC_FILE_PREFIX = "writing-rubric";
    private static final String SYSTEM_PROMPT_FILE = "writing-grading-system-prompt.txt";
    private static final String SYSTEM_PROMPT_FILE_V3 = "writing-grading-system-prompt-v3.txt";
    private static final String RUBRIC_V3_MARKER = "# WRITING RUBRIC";

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

    /**
     * V182 — markedAnswer/criteriaScores CHỈ có giá trị khi rubric là bản "v3" (xem Javadoc lớp); với
     * rubric cũ, cả 2 là {@code null} và feedback vẫn là đoạn văn 7 mục như trước.
     */
    public record GradeResult(int scorePercent, String feedback, String markedAnswer, List<CriteriaScoreItem> criteriaScores) {
    }

    /**
     * Trả null nếu chưa xác định được rubric (xem {@link RubricByGradeTrackLoader}) HOẶC 9Router chấm
     * thất bại (kể cả sau khi tự retry/fallback nội bộ giữa các provider trong combo) — caller
     * (ExerciseAttemptService) coi đây là "chưa chấm được", câu trả lời tự động rơi lại hàng chờ Giáo
     * viên chấm tay (UC-41), KHÔNG chặn học sinh nộp bài.
     */
    public GradeResult grade(String essayText, String taskPrompt, Curriculum curriculum) {
        if (essayText == null || essayText.isBlank()) {
            return null;
        }
        String rubric = rubricLoader.load(RUBRIC_FILE_PREFIX, curriculum.getGradeLevel(), curriculum.getTrack());
        if (rubric == null) {
            return null;
        }
        boolean v3 = rubric.startsWith(RUBRIC_V3_MARKER);
        String rawText = nineRouterAiClient.chat(
                v3 ? systemPromptV3(rubric, taskPrompt) : systemPrompt(rubric),
                "Bài viết của học sinh: \"" + essayText + "\"",
                null);
        if (rawText == null) {
            log.warn("WritingAiGradingService: 9Router chấm thất bại, rơi lại hàng chờ chấm tay.");
            return null;
        }
        try {
            return v3 ? parseResultV3(rawText) : parseResultLegacy(rawText);
        } catch (IOException e) {
            log.warn("WritingAiGradingService: parse kết quả chấm thất bại, rơi lại hàng chờ chấm tay. {}", e.getMessage());
            return null;
        }
    }

    private String systemPrompt(String rubric) {
        return promptTemplateLoader.load(SYSTEM_PROMPT_FILE, Map.of("RUBRIC", rubric));
    }

    private String systemPromptV3(String rubric, String taskPrompt) {
        return promptTemplateLoader.load(SYSTEM_PROMPT_FILE_V3, Map.of(
                "TASK_PROMPT", taskPrompt == null ? "" : taskPrompt,
                "RUBRIC", rubric));
    }

    /** LLM đôi khi bọc thêm text/markdown quanh JSON dù đã dặn "chỉ trả JSON" — cắt từ '{' đầu tới '}' cuối cho an toàn. */
    private GradeResult parseResultLegacy(String rawText) throws IOException {
        int start = rawText.indexOf('{');
        int end = rawText.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IOException("Model chấm bài không trả về JSON hợp lệ: " + rawText);
        }
        JsonNode parsed = objectMapper.readTree(rawText.substring(start, end + 1));
        int scorePercent = Math.min(100, Math.max(0, parsed.path("scorePercent").asInt(0)));
        return new GradeResult(scorePercent, parsed.path("feedback").asText(""), null, null);
    }

    private static final Pattern SECTION_HEADER = Pattern.compile("(?m)^###\\s*(\\d+)\\.[^\\n]*$");
    private static final Pattern SCORE_ROW = Pattern.compile("^\\|\\s*\\*{0,2}([^|]+?)\\*{0,2}\\s*\\|\\s*\\*{0,2}(\\d+)\\s*%?\\*{0,2}\\s*\\|?\\s*$");

    /**
     * Parse output rubric v3 (markdown 3 mục đánh số — xem mục "Output bắt buộc" trong
     * {@code writing-rubric-grade7-ielts.md}, KHÔNG phải JSON). Ném IOException nếu thiếu mục 1/2 hoặc
     * bảng điểm không có dòng "Final" — caller coi như chấm thất bại, rơi lại hàng chờ chấm tay.
     */
    private GradeResult parseResultV3(String rawText) throws IOException {
        Map<Integer, String> sections = splitNumberedSections(rawText);
        String markedAnswer = stripCodeFence(sections.getOrDefault(1, ""));
        String scoreTable = sections.getOrDefault(2, "");
        String feedback = sections.getOrDefault(3, "").trim();
        if (markedAnswer.isBlank() || scoreTable.isBlank()) {
            throw new IOException("Model chấm bài (v3) không trả đủ mục 1/2 theo định dạng yêu cầu: " + rawText);
        }
        List<CriteriaScoreItem> criteriaScores = new ArrayList<>();
        Integer finalPercent = null;
        for (String line : scoreTable.split("\n")) {
            Matcher rowMatcher = SCORE_ROW.matcher(line.trim());
            if (!rowMatcher.matches()) {
                continue;
            }
            String criterion = rowMatcher.group(1).trim();
            int percent = Math.min(100, Math.max(0, Integer.parseInt(rowMatcher.group(2))));
            if (criterion.equalsIgnoreCase("Final")) {
                finalPercent = percent;
            } else {
                criteriaScores.add(new CriteriaScoreItem(criterion, percent));
            }
        }
        if (finalPercent == null) {
            throw new IOException("Model chấm bài (v3) không xuất dòng Final % trong bảng điểm: " + rawText);
        }
        return new GradeResult(finalPercent, feedback, markedAnswer, criteriaScores);
    }

    /** Cắt văn bản thành các mục theo header {@code ### N. ...} — key là số N, value là nội dung mục đó. */
    private Map<Integer, String> splitNumberedSections(String text) {
        record Header(int number, int headerStart, int contentStart) {
        }
        Matcher m = SECTION_HEADER.matcher(text);
        List<Header> headers = new ArrayList<>();
        while (m.find()) {
            headers.add(new Header(Integer.parseInt(m.group(1)), m.start(), m.end()));
        }
        Map<Integer, String> sections = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            int contentStart = headers.get(i).contentStart();
            int contentEnd = (i + 1 < headers.size()) ? headers.get(i + 1).headerStart() : text.length();
            sections.put(headers.get(i).number(), text.substring(contentStart, contentEnd).trim());
        }
        return sections;
    }

    /** LLM đôi khi bọc mục 1 (bài viết đã đánh dấu) trong ``` dù rubric đã dặn không làm vậy — cắt bỏ cho an toàn. */
    private String stripCodeFence(String section) {
        String trimmed = section.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline >= 0) {
            trimmed = trimmed.substring(firstNewline + 1);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
