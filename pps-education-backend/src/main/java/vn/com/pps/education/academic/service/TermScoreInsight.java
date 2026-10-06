package vn.com.pps.education.academic.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UC-76 bước 4 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05) — phân tích điểm Giữa kỳ/Cuối kỳ của
 * 1 học sinh và quy ra LỜI trước khi gửi AI. Thuần tính toán, không gọi AI, không chạm JPA: kết quả ổn định và test
 * được, AI chỉ nhận nhận định đã tính sẵn (không nhận con số nên không thể tự tính sai hay chép điểm vào nhận xét).
 *
 * <p>Thang điểm: kỹ năng thang số/% xếp mức theo % điểm tối đa; kỹ năng thang band (IELTS) KHÔNG xếp mức theo % (band
 * 6.5/9 ≈ 72% không có nghĩa là "trung bình"), chỉ dùng ngưỡng đạt và so sánh tương đối/tiến bộ theo band.</p>
 */
final class TermScoreInsight {

    static final String STRONG = "tốt";
    static final String WEAK = "cần cố gắng";
    static final String BELOW_PASS = "chưa đạt mức yêu cầu";
    static final String RELATIVE_STRONG = "nổi bật so với các kỹ năng khác của em";
    static final String RELATIVE_WEAK = "cần tập trung cải thiện so với các kỹ năng khác của em";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param strongPercent     từ mức này (% điểm tối đa) trở lên là "tốt" (UC-76 mặc định 85).
     * @param weakPercent       dưới mức này là "cần cố gắng" (mặc định 50).
     * @param relativeGapPoints lệch so với trung bình các kỹ năng của chính học sinh (điểm %) để coi là nổi bật/cần tập trung (mặc định 10).
     * @param trendPoints       chênh lệch Cuối kỳ − Giữa kỳ (điểm %) để coi là tiến bộ rõ/giảm (mặc định 10).
     * @param trendBand         như trendPoints nhưng cho thang band (mặc định 0.5 band).
     */
    record Settings(double strongPercent, double weakPercent, double relativeGapPoints, double trendPoints, double trendBand) {
    }

    /**
     * Điểm 1 thành phần. {@code key} dùng ghép Cuối kỳ với Giữa kỳ (mã kỹ năng, hoặc kỹ năng danh mục/tên khi mã OTHER).
     *
     * @param score null khi chưa nhập điểm (bỏ qua thành phần này).
     */
    record ComponentScore(String key, String name, BigDecimal score, BigDecimal maxScore, BigDecimal passThreshold,
                          boolean absent, boolean band) {

        boolean scored() {
            return !absent && score != null && maxScore != null && maxScore.signum() > 0;
        }

        double percent() {
            return score.multiply(HUNDRED).divide(maxScore, 4, RoundingMode.HALF_UP).doubleValue();
        }
    }

    /** Overall đã nhập ở 1 setup; {@code maxScore} suy từ thang của setup (10 / 100 / 9). */
    record OverallScore(BigDecimal score, BigDecimal maxScore, boolean band) {

        double percent() {
            return score.multiply(HUNDRED).divide(maxScore, 4, RoundingMode.HALF_UP).doubleValue();
        }
    }

    record SkillInsight(String name, List<String> assessment) {
    }

    /**
     * @param overall     nhận định chung (null khi không có Overall hoặc thang band).
     * @param trend       thay đổi so với Giữa kỳ (rỗng khi không phải Cuối kỳ hoặc không có thay đổi rõ).
     * @param absentParts tên các phần học sinh vắng thi.
     */
    record Insight(List<SkillInsight> skills, String overall, List<String> trend, List<String> absentParts) {

        boolean hasData() {
            return !skills.isEmpty() || overall != null || !trend.isEmpty();
        }

