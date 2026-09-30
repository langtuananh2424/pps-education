package vn.com.pps.education.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * UC-74 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-30) — "đào sâu" thống kê BTVN nhiều buổi
 * cho trợ lý nhận xét: xu hướng theo kỹ năng, điểm yếu/mạnh cụ thể (dạng câu hỏi, độ khó, tiêu chí chấm
 * Writing/Speaking) và thói quen làm bài. Thuần tính toán, test được; trả LỜI không kèm chữ số (AI không được
 * ghi số vào nhận xét).
 *
 * <p>Quy tắc đã chốt:</p>
 * <ul>
 *   <li>Xu hướng: 3 lần BTVN gần nhất của 1 kỹ năng đều có điểm, tăng (giảm) liên tục và lần mới nhất chênh lần
 *       đầu ≥ {@link #TREND_MIN_POINTS} điểm %.</li>
 *   <li>Điểm yếu/mạnh cụ thể: cùng ngưỡng với {@link HomeworkScoreInsight} (≤ 50% yếu, ≥ 85% mạnh); chỉ nêu 1 điểm
 *       yếu nhất, hoặc 1 điểm mạnh nhất khi không có điểm yếu.</li>
 *   <li>Thói quen: làm lại ≥ 2 lượt và lượt sau cao hơn → khen; {@link #REGULAR_ROUNDS} lần BTVN liên tiếp làm đủ
 *       → khen; cùng 1 kỹ năng chưa làm 2 lần liền → nhắc nhẹ; nộp muộn ≥ 2 trong 3 lần gần nhất → nhắc nhẹ.</li>
 * </ul>
 * <p>Thứ tự ưu tiên (AI chỉ dùng tối đa 2 câu về BTVN): bỏ bài/nộp muộn → điểm yếu cụ thể → xu hướng → khen.</p>
 */
public final class HomeworkHistoryInsight {

    static final int TREND_ROUNDS = 3;
    static final int TREND_MIN_POINTS = 20;
    static final int REGULAR_ROUNDS = 4;
    static final int WEAK_MAX_PERCENT = HomeworkScoreInsight.LOW_MAX_PERCENT;
    static final int STRONG_MIN_PERCENT = HomeworkScoreInsight.GOOD_MIN_PERCENT;

    private HomeworkHistoryInsight() {
    }

    /**
     * Kết quả 1 kỹ năng trong 1 lần BTVN.
     *
     * @param percent % đọc được (trung bình các kênh cùng kỹ năng), {@code null} nếu chưa có (đang chờ chấm, chưa làm).
     * @param notDone có kênh nào của kỹ năng này "Chưa làm bài".
     */
    public record SkillResult(String skill, Integer percent, boolean notDone) {
    }

    /** 1 lần BTVN (bài giao ở 1 buổi, xem lại ở buổi sau) — {@code late}: có bài nộp muộn. */
    public record Round(List<SkillResult> skills, boolean late) {
    }

    /**
     * 1 điểm có thể nêu cụ thể (VD "dạng câu điền từ trong bài ngữ pháp", "tiêu chí Phát âm trong bài nói").
     *
     * @param praisable được dùng để khen (VD dạng câu hỏi chỉ khen khi học sinh làm ≥ 2 dạng, tránh khen trùng
     *                  với khen điểm cả bài).
     */
    public record Point(String label, int percent, boolean praisable) {
    }

    /**
     * @param rounds        các lần BTVN, MỚI NHẤT TRƯỚC (phần tử 0 = BTVN buổi trước), tối đa {@link #REGULAR_ROUNDS}.
     * @param retryImproved BTVN buổi trước có bài làm lại ≥ 2 lượt và lượt sau điểm cao hơn.
     * @param points        các điểm cụ thể của BTVN buổi trước.
     */
    public record Input(List<Round> rounds, boolean retryImproved, List<Point> points) {
    }

    /** Các ý BTVN bằng lời theo thứ tự ưu tiên; rỗng nếu không có gì đáng nhắc. */
    public static List<String> describe(Input input) {
        List<Round> rounds = input.rounds() == null ? List.of() : input.rounds();
        List<String> reminders = new ArrayList<>();
        List<String> trends = new ArrayList<>();
        List<String> praises = new ArrayList<>();

        if (rounds.size() >= 2) {
            for (String skill : notDoneSkills(rounds.get(0))) {
                if (notDoneSkills(rounds.get(1)).contains(skill)) {
                    reminders.add("chưa làm BTVN kỹ năng " + skill + " hai lần liền (nhắc nhẹ hoàn thành bài)");
                }
            }
        }
        long lateCount = rounds.stream().limit(TREND_ROUNDS).filter(Round::late).count();
        if (lateCount >= 2) {
            reminders.add("nộp BTVN muộn nhiều lần gần đây (nhắc nhẹ nộp đúng hạn)");
        }

        Optional<Point> weakest = input.points() == null ? Optional.empty() : input.points().stream()
                .filter(p -> p.percent() <= WEAK_MAX_PERCENT).min(Comparator.comparingInt(Point::percent));
        weakest.ifPresent(p -> reminders.add("điểm cần cải thiện cụ thể: " + p.label()));

        if (rounds.size() >= TREND_ROUNDS) {
            for (String skill : skillsInOrder(rounds)) {
                List<Integer> series = new ArrayList<>();
                for (int i = TREND_ROUNDS - 1; i >= 0; i--) {
                    series.add(percentOf(rounds.get(i), skill));
                }
                if (series.contains(null)) {
                    continue;
                }
                int first = series.get(0);
                int middle = series.get(1);
                int last = series.get(2);
                if (first < middle && middle < last && last - first >= TREND_MIN_POINTS) {
                    trends.add("kỹ năng " + skill + " tiến bộ đều qua các buổi gần đây");
                } else if (first > middle && middle > last && first - last >= TREND_MIN_POINTS) {
                    trends.add(0, "kỹ năng " + skill + " đi xuống qua các buổi gần đây");
                }
            }
        }

        if (input.retryImproved()) {
            praises.add("chăm làm lại bài để cải thiện điểm");
        }
        if (rounds.size() >= REGULAR_ROUNDS && rounds.stream().limit(REGULAR_ROUNDS).allMatch(HomeworkHistoryInsight::complete)) {
            praises.add("làm đủ BTVN đều đặn nhiều buổi liên tiếp");
        }
        if (weakest.isEmpty() && input.points() != null) {
            input.points().stream().filter(p -> p.praisable() && p.percent() >= STRONG_MIN_PERCENT)
                    .max(Comparator.comparingInt(Point::percent))
                    .ifPresent(p -> praises.add("làm tốt " + p.label()));
        }

        List<String> result = new ArrayList<>(reminders);
        result.addAll(trends);
        result.addAll(praises);
        return result;
    }

    private static Set<String> notDoneSkills(Round round) {
        Set<String> skills = new LinkedHashSet<>();
        round.skills().stream().filter(SkillResult::notDone).forEach(s -> skills.add(s.skill()));
        return skills;
    }

    private static Integer percentOf(Round round, String skill) {
        return round.skills().stream().filter(s -> s.skill().equals(skill) && s.percent() != null)
                .map(SkillResult::percent).findFirst().orElse(null);
    }

    private static List<String> skillsInOrder(List<Round> rounds) {
        Map<String, Boolean> skills = new LinkedHashMap<>();
        rounds.forEach(r -> r.skills().forEach(s -> skills.putIfAbsent(s.skill(), true)));
        return new ArrayList<>(skills.keySet());
    }

    /** 1 lần BTVN "làm đủ": có ít nhất 1 kỹ năng được giao và không kỹ năng nào "Chưa làm bài". */
    private static boolean complete(Round round) {
        return !round.skills().isEmpty() && round.skills().stream().noneMatch(SkillResult::notDone);
    }
}
