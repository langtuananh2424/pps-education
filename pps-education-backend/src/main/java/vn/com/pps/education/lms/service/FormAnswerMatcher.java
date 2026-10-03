package vn.com.pps.education.lms.service;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * So khớp đáp án cho dạng "Nghe điền phiếu thông tin" (WORD_BANK có structuredContent.format="form",
 * import kind NGHE_PHIEU_THONG_TIN) — mô phỏng cách chấm bài Listening dạng note/form completion:
 * mỗi chỗ trống nhận NHIỀU đáp án đúng phân tách bằng dấu "/" (VD "1999/nineteen ninety-nine"), và
 * so khớp sau khi chuẩn hóa số/giờ để "7:30", "7.30", "7h30" hay "five" và "5" không bị chấm sai
 * chỉ vì cách viết. Chỉ dùng cho dạng form — WORD_BANK thường vẫn so khớp chính xác như cũ.
 */
final class FormAnswerMatcher {

    private static final Pattern AM_PM_DOTS = Pattern.compile("\\b([ap])\\.\\s?m\\.?", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACE_BEFORE_AM_PM = Pattern.compile("(\\d)\\s+(am|pm)\\b");
    private static final Pattern TIME = Pattern.compile("\\b(\\d{1,2})\\s?[.:h]\\s?(\\d{2})\\b");
    private static final Pattern THOUSANDS = Pattern.compile("(?<=\\d),(?=\\d{3}\\b)");
    private static final Pattern TRAILING_PUNCT = Pattern.compile("\\p{Punct}+$");

    private static final Map<String, String> NUMBER_WORDS = Map.ofEntries(
            Map.entry("zero", "0"), Map.entry("one", "1"), Map.entry("two", "2"), Map.entry("three", "3"),
            Map.entry("four", "4"), Map.entry("five", "5"), Map.entry("six", "6"), Map.entry("seven", "7"),
            Map.entry("eight", "8"), Map.entry("nine", "9"), Map.entry("ten", "10"), Map.entry("eleven", "11"),
            Map.entry("twelve", "12"), Map.entry("thirteen", "13"), Map.entry("fourteen", "14"),
            Map.entry("fifteen", "15"), Map.entry("sixteen", "16"), Map.entry("seventeen", "17"),
            Map.entry("eighteen", "18"), Map.entry("nineteen", "19"), Map.entry("twenty", "20"),
            Map.entry("thirty", "30"), Map.entry("forty", "40"), Map.entry("fifty", "50"),
            Map.entry("sixty", "60"), Map.entry("seventy", "70"), Map.entry("eighty", "80"),
            Map.entry("ninety", "90"), Map.entry("hundred", "100"));

    private FormAnswerMatcher() {
    }

    /** {@code accepted} là 1 ô đáp án, có thể gồm nhiều phương án "a/b/c"; đúng nếu khớp ÍT NHẤT 1 phương án. */
    static boolean matches(String accepted, String given) {
        if (accepted == null) {
            return false;
        }
        String normalizedGiven = normalize(given);
        // Đáp án đúng rỗng (để trống hợp lệ) chỉ khớp ô học sinh cũng để trống.
        if (accepted.isBlank()) {
            return normalizedGiven.isEmpty();
        }
        return Arrays.stream(accepted.split("/"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(variant -> normalize(variant).equals(normalizedGiven));
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String s = text.trim().toLowerCase();
        s = AM_PM_DOTS.matcher(s).replaceAll("$1m");
        s = TRAILING_PUNCT.matcher(s).replaceAll("").trim();
        s = THOUSANDS.matcher(s).replaceAll("");
        s = TIME.matcher(s).replaceAll("$1:$2");
        s = SPACE_BEFORE_AM_PM.matcher(s).replaceAll("$1$2");
        s = s.replaceAll("\\s+", " ");
        // Từ số đứng 1 mình -> chữ số ("five" == "5"); cụm nhiều từ ("twenty five") không đoán.
        String digit = NUMBER_WORDS.get(s);
        return digit != null ? digit : s;
    }
}
