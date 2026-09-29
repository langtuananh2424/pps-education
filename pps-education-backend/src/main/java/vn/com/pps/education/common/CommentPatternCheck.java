package vn.com.pps.education.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UC-74 bước 7 (bổ sung 2026-09-29, đã xác nhận với người dùng) — chặn lặp KIỂU câu mở đầu/câu kết giữa các
 * học sinh trong 1 buổi. {@link CommentSimilarity} so cả đoạn nên 2 nhận xét cùng câu mở đầu nhưng phần sau
 * khác nhau vẫn lọt; lớp này so riêng "khoá" của câu đầu và câu cuối.
 *
 * <p>Khoá câu mở đầu = 3 từ đầu của câu đầu tiên sau khi bỏ các từ trong họ tên học sinh ("An tập trung nghe
 * giảng" và "Bình tập trung nghe giảng" cùng khoá "tập trung nghe"). Khoá câu kết = 3 từ đầu của câu cuối (chỉ
 * khi nhận xét có từ 2 câu). Thuần tính toán, không gọi AI.</p>
 *
 * <p>1 dòng bị coi là lặp khi: (1) cùng khoá với học sinh đứng NGAY TRƯỚC, hoặc (2) khoá đó đã xuất hiện quá
 * {@code max(2, floor(maxShare × số dòng))} lần — các lần vượt ngưỡng bị đánh dấu (giữ lại các lần đầu).</p>
 */
public final class CommentPatternCheck {

    private static final int KEY_WORDS = 3;

    private CommentPatternCheck() {
    }

    /** 1 dòng nhận xét cần kiểm tra, theo đúng thứ tự hiển thị trong buổi. */
    public record Entry(Long id, String fullName, String content) {
    }

    /** @param openingIds dòng lặp kiểu mở đầu; {@code closingIds} dòng lặp kiểu câu kết. */
    public record Result(Set<Long> openingIds, Set<Long> closingIds) {
        public Set<Long> all() {
            Set<Long> all = new LinkedHashSet<>(openingIds);
            all.addAll(closingIds);
            return all;
        }
    }

    public static Result check(List<Entry> entries, double maxShare) {
        List<String> openings = new ArrayList<>();
        List<String> closings = new ArrayList<>();
        for (Entry entry : entries) {
            openings.add(openingKey(entry.content(), entry.fullName()));
            closings.add(closingKey(entry.content()));
        }
        int written = (int) entries.stream().filter(e -> e.content() != null && !e.content().isBlank()).count();
        int allowed = Math.max(2, (int) Math.floor(maxShare * written));
        return new Result(repeated(entries, openings, allowed), repeated(entries, closings, allowed));
    }

    /** Tỷ lệ dòng có khoá mở đầu trùng với ít nhất 1 dòng khác (0..1) — dùng cho log chỉ số. */
    public static double openingRepeatRate(List<Entry> entries) {
        List<String> keys = entries.stream().map(e -> openingKey(e.content(), e.fullName())).filter(k -> k != null).toList();
        return repeatRate(keys);
    }

    /** Tỷ lệ dòng có khoá câu kết trùng với ít nhất 1 dòng khác (0..1) — dùng cho log chỉ số. */
    public static double closingRepeatRate(List<Entry> entries) {
        List<String> keys = entries.stream().map(e -> closingKey(e.content())).filter(k -> k != null).toList();
        return repeatRate(keys);
    }

    public static String openingKey(String content, String fullName) {
        List<String> sentences = sentences(content);
        if (sentences.isEmpty()) {
            return null;
        }
        Set<String> nameWords = new HashSet<>(CommentSimilarity.words(fullName));
        List<String> words = CommentSimilarity.words(sentences.get(0)).stream().filter(w -> !nameWords.contains(w)).toList();
        return key(words);
    }

    public static String closingKey(String content) {
        List<String> sentences = sentences(content);
        if (sentences.size() < 2) {
            return null;
        }
        return key(CommentSimilarity.words(sentences.get(sentences.size() - 1)));
    }

    private static Set<Long> repeated(List<Entry> entries, List<String> keys, int allowed) {
        Set<Long> flagged = new LinkedHashSet<>();
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            String key = keys.get(i);
            if (key == null) {
                continue;
            }
            int count = seen.merge(key, 1, Integer::sum);
            boolean sameAsPrevious = i > 0 && key.equals(keys.get(i - 1));
            if (sameAsPrevious || count > allowed) {
                flagged.add(entries.get(i).id());
            }
        }
        return flagged;
    }

    private static double repeatRate(List<String> keys) {
        if (keys.isEmpty()) {
            return 0.0;
        }
        Map<String, Integer> counts = new HashMap<>();
        keys.forEach(k -> counts.merge(k, 1, Integer::sum));
        long repeated = keys.stream().filter(k -> counts.get(k) > 1).count();
        return (double) repeated / keys.size();
    }

    private static String key(List<String> words) {
        if (words.isEmpty()) {
            return null;
        }
        return String.join(" ", words.subList(0, Math.min(KEY_WORDS, words.size())));
    }

    private static List<String> sentences(String content) {
        List<String> result = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return result;
        }
        for (String part : content.trim().split("[.!?…]+")) {
            if (!part.isBlank()) {
                result.add(part.trim());
            }
        }
        return result;
    }
}
