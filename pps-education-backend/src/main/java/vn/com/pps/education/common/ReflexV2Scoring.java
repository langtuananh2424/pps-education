package vn.com.pps.education.common;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21 — tính điểm TẤT ĐỊNH cho bộ tiêu chí
 * Speaking v2 (Khối 6-7), mirror {@code computeScores}/{@code applyRedCap}/{@code locateHighlights}/
 * {@code trimFeedback} trong {@code ma-nguon-tham-chieu/prompts.js} + {@code grading.js} do người training
 * bàn giao. AI CHỈ trả checkpoint 0/0,5/1 và trần % do cổng chặn — quy đổi sang % làm ở đây, không để
 * AI tự tính (xem mục "Không tự quy đổi phần trăm" trong prompt).
 *
 * KHÁC bản tham chiếu 1 điểm có chủ đích: bản JS tự điền 0,5 khi AI trả THIẾU checkpoint (âm thầm nới
 * điểm); ở đây trả thiếu/sai là kết quả hỏng → {@link IllegalStateException}, caller coi như "AI chấm
 * lỗi" để học sinh nộp lại (đúng nguyên tắc "mặc định mức thấp" của quy tắc chung §B.1).
 */
public final class ReflexV2Scoring {

    private ReflexV2Scoring() {
    }

    /** Từ 2 lỗi đỏ trở lên → tiêu chí Ngữ pháp không vượt mức này (quy tắc chung §C). */
    public static final int RED_CAP = 60;

    public record CriterionScore(String code, int percent, boolean cappedByRedErrors) {
    }

    public record ScoreSet(List<CriterionScore> criteria, int finalPercent, List<String> gates) {
    }

    public record Highlight(int start, int end, String level, String label, String tag) {
    }

    /** Trung bình cộng các % tiêu chí, làm tròn XUỐNG bội số 5 (+1e-9 chống sai số dấu phẩy động, như bản JS). */
    public static int floorTo5(double average) {
        return (int) (Math.floor(average / 5 + 1e-9) * 5);
    }

    private static double snapHalf(double n) {
        return Math.floor(n * 2 + 1e-9) / 2;
    }

    /**
     * @param task  dạng bài — quyết định những mã cổng chặn hợp lệ (Khối 8-9 IELTS chỉ có C1/C2, xem {@link ReflexV2Task.GateScheme}).
     * @param codes các mã tiêu chí AI phải chấm ở lượt này, đúng thứ tự (VD Bước 1 Khối 7 IELTS: LR, GRA).
     * @param data  JSON AI trả về (đã parse) theo schema của {@code ReflexV2Prompts}.
     * @throws IllegalStateException nếu AI thiếu tiêu chí hoặc trả không đủ 5 checkpoint hợp lệ.
     */
    public static ScoreSet computeScores(ReflexV2Task task, List<String> codes, JsonNode data) {
        boolean insufficient = data.path("insufficient_data").asBoolean(false);
        List<CriterionScore> criteria = new ArrayList<>();
        for (String code : codes) {
            int percent = 0;
            if (!insufficient) {
                JsonNode entry = findCriterion(data.path("criteria"), code);
                if (entry == null) {
                    throw new IllegalStateException("AI không trả tiêu chí " + code);
                }
                JsonNode checkpoints = entry.path("checkpoints");
                if (!checkpoints.isArray() || checkpoints.size() != 5) {
                    throw new IllegalStateException("AI trả sai số checkpoint của " + code + " (cần đúng 5)");
                }
                double sum = 0;
                for (JsonNode cp : checkpoints) {
                    if (!cp.isNumber()) {
                        throw new IllegalStateException("Checkpoint của " + code + " không phải số");
                    }
                    double v = cp.asDouble();
                    sum += v >= 1 ? 1 : (v >= 0.5 ? 0.5 : 0);
                }
                double total = snapHalf(Math.max(0, Math.min(5, sum)));
                JsonNode capNode = entry.path("cap_percent");
                double cap = capNode.isNumber() ? Math.max(0, Math.min(100, capNode.asDouble())) : 100;
                percent = (int) Math.min(total * 20, cap);
            }
            criteria.add(new CriterionScore(code, percent, false));
        }
        return new ScoreSet(criteria, average(criteria), gateCodes(task, data));
    }

