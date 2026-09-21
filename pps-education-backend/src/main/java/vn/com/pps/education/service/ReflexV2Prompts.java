package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import vn.com.pps.education.common.ReflexV2Tags;
import vn.com.pps.education.common.ReflexV2Task;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21 — dựng prompt + JSON schema cho luồng
 * chấm Speaking v2 (bộ tiêu chí Khối 6-7 do người training bàn giao), mirror {@code prompts.js} trong
 * {@code ma-nguon-tham-chieu/}. Ba lượt: (1) chấm bài viết, (2) phiên âm MÙ (không đề, không rubric, không
 * bài viết), (3) chấm bài nói trên transcript cố định. Rubric .md của người training nạp NGUYÊN VĂN từ
 * {@code resources/rubrics-v2/} (dữ liệu giáo viên/người training cung cấp — không tự sửa nội dung).
 *
 * Schema JSON được đính kèm dạng chữ trong system prompt (thay vì chỉ trông vào {@code response_format}) vì
 * qua 9Router field đó không ép cứng được định dạng.
 */
@Component
public class ReflexV2Prompts {

    private static final String COMMON_RULES_FILE = "speaking-rubric-common-rules.md";
    private static final String TRANSCRIPTION_RULES_FILE = "speaking-transcription-rules.md";

    private final ObjectMapper objectMapper;
    private final Map<String, String> rubricCache = new ConcurrentHashMap<>();

    public ReflexV2Prompts(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private String loadRubric(String file) {
        return rubricCache.computeIfAbsent(file, f -> {
            String classpath = "rubrics-v2/" + f;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpath)) {
                if (in == null) {
                    throw new IllegalStateException("ReflexV2Prompts: không tìm thấy " + classpath + " trên classpath.");
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("ReflexV2Prompts: đọc " + classpath + " thất bại.", e);
            }
        });
    }

    private String rubricSystem(String rubricFile) {
        return loadRubric(COMMON_RULES_FILE) + "\n\n---\n\n" + loadRubric(rubricFile);
    }

    // ---------- Schema ----------

