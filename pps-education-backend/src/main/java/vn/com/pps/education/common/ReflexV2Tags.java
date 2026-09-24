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
            "thieu_thanh_phan", "cau_truc_cau", "trat_tu_tu", "dung_tu", "tu_loai", "tieng_viet", "khong_ro", "lac_y");

    /** Tên tiếng Anh / tiếng Việt của tiêu chí, theo mã. */
    public static final Map<String, String> CRITERIA_EN = new LinkedHashMap<>();
    public static final Map<String, String> CRITERIA_VI = new LinkedHashMap<>();

    static {
        ERROR_TAGS.put("thi_dong_tu", "Thì / dạng động từ");
        ERROR_TAGS.put("hoa_hop_chu_vi", "Hòa hợp chủ ngữ và động từ");
        ERROR_TAGS.put("mao_tu", "Mạo từ");
        ERROR_TAGS.put("gioi_tu", "Giới từ");
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
