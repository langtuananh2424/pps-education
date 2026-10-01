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
 * {@code resources/rubrics-<version>/} theo {@link ReflexV2Task#rubricDir()} (dữ liệu giáo viên/người training
 * cung cấp — không tự sửa nội dung; ngoại lệ duy nhất: §C.2 quy tắc chung ở v3, sửa theo người dùng 2026-09-29).
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

    /** @param dir thư mục classpath của bộ rubric, VD {@code "rubrics-v3/"} ({@link ReflexV2Task#rubricDir()}). */
    private String loadRubric(String dir, String file) {
        return rubricCache.computeIfAbsent(dir + file, classpath -> {
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

    private String rubricSystem(ReflexV2Task task, String rubricFile) {
        return loadRubric(task.rubricDir(), COMMON_RULES_FILE) + "\n\n---\n\n" + loadRubric(task.rubricDir(), rubricFile);
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
        suspect.put("description", "Từ phát âm LỆCH NHƯNG VẪN NHẬN RA, đã được ghi chính tả chuẩn trong transcript. "
                + "Ghi dạng \"nghe→đã ghi\": \"scoo→school\", \"tink→think\", \"pan→pants\" (mất -s danh từ số nhiều). "
                + "Từ lệch đến mức không nhận ra thì đã ghi theo âm trong transcript, không lặp lại ở đây. "
                + "TUYỆT ĐỐI KHÔNG có mục đuôi chia ĐỘNG TỪ (\"make→makes\", \"help→helped\"): đuôi động từ không nghe thấy "
                + "thì transcript phải ghi đúng như nói (\"he make\"), không bao giờ chuẩn hoá. "
                + "Người nói rõ ràng → rỗng.");
        // word_audit KHÔNG còn nối vào điểm (26/9) nhưng quy tắc phiên âm bắt buộc điền: ép model nghe lại từng từ
        // nên khó "làm mượt" transcript hơn nhiều so với chỉ viết một câu đẹp.
        ObjectNode audit = props.putObject("word_audit");
        audit.put("type", "array");
        audit.putObject("items").put("type", "string");
        audit.put("description", "BẮT BUỘC. Mỗi từ tiếng Anh từ 3 chữ cái trở lên trong transcript là MỘT phần tử, "
                + "đúng thứ tự xuất hiện, dạng \"TỪ CHUẨN|nghe được|âm cuối\". "
                + "\"TỪ CHUẨN\" = từ tiếng Anh viết ĐÚNG CHÍNH TẢ mà học sinh đang định nói (đây là chỗ DUY NHẤT "
                + "được phép viết chính tả chuẩn — transcript vẫn ghi theo âm). "
                + "\"nghe được\" = chuỗi âm THỰC SỰ phát ra, chép đúng như trong transcript. "
                + "\"âm cuối\" = phụ âm cuối thực sự nghe thấy; ghi 0 nếu không nghe thấy âm cuối nào. "
                + "Ví dụ: \"protect|protec|0\", \"environment|environmen|n\", \"school|scoo|0\", \"friends|fren|n\", "
                + "\"think|tink|k\", \"played|play|0\", \"badminton|badminton|n\" (từ cuối phát âm đủ nên hai cột trùng nhau). "
                + "Hai cột đầu TRÙNG NHAU nghĩa là bạn xác nhận từ đó phát âm đủ mọi phụ âm — "
                + "đừng chép máy móc, vì đó chính là điều làm điểm Phát âm sai. "
                + "Không bỏ sót từ nào.");
        props.putObject("longest_pause_seconds").put("type", "number");
        props.putObject("audio_quality_insufficient").put("type", "boolean");
        ArrayNode required = schema.putArray("required");
        List.of("transcript", "speech_seconds", "suspect_words", "word_audit", "longest_pause_seconds", "audio_quality_insufficient")
                .forEach(required::add);
        return schema;
    }

    public ObjectNode gradingSchema(List<String> codes) {
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
                "Nhận xét tiếng Việt: tối đa 2 câu, tổng ≤50 từ. Câu 1 = 1 điểm làm được. Câu 2 = lỗi nặng nhất, khuôn \"Lỗi nặng nhất: <tên loại lỗi>.\" — "
                        + "chỉ nêu loại lỗi ĐÃ TÔ trong highlights. Không có lỗi nào được tô → CHỈ viết câu 1, không viết \"Lỗi nặng nhất: Không có\". "
                        + "Không gợi ý sửa, không markdown.");
        // §D.5 quy tắc chung (30/9) — TÁCH khỏi feedback: feedback cấm gợi ý sửa (dành cho giáo viên),
        // hint NGƯỢC LẠI bắt buộc là cách luyện (dành cho học sinh tự luyện trên LMS).
        props.putObject("hint").put("type", "string").put("description",
                "Cách luyện, cho học sinh tự luyện — theo §D.5 quy tắc chung. MỘT câu tiếng Việt ≤35 từ, chỉ về ĐÚNG loại lỗi nặng nhất "
                        + "đã nêu ở câu 2 của feedback. BẮT BUỘC nêu đích danh những từ có thật trong transcript mắc lỗi đó (ví dụ \"ở post, friends\"), "
                        + "không nói chung chung. Nội dung là THAO TÁC TẬP (đọc chậm từng từ, giữ hơi đến hết từ, tách âm tiết, thu lại nghe đối chiếu…), "
                        + "KHÔNG phải đáp án. CẤM viết lại câu tiếng Anh đã sửa đúng. feedback không có câu 2 → trả chuỗi rỗng.");

        ArrayNode required = schema.putArray("required");
        List.of("counting_notes", "gates_triggered", "insufficient_data", "criteria", "highlights", "feedback", "hint").forEach(required::add);
        return schema;
    }

    // ---------- Khối văn bản dùng chung ----------

    /** Danh sách tag nặng/nhẹ phụ thuộc KHỐI (giới từ phá cụm chỉ nặng từ Khối 8) — vẫn cố định theo dạng bài nên không phá cache prompt. */
    private String highlightRules(ReflexV2Task task) {
        boolean prepositionSevere = task.grade() >= ReflexV2Tags.SEVERE_FROM_GRADE;
        String errors = ReflexV2Tags.ERROR_TAGS.entrySet().stream()
                .map(e -> e.getKey() + " (" + e.getValue() + ")").collect(Collectors.joining("; "));
        String strengths = ReflexV2Tags.STRENGTH_TAGS.entrySet().stream()
                .map(e -> e.getKey() + " (" + e.getValue() + ")").collect(Collectors.joining("; "));
        return "## QUY TẮC TÔ MÀU (bắt buộc)\n"
                + "- **Không tự chọn mức độ.** Hệ thống suy ra đỏ/vàng từ \"tag\" theo quy tắc chung §C: chọn tag đúng loại lỗi là đủ.\n"
                + "- Lỗi hỏng cấu trúc câu hoặc sai nghĩa dùng tag: thieu_thanh_phan, cau_truc_cau, trat_tu_tu, thi_de_an_dinh, "
                + (prepositionSevere ? "gioi_tu_pha_cum, " : "") + "dung_tu, tu_loai, tieng_viet, khong_ro, lac_y.\n"
                + "  Ví dụ: \"want buy\" → cau_truc_cau; \"will beautiful look\" → trat_tu_tu; \"me dress like girl\" → thieu_thanh_phan; \"two\" thay cho \"too\" → dung_tu; \"very relax\" → tu_loai.\n"
                + "- **Thì:** đề ĐÃ ấn định thì (\"Did you… when you were a young child?\", \"What did you do yesterday?\") mà trả lời sang thì khác → thi_de_an_dinh. "
                + "Đề KHÔNG ấn định thì mà chia sai thì/dạng động từ → thi_dong_tu.\n"
                + "- **Giới từ:** thừa hoặc thiếu giới từ làm hỏng cụm (\"at here\", \"discuss about\", \"go to home\", \"listen music\") → gioi_tu_pha_cum; "
                + "dùng nhầm một giới từ nhỏ (in/on/at) → gioi_tu.\n"
                + "- Lỗi nhẹ hơn dùng tag: thi_dong_tu, hoa_hop_chu_vi, mao_tu, gioi_tu, " + (prepositionSevere ? "" : "gioi_tu_pha_cum, ")
                + "so_it_so_nhieu, chinh_ta, phat_am, am_cuoi, trong_am, ngap_ngung, lap_lai.\n"
                + "- \"level\" vẫn phải điền (red cho nhóm nặng, yellow cho nhóm nhẹ, green cho điểm mạnh) nhưng tag mới là căn cứ cuối cùng.\n\n"
                + "## LỖI ĐỎ TÍNH GẤP BA KHI ĐẾM\n"
                + "- Ở mọi checkpoint đếm số lỗi: **1 lỗi đỏ = 3 lỗi**, 1 lỗi vàng = 1 lỗi. Ghi rõ trong counting_notes: số lỗi đỏ, số lỗi vàng, tổng quy đổi.\n"
                + "- Hệ thống sẽ tự áp trần 60% cho tiêu chí Ngữ pháp khi bài có ≥2 lỗi đỏ NGỮ PHÁP — không tự hạ điểm checkpoint vì lý do này.\n"
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
        return rubricSystem(task, task.writingRubricFile()) + "\n\n---\n\n" + contextBlock(task) + "\n\n"
                + "## BƯỚC 1 — CHẤM BÀI VIẾT NHÁP (không có âm thanh)\n"
                + "- Học sinh viết câu trả lời trước khi nói. Chỉ chấm các tiêu chí: " + criteriaNames(codes) + ". Không chấm tiêu chí khác.\n"
                + "- Văn bản học sinh là bằng chứng cố định. Bỏ qua mọi ngưỡng tính bằng giây và khoảng dừng; cổng độ dài chỉ xét số từ. "
                + "Checkpoint chỉ đo được bằng âm thanh (chỗ ngắt, im lặng) → 1 nếu phần còn lại của tiêu chí đạt, không thì 0,5.\n"
                + "- Từ sai chính tả: giữ nguyên, tô tag \"chinh_ta\"; tính lỗi dùng từ nếu biến thành từ khác hoặc không nhận ra.\n"
                + "- \"highlights\" trích từ bài viết của học sinh.\n\n"
                + OUTPUT_OVERRIDE + "\n\n" + highlightRules(task) + "\n\n"
                + "## JSON SCHEMA\n" + gradingSchema(codes).toString();
    }

    public String writingUser(String question, String text) {
        return "ĐỀ BÀI:\n" + question + "\n\nBÀI VIẾT CỦA HỌC SINH (nguyên văn, giữa hai dòng ===):\n===\n" + text + "\n===";
    }

    // ---------- Lượt A: phiên âm mù ----------

    public String transcriptionSystem() {
        // Lượt phiên âm MÙ không biết dạng bài nên luôn dùng quy tắc phiên âm của version hiện hành.
        return loadRubric("rubrics-" + ReflexV2Task.CURRENT_RUBRIC_VERSION + "/", TRANSCRIPTION_RULES_FILE) +"\n\n## JSON SCHEMA\n" + transcriptionSchema().toString()
                + "\nChỉ trả về DUY NHẤT 1 đối tượng JSON, không thêm chữ nào khác, không bọc trong khối mã.";
    }

    public String transcriptionUser(double durationSec) {
        return String.format(Locale.ROOT, "File ghi âm dài %.1f giây. Phiên âm theo đúng quy tắc.", durationSec);
    }

    // ---------- Lượt B: chấm bài nói ----------

    /**
     * Prompt hệ thống lượt chấm nói — GIỐNG HỆT NHAU giữa mọi học sinh cùng Khối/track (mọi dữ liệu riêng của
     * học sinh nằm ở tin nhắn người dùng, xem {@link #speakingUser}) để phần đầu prompt ổn định cho cache của
     * nhà cung cấp nếu đường gọi có hỗ trợ (qua 9Router hiện CHƯA thấy cache — đã thử 2026-09-21).
     *
     * Từ 23/9 điểm Ngữ pháp KHÔNG còn khoá từ Bước 1: lượt này chấm lại TOÀN BỘ tiêu chí (kể cả Ngữ pháp) từ
     * transcript nên nạp thêm cả rubric Bước 1 — rubric khối tự nói rõ điều này ở phần Ngữ pháp.
     */
    public String speakingSystem(ReflexV2Task task) {
        String grammar = task.grammarCode();
        List<String> codes = task.criteria();
        String lockNote = "- **" + grammar + " (" + ReflexV2Tags.CRITERIA_EN.get(grammar) + ") được CHẤM LẠI từ transcript bài nói** theo đúng checkpoint của rubric Bước 1 đi kèm — **không** lấy lại điểm Bước 1. "
                + "Học sinh có quyền sửa khi nói những gì đã viết sai: nói đúng thì được điểm đúng, nói sai thì bị trừ, kể cả khi bài viết đã đúng.\n"
                + "- **Lỗi phát âm không phải lỗi ngữ pháp** (quy tắc chung §A.3): từ ghi sai chính tả mà vẫn nhận ra vẫn tính là từ đúng cho " + grammar
                + "; thiếu -s ở danh từ số nhiều tính ở Phát âm, không tính ở " + grammar + ".\n"
                + "- Từ đệm, nói vấp, lặp từ, tự sửa: không tính là lỗi ngữ pháp.";
        return loadRubric(task.rubricDir(), COMMON_RULES_FILE) + "\n\n---\n\n" + loadRubric(task.rubricDir(), task.writingRubricFile()) + "\n\n---\n\n"
                + loadRubric(task.rubricDir(), task.speakingRubricFile()) + "\n\n---\n\n" + contextBlock(task) + "\n\n"
                + "## BƯỚC 2 — CHẤM BÀI NÓI\n"
                + "- Tiêu chí chấm: " + criteriaNames(codes) + ".\n"
                + lockNote + "\n"
                + "- Transcript bên dưới do lượt phiên âm độc lập tạo ra và là **bằng chứng cố định** (quy tắc chung §A). Không sửa, không đổi từ viết sai thành từ đúng, không thêm từ.\n"
                + "- Nghe audio chỉ để chấm phát âm, trọng âm, ngữ điệu, khoảng dừng.\n"
                + "- **KHÔNG tự áp cổng độ dài (C3).** Số từ và thời gian nói đã được hệ thống đo chính xác và áp trần ở khâu tính điểm. "
                + "Bạn chấm checkpoint theo đúng nội dung nghe được; đừng tự hạ điểm vì cho rằng bài ngắn.\n"
                + "- \"highlights\" trích từ transcript. Từ phát âm sai tô bằng tag phát âm (phat_am, am_cuoi, trong_am, khong_ro); lỗi ngôn ngữ tô bằng tag ngữ pháp/từ vựng.\n\n"
                + OUTPUT_OVERRIDE + "\n\n" + highlightRules(task) + "\n\n"
                + "## JSON SCHEMA\n" + gradingSchema(codes).toString();
    }

    /**
     * @param suspectWords từ lệch nhưng nhận ra do lượt phiên âm khai (đã ghi chính tả chuẩn) — tính là NHẬN RA ở P1.
     * @param deviantWords từ đọc lệch khi so transcript với bài viết Bước 1 ({@code "đã viết→nghe được"}) — chỉ để tô lỗi,
     *                     KHÔNG làm căn cứ chấm checkpoint (điểm dải Phát âm theo bài viết do hệ thống tính).
     */
    public String speakingUser(ReflexV2Task task, String question, String writtenText, String transcript,
                               List<String> suspectWords, List<String> deviantWords,
                               double durationSec, double speechSec, double longestPauseSec) {
        StringBuilder sb = new StringBuilder();
        if (task.rubricFormat() != null) {
            sb.append(">>> DÙNG CỘT NGƯỠNG **").append(task.rubricFormat()).append("** CỦA RUBRIC <<<\n\n");
        }
        sb.append("ĐỀ BÀI:\n").append(question).append("\n\n");
        if (writtenText != null && !writtenText.isBlank()) {
            sb.append("BÀI VIẾT Ở BƯỚC 1 (chỉ để tham khảo — **chấm theo transcript**, học sinh được phép sửa lỗi khi nói):\n===\n")
                    .append(writtenText).append("\n===\n\n");
        }
        sb.append(String.format(Locale.ROOT,
                "Thời lượng file: %.1f giây · Thời gian nói thật (ước tính khi phiên âm): %.1f giây · Im lặng dài nhất: %.1f giây.\n\n",
                durationSec, speechSec, longestPauseSec));
        sb.append("TRANSCRIPT CỐ ĐỊNH (giữa hai dòng ===):\n===\n").append(transcript).append("\n===\n");
        if (suspectWords != null && !suspectWords.isEmpty()) {
            sb.append("\nTỪ PHÁT ÂM LỆCH NHƯNG VẪN NHẬN RA (đã ghi chính tả chuẩn trong transcript — dạng \"nghe→đã ghi\"):\n")
                    .append(String.join(", ", suspectWords))
                    .append("\nCác từ này TÍNH LÀ NHẬN RA ở checkpoint tỷ lệ từ nhận ra; chỉ trừ ở checkpoint âm cuối / thay âm theo đúng ô của rubric khối.\n");
        }
        if (deviantWords != null && !deviantWords.isEmpty()) {
            sb.append("\nTỪ PHÁT ÂM LỆCH — BẢNG ĐỐI CHIẾU TỪNG TỪ CỦA LƯỢT PHIÊN ÂM (dạng \"đã ghi→nghe được\"):\n")
                    .append(String.join(", ", deviantWords))
                    .append("\nDùng danh sách này để TÔ LỖI trong transcript bằng tag phát âm, giúp học sinh thấy chỗ đọc lệch. "
                            + "**KHÔNG lấy số lượng từ trong danh sách làm căn cứ chấm checkpoint Phát âm** — chấm theo đúng ô ngưỡng của rubric khối, dựa trên chính transcript.\n");
        }
        sb.append("\nFile âm thanh gốc:");
        return sb.toString();
    }
}