    /** Trần 60% cho tiêu chí Ngữ pháp khi bài có từ 2 lỗi đỏ (Bước 1). Chỉ giảm, không bao giờ tăng. */
    public static ScoreSet applyRedCap(String grammarCode, ScoreSet scored, int redCount) {
        if (redCount < 2) {
            return scored;
        }
        List<CriterionScore> criteria = new ArrayList<>();
        for (CriterionScore c : scored.criteria()) {
            if (c.code().equals(grammarCode) && c.percent() > RED_CAP) {
                criteria.add(new CriterionScore(c.code(), RED_CAP, true));
            } else {
                criteria.add(c);
            }
        }
        return new ScoreSet(criteria, average(criteria), scored.gates());
    }

    public static int average(List<CriterionScore> criteria) {
        double avg = criteria.stream().mapToInt(CriterionScore::percent).average().orElse(0);
        return floorTo5(avg);
    }

    private static JsonNode findCriterion(JsonNode array, String code) {
        if (array.isArray()) {
            for (JsonNode c : array) {
                if (code.equals(c.path("code").asText())) {
                    return c;
                }
            }
        }
        return null;
    }

    /** Bản JS tham chiếu cũng lọc mã cổng theo {@code task.gates} — mã không thuộc dạng bài (VD C3 ở Khối 8-9 IELTS) bị bỏ. */
    private static List<String> gateCodes(ReflexV2Task task, JsonNode data) {
        Set<String> valid = task.gateScheme() == ReflexV2Task.GateScheme.V3 ? Set.of("C1", "C2") : Set.of("C1", "C2", "C3");
        Set<String> gates = new LinkedHashSet<>();
        for (JsonNode g : data.path("gates_triggered")) {
            if (valid.contains(g.asText())) {
                gates.add(g.asText());
            }
        }
        return new ArrayList<>(gates);
    }

    /**
     * Tìm vị trí từng đoạn trích trong văn bản gốc; bỏ đoạn không tìm thấy hoặc chồng lấn. Mức độ
     * (đỏ/vàng/xanh) do LOẠI lỗi (tag) quyết định, KHÔNG dùng {@code level} AI tự điền.
     */
    public static List<Highlight> locateHighlights(String source, JsonNode highlights) {
        List<Highlight> found = new ArrayList<>();
        if (source == null || !highlights.isArray()) {
            return found;
        }
        for (JsonNode h : highlights) {
            String quote = h.path("quote").asText("");
            if (quote.isEmpty()) {
                continue;
            }
            String tag = h.path("tag").asText("");
            String errorLabel = ReflexV2Tags.ERROR_TAGS.get(tag);
            String strengthLabel = ReflexV2Tags.STRENGTH_TAGS.get(tag);
            if (errorLabel == null && strengthLabel == null) {
                continue;
            }
            String level = strengthLabel != null ? "green" : (ReflexV2Tags.SEVERE_TAGS.contains(tag) ? "red" : "yellow");
            int occurrence = Math.max(1, h.path("occurrence").asInt(1));
            int idx = -1;
            int from = 0;
            for (int i = 0; i < occurrence; i++) {
                idx = source.indexOf(quote, from);
                if (idx == -1) {
                    break;
                }
                from = idx + quote.length();
            }
            if (idx == -1) {
                idx = source.indexOf(quote);
            }
            if (idx == -1) {
                continue;
            }
            found.add(new Highlight(idx, idx + quote.length(), level, errorLabel != null ? errorLabel : strengthLabel, tag));
        }
        found.sort(Comparator.comparingInt(Highlight::start).thenComparing(Comparator.comparingInt(Highlight::end).reversed()));
        List<Highlight> result = new ArrayList<>();
        int lastEnd = -1;
        for (Highlight f : found) {
            if (f.start() < lastEnd) {
                continue;
            }
            result.add(f);
            lastEnd = f.end();
        }
        return result;
    }

