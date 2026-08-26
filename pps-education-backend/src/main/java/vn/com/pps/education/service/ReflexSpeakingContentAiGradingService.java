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
 * bước 2 của mỗi câu hỏi (SAU khi đạt phần viết trước — xem {@link ReflexWritingGrammarAiGradingService}):
 * chuyển giọng nói (audio đã ghi) sang chữ RỒI chấm nội dung theo rubric — đạt ngưỡng mới mở khoá câu
 * tiếp theo (xem {@link ReflexSequentialGradingService}).
 *
 * V145 (2026-08-24) — bước đầu tách 2 bước TRƯỚC đây gộp chung 1 lệnh gọi Gemini thành
 * transcribe (Groq qua {@link NineRouterAiClient#transcribe}) RỒI chấm text riêng (9Router
 * {@link NineRouterAiClient#chat}) — nhưng phát hiện SAI: rubric Speaking có tiêu chí "Phát âm" (âm
 * đuôi, trọng âm, ngữ điệu — xem {@code speaking-rubric-*.md}) mà 1 bản transcript dạng CHỮ không thể
 * hiện được, bất kể LLM nào chấm text đó giỏi tới đâu.
 *
 * V146 (2026-08-24, xác nhận với người dùng) — SỬA LẠI: chấm dựa TRỰC TIẾP trên audio gốc trong 1 lệnh
 * gọi duy nhất qua {@link NineRouterAiClient#chatWithAudio} (multimodal, model Gemini qua Dashboard
 * 9Router → Combo & Vision Adapter — PHẢI là model/combo nhận audio, KHÔNG dùng combo có Claude). AI
 * vừa transcribe vừa chấm (kể cả tiêu chí Phát âm) trong cùng 1 lần nghe audio, giống thiết kế Gemini
 * gốc trước khi tách 9Router, chỉ khác là giờ đi qua kênh 9Router thay vì gọi thẳng Gemini.
 *
 * LƯU Ý VẬN HÀNH: 9Router hiện CHỈ chạy local trên máy dev (localhost:20128, xem
 * app.ai-grading.nine-router-base-url) — CHƯA có kế hoạch tự host cho staging/production, luồng chấm
 * Speaking thật sẽ fail nếu 9Router không chạy. Phải giải quyết trước khi coi tính năng này sẵn sàng
 * phục vụ học sinh thật (không chỉ máy dev).
 *
 * V140 (2026-08-23) — rubric giờ chọn theo Khối (6/7/8/9) + chương trình (IELTS/CAMBRIDGE) của
 * Curriculum chứa video (xem {@link RubricByGradeTrackLoader}), KHÔNG còn 1 rubric tĩnh cho mọi học
 * sinh. AI trả thẳng % theo đúng thang "Mức điểm (%)" của bảng, không còn quy đổi band 0-9 → %.
 * Lỗi gọi API HOẶC chưa xác định được đúng rubric trả về {@code null} — caller tự quyết định.
 *
 * V147 (2026-08-25, xác nhận với người dùng, phát hiện thật khi test) — fix bug thật: cả 6 file
 * {@code speaking-rubric-*.md} (Khối 6-9, IELTS/CAMBRIDGE) CHỈ có cột Ngữ pháp/Phát âm/Giao tiếp tương
 * tác (+ Discourse Management/Fluency tuỳ khối) — điều kiện "lạc đề" CHỈ xuất hiện ở đúng mức 0% ("nói
 * hoàn toàn không liên quan đến chủ đề"), KHÔNG có cột Nội dung xuyên suốt các mức như rubric Writing.
 * Hệ quả: 1 câu trả lời lạc đề nhưng ngữ pháp/phát âm/phản xạ tốt (VD hỏi "môn học yêu thích" nhưng trả
 * lời "tôi thích ngủ") vẫn được chấm cao vì rubric không phạt nội dung ở các mức giữa. KHÔNG tự sửa nội
 * dung rubric (dữ liệu giáo viên cung cấp, xem .claude/rules/business-fidelity.md) — thêm hướng dẫn
 * kiểm tra độ liên quan chủ đề NGAY TRONG system prompt (độc lập với rubric đang tải), áp dụng cho MỌI
 * khối/track. Cần truyền thêm {@code questionPrompt} (câu hỏi gốc, {@link ReviewVideoQuestion#getPrompt})
 * để AI có căn cứ đối chiếu — trước đây chỉ có audio, không biết câu hỏi là gì.
 */
@Service
public class ReflexSpeakingContentAiGradingService {

    private static final Logger log = LoggerFactory.getLogger(ReflexSpeakingContentAiGradingService.class);

    private static final String RUBRIC_FILE_PREFIX = "speaking-rubric";
    private static final String SYSTEM_PROMPT_FILE = "reflex-speaking-grading-system-prompt.txt";

    private final ObjectMapper objectMapper;
    private final RubricByGradeTrackLoader rubricLoader;
    private final NineRouterAiClient nineRouterAiClient;
    private final PromptTemplateLoader promptTemplateLoader;

    public ReflexSpeakingContentAiGradingService(ObjectMapper objectMapper,
                                                  RubricByGradeTrackLoader rubricLoader,
                                                  NineRouterAiClient nineRouterAiClient,
                                                  PromptTemplateLoader promptTemplateLoader) {
        this.objectMapper = objectMapper;
        this.rubricLoader = rubricLoader;
        this.nineRouterAiClient = nineRouterAiClient;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    public record GradeResult(String transcript, int scorePercent, String feedback) {
    }

    /**
     * audioBytes: tải trực tiếp từ audioUrl đã lưu R2 (caller tự tải, xem
     * {@link ReflexSequentialGradingService}) — tránh phụ thuộc MultipartFile ở service này.
     * Trả null nếu chưa xác định được rubric (xem {@link RubricByGradeTrackLoader}) HOẶC 9Router chấm
     * thất bại — caller tự quyết định, KHÔNG tự cho qua.
     */
    public GradeResult grade(byte[] audioBytes, String mimeType, String questionPrompt, Curriculum curriculum) {
        String rubric = rubricLoader.load(RUBRIC_FILE_PREFIX, curriculum.getGradeLevel(), curriculum.getTrack());
        if (rubric == null) {
            return null;
        }
        String rawText = nineRouterAiClient.chatWithAudio(
                systemPrompt(rubric, questionPrompt),
                "Đây là audio câu trả lời speaking của học sinh cho câu hỏi \"" + questionPrompt + "\". Hãy transcribe rồi chấm theo tiêu chí đã cho — kể cả tiêu chí Phát âm/ngữ điệu, chỉ đánh giá được vì bạn nghe trực tiếp audio gốc.",
                audioBytes, mimeType, null);
        if (rawText == null) {
            log.warn("ReflexSpeakingContentAiGradingService: 9Router chấm thất bại.");
            return null;
        }
        try {
            return parseResult(rawText);
        } catch (IOException e) {
            log.warn("ReflexSpeakingContentAiGradingService: parse kết quả chấm thất bại. {}", e.getMessage());
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
        return new GradeResult(parsed.path("transcript").asText(""), scorePercent, parsed.path("feedback").asText(""));
    }
}
