package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.common.KeyGrammarDictionary;
import vn.com.pps.education.common.KeyGrammarOutcome;
import vn.com.pps.education.common.WritingV3Grade;
import vn.com.pps.education.common.WritingV3Scoring;
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
 * chung cho mọi học sinh — xem {@link RubricByGradeTrackLoader}. AI trả thẳng % theo đúng thang "Mức
 * điểm (%)" của bảng, không còn quy đổi band 0-9 → %.
 *
 * V145 (2026-08-24, xác nhận với người dùng) — gọi AI chấm qua {@link NineRouterAiClient} (proxy local
 * xoay vòng nhiều provider/API key) thay vì gọi thẳng Gemini/Claude. LƯU Ý VẬN HÀNH: 9Router hiện CHỈ
 * chạy local trên máy dev — CHƯA có kế hoạch tự host cho staging/production.
 *
 * Lỗi gọi API (thiếu model/timeout/HTTP lỗi) HOẶC chưa xác định được đúng rubric trả về {@code null} —
 * KHÔNG throw, để câu trả lời rơi lại đúng hàng chờ chấm tay UC-41 (ManualGradingService) thay vì làm
 * hỏng cả giao dịch nộp bài của học sinh.
 *
 * V182 (2026-09-16, PILOT chỉ Khối 7 IELTS) — rubric "v3" do giáo viên cung cấp KHÔNG còn là 1 bảng mô
 * tả % đơn thuần như {@code writing-rubric-*.md} cũ — mà tự chứa TOÀN BỘ hướng dẫn chấm VÀ tự quy định
 * định dạng output riêng (markdown 3 mục, KHÔNG phải JSON): bài viết được đánh dấu lỗi ngay trong chữ
 * bằng cú pháp {@code {{mã|đoạn văn bản}}}. Nhận diện rubric v3 qua tiêu đề {@code "# WRITING RUBRIC"}.
 * Rubric CŨ (nếu còn) vẫn đi qua đường JSON như cũ — {@link GradeResult#markedAnswer()}/
 * {@link GradeResult#criteriaScores()} là {@code null} trong trường hợp đó.
 *
 * V190 (2026-09-22, xác nhận với người dùng — gói {@code bo-cham-writing-K6-K9} do người training bàn
 * giao, đóng gói 22/09/2026, mở rộng ra ĐỦ 6 khối, không còn PILOT riêng Khối 7 IELTS) — rubric v3 KHÔNG
 * còn "tự đủ" như V182 tưởng: {@code HUONG_DAN_TICH_HOP.md} khẳng định "chỉ gửi file rubric thì điểm
 * dao động 10–40 điểm" — cần thêm 3 lớp KHÔNG nằm trong file {@code .md}:
 * <ol>
 *   <li><b>Đo trước bằng máy</b> — {@link WritingV3Scoring#countWords}/{@link WritingV3Scoring#countCopied}
 *       tính N_total/N_copy/N_net và tự suy kết luận cổng G1, ghim thẳng vào prompt (model KHÔNG được tự
 *       đếm lại) — xem {@link WritingV3PromptBuilder#userPrompt}.</li>
 *   <li><b>Khung "mục 0 Kiểm đếm"</b> — bắt model liệt kê bằng chứng cho mọi checkpoint trước khi chấm,
 *       ẩn khỏi học sinh (chỉ giáo viên xem qua {@link GradeResult#auditMarkdown()}) — xem
 *       {@link WritingV3PromptBuilder#systemPrompt}.</li>
 *   <li><b>Hậu kiểm bằng máy</b> — {@link WritingV3Scoring#enforceScore} đọc lại mục 0, TỰ TÍNH lại bảng
 *       điểm (không tin % model tự điền), áp trần theo mật độ lỗi và trần Tổng kết 35% khi hỏng thì động
 *       từ, ghi đè bảng điểm mục 2 nếu lệch.</li>
 * </ol>
 * Mã khối theo {@link WritingV3Grade} (g6/g7/g7b1/g8/g8b1/g9, khớp {@code HUONG_DAN_TICH_HOP.md} mục 2) —
 * KHÔNG dùng lại {@code RubricByGradeTrackLoader}'s suffix trực tiếp vì cần thêm label/exam/rows/trần mỏng
 * theo khối để dựng prompt, xem {@link WritingV3Grade#forGradeTrack}. Model gọi qua combo riêng
 * {@code app.ai-grading.writing.model} (KHÔNG dùng combo mặc định {@code nine-router-model} — combo đó có
 * thể xoay qua model/mức thinking khác, phá vỡ hiệu chuẩn "gemini-3.6-flash, thinking medium" người training
 * đã chốt, xem {@code 00_BAN_GIAO.md}/{@code HUONG_DAN_TICH_HOP.md} mục 5.1).
 *
 * "Kết quả dở dang" ({@code HUONG_DAN_TICH_HOP.md} mục 5.3: {@code finishReason} khác STOP, hoặc thiếu mục
 * 2/dòng Tổng kết) tự gọi lại tối đa {@value #MAX_ATTEMPTS_TOTAL} lần — vẫn dở dang thì trả {@code null}
 * như mọi lỗi AI khác (rơi hàng chờ chấm tay), KHÔNG trả điểm cụt cho học sinh.
 *
 * V196 (2026-09-22, xác nhận với người dùng) — Key Grammar (filter 2, gói {@code key-grammar} do người
 * training bàn giao cùng đợt) tích hợp đủ: {@code keyGrammarIds} lấy từ {@code Question.keyGrammar} (gắn
 * vào CÂU HỎI, KHÔNG vào Bài hay lượt giao — xem Javadoc {@code Question#keyGrammar}: đổi từ thiết kế ban
 * đầu gắn Bài sau khi xem qua UI thật, xác nhận Key Grammar nên đi cùng đúng đề bài tự luận cụ thể), bơm
 * vào system prompt qua {@link WritingV3PromptBuilder#systemPrompt} khi có,
 * đọc lại số liệu ở mục 0 (KHÔNG tin dòng "Kết luận" model tự viết, mirror triết lý hậu kiểm điểm số) qua
 * {@link WritingV3Scoring#parseKeyGrammarConclusion}, áp trần tiêu chí ngữ pháp qua
 * {@link WritingV3Scoring#enforceScore}. {@link GradeResult#keyGrammar()} mang {@code redoRequired} để FE
 * hiện dải "cần viết lại bài".
 *
 * {@link GradeResult#auditMarkdown()} (mục 0, dùng để giáo viên soát lại vì sao có điểm đó) hiện CHƯA được
 * lưu vào DB — chỉ ghi log khi hậu kiểm có sửa điểm — lưu lâu dài cần thêm cột mới, phải xác nhận với
 * người dùng trước (xem .claude/rules/business-fidelity.md).
 */
@Service
public class WritingAiGradingService {

    private static final Logger log = LoggerFactory.getLogger(WritingAiGradingService.class);

    private static final String RUBRIC_FILE_PREFIX = "writing-rubric";
    private static final String SYSTEM_PROMPT_FILE = "writing-grading-system-prompt.txt";
    private static final String RUBRIC_V3_MARKER = "# WRITING RUBRIC";
    /** 1 lần gọi đầu + tối đa 2 lần chấm lại khi "dở dang" — đúng {@code HUONG_DAN_TICH_HOP.md} mục 5.3. */
    private static final int MAX_ATTEMPTS_TOTAL = 3;

    private final ObjectMapper objectMapper;
    private final RubricByGradeTrackLoader rubricLoader;
    private final NineRouterAiClient nineRouterAiClient;
    private final PromptTemplateLoader promptTemplateLoader;
    private final WritingV3PromptBuilder promptBuilderV3;
    private final KeyGrammarDictionaryLoader keyGrammarDictionaryLoader;

    /**
     * V190 (2026-09-22, xác nhận với người dùng) — combo RIÊNG cho Writing, do người dùng tự tạo trong
     * Dashboard 9Router (chỉ gồm {@code ag/gemini-3.6-flash-medium}, KHÔNG fallback sang mức thinking
     * khác — đã kiểm tra qua gọi thật 2026-09-22: response không có field nào phân biệt được member nào
     * của combo đã trả lời, nên combo fallback sang model/mức thinking KHÁC sẽ ÂM THẦM chấm bằng 1 model
     * chưa từng được hiệu chuẩn, vi phạm business-fidelity). KHÔNG dùng {@code nine-router-model} mặc định
     * (combo {@code pps-edu}, dùng chung nhiều mục đích khác, có thể gồm nhiều model/mức thinking).
     */
    @Value("${app.ai-grading.writing.model:writing-pps}")
    private String writingModel;

    public WritingAiGradingService(ObjectMapper objectMapper, RubricByGradeTrackLoader rubricLoader,
                                    NineRouterAiClient nineRouterAiClient, PromptTemplateLoader promptTemplateLoader,
                                    WritingV3PromptBuilder promptBuilderV3, KeyGrammarDictionaryLoader keyGrammarDictionaryLoader) {
        this.objectMapper = objectMapper;
        this.rubricLoader = rubricLoader;
        this.nineRouterAiClient = nineRouterAiClient;
        this.promptTemplateLoader = promptTemplateLoader;
        this.promptBuilderV3 = promptBuilderV3;
        this.keyGrammarDictionaryLoader = keyGrammarDictionaryLoader;
    }

    /**
     * V182 — markedAnswer/criteriaScores CHỈ có giá trị khi rubric là bản "v3"; với rubric cũ, cả 2 là
     * {@code null} và feedback vẫn là đoạn văn 7 mục như trước.
     *
     * @param auditMarkdown V190 — mục 0 "Kiểm đếm" (bằng chứng + phép cộng checkpoint từng tiêu chí), chỉ
     *                      có giá trị ở đường v3. KHÔNG BAO GIỜ hiển thị cho học sinh — dùng để giáo viên
     *                      soát lại hoặc gỡ lỗi khi điểm trông bất thường. {@code null} ở đường rubric cũ.
     * @param keyGrammar V196 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22, Key Grammar
     *                   filter 2) — {@code null} khi Bài không gắn Key Grammar hoặc đang ở đường rubric cũ.
     */
    public record GradeResult(int scorePercent, String feedback, String markedAnswer, List<CriteriaScoreItem> criteriaScores,
                              String auditMarkdown, KeyGrammarOutcome keyGrammar) {
    }

    /**
     * Trả null nếu chưa xác định được rubric/khối (xem {@link RubricByGradeTrackLoader}/{@link WritingV3Grade})
     * HOẶC 9Router chấm thất bại/dở dang sau {@value #MAX_ATTEMPTS_TOTAL} lần — caller (ExerciseAttemptService)
     * coi đây là "chưa chấm được", câu trả lời tự động rơi lại hàng chờ Giáo viên chấm tay (UC-41), KHÔNG
     * chặn học sinh nộp bài.
     *
     * @param keyGrammarIds V196 — {@code answer.getQuestion().getKeyGrammar()} của câu hỏi đang chấm,
     *                      {@code null}/rỗng = không gắn Key Grammar (giữ nguyên hành vi cũ). Ở đường
     *                      rubric cũ (legacy) bị bỏ qua — chưa hỗ trợ Key Grammar ngoài rubric "v3".
     */
    public GradeResult grade(String essayText, String taskPrompt, Curriculum curriculum, List<String> keyGrammarIds) {
        if (essayText == null || essayText.isBlank()) {
            return null;
        }
        String rubric = rubricLoader.load(RUBRIC_FILE_PREFIX, curriculum.getGradeLevel(), curriculum.getTrack());
        if (rubric == null) {
            return null;
        }
        if (!rubric.startsWith(RUBRIC_V3_MARKER)) {
            return gradeLegacy(essayText, rubric);
        }
        return gradeV3(essayText, taskPrompt, curriculum, keyGrammarIds);
    }

    private GradeResult gradeLegacy(String essayText, String rubric) {
        String rawText = nineRouterAiClient.chat(systemPrompt(rubric), "Bài viết của học sinh: \"" + essayText + "\"", null);
        if (rawText == null) {
            log.warn("WritingAiGradingService: 9Router chấm thất bại, rơi lại hàng chờ chấm tay.");
            return null;
        }
        try {
            return parseResultLegacy(rawText);
        } catch (IOException e) {
            log.warn("WritingAiGradingService: parse kết quả chấm thất bại, rơi lại hàng chờ chấm tay. {}", e.getMessage());
            return null;
        }
    }

    private GradeResult gradeV3(String essayText, String taskPrompt, Curriculum curriculum, List<String> keyGrammarIds) {
        WritingV3Grade grade = WritingV3Grade.forGradeTrack(curriculum.getGradeLevel(), curriculum.getTrack());
        if (grade == null) {
            log.warn("WritingAiGradingService: rubric v3 tồn tại nhưng chưa xác định được mã khối (gradeLevel={}, track={}) — rơi lại hàng chờ chấm tay.",
                    curriculum.getGradeLevel(), curriculum.getTrack());
            return null;
        }
        String rubric = rubricLoader.load(RUBRIC_FILE_PREFIX, curriculum.getGradeLevel(), curriculum.getTrack());
        KeyGrammarDictionary keyGrammarDictionary = (keyGrammarIds == null || keyGrammarIds.isEmpty())
                ? null : keyGrammarDictionaryLoader.load(grade);
        String system = promptBuilderV3.systemPrompt(grade, rubric, keyGrammarDictionary, keyGrammarIds);
        String user = promptBuilderV3.userPrompt(grade, taskPrompt, essayText);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS_TOTAL; attempt++) {
            NineRouterAiClient.ChatResult result = nineRouterAiClient.chatWithFinishReason(system, user, writingModel);
            if (result == null || result.content() == null || result.content().isBlank()) {
                log.warn("WritingAiGradingService: 9Router chấm (v3) thất bại, rơi lại hàng chờ chấm tay.");
                return null;
            }
            if (isTruncated(result)) {
                log.warn("WritingAiGradingService: kết quả dở dang (finishReason={}, lần {}/{}) — {}.",
                        result.finishReason(), attempt, MAX_ATTEMPTS_TOTAL,
                        attempt < MAX_ATTEMPTS_TOTAL ? "thử chấm lại" : "rơi lại hàng chờ chấm tay");
                continue;
            }
            try {
                return parseResultV3(result.content(), grade, keyGrammarDictionary);
            } catch (IOException e) {
                log.warn("WritingAiGradingService: parse kết quả chấm (v3) thất bại (lần {}/{}). {}", attempt, MAX_ATTEMPTS_TOTAL, e.getMessage());
                // Coi như "dở dang" theo tinh thần mục 5.3 — thử lại thay vì bỏ cuộc ngay từ lần đầu.
            }
        }
        return null;
    }

    /**
     * "Dở dang" theo {@code HUONG_DAN_TICH_HOP.md} mục 5.3: {@code finishReason} khác STOP (kể cả
     * MAX_TOKENS), hoặc thiếu mục 2/dòng "Tổng kết". {@code finishReason == null} (9Router không trả field
     * này ở 1 số nhánh) KHÔNG tự coi là dở dang — chỉ dựa vào nội dung trong trường hợp đó.
     */
    private boolean isTruncated(NineRouterAiClient.ChatResult result) {
        String fr = result.finishReason();
        boolean badFinish = fr != null && !fr.equalsIgnoreCase("stop");
        boolean missingSections = !result.content().contains("### 2.") || !result.content().contains("Tổng kết");
        return badFinish || missingSections;
    }

    private String systemPrompt(String rubric) {
        return promptTemplateLoader.load(SYSTEM_PROMPT_FILE, Map.of("RUBRIC", rubric));
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
        return new GradeResult(scorePercent, parsed.path("feedback").asText(""), null, null, null, null);
    }

    private static final Pattern SECTION_HEADER = Pattern.compile("(?m)^###\\s*(\\d+)\\.[^\\n]*$");

    /**
     * Parse output rubric v3 (markdown 3 mục đánh số + mục 0 Kiểm đếm — xem "Output bắt buộc" trong
     * {@code rubric/00_BAN_GIAO.md}, KHÔNG phải JSON). V190: chạy hậu kiểm ({@link WritingV3Scoring#enforceScore})
     * TRƯỚC khi tách mục 0, vì hậu kiểm cần đọc số liệu trong mục 0 để tự tính lại bảng điểm. Ném
     * IOException nếu thiếu mục 0/1/2 hoặc bảng điểm không đủ tiêu chí/dòng Tổng kết — caller coi như chấm
     * thất bại (thử lại hoặc rơi hàng chờ chấm tay).
     */
    private GradeResult parseResultV3(String rawText, WritingV3Grade grade, KeyGrammarDictionary keyGrammarDictionary) throws IOException {
        if (!rawText.contains("### 0.")) {
            throw new IOException("Model chấm bài (v3) thiếu mục 0 Kiểm đếm — không hậu kiểm được: " + rawText);
        }
        String corrected = WritingV3Scoring.enforceScore(rawText, grade, keyGrammarDictionary);
        WritingV3Scoring.AuditSplit split = WritingV3Scoring.splitAudit(corrected);
        String visible = split.visible();

        Map<Integer, String> sections = splitNumberedSections(visible);
        String markedAnswer = stripCodeFence(sections.getOrDefault(1, ""));
        String scoreTableSection = sections.getOrDefault(2, "");
        String feedback = stripFeedbackMarkdown(sections.getOrDefault(3, ""));
        if (markedAnswer.isBlank() || scoreTableSection.isBlank()) {
            throw new IOException("Model chấm bài (v3) không trả đủ mục 1/2 theo định dạng yêu cầu: " + rawText);
        }

        Map<String, Integer> scores = WritingV3Scoring.readScoreTable(visible, grade.rows());
        Integer total = WritingV3Scoring.readTotal(visible);
        if (total == null || scores.size() != grade.rows().size()) {
            throw new IOException("Model chấm bài (v3) không xuất đủ bảng điểm (thiếu tiêu chí hoặc dòng Tổng kết): " + rawText);
        }
        List<CriteriaScoreItem> criteriaScores = new ArrayList<>();
        for (String row : grade.rows()) {
            criteriaScores.add(new CriteriaScoreItem(row, scores.get(row)));
        }
        if (!corrected.equals(rawText)) {
            log.info("WritingAiGradingService: hậu kiểm đã sửa bảng điểm model tự điền — audit:\n{}", split.audit());
        }
        String finalFeedback = feedback;
        KeyGrammarOutcome keyGrammarOutcome = null;
        if (keyGrammarDictionary != null) {
            WritingV3Scoring.KeyGrammarConclusion c =
                    WritingV3Scoring.parseKeyGrammarConclusion(split.audit(), keyGrammarDictionary.passIfAtLeast());
            if (c == null) {
                log.warn("WritingAiGradingService: Bài có gắn Key Grammar nhưng model không in khối 'Key grammar được giao' ở mục 0.");
            } else {
                if ("unparsed".equals(c.status())) {
                    log.warn("WritingAiGradingService: Key Grammar status=unparsed (đọc được header nhưng không đọc được 'Dùng đúng: N') — audit:\n{}", split.audit());
                }
                FeedbackSplit fs = splitKeyGrammarFeedback(feedback);
                finalFeedback = fs.mainFeedback();
                keyGrammarOutcome = new KeyGrammarOutcome(c.status(), c.correct(), c.attempts(), "fail".equals(c.status()), fs.keyGrammarNote());
            }
        }
        return new GradeResult(Math.min(100, Math.max(0, total)), finalFeedback, markedAnswer, criteriaScores, split.audit(), keyGrammarOutcome);
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

    /**
     * V190 — mục 3 của rubric v3 bắt đầu bằng {@code **Nhận xét chung:**} (markdown in đậm), nhưng FE
     * ({@code TakeExerciseModal.tsx}) hiện hiển thị {@code gradingFeedback} dạng văn bản thuần
     * ({@code whitespace-pre-line}), không tự parse markdown — để nguyên sẽ lộ dấu {@code **} thô cho học
     * sinh. Bỏ nhãn "Nhận xét chung:" và mọi cặp {@code **} còn sót, KHÔNG đổi nội dung nhận xét.
     */
    private String stripFeedbackMarkdown(String section) {
        return section.replaceFirst("(?i)^\\*{0,2}\\s*Nhận xét chung\\s*:\\s*\\*{0,2}\\s*", "")
                .replace("**", "")
                .trim();
    }

    private record FeedbackSplit(String mainFeedback, String keyGrammarNote) {
    }

    /**
     * V196 — {@code feedback} (mục 3) chứa CẢ nhận xét chung LẪN khối "Key grammar: ..." liền ngay dưới
     * (đúng format ở {@link WritingV3PromptBuilder#applyKeyGrammar}) — tách riêng để FE hiện dải cảnh báo
     * "cần viết lại bài" thay vì lẫn vào 1 đoạn văn dài. Không tìm thấy marker (model bỏ sót dù Bài có gắn
     * Key Grammar) thì trả nguyên {@code feedback}, {@code keyGrammarNote=null}.
     */
    private FeedbackSplit splitKeyGrammarFeedback(String feedback) {
        int idx = feedback.indexOf("Key grammar:");
        if (idx < 0) {
            return new FeedbackSplit(feedback, null);
        }
        String main = feedback.substring(0, idx).trim();
        String[] lines = feedback.substring(idx).split("\n", -1);
        // lines[0] = "Key grammar: <tên cấu trúc>" ; lines[1] = "Đạt/Chưa đạt · dùng đúng N/N lần (...)"; phần còn lại = 2 câu nhận xét.
        String note = lines.length > 2
                ? String.join("\n", java.util.Arrays.asList(lines).subList(2, lines.length)).trim()
                : "";
        return new FeedbackSplit(main, note.isBlank() ? null : note);
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