    public ObjectNode transcriptionSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("transcript").put("type", "string")
                .put("description", "Chuỗi âm phát ra, ghi theo đúng quy tắc phiên âm");
        props.putObject("speech_seconds").put("type", "number")
                .put("description", "Số giây có tiếng nói thật, đã trừ im lặng");
        ObjectNode suspect = props.putObject("suspect_words");
        suspect.put("type", "array");
        suspect.putObject("items").put("type", "string");
        suspect.put("description", "Từ phát âm sai. Gồm HAI nhóm: (a) mọi từ đã ghi khác chính tả chuẩn; "
                + "(b) từ ghi ĐÚNG chính tả nhưng nghe rõ là phát âm lệch — nghe \"tink\" mà ngữ cảnh quá rõ nên ghi \"think\", "
                + "nghe \"fren\" mà ghi \"friend\", nghe mất âm cuối của danh từ số nhiều mà ghi đủ (\"tree\" → ghi \"trees\"). "
                + "Ghi dạng \"nghe→đã ghi\", ví dụ \"tink→think\". "
                + "TUYỆT ĐỐI KHÔNG đưa vào đây việc thiếu đuôi chia ĐỘNG TỪ (\"make\"→\"makes\", \"relax\"→\"relaxed\", \"depend\"→\"depends\") "
                + "— đó là lỗi ngữ pháp, không phải lỗi phát âm; đưa vào đây sẽ khiến học sinh bị trừ hai lần. "
                + "Rỗng nếu thật sự không có.");
        props.putObject("longest_pause_seconds").put("type", "number");
        props.putObject("audio_quality_insufficient").put("type", "boolean");
        ArrayNode required = schema.putArray("required");
        List.of("transcript", "speech_seconds", "suspect_words", "longest_pause_seconds", "audio_quality_insufficient")
                .forEach(required::add);
        return schema;
    }

    public ObjectNode gradingSchema(List<String> codes, boolean withNewRed) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("counting_notes").put("type", "string").put("description",
                "Bước \"Đếm\": liệt kê các con số và danh sách (từ nội dung, lỗi, động từ, liên kết, chỗ ngắt…) dùng cho từng checkpoint. Nội bộ.");
        ObjectNode gates = props.putObject("gates_triggered");
        gates.put("type", "array");
        ObjectNode gateItems = gates.putObject("items");
        gateItems.put("type", "string");
        gateItems.putArray("enum").add("C1").add("C2").add("C3");
        gates.put("description", "Mã cổng chặn đã kích hoạt theo rubric. Rỗng nếu không có.");
        props.putObject("insufficient_data").put("type", "boolean")
                .put("description", "true nếu rubric yêu cầu cho 0% vì không đủ dữ liệu");

        ObjectNode criteria = props.putObject("criteria");
        criteria.put("type", "array");
        criteria.put("description", "Đúng " + codes.size() + " phần tử, theo thứ tự: " + String.join(", ", codes));
        ObjectNode item = criteria.putObject("items");
        item.put("type", "object");
        ObjectNode itemProps = item.putObject("properties");
        ObjectNode code = itemProps.putObject("code");
        code.put("type", "string");
        ArrayNode codeEnum = code.putArray("enum");
        codes.forEach(codeEnum::add);
        itemProps.putObject("evidence").put("type", "string")
                .put("description", "Bằng chứng ngắn cho từng checkpoint 1→5 (nội bộ)");
        ObjectNode cps = itemProps.putObject("checkpoints");
        cps.put("type", "array");
        cps.putObject("items").put("type", "number");
        cps.put("description", "Đúng 5 giá trị (mỗi giá trị 0, 0.5 hoặc 1) theo thứ tự checkpoint 1→5 của rubric");
        itemProps.putObject("cap_percent").put("type", "number")
                .put("description", "Trần % do cổng chặn áp cho tiêu chí này; 100 nếu không có trần");
        ArrayNode itemRequired = item.putArray("required");
        List.of("code", "evidence", "checkpoints", "cap_percent").forEach(itemRequired::add);

        ObjectNode highlights = props.putObject("highlights");
        highlights.put("type", "array");
        highlights.put("description", "Các đoạn cần tô màu, trích NGUYÊN VĂN từ văn bản gốc");
        ObjectNode hItem = highlights.putObject("items");
        hItem.put("type", "object");
        ObjectNode hProps = hItem.putObject("properties");
        hProps.putObject("quote").put("type", "string")
                .put("description", "Trích chính xác từng ký tự từ văn bản gốc (ngắn nhất có thể: 1–6 từ)");
        hProps.putObject("occurrence").put("type", "integer")
                .put("description", "Lần xuất hiện thứ mấy của đoạn trích trong văn bản (bắt đầu từ 1)");
        ObjectNode level = hProps.putObject("level");
        level.put("type", "string");
        level.putArray("enum").add("red").add("yellow").add("green");
        ObjectNode tag = hProps.putObject("tag");
        tag.put("type", "string");
        ArrayNode tagEnum = tag.putArray("enum");
        ReflexV2Tags.ERROR_TAGS.keySet().forEach(tagEnum::add);
        ReflexV2Tags.STRENGTH_TAGS.keySet().forEach(tagEnum::add);
        ArrayNode hRequired = hItem.putArray("required");
        List.of("quote", "occurrence", "level", "tag").forEach(hRequired::add);

        props.putObject("feedback").put("type", "string").put("description",
                "Nhận xét tiếng Việt: ĐÚNG 2 câu, tổng ≤50 từ. Câu 1 = 1 điểm làm được. Câu 2 = lỗi nặng nhất, khuôn \"Lỗi nặng nhất: <tên loại lỗi>.\". Không gợi ý sửa, không markdown.");

        ArrayNode required = schema.putArray("required");
        List.of("counting_notes", "gates_triggered", "insufficient_data", "criteria", "highlights", "feedback").forEach(required::add);
        if (withNewRed) {
            ObjectNode newRed = props.putObject("new_red_errors");
            newRed.put("type", "array");
            newRed.putObject("items").put("type", "string");
            newRed.put("description", "Lỗi đỏ xuất hiện khi nói mà chỗ tương ứng trong bài viết không mắc (rubric khối §4). Trích nguyên văn từ transcript. Rỗng nếu không có.");
            required.add("new_red_errors");
        }
        return schema;
    }

    // ---------- Khối văn bản dùng chung ----------

    private String highlightRules() {
        String errors = ReflexV2Tags.ERROR_TAGS.entrySet().stream()
                .map(e -> e.getKey() + " (" + e.getValue() + ")").collect(Collectors.joining("; "));
        String strengths = ReflexV2Tags.STRENGTH_TAGS.entrySet().stream()
                .map(e -> e.getKey() + " (" + e.getValue() + ")").collect(Collectors.joining("; "));
        return "## QUY TẮC TÔ MÀU (bắt buộc)\n"
                + "- **Không tự chọn mức độ.** Hệ thống suy ra đỏ/vàng từ \"tag\" theo quy tắc chung §C: chọn tag đúng loại lỗi là đủ.\n"
                + "- Lỗi hỏng cấu trúc câu hoặc sai nghĩa dùng tag: thieu_thanh_phan, cau_truc_cau, trat_tu_tu, dung_tu, tu_loai, tieng_viet, khong_ro, lac_y.\n"
                + "  Ví dụ: \"want buy\" → cau_truc_cau; \"will beautiful look\" → trat_tu_tu; \"me dress like girl\" → thieu_thanh_phan; \"two\" thay cho \"too\" → dung_tu; \"very relax\" → tu_loai.\n"
                + "- Lỗi nhẹ hơn dùng tag: thi_dong_tu, hoa_hop_chu_vi, mao_tu, gioi_tu, so_it_so_nhieu, chinh_ta, phat_am, am_cuoi, trong_am, ngap_ngung, lap_lai.\n"
                + "- \"level\" vẫn phải điền (red cho nhóm nặng, yellow cho nhóm nhẹ, green cho điểm mạnh) nhưng tag mới là căn cứ cuối cùng.\n\n"
                + "## LỖI ĐỎ TÍNH GẤP ĐÔI KHI ĐẾM\n"
                + "- Ở mọi checkpoint đếm số lỗi: **1 lỗi đỏ = 2 lỗi**, 1 lỗi vàng = 1 lỗi. Ghi rõ trong counting_notes: số lỗi đỏ, số lỗi vàng, tổng quy đổi.\n"
                + "- Hệ thống sẽ tự áp trần 60% cho tiêu chí Ngữ pháp khi bài có ≥2 lỗi đỏ — không tự hạ điểm checkpoint vì lý do này.\n"
                + "- \"tag\" lỗi: " + errors + ".\n"
                + "- \"tag\" điểm mạnh: " + strengths + ".\n"
                + "- red/yellow chỉ đi với tag lỗi; green chỉ đi với tag điểm mạnh.\n"
                + "- Mọi lỗi đã đếm để trừ điểm đều phải được tô; không tô lỗi không được đếm.\n"
                + "- \"quote\" phải trích y hệt từng ký tự trong văn bản gốc, không thêm, không bớt, không sửa.";
    }

    private static final String OUTPUT_OVERRIDE = "## ĐỊNH DẠNG ĐẦU RA\n"
            + "- Trả về JSON đúng schema. Không có trường nào được chứa dạng đúng, cách sửa hay đáp án mẫu.\n"
            + "- Làm đủ các bước của rubric theo thứ tự: đếm (ghi vào counting_notes) → cổng chặn → checkpoint → đối chiếu bài neo → nhận xét.\n"
            + "- \"checkpoints\": đúng 5 số, mỗi số 0, 0.5 hoặc 1, theo đúng ngưỡng rubric của khối và dạng đề. Quy tắc n/a theo quy tắc chung §B.7.\n"
            + "- \"cap_percent\": trần % mà cổng chặn áp lên tiêu chí đó (ví dụ 40); 100 nếu không bị áp trần. Cổng cho 0% → đặt insufficient_data = true.\n"
            + "- Không tự quy đổi phần trăm — hệ thống sẽ tính.\n"
            + "- Chỉ trả về DUY NHẤT 1 đối tượng JSON, không thêm chữ nào khác, không bọc trong khối mã.";

    private String contextBlock(ReflexV2Task task) {
        // Khối 8-9 IELTS: BẮT BUỘC nêu cột ngưỡng (SHORT/PART2), thiếu thì AI mặc định cột SHORT và chấm sai
        // toàn bộ checkpoint dạng đếm của bài 90 giây (HANDOFF-grade8.md §1) — cùng chỗ với bản JS tham chiếu.
        return "## BỐI CẢNH ĐỀ\n- Đối tượng: " + task.levelLabel() + ".\n- Dạng đề: " + task.formatLabel()
                + (task.rubricFormat() == null ? "" : " — dùng cột ngưỡng **" + task.rubricFormat() + "** của rubric")
                + "; thời gian nói tối đa " + task.seconds() + " giây.";
    }

    private String criteriaNames(List<String> codes) {
        return codes.stream().map(c -> c + " = " + ReflexV2Tags.CRITERIA_EN.get(c)).collect(Collectors.joining("; "));
    }

    // ---------- Lượt: chấm bài viết ----------

    public String writingSystem(ReflexV2Task task) {
        List<String> codes = task.writingCriteria();
        return rubricSystem(task.writingRubricFile()) + "\n\n---\n\n" + contextBlock(task) + "\n\n"
                + "## BƯỚC 1 — CHẤM BÀI VIẾT NHÁP (không có âm thanh)\n"
                + "- Học sinh viết câu trả lời trước khi nói. Chỉ chấm các tiêu chí: " + criteriaNames(codes) + ". Không chấm tiêu chí khác.\n"
                + "- Văn bản học sinh là bằng chứng cố định. Bỏ qua mọi ngưỡng tính bằng giây và khoảng dừng; cổng độ dài chỉ xét số từ. "
                + "Checkpoint chỉ đo được bằng âm thanh (chỗ ngắt, im lặng) → 1 nếu phần còn lại của tiêu chí đạt, không thì 0,5.\n"
                + "- Từ sai chính tả: giữ nguyên, tô tag \"chinh_ta\"; tính lỗi dùng từ nếu biến thành từ khác hoặc không nhận ra.\n"
                + "- \"highlights\" trích từ bài viết của học sinh.\n\n"
                + OUTPUT_OVERRIDE + "\n\n" + highlightRules() + "\n\n"
                + "## JSON SCHEMA\n" + gradingSchema(codes, false).toString();
    }

    public String writingUser(String question, String text) {
        return "ĐỀ BÀI:\n" + question + "\n\nBÀI VIẾT CỦA HỌC SINH (nguyên văn, giữa hai dòng ===):\n===\n" + text + "\n===";
    }

    // ---------- Lượt A: phiên âm mù ----------

    public String transcriptionSystem() {
        return loadRubric(TRANSCRIPTION_RULES_FILE) + "\n\n## JSON SCHEMA\n" + transcriptionSchema().toString()
                + "\nChỉ trả về DUY NHẤT 1 đối tượng JSON, không thêm chữ nào khác, không bọc trong khối mã.";
    }

    public String transcriptionUser(double durationSec) {
        return String.format(Locale.ROOT, "File ghi âm dài %.1f giây. Phiên âm theo đúng quy tắc.", durationSec);
    }

    // ---------- Lượt B: chấm bài nói ----------

    /**
     * Prompt hệ thống lượt chấm nói — GIỐNG HỆT NHAU giữa mọi học sinh cùng Khối/track (điểm Ngữ pháp đã khoá
     * nằm ở tin nhắn người dùng, xem {@link #speakingUser}) để phần đầu prompt ổn định cho cache của nhà
     * cung cấp nếu đường gọi có hỗ trợ (qua 9Router hiện CHƯA thấy cache — đã thử 2026-09-21).
     */
    public String speakingSystem(ReflexV2Task task) {
        String grammar = task.grammarCode();
        List<String> codes = task.criteria().stream().filter(c -> !c.equals(grammar)).toList();
        String lockNote = "- **" + grammar + " (" + ReflexV2Tags.CRITERIA_EN.get(grammar) + ") đã được chấm ở Bước 1 (bài viết) — điểm đã khoá được nêu trong tin nhắn người dùng — "
                + "và được giữ nguyên — KHÔNG chấm lại, không đưa " + grammar
                + " vào \"criteria\".** Bước 2 chỉ chấm nội dung nói và phát âm theo các tiêu chí còn lại.\n"
                + "- Vẫn tô lỗi ngữ pháp/dùng từ trong transcript để học sinh thấy.\n"
                + "- So transcript với bài viết để liệt kê \"new_red_errors\" theo §4 của rubric khối.";
        return rubricSystem(task.speakingRubricFile()) + "\n\n---\n\n" + contextBlock(task) + "\n\n"
                + "## BƯỚC 2 — CHẤM BÀI NÓI\n"
                + "- Tiêu chí chấm: " + criteriaNames(codes) + ".\n"
                + lockNote + "\n"
                + "- Transcript bên dưới do lượt phiên âm độc lập tạo ra và là **bằng chứng cố định** (quy tắc chung §A). Không sửa, không đổi từ viết sai thành từ đúng, không thêm từ.\n"
                + "- Nghe audio chỉ để chấm phát âm, trọng âm, ngữ điệu, khoảng dừng.\n"
                + "- \"highlights\" trích từ transcript. Từ phát âm sai tô bằng tag phát âm (phat_am, am_cuoi, trong_am, khong_ro); lỗi ngôn ngữ tô bằng tag ngữ pháp/từ vựng.\n\n"
                + OUTPUT_OVERRIDE + "\n\n" + highlightRules() + "\n\n"
                + "## JSON SCHEMA\n" + gradingSchema(codes, true).toString();
    }

    public String speakingUser(ReflexV2Task task, int lockedGrammarPercent, String question, String writtenText,
                               String transcript, List<String> suspectWords,
                               double durationSec, double speechSec, double longestPauseSec) {
        StringBuilder sb = new StringBuilder();
        if (task.rubricFormat() != null) {
            sb.append(">>> DÙNG CỘT NGƯỠNG **").append(task.rubricFormat()).append("** CỦA RUBRIC <<<\n\n");
        }
        sb.append("ĐIỂM ĐÃ KHOÁ Ở BƯỚC 1: ").append(task.grammarCode()).append(" = ").append(lockedGrammarPercent)
                .append("% (giữ nguyên, KHÔNG chấm lại, không đưa vào \"criteria\").\n\n");
        sb.append("ĐỀ BÀI:\n").append(question).append("\n\n");
        sb.append("BÀI VIẾT CỦA HỌC SINH Ở BƯỚC 1 (chỉ để so lỗi đỏ mới, không chấm lại):\n===\n").append(writtenText).append("\n===\n\n");
        sb.append(String.format(Locale.ROOT,
                "Thời lượng file: %.1f giây · Thời gian nói thật: %.1f giây · Im lặng dài nhất: %.1f giây.\n\n",
                durationSec, speechSec, longestPauseSec));
        sb.append("TRANSCRIPT CỐ ĐỊNH (giữa hai dòng ===):\n===\n").append(transcript).append("\n===\n");
        if (suspectWords != null && !suspectWords.isEmpty()) {
            sb.append("\nTỪ PHÁT ÂM SAI DO LƯỢT PHIÊN ÂM GHI NHẬN (kể cả từ đã ghi đúng chính tả nhưng nghe lệch — dạng \"nghe→đã ghi\"):\n")
                    .append(String.join(", ", suspectWords))
                    .append("\nCoi mọi từ trong danh sách này là PHÁT ÂM SAI khi chấm các checkpoint Phát âm.\n");
        }
        sb.append("\nFile âm thanh gốc:");
        return sb.toString();
    }
}
