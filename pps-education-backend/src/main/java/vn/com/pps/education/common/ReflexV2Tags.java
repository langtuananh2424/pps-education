package vn.com.pps.education.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21 — danh sách nhãn (tag) tô màu và tên tiêu
 * chí của bộ tiêu chí Speaking v2, mirror {@code prompts.js} (ERROR_TAGS/SEVERE_TAGS/STRENGTH_TAGS/
 * CRITERIA) do người training bàn giao. Nhãn là enum cố định để AI KHÔNG thể chèn gợi ý sửa vào phần tô
 * màu; mức đỏ/vàng do LOẠI lỗi quyết định (quy tắc chung §C), không để AI tự chọn.
 */
public final class ReflexV2Tags {

    private ReflexV2Tags() {
    }

    public static final Map<String, String> ERROR_TAGS = new LinkedHashMap<>();
    public static final Map<String, String> STRENGTH_TAGS = new LinkedHashMap<>();
    /** Lỗi nặng (tô đỏ) — hỏng cấu trúc câu hoặc sai nghĩa. */
    public static final Set<String> SEVERE_TAGS = Set.of(
            "thieu_thanh_phan", "cau_truc_cau", "trat_tu_tu", "thi_de_an_dinh", "dung_tu", "tu_loai", "tieng_viet", "khong_ro", "lac_y");

    /**
     * Lỗi chỉ ĐỎ từ Khối 8 (chuẩn B1+), vàng ở Khối 6-7 — quy tắc chung §C: "thừa hoặc thiếu giới từ làm hỏng
     * cụm" (At here, discuss about, go to home, listen music). Sai giới từ nhỏ ({@code gioi_tu}) vẫn vàng mọi khối.
     */
    public static final Set<String> SEVERE_FROM_GRADE_8_TAGS = Set.of("gioi_tu_pha_cum");
    public static final int SEVERE_FROM_GRADE = 8;

    /**
     * Lỗi đỏ NGỮ PHÁP — loại DUY NHẤT được đếm cho trần 60% tiêu chí Ngữ pháp (đã xác nhận với người dùng
     * 2026-09-29, ghi vào §C.2 của {@code rubrics-v3}): thiếu thành phần câu, sai cấu trúc câu, sai trật tự từ, sai
     * thì mà đề đã ấn định, và thừa/thiếu giới từ làm hỏng cụm (chỉ khi tag đó ĐỎ, tức từ Khối 8). Dùng từ/từ loại/tiếng Việt/
     * không nghe rõ/lạc ý vẫn tô đỏ, vẫn nhân ba ở checkpoint đếm lỗi nhưng không kéo trần Ngữ pháp — trước đó
     * hai từ phiên âm nghe không ra ({@code khong_ro}) cũng đủ hạ Ngữ pháp bài nói xuống 60%.
     */
    public static final Set<String> GRAMMAR_SEVERE_TAGS = Set.of(
            "thieu_thanh_phan", "cau_truc_cau", "trat_tu_tu", "thi_de_an_dinh", "gioi_tu_pha_cum");

    /** Mức đỏ của một tag lỗi theo khối — mức độ do LOẠI lỗi (và khối) quyết định, không để AI tự chọn. */
    public static boolean isSevere(String tag, int grade) {
        return SEVERE_TAGS.contains(tag) || (grade >= SEVERE_FROM_GRADE && SEVERE_FROM_GRADE_8_TAGS.contains(tag));
    }

    /** Lỗi tô trong transcript thuộc tiêu chí Phát âm (dùng khi phân lỗi vào từng tiêu chí để tính trần). */
    public static final Set<String> PRON_TAGS = Set.of("phat_am", "am_cuoi", "trong_am", "khong_ro");
    /** Lỗi thuộc tiêu chí Từ vựng (chỉ khi dạng bài có tiêu chí LR; nếu không thì tính vào Ngữ pháp). */
    public static final Set<String> VOCAB_TAGS = Set.of("dung_tu", "tu_loai", "tieng_viet");