    public static int countRed(List<Highlight> highlights) {
        return (int) highlights.stream().filter(h -> h.level().equals("red")).count();
    }

    /**
     * Chuyển highlight sang markup {@code {{err}}...{{/err}}} mà FE hiện tại đã render (V178/V181) —
     * CHỈ đỏ/vàng (lỗi) được bọc; điểm mạnh (xanh) bỏ qua. FE chưa phân biệt đỏ/vàng nên mọi lỗi cùng 1
     * kiểu bôi (đợt sau có thể nâng FE dùng đúng mức độ).
     */
    public static String toErrMarkup(String source, List<Highlight> highlights) {
        if (source == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (Highlight h : highlights) {
            if (h.level().equals("green")) {
                continue;
            }
            out.append(source, cursor, h.start());
            out.append("{{err}}").append(source, h.start(), h.end()).append("{{/err}}");
            cursor = h.end();
        }
        out.append(source.substring(cursor));
        return out.toString();
    }

    private static final Pattern WORD = Pattern.compile("[A-Za-zÀ-ỹ0-9']+");

    public static int wordCount(String s) {
        if (s == null) {
            return 0;
        }
        Matcher m = WORD.matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** Nhận xét bị giới hạn 50 từ theo rubric; bỏ ký tự markdown. */
    public static String trimFeedback(String s) {
        String plain = s == null ? "" : s.replaceAll("\\*\\*|__|[*_`#>]", "").trim();
        if (plain.isEmpty()) {
            return "";
        }
        String[] words = plain.split("\\s+");
        if (words.length <= 50) {
            return String.join(" ", words);
        }
        return String.join(" ", java.util.Arrays.copyOf(words, 50)) + "…";
    }

    /**
     * Câu giải thích khi cổng chặn kích hoạt — do BACKEND tự soạn (bộ tiêu chí mới cấm AI đưa gợi ý vào
     * {@code feedback}, xem {@code TRA-LOI-TICH-HOP.md} mục F), giọng "Yêu cầu:" hướng dẫn, không phê phán
     * điểm số (đã xác nhận với người dùng V184). Trả rỗng nếu không cổng nào cần giải thích.
     */
    public static String buildGateNote(ReflexV2Task task, List<String> gates, int wordCount) {
        return buildGateNote(task, gates, wordCount, false);
    }

    /** @param spoken true khi giải thích cho bài NÓI (dùng từ "nói thêm"), false cho bài VIẾT. */
    public static String buildGateNote(ReflexV2Task task, List<String> gates, int wordCount, boolean spoken) {
        // Cùng mã C1 nhưng nghĩa khác nhau theo dạng bài (xem ReflexV2Task.GateScheme).
        boolean v3 = task.gateScheme() == ReflexV2Task.GateScheme.V3;
        String tooShortCode = v3 ? "C1" : "C3";
        String offTopicCode = v3 ? "C2" : "C1";
        StringBuilder note = new StringBuilder();
        if (gates.contains(tooShortCode)) {
            note.append(spoken ? "Yêu cầu: Bài nói cần tối thiểu " : "Yêu cầu: Bài cần tối thiểu ").append(task.minWords())
                    .append(" từ tiếng Anh (hiện có ").append(wordCount).append(spoken
                            ? " từ) — hãy nói thêm 1-2 câu nêu chi tiết hoặc lý do để đủ độ dài."
                            : " từ) — hãy viết thêm 1-2 câu nêu chi tiết hoặc lý do để đủ độ dài.");
        }
        if (gates.contains(offTopicCode)) {
            if (note.length() > 0) {
                note.append(' ');
            }
            note.append("Yêu cầu: Câu trả lời chưa đúng trọng tâm câu hỏi — hãy đọc lại câu hỏi và trả lời đúng ý được hỏi.");
        }
        return note.toString();
    }
}
