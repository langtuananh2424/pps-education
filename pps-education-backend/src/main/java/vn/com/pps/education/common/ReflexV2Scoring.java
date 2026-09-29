package vn.com.pps.education.common;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21 (cập nhật theo bản bàn giao 26/9) — tính
 * điểm TẤT ĐỊNH cho bộ tiêu chí Speaking v2 (Khối 6-9), mirror {@code computeScores}/{@code applyRedCap}/
 * {@code applyErrorCaps}/{@code locateHighlights}/{@code trimFeedback} trong {@code prompts.js} +
 * {@code grading.js} do người training bàn giao. AI CHỈ trả checkpoint 0/0,5/1 và trần % do cổng chặn —
 * quy đổi sang % và mọi trần đo được từ transcript làm ở đây, không để AI tự tính.
 *
 * KHÁC bản tham chiếu 1 điểm có chủ đích: bản JS tự điền 0,5 khi AI trả THIẾU checkpoint (âm thầm nới
 * điểm); ở đây trả thiếu/sai là kết quả hỏng → {@link IllegalStateException}, caller coi như "AI chấm
 * lỗi" để học sinh nộp lại (đúng nguyên tắc "mặc định mức thấp" của quy tắc chung §B.1).
 */
public final class ReflexV2Scoring {

    private ReflexV2Scoring() {
    }

    /** Từ 2 lỗi đỏ NGỮ PHÁP trở lên ({@link #countRed}) → tiêu chí Ngữ pháp không vượt mức này (quy tắc chung §C.2). */
    public static final int RED_CAP = 60;

    /** 100% nghĩa là gần như bản ngữ — học sinh THCS Việt Nam không có tiêu chí bài NÓI nào vượt mức này (phòng đào tạo 24/9). */
    public static final int NON_NATIVE_CAP = 90;

    /**
     * Trần Phát âm khi transcript không còn bằng chứng (lượt phiên âm ghi toàn chính tả chuẩn). Đánh đổi đã
     * biết: học sinh Part 2 phát âm thật sự tốt cũng bị chặn ở đây; muốn bỏ trần thì đặt = 100.
     */
    public static final int NO_EVIDENCE_P_CAP = 80;

    public record CriterionScore(String code, int percent, boolean cappedByRedErrors, Integer floorPercent, List<String> caps) {

        public CriterionScore(String code, int percent, boolean cappedByRedErrors) {
            this(code, percent, cappedByRedErrors, null, List.of());
        }

        private CriterionScore with(int newPercent, String note) {
            List<String> all = new ArrayList<>(caps);
            all.add(note);
            return new CriterionScore(code, newPercent, cappedByRedErrors, floorPercent, all);
        }

        private CriterionScore cappedTo(int limit, String note) {
            return percent > limit ? with(limit, note) : this;
        }
    }

    public record ScoreSet(List<CriterionScore> criteria, int finalPercent, List<String> gates) {
    }

    public record Highlight(int start, int end, String level, String label, String tag) {
    }

    /** Độ rộng từ vựng: số từ nội dung KHÁC NHAU trên mỗi giây đề cho. */
    public record LexicalEvidence(int distinct, int total, double density, int ceil) {
    }

    /** Trôi chảy: tỷ lệ từ đệm + tự sửa, và khoảng dừng dài nhất. */
    public record FluencyEvidence(int fillers, int words, double ratio, double pauseSec, int ceil) {
    }

    /** Cổng độ dài bài Part 2 — hệ thống tự đo, model KHÔNG được tự áp. */
    public record LengthEvidence(int words, double spokenSec, boolean enough, int fcCeil, int lrCeil) {
    }

    /** So transcript với bài viết Bước 1: chỗ nào lệch khỏi từ đã viết là chỗ đọc lệch. */
    public record Readback(int total, int count, double ratio, List<String> words) {
    }

    /** Bằng chứng đo được từ transcript; bài VIẾT không có bằng chứng nào trong số này ({@link #NONE}). */
    public record ErrorEvidence(LexicalEvidence lexical, FluencyEvidence fluency, LengthEvidence length, Readback readback) {
        public static final ErrorEvidence NONE = new ErrorEvidence(null, null, null, null);
    }

    /** Trung bình cộng các % tiêu chí, làm tròn XUỐNG bội số 5 (+1e-9 chống sai số dấu phẩy động, như bản JS). */
    public static int floorTo5(double average) {
        return (int) (Math.floor(average / 5 + 1e-9) * 5);
    }

    private static double snapHalf(double n) {
        return Math.floor(n * 2 + 1e-9) / 2;
    }

