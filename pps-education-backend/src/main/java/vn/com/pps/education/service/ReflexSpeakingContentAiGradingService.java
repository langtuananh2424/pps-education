package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.com.pps.education.common.AiTokenUsage;
import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.domain.Curriculum;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
 *
 * V178 (2026-09-16, bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — feedback trả cho học sinh trước
 * đây là 1 khối văn xuôi 7 mục quá dài. Đổi sang: (1) transcript có đánh dấu lỗi ngữ pháp/từ vựng nhận
 * diện được qua chữ bằng markup {@code {{err}}...{{/err}}} (lỗi phát âm KHÔNG đánh dấu được vì không
 * thể hiện bằng chữ), (2) {@code criteriaScores} — % từng tiêu chí tách thành mảng có cấu trúc riêng
 * thay vì nhúng thành dòng text trong feedback, (3) {@code feedback} rút gọn còn 1 đoạn tối đa 50 từ.
 *
 * V183 (2026-09-16, bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — fix bug thật phát hiện qua
 * test tay: transcript nhiều lúc bịa hẳn 1 câu khác (không phải lỗi nghe nhầm từng từ) — nguyên nhân
 * kép: (a) audio ghi được có lúc mất gần hết dải tần <2000Hz do FE không truyền constraint tường minh
 * cho {@code getUserMedia} (xem fix ở {@code ReflexVideoTaskPage.tsx#ensureStream}), (b) prompt cũ
 * KHÔNG có chỉ dẫn "không được làm mượt/tự suy đoán" — trong khi rubric giáo viên cho khối 6-7
 * ({@code speaking-rubric-grade6-shared.md}/{@code grade7-*.md}) ĐÃ tự có sẵn chỉ dẫn này (§0/§1) mà
 * KHÔNG được prompt hệ thống nhắc lại/tăng cường, nên bị model bỏ qua khi phải cân bằng với yêu cầu
 * xuất JSON đúng schema. SỬA: thêm hẳn 1 đoạn "QUY TẮC TRANSCRIBE BẮT BUỘC" ở prompt (áp dụng cho MỌI
 * khối kể cả 8-9 vốn rubric chưa có chỉ dẫn này) — cấm làm mượt, bắt buộc đoán sát âm thanh thay vì
 * đoán theo ngữ nghĩa, và MỞ RỘNG markup {@code {{err}}} để đánh dấu luôn cả đoạn nghe không rõ (trước
 * đây chỉ đánh dấu lỗi ngữ pháp/từ vựng). KHÔNG đổi sang kiến trúc tách 3 lệnh gọi
 * (Whisper transcript + Gemini riêng Phát âm + LLM text chấm phần còn lại) như từng cân nhắc — vi phạm
 * trực tiếp §0 rubric khối 6-7 ("Không chấm từ transcript do STT sinh sẵn").
 *
 * V185 (2026-09-16, bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — V183 vẫn bắt AI "đoán sát âm
 * thanh nhất" cho đoạn không nghe rõ rồi bọc {@code {{err}}} — nhưng đoán sai vẫn hiện ra như 1 từ chắc
 * chắn (chỉ khác màu), dễ gây hiểu lầm. SỬA: AI KHÔNG được đoán nữa, thay thế đoạn không nghe rõ bằng
 * đúng ký hiệu {@code [?]} (không kèm chữ đoán nào) — FE hiện thành dấu hỏi có tooltip "Không rõ từ" khi
 * trỏ chuột vào (xem {@code renderHighlightedErrors}/{@code renderUnclearMarkers} trong
 * {@code ReflexVideoTaskPage.tsx}). {@code {{err}}} từ nay CHỈ dùng cho lỗi ngữ pháp/từ vựng nhận diện
 * được rõ ràng, không còn dùng chung cho cả trường hợp không nghe rõ.
 *
 * V186 (2026-09-16, bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — fix bug thật khác với V183/185:
 * tên riêng LẠ (VD "PPS School") bị AI nghe nhầm THÀNH 1 tên riêng PHỔ BIẾN nó "biết" (VD "Vinschool")
 * — đây không phải kiểu lỗi "không chắc nên bịa/làm mượt" ([?] không xử lý được vì bản thân model KHÔNG
 * tự thấy mình đang không chắc, nó tin là nghe đúng "Vinschool"). SỬA: truyền thêm câu học sinh đã viết
 * ở bước 1 ({@code writtenAnswerHint}) làm GỢI Ý từ vựng/tên riêng cho AI trước khi nghe audio — dặn rõ
 * trong prompt đây KHÔNG phải nội dung chuẩn để copy (học sinh có thể nói khác hẳn câu đã viết, xem V178
 * — câu 6 thực tế), chỉ giúp AI nhận diện đúng từ/tên riêng khó đoán thuần từ âm thanh.
 *
 * V188 (2026-09-16, phát hiện qua test thật, xác nhận với người dùng) — ĐÃ REVERT V186: dặn dò trong
 * prompt ("KHÔNG copy nguyên văn") KHÔNG đủ sức cản model — kiểm tra DB sau khi triển khai V186 cho thấy
 * {@code speaking_transcript} GIỐNG HỆT {@code answer_text} (câu viết ở bước 1) ở NHIỀU bản ghi liên
 * tiếp, kể cả criteriaScores Phát âm vẫn ra 90-100% — tức model không thực sự nghe/phân tích audio nữa,
 * chỉ "rubber-stamp" lại y nguyên câu gợi ý kèm điểm cao mặc định. Đây là hồi quy NGHIÊM TRỌNG hơn hẳn
 * vấn đề gốc V186 định sửa (thỉnh thoảng nghe nhầm 1 tên riêng lạ) — chấp nhận đánh đổi ngược lại: bỏ hẳn
 * gợi ý câu viết, sống chung với rủi ro hiếm gặp "tên riêng lạ bị nghe nhầm thành tên phổ biến" thay vì
 * làm hỏng toàn bộ tính xác thực của việc chấm Phát âm/nội dung. KHÔNG thử lại hướng "gợi ý cả câu" nữa
 * nếu không có cơ chế RÀNG BUỘC CỨNG (ngoài dặn dò bằng lời) chống copy nguyên văn.
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

    /** {@code usage} (V192) — chi phí token của CHÍNH lượt chấm này, caller (ReflexSequentialGradingService) lưu kèm ngữ cảnh học sinh. */
    public record GradeResult(String transcript, List<CriteriaScoreItem> criteriaScores, int scorePercent, String feedback,
                              AiTokenUsage usage) {
    }

    /**
     * audioBytes: tải trực tiếp từ audioUrl đã lưu R2 (caller tự tải, xem
     * {@link ReflexSequentialGradingService}) — tránh phụ thuộc MultipartFile ở service này.
     * Trả null nếu chưa xác định được rubric (xem {@link RubricByGradeTrackLoader}) HOẶC 9Router chấm
     * thất bại — caller tự quyết định, KHÔNG tự cho qua.
     *
     */
    public GradeResult grade(byte[] audioBytes, String mimeType, String questionPrompt, Curriculum curriculum) {
        String rubric = rubricLoader.load(RUBRIC_FILE_PREFIX, curriculum.getGradeLevel(), curriculum.getTrack());
        if (rubric == null) {
            return null;
        }
        NineRouterAiClient.AiTextResponse response = nineRouterAiClient.chatWithAudioWithUsage(
                systemPrompt(rubric, questionPrompt),
                "Đây là audio câu trả lời speaking của học sinh cho câu hỏi \"" + questionPrompt + "\". Hãy transcribe rồi chấm theo tiêu chí đã cho — kể cả tiêu chí Phát âm/ngữ điệu, chỉ đánh giá được vì bạn nghe trực tiếp audio gốc.",
                audioBytes, mimeType, null);
        if (response == null) {
            log.warn("ReflexSpeakingContentAiGradingService: 9Router chấm thất bại.");
            return null;
        }
        try {
            GradeResult result = parseResult(response.content());
            return new GradeResult(result.transcript(), result.criteriaScores(), result.scorePercent(), result.feedback(),
                    response.usage());
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
        List<CriteriaScoreItem> criteriaScores = new ArrayList<>();
        for (JsonNode item : parsed.path("criteriaScores")) {
            int percent = Math.min(100, Math.max(0, item.path("percent").asInt(0)));
            criteriaScores.add(new CriteriaScoreItem(item.path("criterion").asText(""), percent));
        }
        return new GradeResult(parsed.path("transcript").asText(""), criteriaScores, scorePercent, parsed.path("feedback").asText(""), null);
    }
}
