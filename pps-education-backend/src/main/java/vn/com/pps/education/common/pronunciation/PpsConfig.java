package vn.com.pps.education.common.pronunciation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tham số chỉnh được của bộ chấm PPS Hybrid v2 và của chấm bài nói tự do. Bất biến; dạng lưu là bản phẳng
 * {@code "word.ending_penalty" -> 1.5}, dạng gửi/nhận qua API là JSON lồng nhau cùng các khoá đó.
 *
 * <p>Giá trị mặc định là đúng các hằng số của đặc tả ({@code pps_speaking_lab_scoring_v2_spec.md}), nên chưa chỉnh gì thì điểm
 * không đổi. Bảng {@link #FIELDS} (khoảng cho phép, nhãn, mô tả) được sinh từ {@code pps_config.py} của lab; giao diện dùng
 * nó để vẽ biểu mẫu. Hai việc diễn ra lúc chấm (gióng hàng bài viết với lời nói, chọn từ nói thêm) không nằm ở đây.
 */
public final class PpsConfig {

    /** Một tham số: đường dẫn, nhóm hiển thị, nhãn, mô tả, khoảng cho phép, bước nhập và giá trị mặc định. */
    public record Field(String path, String group, String label, String help, double min, double max, double step,
                        double defaultValue) {
    }

    public static final List<Field> FIELDS = List.of(
            new Field("phone.floor", "Âm vị", "Điểm GOPT coi là sai hẳn", "Âm vị có điểm GOPT (0-2) dưới mức này nhận 0 điểm.", 0.0, 1.9, 0.01, 0.8),
            new Field("phone.top", "Âm vị", "Điểm GOPT coi là đạt tối đa", "Âm vị có điểm GOPT từ mức này trở lên nhận điểm tối đa.", 1.0, 2.0, 0.01, 1.95),
            new Field("phone.exponent", "Âm vị", "Độ gắt của đường cong", "Số mũ kéo giãn điểm âm vị: càng lớn, âm chưa tốt càng bị hạ nhiều.", 0.5, 4.0, 0.05, 1.8),
            new Field("word.ending_penalty", "Điểm từ", "Điểm trừ khi thiếu phụ âm cuối", "Trừ vào điểm từ (thang 10) khi wav2vec2 nghe thiếu hoặc sai phụ âm cuối.", 0.0, 5.0, 0.1, 1.5),
            new Field("word.ipa_weight_low", "Điểm từ", "Trọng số wav2vec2 khi nó thấy sai nặng hơn GOPT", "Phần còn lại là GOPT.", 0.0, 1.0, 0.05, 0.6),
            new Field("word.ipa_weight_high", "Điểm từ", "Trọng số wav2vec2 trong các trường hợp còn lại", "Phần còn lại là GOPT.", 0.0, 1.0, 0.05, 0.45),
            new Field("word.stress_share", "Điểm từ", "Tỉ lệ trọng âm trong điểm tổng của từ", "Điểm tổng của từ = (1 - tỉ lệ) × độ chính xác + tỉ lệ × trọng âm.", 0.0, 0.5, 0.05, 0.2),
            new Field("whisper.mult.extra", "Whisper", "Hệ số điểm từ nói thêm", "Từ nói thêm ngoài bài viết (lấy từ Whisper).", 0.0, 1.0, 0.01, 0.85),
            new Field("whisper.mult.diff", "Whisper", "Hệ số điểm từ bị nghe thành từ khác", "Whisper nghe thành từ khác với từ cần đọc.", 0.0, 1.0, 0.01, 0.78),
            new Field("whisper.mult.unverified", "Whisper", "Hệ số điểm khi không kiểm chứng được", "Whisper không nhận ra lời nói, chấm trên bài viết.", 0.0, 1.0, 0.01, 0.55),
            new Field("whisper.cap.extra", "Whisper", "Trần điểm từ nói thêm", "Thang 10.", 0.0, 10.0, 0.1, 9.0),
            new Field("whisper.cap.diff", "Whisper", "Trần điểm từ bị nghe thành từ khác", "Thang 10.", 0.0, 10.0, 0.1, 6.8),
            new Field("whisper.cap.unverified", "Whisper", "Trần điểm khi không kiểm chứng được", "Thang 10.", 0.0, 10.0, 0.1, 5.0),
            new Field("fluency.target_wpm.6", "Trôi chảy", "Tốc độ chuẩn khối 6 (từ/phút)", "Từ mức này trở lên điểm tốc độ đạt từ 7,5.", 40.0, 220.0, 1.0, 95.0),
            new Field("fluency.target_wpm.7", "Trôi chảy", "Tốc độ chuẩn khối 7 (từ/phút)", "Khối khác hoặc chưa rõ tính như khối 7.", 40.0, 220.0, 1.0, 105.0),
            new Field("fluency.target_wpm.8", "Trôi chảy", "Tốc độ chuẩn khối 8 (từ/phút)", "", 40.0, 220.0, 1.0, 115.0),
            new Field("fluency.short_pause_s", "Trôi chảy", "Ngắt ngắn từ (giây)", "Khoảng lặng giữa hai từ từ mức này tính là đọc rời rạc.", 0.05, 1.0, 0.01, 0.25),
            new Field("fluency.long_pause_s", "Trôi chảy", "Ngắt dài từ (giây)", "Khoảng lặng từ mức này tính là ngập ngừng.", 0.2, 3.0, 0.05, 0.6),
            new Field("fluency.short_pause_penalty", "Trôi chảy", "Phạt mỗi lần ngắt ngắn", "", 0.0, 3.0, 0.05, 0.35),
            new Field("fluency.long_pause_penalty", "Trôi chảy", "Phạt mỗi lần ngắt dài", "", 0.0, 5.0, 0.05, 0.9),
            new Field("fluency.gopt_share", "Trôi chảy", "Tỉ lệ điểm GOPT trong trôi chảy", "Phần còn lại là điểm từ tốc độ và ngắt nghỉ.", 0.0, 1.0, 0.05, 0.45),
            new Field("utterance.weights.accuracy", "Điểm câu", "Trọng số Accuracy trong Total", "Ba trọng số của Total phải cộng bằng 1.", 0.0, 1.0, 0.05, 0.45),
            new Field("utterance.weights.fluency", "Điểm câu", "Trọng số Fluency trong Total", "", 0.0, 1.0, 0.05, 0.3),
            new Field("utterance.weights.prosody", "Điểm câu", "Trọng số Prosody trong Total", "", 0.0, 1.0, 0.05, 0.25),
            new Field("utterance.word_share", "Điểm câu", "Tỉ lệ điểm từ trong Accuracy", "Phần còn lại là điểm GOPT của câu.", 0.0, 1.0, 0.05, 0.55),
            new Field("utterance.bad_word_threshold", "Điểm câu", "Từ yếu: dưới điểm", "Từ có điểm dưới mức này (thang 10) tính là yếu.", 0.0, 10.0, 0.1, 5.0),
            new Field("utterance.bad_word_penalty", "Điểm câu", "Phạt theo tỉ lệ từ yếu", "Trừ vào Accuracy: phạt × tỉ lệ từ yếu.", 0.0, 5.0, 0.1, 1.2),
            new Field("utterance.coverage_gate_from", "Điểm câu", "Độ phủ bài viết bắt đầu bị phạt dưới", "Dưới mức này (0-1) điểm bị nhân hệ số nhỏ hơn 1.", 0.1, 1.0, 0.05, 0.8),
            new Field("utterance.min_phones", "Điểm câu", "Số âm vị tối thiểu của câu", "Câu ngắn hơn bị hạ Fluency và Prosody.", 1.0, 80.0, 1.0, 24.0),
            new Field("utterance.fallback_gate", "Điểm câu", "Hệ số khi dùng bài viết thay Whisper", "", 0.1, 1.0, 0.05, 0.6),
            new Field("utterance.grade_shift.6", "Điểm câu", "Điều chỉnh khối 6", "Cộng vào điểm (thang 10).", -2.0, 2.0, 0.05, 0.2),
            new Field("utterance.grade_shift.7", "Điểm câu", "Điều chỉnh khối 7", "", -2.0, 2.0, 0.05, 0.0),
            new Field("utterance.grade_shift.8", "Điểm câu", "Điều chỉnh khối 8", "", -2.0, 2.0, 0.05, -0.25),
            new Field("utterance.fallback_cap", "Điểm câu", "Trần điểm khi dùng bài viết thay Whisper", "Thang 10.", 0.0, 10.0, 0.1, 4.5),
            new Field("utterance.low_coverage_cap", "Điểm câu", "Trần Total khi phủ dưới 50% bài viết", "Thang 10.", 0.0, 10.0, 0.1, 3.9),
            new Field("spontaneous.weights.pronunciation", "Nói tự do", "Trọng số P (phát âm)", "Bốn trọng số phải cộng bằng 1.", 0.0, 1.0, 0.05, 0.3),
            new Field("spontaneous.weights.fluency", "Nói tự do", "Trọng số F (trôi chảy)", "", 0.0, 1.0, 0.05, 0.25),
            new Field("spontaneous.weights.grammar_vocab", "Nói tự do", "Trọng số GV (ngữ pháp, từ vựng)", "", 0.0, 1.0, 0.05, 0.25),
            new Field("spontaneous.weights.task_response", "Nói tự do", "Trọng số TR (đúng đề)", "", 0.0, 1.0, 0.05, 0.2),
            new Field("spontaneous.min_words.7", "Nói tự do", "Số từ tối thiểu khối 7", "Ít hơn thì Total nhân hệ số nhỏ hơn 1.", 1.0, 60.0, 1.0, 8.0),
            new Field("spontaneous.min_words.8", "Nói tự do", "Số từ tối thiểu khối 8", "", 1.0, 60.0, 1.0, 12.0),
            new Field("spontaneous.target_wpm.7", "Nói tự do", "Tốc độ chuẩn khối 7 (từ/phút)", "", 40.0, 220.0, 1.0, 95.0),
            new Field("spontaneous.target_wpm.8", "Nói tự do", "Tốc độ chuẩn khối 8 (từ/phút)", "", 40.0, 220.0, 1.0, 105.0),
            new Field("spontaneous.min_words_hard", "Nói tự do", "Dưới số từ này là quá ngắn", "Bài quá ngắn không chấm Haiku.", 1.0, 20.0, 1.0, 4.0),
            new Field("spontaneous.min_speech_seconds", "Nói tự do", "Nói dưới số giây này là quá ngắn", "", 0.5, 20.0, 0.5, 3.0)
    );

    private static final List<String> WEIGHT_GROUPS = List.of("utterance.weights", "spontaneous.weights");
    private static final Map<String, Field> BY_PATH = new LinkedHashMap<>();
    private static final PpsConfig DEFAULTS;

    static {
        Map<String, Double> d = new LinkedHashMap<>();
        for (Field f : FIELDS) {
            BY_PATH.put(f.path(), f);
            d.put(f.path(), f.defaultValue());
        }
        DEFAULTS = new PpsConfig(d);
    }

    private final Map<String, Double> values;

    private PpsConfig(Map<String, Double> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static PpsConfig defaults() {
        return DEFAULTS;
    }

    public double get(String path) {
        Double v = values.get(path);
        if (v == null) {
            throw new IllegalArgumentException("Không có tham số '" + path + "'");
        }
        return v;
    }

    /** Tham số theo khối lớp: {@code prefix + "." + grade}, khối không có trong bảng thì lấy khối 7. */
    public double byGrade(String prefix, int grade) {
        Double v = values.get(prefix + "." + grade);
        return v != null ? v : get(prefix + ".7");
    }

    /** Bản phẳng {đường dẫn: giá trị}, chỉ đọc. */
    public Map<String, Double> flat() {
        return values;
    }

    /** Bản lồng nhau, đúng dạng JSON của API ({@code {"word": {"ending_penalty": 1.5}, ...}}). */
    public Map<String, Object> nested() {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : values.entrySet()) {
            String[] parts = e.getKey().split("\\.");
            Map<String, Object> cur = root;
            for (int i = 0; i < parts.length - 1; i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> next = (Map<String, Object>) cur.computeIfAbsent(parts[i], k -> new LinkedHashMap<String, Object>());
                cur = next;
            }
            cur.put(parts[parts.length - 1], e.getValue());
        }
        return root;
    }

    /** Các tham số khác mặc định, dạng phẳng; rỗng nghĩa là đang dùng mặc định. */
    public Map<String, Double> changed() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : values.entrySet()) {
            if (Math.abs(e.getValue() - DEFAULTS.values.get(e.getKey())) > 1e-9) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /**
     * Trộn {@code raw} (có thể chỉ một phần, dạng lồng nhau hoặc khoá phẳng "a.b.c") vào {@code base} (mặc định: giá trị mặc định)
     * và kiểm tra: khoá phải có thật, giá trị là số trong khoảng cho phép, các nhóm trọng số cộng bằng 1 (sai số 0,01).
     *
     * @throws IllegalArgumentException kèm đường dẫn khoá sai
     */
    public static PpsConfig validate(Map<String, ?> raw, PpsConfig base) {
        Map<String, Double> out = new LinkedHashMap<>((base != null ? base : DEFAULTS).values);
        Map<String, ?> src = raw == null ? Map.of() : raw;
        if (src.size() <= 2 && src.get("config") instanceof Map<?, ?> inner) {   // file xuất ra có thể bọc trong {"config": ...}
            @SuppressWarnings("unchecked")
            Map<String, ?> unwrapped = (Map<String, ?>) inner;
            src = unwrapped;
        }
        Map<String, Object> leaves = new LinkedHashMap<>();
        collectLeaves(src, "", leaves);
        for (Map.Entry<String, Object> e : leaves.entrySet()) {
            Field f = BY_PATH.get(e.getKey());
            if (f == null) {
                throw new IllegalArgumentException("Không có tham số '" + e.getKey() + "'");
            }
            if (!(e.getValue() instanceof Number n) || e.getValue() instanceof Boolean) {
                throw new IllegalArgumentException("Tham số '" + e.getKey() + "' phải là số");
            }
            double v = n.doubleValue();
            if (!(v >= f.min() && v <= f.max())) {   // so sánh phủ định để NaN cũng bị từ chối
                throw new IllegalArgumentException("Tham số '" + e.getKey() + "' phải từ " + fmt(f.min()) + " đến "
                        + fmt(f.max()) + " (nhận " + fmt(v) + ")");
            }
            out.put(e.getKey(), v);
        }
        for (String g : WEIGHT_GROUPS) {
            double sum = 0;
            for (Map.Entry<String, Double> e : out.entrySet()) {
                if (e.getKey().startsWith(g + ".")) {
                    sum += e.getValue();
                }
            }
            if (Math.abs(sum - 1.0) > 0.01) {
                throw new IllegalArgumentException(String.format("Các trọng số của '%s' phải cộng bằng 1 (hiện %.2f)", g, sum));
            }
        }
        if (out.get("phone.top") <= out.get("phone.floor")) {
            throw new IllegalArgumentException("phone.top phải lớn hơn phone.floor");
        }
        if (out.get("fluency.long_pause_s") <= out.get("fluency.short_pause_s")) {
            throw new IllegalArgumentException("fluency.long_pause_s phải lớn hơn fluency.short_pause_s");
        }
        return new PpsConfig(out);
    }

    private static void collectLeaves(Map<String, ?> node, String prefix, Map<String, Object> into) {
        for (Map.Entry<String, ?> e : node.entrySet()) {
            String path = prefix + e.getKey();
            if (e.getValue() instanceof Map<?, ?> child) {
                @SuppressWarnings("unchecked")
                Map<String, ?> c = (Map<String, ?>) child;
                collectLeaves(c, path + ".", into);
            } else {
                into.put(path, e.getValue());
            }
        }
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }

    /** Danh sách tham số theo nhóm hiển thị, giữ thứ tự khai báo. */
    public static Map<String, List<Field>> fieldsByGroup() {
        Map<String, List<Field>> out = new LinkedHashMap<>();
        for (Field f : FIELDS) {
            out.computeIfAbsent(f.group(), k -> new ArrayList<>()).add(f);
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PpsConfig other && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