    /**
     * @param task  dạng bài — quyết định những mã cổng chặn hợp lệ (Khối 8-9 IELTS chỉ có C1/C2, xem {@link ReflexV2Task.GateScheme}).
     * @param codes các mã tiêu chí AI phải chấm ở lượt này, đúng thứ tự (VD Bước 1 Khối 7 IELTS: LR, GRA).
     * @param data  JSON AI trả về (đã parse) theo schema của {@code ReflexV2Prompts}.
     * @throws IllegalStateException nếu AI thiếu tiêu chí hoặc trả không đủ 5 checkpoint hợp lệ.
     */
    public static ScoreSet computeScores(ReflexV2Task task, List<String> codes, JsonNode data) {
        boolean insufficient = data.path("insufficient_data").asBoolean(false);
        List<CriterionScore> criteria = new ArrayList<>();
        for (String code : codes) {
            int percent = 0;
            if (!insufficient) {
                JsonNode entry = findCriterion(data.path("criteria"), code);
                if (entry == null) {
                    throw new IllegalStateException("AI không trả tiêu chí " + code);
                }
                JsonNode checkpoints = entry.path("checkpoints");
                if (!checkpoints.isArray() || checkpoints.size() != 5) {
                    throw new IllegalStateException("AI trả sai số checkpoint của " + code + " (cần đúng 5)");
                }
                double sum = 0;
                for (JsonNode cp : checkpoints) {
                    if (!cp.isNumber()) {
                        throw new IllegalStateException("Checkpoint của " + code + " không phải số");
                    }
                    double v = cp.asDouble();
                    sum += v >= 1 ? 1 : (v >= 0.5 ? 0.5 : 0);
                }
                double total = snapHalf(Math.max(0, Math.min(5, sum)));
                JsonNode capNode = entry.path("cap_percent");
                double cap = capNode.isNumber() ? Math.max(0, Math.min(100, capNode.asDouble())) : 100;
                percent = (int) Math.min(total * 20, cap);
            }
            criteria.add(new CriterionScore(code, percent, false));
        }
        return new ScoreSet(criteria, average(criteria), gateCodes(task, data));
    }

    /** Trần 60% cho tiêu chí Ngữ pháp khi bài có từ 2 lỗi đỏ ngữ pháp (Bước 1, đếm bằng {@link #countRed}). Chỉ giảm, không bao giờ tăng. */
    public static ScoreSet applyRedCap(String grammarCode, ScoreSet scored, int redCount) {
        if (redCount < 2) {
            return scored;
        }
        List<CriterionScore> criteria = new ArrayList<>();
        for (CriterionScore c : scored.criteria()) {
            if (c.code().equals(grammarCode) && c.percent() > RED_CAP) {
                criteria.add(new CriterionScore(c.code(), RED_CAP, true, c.floorPercent(), c.caps()));
            } else {
                criteria.add(c);
            }
        }
        return new ScoreSet(criteria, average(criteria), scored.gates());
    }

    public static int average(List<CriterionScore> criteria) {
        double avg = criteria.stream().mapToInt(CriterionScore::percent).average().orElse(0);
        return floorTo5(avg);
    }

    private static JsonNode findCriterion(JsonNode array, String code) {
        if (array.isArray()) {
            for (JsonNode c : array) {
                if (code.equals(c.path("code").asText())) {
                    return c;
                }
            }
        }
        return null;
    }

    /** Bản JS tham chiếu cũng lọc mã cổng theo {@code task.gates} — mã không thuộc dạng bài (VD C3 ở Khối 8-9 IELTS) bị bỏ. */
    private static List<String> gateCodes(ReflexV2Task task, JsonNode data) {
        Set<String> valid = task.gateScheme() == ReflexV2Task.GateScheme.V3 ? Set.of("C1", "C2") : Set.of("C1", "C2", "C3");
        Set<String> gates = new LinkedHashSet<>();
        for (JsonNode g : data.path("gates_triggered")) {
            if (valid.contains(g.asText())) {
                gates.add(g.asText());
            }
        }
        return new ArrayList<>(gates);
    }

    // ===================== Chấm lại Ngữ pháp từ transcript + các trần tất định =====================

    /**
     * Từ 23/9 điểm Ngữ pháp KHÔNG còn khoá từ Bước 1: chấm lại từ transcript, trần 60% nếu bài NÓI có ≥2 lỗi
     * đỏ, rồi áp SÀN = nửa điểm Bước 1 (làm tròn xuống bội 5). Không áp sàn khi không nói được gì — khi đó
     * điểm phải là thật. Sàn được ghi vào {@code floorPercent} để {@link #applyGrammarFloor} áp lại sau các
     * trần theo lỗi (tránh một lỗi bị phạt hai lần).
     */
    public static CriterionScore regradeGrammar(CriterionScore graded, int spokenRedCount, int step1Percent, boolean noSpeech) {
        boolean capped = spokenRedCount >= 2 && graded.percent() > RED_CAP;
        int percent = capped ? RED_CAP : graded.percent();
        Integer floor = noSpeech ? null : (int) (Math.floor(step1Percent / 2.0 / 5) * 5);
        if (floor != null && percent < floor) {
            percent = floor;
        }
        return new CriterionScore(graded.code(), percent, capped, floor, graded.caps());
    }

