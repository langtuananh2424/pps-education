package vn.com.pps.education.common;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * UC-74 bước 7 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-28) — đo mức "trùng lặp máy
 * móc" giữa 2 đoạn nhận xét: tỷ lệ Jaccard trên tập cụm 3 từ liên tiếp (shingle), sau khi chuẩn hoá chữ
 * thường + bỏ dấu câu (GIỮ dấu thanh tiếng Việt — "ban" và "bạn" là 2 từ khác nhau). Thuần tính toán, không
 * gọi AI, để kết quả kiểm tra luôn ổn định và test được: 2 câu cùng ý nhưng diễn đạt khác cho điểm thấp,
 * 2 câu chép lại gần nguyên văn cho điểm cao.
 *
 * Đoạn quá ngắn (dưới 3 từ) không đủ cụm 3 từ nên so theo từng từ đơn.
 */
public final class CommentSimilarity {

    private static final int SHINGLE_SIZE = 3;

    private CommentSimilarity() {
    }

    /** @return 0.0 (không có cụm nào chung) tới 1.0 (giống hệt); 0.0 nếu 1 trong 2 đoạn rỗng. */
    public static double similarity(String a, String b) {
        List<String> wordsA = words(a);
        List<String> wordsB = words(b);
        if (wordsA.isEmpty() || wordsB.isEmpty()) {
            return 0.0;
        }
        int size = Math.min(SHINGLE_SIZE, Math.min(wordsA.size(), wordsB.size()));
        Set<String> shinglesA = shingles(wordsA, size);
        Set<String> shinglesB = shingles(wordsB, size);
        Set<String> intersection = new HashSet<>(shinglesA);
        intersection.retainAll(shinglesB);
        Set<String> union = new HashSet<>(shinglesA);
        union.addAll(shinglesB);
        return (double) intersection.size() / union.size();
    }

    static List<String> words(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        List<String> result = new ArrayList<>();
        for (String token : normalized.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return result;
    }

    private static Set<String> shingles(List<String> words, int size) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i + size <= words.size(); i++) {
            result.add(String.join(" ", words.subList(i, i + size)));
        }
        return result;
    }
}
