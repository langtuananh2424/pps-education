package vn.com.pps.education.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import vn.com.pps.education.dto.CommentAiDraftResult;

/**
 * UC-74 (bổ sung 2026-09-29, đã xác nhận với người dùng — phương án A, không đổi schema) — chỉ số chất lượng
 * của 1 lần soạn nháp, ghi thành 1 dòng log có cấu trúc (JSON) để script {@code scripts/comment-ai-metrics.py}
 * tổng hợp theo tuần. Chỉ đo trên bản nháp AI trả về (trước khi giáo viên sửa), không chứa nội dung nhận xét
 * hay tên học sinh — log không lộ dữ liệu cá nhân.
 */
public final class CommentAiDraftMetrics {

    /** Ngưỡng "câu ngắn" theo rubric (≥ 1 câu ≤ 8 từ mỗi nhận xét). */
    private static final int SHORT_SENTENCE_WORDS = 8;
    private static final Pattern HOMEWORK_MENTION = Pattern.compile("(?iu)(bài tập về nhà|btvn)");

    private CommentAiDraftMetrics() {
    }

    /**
     * @param event                 {@code DRAFT} (soạn từ audio/ghi chú) hoặc {@code REWRITE_ALL} (viết lại toàn bộ).
     * @param studentsWithHomework  học sinh có dữ liệu BTVN gửi cho AI — nhắc BTVN ngoài nhóm này là "không có dữ liệu".
     */
    public static Map<String, Object> of(String event, Long classSessionId, List<CommentAiDraftResult.Row> rows,
                                         int unmatchedCount, String teacherPronoun, Set<Long> studentsWithHomework) {
        List<CommentPatternCheck.Entry> entries = rows.stream()
                .map(r -> new CommentPatternCheck.Entry(r.studentId(), r.studentFullName(), r.content())).toList();
        List<String> contents = rows.stream().map(CommentAiDraftResult.Row::content)
                .filter(c -> c != null && !c.isBlank()).toList();

        Map<String, Integer> warningRows = new TreeMap<>();
        Map<String, Integer> attitudeRows = new TreeMap<>();
        int homeworkWithoutData = 0;
        int homeworkMentions = 0;
        for (CommentAiDraftResult.Row row : rows) {
            row.warnings().stream().map(CommentAiDraftResult.Warning::type).distinct()
                    .forEach(type -> warningRows.merge(type, 1, Integer::sum));
            attitudeRows.merge(row.attitude() == null ? "NONE" : row.attitude(), 1, Integer::sum);
            if (row.content() != null && HOMEWORK_MENTION.matcher(row.content()).find()) {
                homeworkMentions++;
                if (!studentsWithHomework.contains(row.studentId())) {
                    homeworkWithoutData++;
                }
            }
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("event", event);
        metrics.put("classSessionId", classSessionId);
        metrics.put("rows", rows.size());
        metrics.put("written", contents.size());
        metrics.put("individualRows", rows.stream().filter(r -> "INDIVIDUAL".equals(r.source())).count());
        metrics.put("unmatched", unmatchedCount);
        metrics.put("teacherPronoun", teacherPronoun == null ? "NONE" : teacherPronoun);
        metrics.put("avgLength", contents.isEmpty() ? 0 : Math.round(contents.stream().mapToInt(String::length).average().orElse(0)));
        metrics.put("shortSentenceRows", contents.stream().filter(CommentAiDraftMetrics::hasShortSentence).count());
        metrics.put("openingRepeatRate", round(CommentPatternCheck.openingRepeatRate(entries)));
        metrics.put("closingRepeatRate", round(CommentPatternCheck.closingRepeatRate(entries)));
        metrics.put("homeworkMentions", homeworkMentions);
        metrics.put("homeworkWithoutData", homeworkWithoutData);
        metrics.put("warningRows", warningRows);
        metrics.put("attitudeRows", attitudeRows);
        return metrics;
    }

    static boolean hasShortSentence(String content) {
        for (String sentence : content.split("[.!?…]+")) {
            int words = CommentSimilarity.words(sentence).size();
            if (words > 0 && words <= SHORT_SENTENCE_WORDS) {
                return true;
            }
        }
        return false;
    }

    private static double round(double value) {
        return Double.parseDouble(String.format(Locale.ROOT, "%.3f", value));
    }
}
