package vn.com.pps.education.common;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21 — so nội dung bài nói với bài viết ở
 * Bước 1 (không tốn token AI), mirror {@code match.js} trong {@code ma-nguon-tham-chieu/} do người
 * training bàn giao. Học sinh phải nói lại ĐÚNG bài đã viết; nói khác hẳn → yêu cầu nói lại, không chấm.
 *
 * Từ phát âm sai vẫn khớp gần đúng (fren≈friend, scoo≈school) nhờ khoảng cách Levenshtein ≤ 34% độ dài;
 * ngưỡng khớp tối thiểu giãn theo độ dài bài viết (bài rất ngắn: mỗi từ phát âm sai kéo tỷ lệ xuống rất
 * mạnh, giữ ngưỡng cứng sẽ chặn oan chính những em yếu nhất).
 */
public final class ReflexContentOverlap {

    private ReflexContentOverlap() {
    }

    private static final double MAX_EDIT_RATIO = 0.34;
    private static final double MIN_OVERLAP = 0.45;

    private static final Set<String> STOP = new LinkedHashSet<>(Arrays.asList((
            "a an the and but or so because when if then to of in on at for with from by up about into "
                    + "i you he she it we they me him her us them my your his its our their this that these those "
                    + "is am are was were be been being do does did have has had can will would could should very too also not no yes um uh")
            .split(" ")));

    static List<String> norm(String s) {
        String lower = s == null ? "" : s.toLowerCase(Locale.ROOT);
        String plain = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("[\\u0300-\\u036f]", "")
                .replace('đ', 'd')
                .replaceAll("\\(\\.\\.\\.\\d+s\\)", " ")
                .replaceAll("[-\\u2019']", "")
                .replaceAll("[^a-z\\s]", " ");
        List<String> words = new ArrayList<>();
        for (String w : plain.split("\\s+")) {
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        return words;
    }

    static int levenshtein(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            }
        }
        return d[a.length()][b.length()];
    }

    private static boolean similar(String w, String t) {
        if (w.equals(t)) {
            return true;
        }
        return w.length() >= 4 && t.length() >= 3
                && (double) levenshtein(w, t) / Math.max(w.length(), t.length()) <= MAX_EDIT_RATIO;
    }

    private static Set<String> contentWords(String written) {
        Set<String> words = new LinkedHashSet<>();
        for (String w : norm(written)) {
            if (!STOP.contains(w)) {
                words.add(w);
            }
        }
        return words;
    }

    /** Tỷ lệ từ nội dung của bài viết có mặt (gần đúng) trong transcript, 0..1. */
    public static double contentOverlap(String written, String transcript) {
        List<String> said = norm(transcript);
        // Gộp cả cặp mảnh liền nhau ("bot cus" → "botcus") để khớp từ vỡ mảnh.
        List<String> pool = new ArrayList<>(said);
        for (int i = 1; i < said.size(); i++) {
            pool.add(said.get(i - 1) + said.get(i));
        }
        Set<String> words = contentWords(written);
        if (words.isEmpty()) {
            return 1;
        }
        long hit = words.stream().filter(w -> pool.stream().anyMatch(t -> similar(w, t))).count();
        return (double) hit / words.size();
    }

    /** Ngưỡng khớp tối thiểu, nới cho bài quá ngắn (≤6 từ nội dung → 0,25; ≤10 → 0,35; còn lại 0,45). */
    public static double minOverlapFor(String written) {
        int n = contentWords(written).size();
        if (n <= 6) {
            return 0.25;
        }
        if (n <= 10) {
            return 0.35;
        }
        return MIN_OVERLAP;
    }
}