        /** Tóm tắt ngắn cho bản xem trước trên UI (không có con số). */
        String summary() {
            List<String> parts = new ArrayList<>();
            for (SkillInsight skill : skills) {
                parts.add(skill.name() + ": " + (skill.assessment().isEmpty() ? "bình thường" : String.join(", ", skill.assessment())));
            }
            if (overall != null) {
                parts.add("Overall: " + overall);
            }
            parts.addAll(trend);
            absentParts.forEach(part -> parts.add(part + ": vắng thi"));
            return String.join(" · ", parts);
        }
    }

    private TermScoreInsight() {
    }

    /**
     * @param current        điểm thành phần của setup đang soạn, theo thứ tự hiển thị.
     * @param currentOverall Overall của setup đang soạn (null nếu chưa nhập).
     * @param midTermByKey   điểm thành phần Giữa kỳ cùng kỳ học theo {@code key} — rỗng khi đang soạn Giữa kỳ.
     * @param midTermOverall Overall Giữa kỳ (null nếu không có / đang soạn Giữa kỳ).
     */
    static Insight analyze(List<ComponentScore> current, OverallScore currentOverall,
                           Map<String, ComponentScore> midTermByKey, OverallScore midTermOverall, Settings settings) {
        List<ComponentScore> scored = current.stream().filter(ComponentScore::scored).toList();
        double average = scored.stream().mapToDouble(ComponentScore::percent).average().orElse(0);
        List<SkillInsight> skills = new ArrayList<>();
        List<String> trend = new ArrayList<>();
        for (ComponentScore component : scored) {
            List<String> assessment = new ArrayList<>();
            double percent = component.percent();
            boolean belowPass = component.passThreshold() != null && component.score().compareTo(component.passThreshold()) < 0;
            if (belowPass) {
                assessment.add(BELOW_PASS);
            } else if (!component.band() && percent < settings.weakPercent()) {
                assessment.add(WEAK);
            } else if (!component.band() && percent >= settings.strongPercent()) {
                assessment.add(STRONG);
            }
            if (scored.size() >= 2) {
                if (percent >= average + settings.relativeGapPoints()) {
                    assessment.add(RELATIVE_STRONG);
                } else if (percent <= average - settings.relativeGapPoints()) {
                    assessment.add(RELATIVE_WEAK);
                }
            }
            skills.add(new SkillInsight(component.name(), assessment));

            ComponentScore previous = midTermByKey.get(component.key());
            if (previous != null && previous.scored() && previous.band() == component.band()) {
                String change = change(component.band() ? component.score().subtract(previous.score()).doubleValue()
                        : percent - previous.percent(), component.band(), settings);
                if (change != null) {
                    trend.add(component.name() + " " + change);
                }
            }
        }
        String overall = null;
        if (currentOverall != null && currentOverall.score() != null) {
            if (!currentOverall.band()) {
                double percent = currentOverall.percent();
                overall = percent >= settings.strongPercent() ? "kết quả chung tốt"
                        : percent < settings.weakPercent() ? "kết quả chung còn thấp, cần cố gắng nhiều hơn"
                        : "kết quả chung ở mức đạt yêu cầu";
            }
            if (midTermOverall != null && midTermOverall.score() != null && midTermOverall.band() == currentOverall.band()) {
                String change = change(currentOverall.band() ? currentOverall.score().subtract(midTermOverall.score()).doubleValue()
                        : currentOverall.percent() - midTermOverall.percent(), currentOverall.band(), settings);
                if (change != null) {
                    trend.add("kết quả chung " + change);
                }
            }
        }
        List<String> absentParts = current.stream().filter(ComponentScore::absent).map(ComponentScore::name).toList();
        return new Insight(skills, overall, trend, absentParts);
    }

    private static String change(double delta, boolean band, Settings settings) {
        double threshold = band ? settings.trendBand() : settings.trendPoints();
        if (delta >= threshold) {
            return "tiến bộ rõ so với Giữa kỳ";
        }
        if (delta <= -threshold) {
            return "giảm so với Giữa kỳ";
        }
        return null;
    }
}
