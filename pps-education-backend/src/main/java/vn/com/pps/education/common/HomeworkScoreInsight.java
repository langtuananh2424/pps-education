package vn.com.pps.education.common;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UC-74/75 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — quy đổi điểm BTVN buổi trước sang
 * LỜI để trợ lý AI viết nhận xét, KHÔNG đưa con số cho AI (nhận xét vẫn không được ghi điểm/%). Thuần tính
 * toán, test được.
 *
 * <p>Ngưỡng đã chốt với người dùng (sửa 2026-09-30): ≥ 85% "làm tốt"; 51–84% "làm được, cần cẩn thận hơn";
 * ≤ 50% "cần cố gắng"; "Chưa làm bài" → "chưa hoàn thành"; "Đang chờ chấm"/không đọc được → không nhắc. Chỉ
 * đưa cho AI các kỹ năng NỔI BẬT (làm tốt / cần cố gắng / chưa hoàn thành) hoặc khi điểm TĂNG/GIẢM RÕ so với
 * buổi trước — mức "làm được" không nổi bật nên không nhắc, tránh học sinh nào cũng có 1 câu BTVN giống khuôn.</p>
 *
 * <p>Bổ sung 2026-09-30 (đã xác nhận với người dùng): mô tả THEO TỪNG KỸ NĂNG (nghe/đọc/viết/ngữ pháp/từ vựng/
 * phản xạ nói) thay vì theo loại bài ("bài tập online") — để AI viết cụ thể "con cần luyện thêm kỹ năng nghe"
 * thay vì câu chung "con cần cố gắng hơn với bài tập về nhà".</p>
 */
public final class HomeworkScoreInsight {

    public enum Level { GOOD, OK, LOW, NOT_DONE }

    static final int GOOD_MIN_PERCENT = 85;
    /** Từ mức này trở xuống là "cần cố gắng" (tính cả đúng 50%). */
    static final int LOW_MAX_PERCENT = 50;

    /** Kỹ năng của kênh Reading/Writing (chỉ buổi GV Việt Nam có 2 kênh này). */
    public static final String SKILL_READING = "đọc";
    public static final String SKILL_WRITING = "viết";

    private static final Pattern RATIO = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*/\\s*(\\d+(?:[.,]\\d+)?)");
    private static final Pattern PERCENT = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*%");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)\\s*$");

    private HomeworkScoreInsight() {
    }

    /**
     * Điểm/nhãn BTVN của 1 kênh.
     *
     * @param skill    kỹ năng của kênh (VD "nghe") — xem {@link #mainChannelSkill}/{@link #videoChannelSkill}.
     * @param source   nguồn điểm để phân biệt khi 2 kênh cùng kỹ năng cho kết quả khác nhau (VD "bài online").
     * @param rawValue điểm/nhãn như trên bảng (VD "80%" / "Chưa làm bài" / "8/10").
     */
    public record Channel(String skill, String source, String rawValue) {
    }

    /**
     * Kỹ năng của kênh chính "BTVN buổi trước" (ô Offline + cột % tự động bên cạnh) — mirror nhãn kênh ở
     * {@code StudentCommentService#grammarChannelLabel}: buổi GVNN giao Bài nghe (skill LISTENING), buổi GV Việt Nam
     * giao Ngữ pháp (VOCAB_GRAMMAR).
     */
    public static String mainChannelSkill(boolean foreignSession) {
        return foreignSession ? "nghe" : "ngữ pháp";
    }

    /** Kỹ năng của kênh video ôn tập — buổi GVNN là "Clip phản xạ", buổi GV Việt Nam là "Từ vựng (TKN)". */
    public static String videoChannelSkill(boolean foreignSession) {
        return foreignSession ? "phản xạ nói" : "từ vựng";
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
        return p >= GOOD_MIN_PERCENT ? Level.GOOD : p > LOW_MAX_PERCENT ? Level.OK : Level.LOW;
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
     * Câu mô tả BẰNG LỜI (không có số) theo từng kỹ năng cho AI; {@code null} nếu không có gì nổi bật để nhắc.
     * VD "BTVN buổi trước theo kỹ năng: nghe — cần cố gắng; đọc — làm tốt." Hai kênh cùng kỹ năng cho mức khác
     * nhau thì ghi rõ nguồn: "đọc — làm tốt (bài online), chưa hoàn thành (bài trên giấy)".
     *
     * @param includeOk       {@code true} để ghi cả mức "làm được" (dữ liệu đối chiếu cho UC-75); {@code false}
     *                        cho UC-74 — chỉ kỹ năng nổi bật.
     * @param currentAverage  trung bình % điểm NHẬP TAY buổi này — so với {@code previousAverage} cùng nguồn (nhập tay)
     *                        để không so lệch nguồn (online vs offline).
     * @param previousAverage trung bình % điểm nhập tay ở nhận xét buổi trước; rỗng nếu không có dữ liệu.
     * @param trendPoints     chênh lệch tối thiểu (điểm %) coi là tăng/giảm rõ.
     */
    public static String describe(List<Channel> channels, boolean includeOk, OptionalInt currentAverage,
                                  OptionalInt previousAverage, int trendPoints) {
        Map<String, Map<Level, Set<String>>> bySkill = new LinkedHashMap<>();
        for (Channel channel : channels) {
            Level level = levelOf(channel.rawValue());
            if (level == null || (level == Level.OK && !includeOk)) {
                continue;
            }
            bySkill.computeIfAbsent(channel.skill(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(level, k -> new LinkedHashSet<>()).add(channel.source());
        }
        List<String> parts = new ArrayList<>();
        bySkill.forEach((skill, levels) -> {
            List<String> phrases = new ArrayList<>();
            for (Level level : List.of(Level.GOOD, Level.OK, Level.LOW, Level.NOT_DONE)) {
                Set<String> sources = levels.get(level);
                if (sources != null) {
                    phrases.add(levels.size() == 1 ? phrase(level) : phrase(level) + " (" + String.join(", ", sources) + ")");
                }
            }
            parts.add(skill + " — " + String.join(", ", phrases));
        });
        if (currentAverage.isPresent() && previousAverage.isPresent()) {
            int diff = currentAverage.getAsInt() - previousAverage.getAsInt();
            if (diff >= trendPoints) {
                parts.add("nhìn chung tiến bộ rõ so với buổi trước");
            } else if (-diff >= trendPoints) {
                parts.add("nhìn chung giảm rõ so với buổi trước");
            }
        }
        return parts.isEmpty() ? null : "BTVN buổi trước theo kỹ năng: " + String.join("; ", parts) + ".";
    }

    private static double number(String text) {
        return Double.parseDouble(text.replace(',', '.'));
    }

    private static OptionalInt clamp(double value) {
        return OptionalInt.of((int) Math.round(Math.max(0, Math.min(100, value))));
    }
}