    /** Tên tiếng Anh / tiếng Việt của tiêu chí, theo mã. */
    public static final Map<String, String> CRITERIA_EN = new LinkedHashMap<>();
    public static final Map<String, String> CRITERIA_VI = new LinkedHashMap<>();

    static {
        ERROR_TAGS.put("thi_dong_tu", "Thì / dạng động từ");
        ERROR_TAGS.put("thi_de_an_dinh", "Sai thì mà đề đã ấn định");
        ERROR_TAGS.put("hoa_hop_chu_vi", "Hòa hợp chủ ngữ và động từ");
        ERROR_TAGS.put("mao_tu", "Mạo từ");
        ERROR_TAGS.put("gioi_tu", "Giới từ");
        ERROR_TAGS.put("gioi_tu_pha_cum", "Thừa / thiếu giới từ làm hỏng cụm");
        ERROR_TAGS.put("so_it_so_nhieu", "Số ít / số nhiều");
        ERROR_TAGS.put("trat_tu_tu", "Trật tự từ");
        ERROR_TAGS.put("thieu_thanh_phan", "Thiếu thành phần câu");
        ERROR_TAGS.put("cau_truc_cau", "Cấu trúc câu");
        ERROR_TAGS.put("dung_tu", "Dùng từ sai nghĩa / sai ngữ cảnh");
        ERROR_TAGS.put("tu_loai", "Sai từ loại");
        ERROR_TAGS.put("chinh_ta", "Chính tả");
        ERROR_TAGS.put("phat_am", "Phát âm sai");
        ERROR_TAGS.put("am_cuoi", "Thiếu / sai âm cuối");
        ERROR_TAGS.put("trong_am", "Sai trọng âm");
        ERROR_TAGS.put("ngap_ngung", "Ngập ngừng / ngắt quãng");
        ERROR_TAGS.put("lap_lai", "Lặp lại / câu giờ");
        ERROR_TAGS.put("tieng_viet", "Chêm tiếng Việt");
        ERROR_TAGS.put("khong_ro", "Không nghe rõ");
        ERROR_TAGS.put("lac_y", "Lạc ý / không rõ nghĩa");

        STRENGTH_TAGS.put("cau_phuc", "Câu phức dùng tốt");
        STRENGTH_TAGS.put("tu_noi", "Từ nối hiệu quả");
        STRENGTH_TAGS.put("cum_tu", "Cụm từ / collocation tự nhiên");
        STRENGTH_TAGS.put("tu_vung", "Từ vựng hay");
        STRENGTH_TAGS.put("mo_rong_y", "Mở rộng ý tốt");
        STRENGTH_TAGS.put("thi_chinh_xac", "Dùng thì chính xác");
        STRENGTH_TAGS.put("phat_am_ro", "Phát âm rõ ràng");
        STRENGTH_TAGS.put("ngu_dieu", "Ngữ điệu tự nhiên");
        STRENGTH_TAGS.put("tu_sua", "Tự sửa lỗi thành công");

        CRITERIA_EN.put("GV", "Grammar and Vocabulary");
        CRITERIA_EN.put("DM", "Discourse Management");
        CRITERIA_EN.put("P", "Pronunciation");
        CRITERIA_EN.put("FC", "Fluency and Coherence");
        CRITERIA_EN.put("LR", "Lexical Resource");
        CRITERIA_EN.put("GRA", "Grammatical Range and Accuracy");

        CRITERIA_VI.put("GV", "Ngữ pháp và Từ vựng");
        CRITERIA_VI.put("DM", "Quản lý diễn ngôn");
        CRITERIA_VI.put("P", "Phát âm");
        CRITERIA_VI.put("FC", "Độ trôi chảy và Mạch lạc");
        CRITERIA_VI.put("LR", "Vốn từ vựng");
        CRITERIA_VI.put("GRA", "Độ đa dạng và Chính xác ngữ pháp");
    }
}