    /** Đưa Ngữ pháp về lại mức sàn nếu trần theo lỗi đã tô đạp xuyên qua nó (đo 26/9: 5 vàng + 2 đỏ cho trần 10% → điểm ra 0%). */
    public static List<CriterionScore> applyGrammarFloor(String grammarCode, List<CriterionScore> criteria) {
        List<CriterionScore> out = new ArrayList<>();
        for (CriterionScore c : criteria) {
            if (c.code().equals(grammarCode) && c.floorPercent() != null && c.percent() < c.floorPercent()) {
                out.add(c.with(c.floorPercent(), "sàn = nửa điểm Bước 1"));
            } else {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Mirror {@code applyErrorCaps} của {@code grading.js}: một tiêu chí KHÔNG thể đạt 100% khi chính nó còn
     * lỗi (mỗi lỗi nhẹ −10%, mỗi lỗi nặng −20%), cộng các trần đo được từ transcript. Là TRẦN chứ không phải
     * trừ chồng lên checkpoint: điểm cuối = min(điểm checkpoint, trần). Riêng dải Phát âm theo bài viết có
     * thể NÂNG điểm lên tới sàn của dải.
     *
     * Dùng cho cả bài VIẾT (bằng chứng = {@link ErrorEvidence#NONE}) lẫn bài NÓI.
     */
    public static List<CriterionScore> applyErrorCaps(ReflexV2Task task, List<CriterionScore> criteria,
                                                      List<Highlight> highlights, ErrorEvidence ev) {
        String g = task.grammarCode();
        boolean hasLR = task.criteria().contains("LR");
        Map<String, int[]> counts = new HashMap<>();
        for (Highlight h : highlights) {
            if (h.level().equals("green")) {
                continue;
            }
            String bucket = ReflexV2Tags.PRON_TAGS.contains(h.tag()) ? "P"
                    : (hasLR && ReflexV2Tags.VOCAB_TAGS.contains(h.tag()) ? "LR" : g);
            counts.computeIfAbsent(bucket, k -> new int[2])[h.level().equals("red") ? 1 : 0]++;
        }
        Integer gPercent = criteria.stream().filter(c -> c.code().equals(g)).map(CriterionScore::percent).findFirst().orElse(null);
        boolean fluencyAppliesToP = !task.criteria().contains("FC") && !task.criteria().contains("DM");

        List<CriterionScore> out = new ArrayList<>();
        for (CriterionScore original : criteria) {
            CriterionScore c = original;
            String code = c.code();
            boolean discourse = code.equals("DM") || code.equals("FC");
            // Bài sai ngữ pháp nặng thì người nghe không theo được mạch ý: DM/FC không vượt quá ngữ pháp + 40.
            if (discourse && gPercent != null) {
                c = c.cappedTo(gPercent + 40, "Ngữ pháp + 40");
            }
            if (code.equals("P") || code.equals("LR") || discourse) {
                c = c.cappedTo(NON_NATIVE_CAP, "trần 90% (không bản ngữ)");
            }
            if (code.equals("LR") && ev.lexical() != null) {
                c = c.cappedTo(ev.lexical().ceil(), "độ rộng từ vựng " + ev.lexical().density() + " từ/giây");
            }
            if (ev.length() != null && !ev.length().enough()) {
                if (code.equals("FC")) {
                    c = c.cappedTo(ev.length().fcCeil(), "chưa đủ độ dài Part 2");
                } else if (code.equals("LR")) {
                    c = c.cappedTo(ev.length().lrCeil(), "chưa đủ độ dài Part 2");
                }
            }
            if (code.equals("P") && ev.readback() != null) {
                int[] band = pronBandFor(ev.readback().ratio());
                if (c.percent() > band[1]) {
                    c = c.with(band[1], "trần dải đọc lệch " + ev.readback().count() + "/" + ev.readback().total());
                } else if (c.percent() < band[0]) {
                    c = c.with(band[0], "sàn dải đọc lệch " + ev.readback().count() + "/" + ev.readback().total());
                }
            }
            if (code.equals("P") && c.percent() > NO_EVIDENCE_P_CAP) {
                Readback rb = ev.readback();
                boolean weak = rb == null || (rb.total() >= 30 && rb.count() <= 1);
                if (weak) {
                    c = c.with(NO_EVIDENCE_P_CAP, "không có bằng chứng phát âm");
                }
            }
            if ((discourse || (code.equals("P") && fluencyAppliesToP)) && ev.fluency() != null) {
                c = c.cappedTo(ev.fluency().ceil(), "từ đệm/khoảng dừng");
            }
            int[] n = counts.get(code);
            if (n != null) {
                c = c.cappedTo(Math.max(0, 100 - 10 * n[0] - 20 * n[1]), "lỗi đã tô (" + n[0] + " nhẹ, " + n[1] + " nặng)");
            }
            out.add(c);
        }
        return out;
    }

    /** Dải Phát âm theo TỶ LỆ từ đọc lệch (không theo số đếm tuyệt đối): [sàn, trần]. */
    static int[] pronBandFor(double ratio) {
        if (ratio < 0.12) {
            return new int[]{85, 100};
        }
        if (ratio < 0.25) {
            return new int[]{75, 90};
        }
        if (ratio < 0.40) {
            return new int[]{60, 80};
        }
        return new int[]{40, 60};
    }

    // ---------- Bằng chứng đo tất định từ transcript ----------

    private static final Set<String> FUNCTION_WORDS = new HashSet<>(Arrays.asList((
            "a an the and or but so because if when while that this these those there here "
                    + "i you he she it we they me him her us them my your his its our their "
                    + "is am are was were be been being do does did done have has had having "
                    + "will would can could shall should may might must to of in on at by for with from into about as "
                    + "not no yes very too also then than very more most much many some any all every "
                    + "um uh er ah oh hmm one two three").split(" ")));

    /** Lookaround thay cho \b vì "à" đứng giữa hai dấu cách không tạo ranh giới ASCII (từ đệm tiếng Việt từng bị bỏ sót). */
    private static final Pattern FILLER = Pattern.compile(
            "(?<![a-zà-ỹ])(u+m+|u+h+|e+r+|a+h+|hm+|ờ+|à+|ừ+|na+h?)(?![a-zà-ỹ])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PAUSE_MARK = Pattern.compile("\\(\\.\\.\\.\\d+s\\)");
    private static final Pattern NON_CONTENT_SPLIT = Pattern.compile("[^a-zà-ỹ']+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static List<String> contentWords(String transcript) {
        String lower = PAUSE_MARK.matcher(transcript == null ? "" : transcript.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return Arrays.stream(NON_CONTENT_SPLIT.split(lower)).filter(w -> w.length() >= 2 && !FUNCTION_WORDS.contains(w)).toList();
    }

    /** Bài quá ngắn (<8 từ nội dung) trả {@code null}: đã có cổng độ dài của rubric lo. */
    public static LexicalEvidence lexicalCeiling(String transcript, int seconds) {
        List<String> words = contentWords(transcript);
        if (seconds <= 0 || words.size() < 8) {
            return null;
        }
        int distinct = (int) words.stream().distinct().count();
        double density = distinct / (double) seconds;
        int ceil = density < 0.40 ? 60 : density < 0.60 ? 80 : density < 0.80 ? 90 : 100;
        return new LexicalEvidence(distinct, words.size(), Math.round(density * 100) / 100.0, ceil);
    }

    /** Cùng một từ lặp lại trong vòng 3 tiếng = nói vấp hoặc tự sửa ("I I go", "because I à because"). */
    private static int selfRepairs(List<String> tokens) {
        int n = 0;
        for (int i = 0; i < tokens.size(); i++) {
            for (int j = i + 1; j <= Math.min(i + 3, tokens.size() - 1); j++) {
                if (!tokens.get(i).isEmpty() && tokens.get(i).equals(tokens.get(j))) {
                    n++;
                    i = j;
                    break;
                }
            }
        }
        return n;
    }

    public static FluencyEvidence fluencyCeiling(String transcript, double longestPauseSec) {
        List<String> all = Arrays.stream(WHITESPACE.split(transcript == null ? "" : transcript)).filter(s -> !s.isEmpty()).toList();
        if (all.size() < 8) {
            return null;
        }
        List<String> norm = all.stream().map(w -> w.toLowerCase(Locale.ROOT).replaceAll("[^a-zà-ỹ']", "")).toList();
        Matcher m = FILLER.matcher(transcript);
        int fillers = 0;
        while (m.find()) {
            fillers++;
        }
        fillers += selfRepairs(norm);
        double ratio = fillers / (double) all.size();
        int ceil = ratio >= 0.15 ? 50 : ratio >= 0.08 ? 70 : ratio >= 0.04 ? 85 : 100;
        if (longestPauseSec >= 5) {
            ceil = Math.min(ceil, 50);
        } else if (longestPauseSec >= 3) {
            ceil = Math.min(ceil, 70);
        }
        return new FluencyEvidence(fillers, all.size(), Math.round(ratio * 100) / 100.0, longestPauseSec, ceil);
    }

    /** Đủ ý cho 4 gợi ý của đề Part 2 (từ tiếng Anh) — Khối 8-9. */
    public static final int PART2_MIN_WORDS = 60;
    /** Giây nói thật — mức trung bình học sinh đạt được — Khối 8-9. */
    public static final double PART2_MIN_SPOKEN_SEC = 45;
    /**
     * Khối 7 (Part 2 tối đa 60 giây, rubric v3 28/9): đòi 45 giây là đòi 75% thời lượng trong khi Khối 8 chỉ 50%, nên
     * mã tham chiếu đặt riêng ≥30 giây hoặc ≥40 từ.
     */
    public static final int PART2_MIN_WORDS_GRADE_7 = 40;
    public static final double PART2_MIN_SPOKEN_SEC_GRADE_7 = 30;

    /** Chỉ áp cho dạng PART2; các dạng khác trả {@code null}. {@code spokenSec} = thời gian nói thật đo từ tín hiệu (0 nếu không đo được). */
    public static LengthEvidence lengthGate(ReflexV2Task task, String transcript, double spokenSec) {
        if (!"PART2".equals(task.rubricFormat())) {
            return null;
        }
        int words = (int) Arrays.stream(WHITESPACE.split(transcript == null ? "" : transcript))
                .filter(w -> !w.isEmpty() && w.chars().anyMatch(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) && w.charAt(0) != '(')
                .count();
        boolean grade7 = task.grade() == 7;
        int minWords = grade7 ? PART2_MIN_WORDS_GRADE_7 : PART2_MIN_WORDS;
        double minSpokenSec = grade7 ? PART2_MIN_SPOKEN_SEC_GRADE_7 : PART2_MIN_SPOKEN_SEC;
        boolean enough = words >= minWords || spokenSec >= minSpokenSec;
        return new LengthEvidence(words, Math.round(spokenSec * 10) / 10.0, enough, enough ? 100 : 60, enough ? 100 : 80);
    }

    // ---------- Bằng chứng phát âm: so transcript với bài viết Bước 1 ----------

    private static final Set<String> SPOKEN_FILLERS = Set.of("um", "uh", "ah", "er", "hm", "a", "o");

    private static String normWord(String w) {
        return w.toLowerCase(Locale.ROOT).replaceAll("[^a-z']", "");
    }

    /** Bộ khung phụ âm: phát âm tiếng Việt làm rụng/đổi phụ âm cuối nên so theo khung, không so chính tả. */
    private static String skeleton(String w) {
        return w.replaceAll("[aeiouy']", "");
    }

    private static int editDistance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
        }
        return d[a.length()][b.length()];
    }

    /**
     * Học sinh đọc lại chính bài mình viết (cổng độ khớp nội dung đã bảo đảm), nên bài viết là vốn từ kỳ
     * vọng của đúng bài đó. Tất định hoàn toàn — không hỏi model. Từ học sinh nói thêm ngoài bài viết không
     * bị tính lỗi. Trả {@code null} nếu bài viết hoặc transcript dưới 4 từ.
     */
    public static Readback writtenVsSpoken(String writtenText, String transcript) {
        List<String> written = Arrays.stream(WHITESPACE.split(writtenText == null ? "" : writtenText))
                .map(ReflexV2Scoring::normWord).filter(w -> w.length() >= 2).toList();
        List<String> spoken = Arrays.stream(WHITESPACE.split(transcript == null ? "" : transcript))
                .map(ReflexV2Scoring::normWord).filter(w -> w.length() >= 2 && !SPOKEN_FILLERS.contains(w)).toList();
        if (written.size() < 4 || spoken.size() < 4) {
            return null;
        }
        boolean[] used = new boolean[written.size()];
        // Lượt 1: từ đọc đúng nguyên văn tiêu thụ từ đã viết, không tính lỗi.
        for (String w : spoken) {
            for (int k = 0; k < written.size(); k++) {
                if (!used[k] && written.get(k).equals(w)) {
                    used[k] = true;
                    break;
                }
            }
        }
        // Lượt 2: từ còn lại tìm từ đã viết chưa bị tiêu thụ; mỗi từ đã viết chỉ khớp MỘT lần.
        List<String> deviant = new ArrayList<>();
        for (String w : spoken) {
            if (written.contains(w)) {
                continue;
            }
            int best = -1;
            int bestScore = Integer.MAX_VALUE;
            for (int k = 0; k < written.size(); k++) {
                String r = written.get(k);
                if (used[k] || r.charAt(0) != w.charAt(0) || Math.abs(r.length() - w.length()) > 3) {
                    continue;
                }
                int score = editDistance(skeleton(w), skeleton(r)) * 2 + editDistance(w, r);
                if (score < bestScore) {
                    bestScore = score;
                    best = k;
                }
            }
            if (best >= 0 && editDistance(skeleton(w), skeleton(written.get(best))) <= 2) {
                deviant.add(written.get(best) + "→" + w);
                used[best] = true;
            }
        }
        return new Readback(spoken.size(), deviant.size(), deviant.size() / (double) spoken.size(), deviant);
    }

    // ---------- Nói giống bài viết → giữ điểm Ngữ pháp Bước 1 (cách B, 2026-09-29) ----------

    /**
     * Tỷ lệ khớp tối thiểu {@code 2·khớp / (số từ viết + số từ nói)} để coi là nói lại đúng bài đã viết — căn cứ
     * DUY NHẤT (người dùng chốt 2026-09-29). Bài rất ngắn nghe nhầm 1 từ có thể rơi dưới ngưỡng và bị chấm lại.
     */
    public static final double SAME_AS_WRITTEN_MIN_RATIO = 0.85;

    private static final Set<String> MATCH_FILLERS = Set.of("um", "uh", "ah", "er", "erm", "hm", "mm");
    private static final Pattern PAUSE_TOKEN = Pattern.compile("\\(\\.\\.\\.\\d+s\\)");

    /**
     * @param matched số từ khớp theo thứ tự (dãy con chung dài nhất).
     * @param diff    số từ lệch = từ viết không được nói + từ nói không có trong bài viết.
     * @param same    nói lại đúng bài đã viết → giữ nguyên điểm Ngữ pháp Bước 1.
     */
    public record SpokenMatch(int writtenWords, int spokenWords, int matched, int diff, double ratio, boolean same) {
    }

    private static List<String> matchTokens(String text, boolean spoken) {
        List<String> out = new ArrayList<>();
        for (String raw : WHITESPACE.split(PAUSE_TOKEN.matcher(text == null ? "" : text).replaceAll(" "))) {
            String w = normWord(raw).replace("'", "");
            if (w.isEmpty() || (spoken && MATCH_FILLERS.contains(w))) {
                continue;
            }
            // Lặp từ khi nói ("I I go") là ngập ngừng, không phải câu khác.
            if (spoken && !out.isEmpty() && out.get(out.size() - 1).equals(w)) {
                continue;
            }
            out.add(w);
        }
        return out;
    }

    /**
     * Cùng một từ theo nghĩa "nói lại bài viết": trùng nguyên văn, hoặc — với từ từ 3 chữ cái — cùng chữ đầu và
     * bộ khung phụ âm lệch ≤2 (như {@link #writtenVsSpoken}). Nhờ vậy từ phát âm lệch / mất âm cuối
     * ({@code fren}≈{@code friends}, {@code play}≈{@code played}) vẫn là "giống": đó là lỗi Phát âm, đã trừ ở P.
     * Từ ngắn (a, is, are, go) phải trùng nguyên văn — thêm/bớt/đổi những từ này mới là đổi câu.
     */
    private static boolean sameWord(String w, String s) {
        if (w.equals(s)) {
            return true;
        }
        return w.length() >= 3 && s.length() >= 3 && w.charAt(0) == s.charAt(0)
                && Math.abs(w.length() - s.length()) <= 3 && editDistance(skeleton(w), skeleton(s)) <= 2;
    }

    /**
     * Cách B (đã xác nhận với người dùng 2026-09-29): học sinh nói lại ĐÚNG câu đã viết thì điểm Ngữ pháp giữ
     * nguyên điểm Bước 1 — AI chấm lại cùng một câu không được ra điểm khác. Chỉ khi nói khác bài viết (sửa lỗi,
     * nói thêm, bỏ bớt) mới dùng điểm chấm lại từ transcript. Tất định hoàn toàn, không hỏi model.
     *
     * So theo thứ tự từ (dãy con chung dài nhất), tính cả từ chức năng vì thay đổi ngữ pháp nằm chủ yếu ở đó.
     * Chừa biên cho lượt phiên âm nghe nhầm vài từ bằng {@link #SAME_AS_WRITTEN_MIN_RATIO}. Trả {@code null} khi
     * thiếu bài viết hoặc transcript rỗng.
     */
    public static SpokenMatch sameAsWritten(String writtenText, String transcript) {
        List<String> written = matchTokens(writtenText, false);
        List<String> spoken = matchTokens(transcript, true);
        if (written.isEmpty() || spoken.isEmpty()) {
            return null;
        }
        int[][] lcs = new int[written.size() + 1][spoken.size() + 1];
        for (int i = 1; i <= written.size(); i++) {
            for (int j = 1; j <= spoken.size(); j++) {
                lcs[i][j] = sameWord(written.get(i - 1), spoken.get(j - 1))
                        ? lcs[i - 1][j - 1] + 1 : Math.max(lcs[i - 1][j], lcs[i][j - 1]);
            }
        }
        int matched = lcs[written.size()][spoken.size()];
        int total = written.size() + spoken.size();
        int diff = total - 2 * matched;
        double ratio = 2.0 * matched / total;
        boolean same = ratio >= SAME_AS_WRITTEN_MIN_RATIO;
        return new SpokenMatch(written.size(), spoken.size(), matched, diff, Math.round(ratio * 100) / 100.0, same);
    }

    /** Các đoạn bị tô đỏ NGỮ PHÁP (đúng tập {@link #countRed} đếm), trích nguyên văn — chỗ giáo viên cần soát. */
    public static List<String> grammarRedQuotes(String source, List<Highlight> highlights) {
        List<String> quotes = new ArrayList<>();
        for (Highlight h : highlights) {
            if (h.level().equals("red") && ReflexV2Tags.GRAMMAR_SEVERE_TAGS.contains(h.tag())) {
                quotes.add(source.substring(h.start(), h.end()));
            }
        }
        return quotes;
    }

    /** Điểm Ngữ pháp Bước 1 giữ nguyên cho bài nói (cách B) — không trần, không sàn, không trần theo lỗi đã tô. */
    public static CriterionScore keptFromStep1(String grammarCode, int step1Percent) {
        return new CriterionScore(grammarCode, step1Percent, false, null, List.of("giữ điểm Bước 1 (nói giống bài viết)"));
    }

    /** Đặt lại tiêu chí Ngữ pháp về điểm Bước 1 sau các trần (cách B) — các tiêu chí khác giữ nguyên. */
    public static List<CriterionScore> keepGrammarFromStep1(String grammarCode, List<CriterionScore> criteria, int step1Percent) {
        List<CriterionScore> out = new ArrayList<>();
        for (CriterionScore c : criteria) {
            out.add(c.code().equals(grammarCode) ? keptFromStep1(grammarCode, step1Percent) : c);
        }
        return out;
    }

    // ===================== Tô màu =====================

    private static boolean isWordChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= 'À' && c <= 'ỹ') || (c >= '0' && c <= '9') || c == '\'';
    }

    /** Tìm đoạn trích như một cụm từ trọn vẹn: "in" không được khớp vào giữa "planning". */
    private static int findWhole(String source, String quote, int from) {
        boolean needStart = isWordChar(quote.charAt(0));
        boolean needEnd = isWordChar(quote.charAt(quote.length() - 1));
        for (int idx = source.indexOf(quote, from); idx != -1; idx = source.indexOf(quote, idx + 1)) {
            if (needStart && idx > 0 && isWordChar(source.charAt(idx - 1))) {
                continue;
            }
            int after = idx + quote.length();
            if (needEnd && after < source.length() && isWordChar(source.charAt(after))) {
                continue;
            }
            return idx;
        }
        return -1;
    }

    /**
     * Tìm vị trí từng đoạn trích trong văn bản gốc; bỏ đoạn không tìm thấy hoặc chồng lấn. Mức độ
     * (đỏ/vàng/xanh) do LOẠI lỗi (tag) quyết định, KHÔNG dùng {@code level} AI tự điền.
     */
    public static List<Highlight> locateHighlights(String source, JsonNode highlights) {
        return locateHighlights(source, highlights, 0);
    }

    /** @param grade khối của dạng bài — quyết định mức đỏ/vàng của tag phụ thuộc khối ({@link ReflexV2Tags#isSevere}). */
    public static List<Highlight> locateHighlights(String source, JsonNode highlights, int grade) {
        List<Highlight> found = new ArrayList<>();
        if (source == null || !highlights.isArray()) {
            return found;
        }
        for (JsonNode h : highlights) {
            String quote = h.path("quote").asText("");
            if (quote.isEmpty()) {
                continue;
            }
            String tag = h.path("tag").asText("");
            String errorLabel = ReflexV2Tags.ERROR_TAGS.get(tag);
            String strengthLabel = ReflexV2Tags.STRENGTH_TAGS.get(tag);
            if (errorLabel == null && strengthLabel == null) {
                continue;
            }
            String level = strengthLabel != null ? "green" : (ReflexV2Tags.isSevere(tag, grade) ? "red" : "yellow");
            int occurrence = Math.max(1, h.path("occurrence").asInt(1));
            int idx = -1;
            int from = 0;
            for (int i = 0; i < occurrence; i++) {
                idx = findWhole(source, quote, from);
                if (idx == -1) {
                    break;
                }
                from = idx + quote.length();
            }
            if (idx == -1) {
                idx = findWhole(source, quote, 0);
            }
            if (idx == -1) {
                continue;
            }
            found.add(new Highlight(idx, idx + quote.length(), level, errorLabel != null ? errorLabel : strengthLabel, tag));
        }
        found.sort(Comparator.comparingInt(Highlight::start).thenComparing(Comparator.comparingInt(Highlight::end).reversed()));
        List<Highlight> result = new ArrayList<>();
        int lastEnd = -1;
        for (Highlight f : found) {
            if (f.start() < lastEnd) {
                continue;
            }
            result.add(f);
            lastEnd = f.end();
        }
        return result;
    }

    /**
     * Số lỗi đỏ NGỮ PHÁP ({@link ReflexV2Tags#GRAMMAR_SEVERE_TAGS}) — căn cứ DUY NHẤT của trần 60% tiêu chí Ngữ pháp,
     * ở cả bài viết lẫn bài nói (2026-09-29). Lỗi đỏ loại khác không đếm ở đây.
     */
    public static int countRed(List<Highlight> highlights) {
        return (int) highlights.stream()
                .filter(h -> h.level().equals("red") && ReflexV2Tags.GRAMMAR_SEVERE_TAGS.contains(h.tag()))
                .count();
    }

    /**
     * Chuyển highlight sang markup {@code {{err}}...{{/err}}} mà FE hiện tại đã render (V178/V181) —
     * CHỈ đỏ/vàng (lỗi) được bọc; điểm mạnh (xanh) bỏ qua. FE chưa phân biệt đỏ/vàng nên mọi lỗi cùng 1
     * kiểu bôi (đợt sau có thể nâng FE dùng đúng mức độ).
     */
    public static String toErrMarkup(String source, List<Highlight> highlights) {
        if (source == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (Highlight h : highlights) {
            if (h.level().equals("green")) {
                continue;
            }
            out.append(source, cursor, h.start());
            out.append("{{err}}").append(source, h.start(), h.end()).append("{{/err}}");
            cursor = h.end();
        }
        out.append(source.substring(cursor));
        return out.toString();
    }

    private static final Pattern WORD = Pattern.compile("[A-Za-zÀ-ỹ0-9']+");

    public static int wordCount(String s) {
        if (s == null) {
            return 0;
        }
        Matcher m = WORD.matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static final Pattern WORST_ERROR = Pattern.compile("\\s*Lỗi nặng nhất\\s*:[^.!?]*[.!?]?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NO_ERROR = Pattern.compile(
            "\\s*Lỗi nặng nhất\\s*:\\s*(không có|chưa có|không phát hiện|không mắc)[^.!?]*[.!?]?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Nhận xét bị giới hạn 50 từ theo rubric; bỏ ký tự markdown. Bài không có lỗi nào được tô thì bỏ câu
     * "Lỗi nặng nhất" (phản hồi phòng đào tạo 22/9) — {@code highlights == null} nghĩa là không xét điều kiện này.
     */
    public static String trimFeedback(String s, List<Highlight> highlights) {
        String plain = NO_ERROR.matcher(s == null ? "" : s.replaceAll("\\*\\*|__|[*_`#>]", "")).replaceAll("");
        if (highlights != null && highlights.stream().noneMatch(h -> h.level().equals("red") || h.level().equals("yellow"))) {
            plain = WORST_ERROR.matcher(plain).replaceAll("");
        }
        plain = plain.trim();
        if (plain.isEmpty()) {
            return "";
        }
        String[] words = WHITESPACE.split(plain);
        if (words.length <= 50) {
            return String.join(" ", words);
        }
        return String.join(" ", Arrays.copyOf(words, 50)) + "…";
    }

    /**
     * Câu giải thích khi cổng chặn kích hoạt — do BACKEND tự soạn (bộ tiêu chí mới cấm AI đưa gợi ý vào
     * {@code feedback}, xem {@code TRA-LOI-TICH-HOP.md} mục F), giọng "Yêu cầu:" hướng dẫn, không phê phán
     * điểm số (đã xác nhận với người dùng V184). Trả rỗng nếu không cổng nào cần giải thích.
     */
    public static String buildGateNote(ReflexV2Task task, List<String> gates, int wordCount) {
        return buildGateNote(task, gates, wordCount, false);
    }

    /** @param spoken true khi giải thích cho bài NÓI (dùng từ "nói thêm"), false cho bài VIẾT. */
    public static String buildGateNote(ReflexV2Task task, List<String> gates, int wordCount, boolean spoken) {
        // Cùng mã C1 nhưng nghĩa khác nhau theo dạng bài (xem ReflexV2Task.GateScheme).
        boolean v3 = task.gateScheme() == ReflexV2Task.GateScheme.V3;
        String tooShortCode = v3 ? "C1" : "C3";
        String offTopicCode = v3 ? "C2" : "C1";
        StringBuilder note = new StringBuilder();
        if (gates.contains(tooShortCode)) {
            note.append(spoken ? "Yêu cầu: Bài nói cần tối thiểu " : "Yêu cầu: Bài cần tối thiểu ").append(task.minWords())
                    .append(" từ tiếng Anh (hiện có ").append(wordCount).append(spoken
                            ? " từ) — hãy nói thêm 1-2 câu nêu chi tiết hoặc lý do để đủ độ dài."
                            : " từ) — hãy viết thêm 1-2 câu nêu chi tiết hoặc lý do để đủ độ dài.");
        }
        if (gates.contains(offTopicCode)) {
            if (note.length() > 0) {
                note.append(' ');
            }
            note.append("Yêu cầu: Câu trả lời chưa đúng trọng tâm câu hỏi — hãy đọc lại câu hỏi và trả lời đúng ý được hỏi.");
        }
        return note.toString();
    }
}
