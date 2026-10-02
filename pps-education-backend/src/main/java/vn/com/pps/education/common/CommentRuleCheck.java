package vn.com.pps.education.common;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Kiểm tra theo quy tắc (không gọi AI) dùng chung cho trợ lý soạn nháp nhận xét (UC-74) và trợ lý duyệt (UC-75) —
 * tách ra 2026-10-01 (đã xác nhận với người dùng) để 2 trợ lý luôn báo cùng 1 lỗi bằng cùng ngưỡng và cùng câu chữ,
 * thay vì mỗi service tự viết lại vòng so trùng/kiểm tra tên bài.
 */
public final class CommentRuleCheck {

    private CommentRuleCheck() {
    }

    /**
     * Nhận xét giống nhất trong 1 tập ứng viên.
     *
     * @param similarity 0..1 theo {@link CommentSimilarity}; 0 nếu không có ứng viên nào.
     * @param source     nhãn của ứng viên giống nhất (tên học sinh hoặc ngày buổi), {@code null} nếu không có.
     */
    public record Match(double similarity, String source) {
        static final Match NONE = new Match(0, null);

        public boolean atLeast(double threshold) {
            return source != null && similarity >= threshold;
        }

        public long percent() {
            return Math.round(similarity * 100);
        }
    }

    public static <T> Match bestMatch(String content, Collection<T> candidates, Function<T, String> contentOf,
                                      Function<T, String> sourceOf) {
        Match best = Match.NONE;
        if (content == null || content.isBlank() || candidates == null) {
            return best;
        }
        for (T candidate : candidates) {
            String other = contentOf.apply(candidate);
            if (other == null || other.isBlank()) {
                continue;
            }
            double similarity = CommentSimilarity.similarity(content, other);
            if (similarity > best.similarity()) {
                best = new Match(similarity, sourceOf.apply(candidate));
            }
        }
        return best;
    }

    /** Cảnh báo {@code SIMILAR_IN_SESSION} — giống nhận xét của học sinh khác cùng buổi. */
    public static String similarInSessionMessage(Match match) {
        return "Giống nhận xét của " + match.source() + " " + match.percent() + "%.";
    }

    /** Cảnh báo {@code SIMILAR_TO_PREVIOUS} — giống nhận xét buổi trước của chính học sinh đó. */
    public static String similarToPreviousMessage(Match match) {
        return "Giống nhận xét buổi " + match.source() + " " + match.percent() + "%.";
    }

    /**
     * Tên bài học (class_sessions.lesson_content, VD "Unit 1: Hello Friend") KHÔNG được đưa vào nhận xét (đã xác
     * nhận với người dùng 2026-09-29) — bắt trường hợp nhận xét vẫn chép nguyên tên bài (VD giáo viên đọc tên bài
     * trong audio).
     */
    public static boolean mentionsLessonTitle(String content, String lessonContent) {
        if (content == null || lessonContent == null || lessonContent.trim().length() < 5) {
            return false;
        }
        Function<String, String> norm = text -> Normalizer.normalize(text, Normalizer.Form.NFC)
                .toLowerCase(Locale.forLanguageTag("vi")).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return norm.apply(content).contains(norm.apply(lessonContent));
    }

    /** Nội dung cảnh báo lặp kiểu câu ({@code REPEATED_PATTERN}) của 1 dòng, xem {@link CommentPatternCheck}. */
    public static String repeatedPatternMessage(CommentPatternCheck.Result patterns, Long id) {
        List<String> parts = new ArrayList<>();
        if (patterns.openingIds().contains(id)) {
            parts.add("câu mở đầu");
        }
        if (patterns.closingIds().contains(id)) {
            parts.add("câu kết");
        }
        if (patterns.phraseIds().contains(id)) {
            parts.add("cụm \"" + String.join("\", \"", patterns.phrasesById().get(id)) + "\"");
        }
        return "Kiểu " + String.join(" và ", parts) + " giống nhiều bạn khác trong buổi — nên đổi cách viết.";
    }
}
