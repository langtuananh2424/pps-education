package vn.com.pps.education.lms.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * UC-40 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30) —
 * nhận diện SONG NGỮ (Việt/Anh) tên cột header (Excel)/nhãn field (Word)
 * khi soạn đề nhanh. Dùng CHUNG cho {@link ExcelQuestionRowParser} (đọc
 * theo TÊN cột, không còn vị trí cố định) và {@link WordQuestionRowParser}
 * (nới nhãn "Nhãn: giá trị" chấp nhận thêm tiếng Anh). Phạm vi CHỈ tên
 * cột/nhãn field — KHÔNG áp dụng cho giá trị enum bên trong ô (VD cột
 * "Loại câu hỏi" vẫn phải điền "TRAC_NGHIEM", không chấp nhận
 * "MULTIPLE_CHOICE" — xem {@code QuestionImportService#VALID_KINDS}).
 */
final class QuestionImportFieldAliases {

    private QuestionImportFieldAliases() {
    }

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("loai cau hoi", "kind"), Map.entry("loai", "kind"),
            Map.entry("question type", "kind"), Map.entry("type", "kind"),

            Map.entry("do kho", "difficulty"), Map.entry("difficulty", "difficulty"),

            Map.entry("noi dung", "content"), Map.entry("content", "content"), Map.entry("question", "content"),

            Map.entry("dap an a", "choiceA"), Map.entry("answer a", "choiceA"), Map.entry("option a", "choiceA"),
            Map.entry("dap an b", "choiceB"), Map.entry("answer b", "choiceB"), Map.entry("option b", "choiceB"),
            Map.entry("dap an c", "choiceC"), Map.entry("answer c", "choiceC"), Map.entry("option c", "choiceC"),
            Map.entry("dap an d", "choiceD"), Map.entry("answer d", "choiceD"), Map.entry("option d", "choiceD"),
            // Bổ sung 2026-10-17 (đã xác nhận với người dùng) — phục vụ DOC_HIEU_NOI_DAP_AN (đọc hiểu —
            // nối đáp án, tối đa 8 lựa chọn dùng chung A-H), xem QuestionImportService#buildSharedGroupChoices.
            // CHỈ loại này đọc 4 cột E-H — DOC_HIEU_LUOI/TRAC_NGHIEM/DOC_DIEN_TU vẫn cap A-D như cũ.
            Map.entry("dap an e", "choiceE"), Map.entry("answer e", "choiceE"), Map.entry("option e", "choiceE"),
            Map.entry("dap an f", "choiceF"), Map.entry("answer f", "choiceF"), Map.entry("option f", "choiceF"),
            Map.entry("dap an g", "choiceG"), Map.entry("answer g", "choiceG"), Map.entry("option g", "choiceG"),
            Map.entry("dap an h", "choiceH"), Map.entry("answer h", "choiceH"), Map.entry("option h", "choiceH"),
            Map.entry("dap an i", "choiceI"), Map.entry("answer i", "choiceI"), Map.entry("option i", "choiceI"),
            Map.entry("dap an j", "choiceJ"), Map.entry("answer j", "choiceJ"), Map.entry("option j", "choiceJ"),

            Map.entry("dap an dung", "correctAnswer"), Map.entry("correct answer", "correctAnswer"),

            Map.entry("url audio", "audioUrl"), Map.entry("audio url", "audioUrl"),

            Map.entry("url hinh anh", "imageUrl"), Map.entry("image url", "imageUrl"),

            Map.entry("transcript tu khoa phat am", "referencePassage"),
            Map.entry("transcript pronunciation keywords", "referencePassage"),
            Map.entry("transcript tu khoa", "referencePassage"),
            Map.entry("transcript", "referencePassage"), Map.entry("tu khoa phat am", "referencePassage"),
            Map.entry("pronunciation keywords", "referencePassage"), Map.entry("keywords", "referencePassage"),
            // Bổ sung 2026-09-08 (đã xác nhận với người dùng — mở khóa import DOC_HIEU_LUOI/DOC_DIEN_TU,
            // xem QuestionImportService) — cùng field referencePassage nhưng đặt tên rõ nghĩa hơn cho
            // ngữ cảnh Reading (khác ngữ cảnh Speaking/WordBank vốn dùng nhãn "Transcript").
            Map.entry("doan van tham chieu", "referencePassage"), Map.entry("reference passage", "referencePassage"),
            Map.entry("doan van", "referencePassage"), Map.entry("passage", "referencePassage"),

            Map.entry("diem", "defaultPoints"), Map.entry("diem mac dinh", "defaultPoints"),
            Map.entry("points", "defaultPoints"), Map.entry("score", "defaultPoints"),

            Map.entry("giai thich", "explanation"), Map.entry("explanation", "explanation"),

            Map.entry("tags", "tags"),

            // Bổ sung 2026-10-19 (đã xác nhận với người dùng) — cột "Kỹ năng" tường minh, xem Javadoc
            // QuestionRowParser.ParsedQuestionRow#skill.
            Map.entry("ky nang", "skill"), Map.entry("skill", "skill"),

            // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-14 — dòng hướng dẫn hiện phía
            // trên câu hỏi dạng Điền từ (xem Javadoc QuestionRowParser.ParsedQuestionRow#instruction).
            Map.entry("huong dan", "instruction"), Map.entry("instruction", "instruction"),
            Map.entry("chi dan", "instruction"), Map.entry("direction", "instruction")
    );

    /** Trả về tên field nội bộ ({@code ParsedQuestionRow}) khớp với 1 header/nhãn, hoặc null nếu không nhận diện được. */
    static String resolveField(String rawHeaderOrLabel) {
        return ALIASES.get(normalize(rawHeaderOrLabel));
    }

    /**
     * Chuẩn hoá so khớp: trim, lowercase, coi "/"/"-"/"_" như khoảng
     * trắng (VD "Transcript/Từ khóa" và "Transcript-Từ khóa" so khớp như
     * nhau), gộp khoảng trắng, bỏ dấu tiếng Việt — dùng chung Excel/Word.
     */
    static String normalize(String text) {
        String lowered = text.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s/\\-_]+", " ").trim();
        String decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return decomposed.replace('đ', 'd');
    }
}
