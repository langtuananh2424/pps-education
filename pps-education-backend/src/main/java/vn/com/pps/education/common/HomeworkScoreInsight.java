package vn.com.pps.education.common;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UC-74/75 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — quy đổi điểm BTVN buổi trước sang
 * LỜI để trợ lý AI viết nhận xét, KHÔNG đưa con số cho AI (nhận xét vẫn không được ghi điểm/%). Thuần tính
 * toán, test được.
 *
 * <p>Ngưỡng đã chốt với người dùng: ≥ 80% "làm tốt"; 50–79% "làm được, cần cẩn thận hơn"; &lt; 50% "cần cố
 * gắng"; "Chưa làm bài" → "chưa hoàn thành"; "Đang chờ chấm"/không đọc được → không nhắc. Chỉ đưa cho AI các
 * kênh NỔI BẬT (làm tốt / cần cố gắng / chưa hoàn thành) hoặc khi điểm TĂNG/GIẢM RÕ so với buổi trước — mức
 * "làm được" không nổi bật nên không nhắc, tránh học sinh nào cũng có 1 câu BTVN giống khuôn.</p>
 */
public final class HomeworkScoreInsight {

    public enum Level { GOOD, OK, LOW, NOT_DONE }

    private static final Pattern RATIO = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*/\\s*(\\d+(?:[.,]\\d+)?)");
    private static final Pattern PERCENT = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*%");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)\\s*$");

    private HomeworkScoreInsight() {
    }

    /** Điểm/nhãn BTVN của 1 kênh (VD "Bài tập ngữ pháp online" → "80%" / "Chưa làm bài" / "8/10"). */
    public record Channel(String name, String rawValue) {
    }

    /** % đọc được từ nhãn/điểm nhập tay; rỗng nếu không đọc được hoặc đang chờ chấm. */
    public static OptionalInt percentOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalInt.empty();
        }
        Matcher ratio = RATIO.matcher(raw);
        if (ratio.find()) {
            double denominator = number(ratio.group(2));
            return denominator <= 0 ? OptionalInt.empty() : clamp(number(ratio.group(1)) * 100 / denominator);
        }
        Matcher percent = PERCENT.matcher(raw);
        if (percent.find()) {
            return clamp(number(percent.group(1)));
        }
        Matcher plain = PLAIN_NUMBER.matcher(raw);
        if (plain.find() && number(plain.group(1)) <= 100) {
            return clamp(number(plain.group(1)));
        }
        return OptionalInt.empty();
    }

    /** Mức của 1 kênh; {@code null} nếu không nhắc được (đang chờ chấm, trống, không đọc được). */
    public static Level levelOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFC).toLowerCase(Locale.forLanguageTag("vi"));
        if (normalized.contains("chưa làm") || normalized.contains("chưa nộp") || normalized.contains("chưa thực hiện")
                || normalized.contains("không làm")) {
            return Level.NOT_DONE;
        }
        OptionalInt percent = percentOf(raw);
        if (percent.isEmpty()) {
            return null;
        }
        int p = percent.getAsInt();
        return p >= 80 ? Level.GOOD : p >= 50 ? Level.OK : Level.LOW;
    }

    public static String phrase(Level level) {
        return switch (level) {
            case GOOD -> "làm tốt";
            case OK -> "làm được, cần cẩn thận hơn";
            case LOW -> "cần cố gắng";
            case NOT_DONE -> "chưa hoàn thành";
        };
    }

    /** Trung bình % các kênh đọc được (bỏ kênh chưa làm/chờ chấm); rỗng nếu không có kênh nào. */
    public static OptionalInt averagePercent(List<String> rawValues) {
        List<Integer> values = new ArrayList<>();
        for (String raw : rawValues) {
            if (levelOf(raw) != Level.NOT_DONE) {
                percentOf(raw).ifPresent(values::add);
            }
        }
        return values.isEmpty() ? OptionalInt.empty()
                : OptionalInt.of((int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0)));
    }

    /**
     * Câu mô tả BẰNG LỜI (không có số) cho AI; {@code null} nếu không có gì nổi bật để nhắc.
     *
     * @param currentAverage  trung bình % điểm NHẬP TAY buổi này — so với {@code previousAverage} cùng nguồn (nhập tay)
     *                        để không so lệch nguồn (online vs offline).
     * @param previousAverage trung bình % điểm nhập tay ở nhận xét buổi trước; rỗng nếu không có dữ liệu.
     * @param trendPoints     chênh lệch tối thiểu (điểm %) coi là tăng/giảm rõ.
     */
    public static String describe(List<Channel> channels, OptionalInt currentAverage, OptionalInt previousAverage, int trendPoints) {
        Map<Level, List<String>> byLevel = new LinkedHashMap<>();
        for (Channel channel : channels) {
            Level level = levelOf(channel.rawValue());
            if (level != null && level != Level.OK) {
                byLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(channel.name());
            }
        }
        List<String> parts = new ArrayList<>();
        for (Level level : List.of(Level.GOOD, Level.LOW, Level.NOT_DONE)) {
            List<String> names = byLevel.get(level);
            if (names != null) {
                parts.add(phrase(level) + " (" + String.join(", ", names) + ")");
            }
        }
        if (currentAverage.isPresent() && previousAverage.isPresent()) {
            int diff = currentAverage.getAsInt() - previousAverage.getAsInt();
            if (diff >= trendPoints) {
                parts.add("tiến bộ rõ so với buổi trước");
            } else if (-diff >= trendPoints) {
                parts.add("giảm rõ so với buổi trước");
            }
        }
        return parts.isEmpty() ? null : "BTVN buổi trước: " + String.join("; ", parts) + ".";
    }

    private static double number(String text) {
        return Double.parseDouble(text.replace(',', '.'));
    }

    private static OptionalInt clamp(double value) {
        return OptionalInt.of((int) Math.round(Math.max(0, Math.min(100, value))));
    }
}
