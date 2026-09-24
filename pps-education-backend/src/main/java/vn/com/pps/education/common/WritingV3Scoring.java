package vn.com.pps.education.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 — port THUẦN JAVA (không gọi mạng, dễ viết
 * test) của {@code countCopied}/{@code errorCap}/{@code errorCapTable}/{@code enforceAnchorBound}/
 * {@code splitAudit} trong {@code tham-khao/index.html} (gói {@code bo-cham-writing-K6-K9}, đóng gói
 * 22/09/2026). {@code HUONG_DAN_TICH_HOP.md}: "khi tài liệu và mã tham chiếu khác nhau, mã tham chiếu là
 * chuẩn" — file này bám {@code index.html}, không bám mô tả văn xuôi.
 *
 * ĐƠN GIẢN HOÁ có chủ đích so với bản gốc: bản gốc còn nhánh "theo neo" cho khối CHƯA hiệu chuẩn xong (vòng
 * &lt;9, {@code ANCHOR_RECONCILED[grade]=0}, kẹp ±10). Tại thời điểm bàn giao 22/09/2026 CẢ 6 KHỐI đều đã
 * {@code ANCHOR_RECONCILED=1} (xem {@code 00_BAN_GIAO.md} mục 3 "đã khớp — vòng 9") — với kẹp = 0, phép
 * tính {@code clamped = max(cp-0, min(cp+0, neo))} LUÔN bằng {@code cp} bất kể "theo neo" ghi gì, nên nhánh
 * đó không còn tác dụng và bị bỏ hẳn ở đây. Nếu sau này có khối rơi lại trạng thái chưa hiệu chuẩn (rubric
 * mới không có dòng {@code Checkpoint:} ở mỗi neo), phải bổ sung lại nhánh này.
 */
public final class WritingV3Scoring {

    private WritingV3Scoring() {
    }

    // ===================== Đếm từ / cổng G1 (mirror countCopied, buildPrompt phần "SỐ LIỆU ĐÃ ĐO") =====================

    public record CopiedResult(int n, int opening) {
    }

    private static final Pattern WORD_NORM = Pattern.compile("[^a-z0-9\\s']");
    /** Khối 6/7 (g6/g7/g7b1): câu mở bài nhắc lại đề không bị trừ khỏi N_net, tối đa 10 từ — mirror OPENING_EXEMPT_MAX. */
    private static final int OPENING_EXEMPT_MAX = 10;
    private static final Pattern FIRST_SENTENCE = Pattern.compile("^[\\s\\S]*?[.!?](\\s|$)");

    /** {@code essay.trim().split(/\s+/).length} — mirror {@code countWords}/N_total, KHÔNG dùng regex Unicode để khớp đúng cách đếm của bản tham chiếu. */
    public static int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private static List<String> normalize(String s) {
        String cleaned = WORD_NORM.matcher(s.toLowerCase(Locale.ROOT)).replaceAll(" ");
        List<String> out = new ArrayList<>();
        for (String w : cleaned.split("\\s+")) {
            if (!w.isBlank()) {
                out.add(w);
            }
        }
        return out;
    }

    /**
     * Dò mọi chuỗi 5-gram trùng giữa bài viết và đề — mirror {@code countCopied()}. {@code opening} là số từ
     * chép nằm trong câu mở bài (dùng cho miễn trừ G3 ở Khối 6/7), đã kẹp tối đa {@link #OPENING_EXEMPT_MAX}.
     */
    public static CopiedResult countCopied(String essay, String task) {
        List<String> t = normalize(task == null ? "" : task);
        List<String> e = normalize(essay == null ? "" : essay);
        if (t.size() < 5 || e.size() < 5) {
            return new CopiedResult(0, 0);
        }
        Map<String, Boolean> grams = new java.util.HashMap<>();
        for (int i = 0; i + 5 <= t.size(); i++) {
            grams.put(String.join(" ", t.subList(i, i + 5)), Boolean.TRUE);
        }
        boolean[] marked = new boolean[e.size()];
        for (int j = 0; j + 5 <= e.size(); j++) {
            if (grams.containsKey(String.join(" ", e.subList(j, j + 5)))) {
                for (int k = j; k < j + 5; k++) {
                    marked[k] = true;
                }
            }
        }
        Matcher fm = FIRST_SENTENCE.matcher(essay == null ? "" : essay);
        String firstSentence = fm.find() ? fm.group() : (essay == null ? "" : essay);
        int firstLen = normalize(firstSentence).size();
        int opening = 0;
        for (int q = 0; q < Math.min(firstLen, e.size()); q++) {
            if (marked[q]) {
                opening++;
            }
        }
        int n = 0;
        for (boolean m : marked) {
            if (m) {
                n++;
            }
        }
        return new CopiedResult(n, Math.min(opening, OPENING_EXEMPT_MAX));
    }

    /** Kết luận cổng G1, ĐÚNG câu chữ ghim vào prompt của bản tham chiếu — model không tự đánh giá lại. */
    public static String gateG1Conclusion(int nNet, int wordLimit, double pct) {
        if (nNet >= Math.ceil(wordLimit * 0.8)) {
            return "G1 KHÔNG kích hoạt (" + fmtPct(pct) + "% >= 80%). KHÔNG được áp trần độ dài nào.";
        }
        if (nNet >= Math.ceil(wordLimit * 0.5)) {
            return "G1 kích hoạt băng 50–79% (" + fmtPct(pct) + "%). Task/Content trần 60%. Các tiêu chí ngôn ngữ KHÔNG bị trần.";
        }
        if (nNet >= Math.ceil(wordLimit * 0.25)) {
            return "G1 kích hoạt băng <50% (" + fmtPct(pct) + "%). MỌI tiêu chí trần 40%.";
        }
        return "G1: N_net < 25% yêu cầu -> 0% insufficient data.";
    }

    public static double percentOfRequirement(int nNet, int wordLimit) {
        if (wordLimit <= 0) {
            return 0;
        }
        return Math.round((nNet * 1000.0) / wordLimit) / 10.0;
    }

    private static String fmtPct(double pct) {
        return pct == Math.floor(pct) ? String.valueOf((int) pct) : String.valueOf(pct);
    }

    /** Số từ đề yêu cầu: đọc từ chính đề (\d{2,4} words / about \d{2,4} / \d{2,4} từ), fallback {@code defaultWordLimit} của khối. */
    private static final Pattern WORD_LIMIT_1 = Pattern.compile("(\\d{2,4})\\s*\\+?\\s*words", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_LIMIT_2 = Pattern.compile("about\\s+(\\d{2,4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_LIMIT_3 = Pattern.compile("(\\d{2,4})\\s*từ");

    public static int extractWordLimit(String task, int fallback) {
        if (task == null) {
            return fallback;
        }
        for (Pattern p : List.of(WORD_LIMIT_1, WORD_LIMIT_2, WORD_LIMIT_3)) {
            Matcher m = p.matcher(task);
            if (m.find()) {
                int n = Integer.parseInt(m.group(1));
                if (n >= 20 && n <= 1000) {
                    return n;
                }
            }
        }
        return fallback;
    }

    // ===================== Trần theo mật độ lỗi (mirror errorCap/errorCapTable) =====================

    /** @param isG6 Khối 6 chỉ áp trần TUYỆT ĐỐI (không tính theo mật độ /100 từ) — mirror {@code byRate = grade !== "g6"}. */
    public static int errorCap(int errors, int totalWords, boolean isG6) {
        if (errors <= 0) {
            return 100;
        }
        int abs = errors <= 2 ? 90 : errors <= 4 ? 80 : (isG6 ? 100 : 80);
        if (isG6) {
            return abs;
        }
        double rate = errors * 100.0 / Math.max(totalWords, 1);
        int byRate = rate <= 2 ? 90 : rate <= 4 ? 80 : rate <= 7 ? 70 : rate <= 10 ? 60 : rate <= 14 ? 50 : 40;
        return Math.min(abs, byRate);
    }

    /** Bảng trần hiển thị trong prompt (VD "0 lỗi → tối đa 100%; 1–2 lỗi → tối đa 90%; …") — mirror {@code errorCapTable}. */
    public static String errorCapTable(int totalWords, boolean isG6) {
        List<String> parts = new ArrayList<>();
        Integer prev = null;
        int from = 0;
        for (int e = 0; e <= 40; e++) {
            int c = errorCap(e, totalWords, isG6);
            if (prev == null || c != prev) {
                if (prev != null) {
                    parts.add((from == e - 1 ? String.valueOf(from) : from + "–" + (e - 1)) + " lỗi → tối đa " + prev + "%");
                }
                prev = c;
                from = e;
            }
        }
        parts.add("≥" + from + " lỗi → tối đa " + prev + "%");
        return String.join("; ", parts);
    }

    // ===================== Tách mục 0 (mirror splitAudit) =====================

    public record AuditSplit(String audit, String visible) {
    }

    private static final Pattern SECTION_0 = Pattern.compile("(?m)^###\\s*0\\.");
    private static final Pattern SECTION_1 = Pattern.compile("(?m)^###\\s*1\\.");

    /** Mục 0 (Kiểm đếm) chỉ để hậu kiểm/giáo viên xem — KHÔNG BAO GIỜ được lộ cho học sinh. */
    public static AuditSplit splitAudit(String md) {
        if (md == null) {
            return new AuditSplit("", "");
        }
        Matcher m0 = SECTION_0.matcher(md);
        if (!m0.find()) {
            return new AuditSplit("", md);
        }
        int start = m0.start();
        String rest = md.substring(start);
        Matcher m1 = SECTION_1.matcher(rest);
        if (!m1.find()) {
            return new AuditSplit(rest, md.substring(0, start));
        }
        int endInRest = m1.start();
        return new AuditSplit(rest.substring(0, endInRest), md.substring(0, start) + rest.substring(endInRest));
    }

    // ===================== Key Grammar (filter 2, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) =====================

    /** {@code status}: {@code "pass"}/{@code "fail"}/{@code "unparsed"} (đọc được header nhưng không đọc được "Dùng đúng: N"). */
    public record KeyGrammarConclusion(String status, int correct, int attempts) {
    }

    private static final Pattern KEY_GRAMMAR_HEADER = Pattern.compile("(?m)^Key grammar được giao\\s*:.*$");

    /**
     * Đọc lại số liệu Key Grammar ở mục 0 (KHÔNG tin dòng "Kết luận" model tự viết — cùng tinh thần
     * {@link #enforceScore}, tự suy Đạt/Chưa đạt từ đúng 2 con số "Dùng đúng"/"Dùng sai"). {@code null}
     * nếu mục 0 không có khối Key Grammar (đúng khi Bài không gắn Key Grammar). {@code status="unparsed"}
     * nếu có header nhưng không đọc được số "Dùng đúng" — theo {@code 00_DAC_TA_GIAO_NHAN.md} mục 4:
     * "chuyển giáo viên xem", KHÔNG áp trần nào trong trường hợp này (xem {@link #keyGrammarCapPercent}).
     */
    public static KeyGrammarConclusion parseKeyGrammarConclusion(String audit, int passIfAtLeast) {
        if (audit == null) {
            return null;
        }
        Matcher h = KEY_GRAMMAR_HEADER.matcher(audit);
        if (!h.find()) {
            return null;
        }
        String window = audit.substring(h.end(), Math.min(audit.length(), h.end() + 600));
        int correct = readInt(window, "Dùng đúng\\s*:\\s*\\**\\s*(\\d+)", -1);
        if (correct < 0) {
            return new KeyGrammarConclusion("unparsed", 0, 0);
        }
        int incorrect = readInt(window, "Dùng sai\\s*:\\s*\\**\\s*(\\d+)", 0);
        int attempts = correct + Math.max(incorrect, 0);
        return new KeyGrammarConclusion(correct >= passIfAtLeast ? "pass" : "fail", correct, attempts);
    }

    /** Trần % tiêu chí ngữ pháp khi Key Grammar Chưa đạt (0 lần đúng → {@code grammarCapAtZero}, 1 lần → {@code grammarCapAtOne}); {@code null} nếu Đạt/unparsed/không gắn. */
    public static Integer keyGrammarCapPercent(KeyGrammarDictionary dictionary, KeyGrammarConclusion conclusion) {
        if (dictionary == null || conclusion == null || !"fail".equals(conclusion.status())) {
            return null;
        }
        return conclusion.correct() == 0 ? dictionary.grammarCapAtZero() : dictionary.grammarCapAtOne();
    }

    // ===================== Hậu kiểm bảng điểm (mirror enforceAnchorBound, đã bỏ nhánh "theo neo") =====================

    private record CheckpointMatch(int cp, Integer sauTran, String trailing) {
    }

    /**
     * Dòng dạng {@code <tên tiêu chí> = 1 + 0.5 + … = 3.5/5 → 70% → sau trần: 60% (tên trần)} trong mục 0 —
     * lấy dòng CUỐI CÙNG khớp tên tiêu chí (mirror {@code .filter(...).pop()}), vì mục 0 chỉ nên có đúng 1
     * dòng nhưng model đôi khi lặp lại khi giải thích.
     */
    private static CheckpointMatch findCheckpointLine(String audit, String criterionName) {
        Pattern p = Pattern.compile("(?m)^.*" + Pattern.quote(criterionName)
                + ".*=\\s*[\\d.]+\\s*/\\s*5\\s*(?:→|->)\\s*(\\d+)\\s*%(?:.*?sau trần[^:\\n]*:\\s*(\\d+)\\s*%)?(.*)$");
        Matcher m = p.matcher(audit);
        CheckpointMatch last = null;
        while (m.find()) {
            int cp = Integer.parseInt(m.group(1));
            Integer sauTran = m.group(2) != null ? Integer.parseInt(m.group(2)) : null;
            String trailing = m.group(3) == null ? "" : m.group(3);
            last = new CheckpointMatch(cp, sauTran, trailing);
        }
        return last;
    }

    private static final Map<String, java.util.function.Function<ErrorCounts, Integer>> ERR_OF = Map.of(
            "Grammatical Range & Accuracy", c -> c.gr + c.pu,
            "Lexical Resource", c -> c.sp + c.wd,
            "Language", c -> c.gr + c.sp + c.wd + c.pu
    );

    private static final Map<String, Pattern> ALIAS = Map.ofEntries(
            Map.entry("Task Response / Achievement", Pattern.compile("Task|TR\\b|TA\\b", Pattern.CASE_INSENSITIVE)),
            Map.entry("Content", Pattern.compile("Content", Pattern.CASE_INSENSITIVE)),
            Map.entry("Coherence & Cohesion", Pattern.compile("Coherence|CC\\b", Pattern.CASE_INSENSITIVE)),
            Map.entry("Lexical Resource", Pattern.compile("Lexical|LR\\b", Pattern.CASE_INSENSITIVE)),
            Map.entry("Grammatical Range & Accuracy", Pattern.compile("Grammatical|GRA\\b", Pattern.CASE_INSENSITIVE)),
            Map.entry("Language", Pattern.compile("Language", Pattern.CASE_INSENSITIVE)),
            Map.entry("Communicative Achievement", Pattern.compile("Communicative|CA\\b", Pattern.CASE_INSENSITIVE)),
            Map.entry("Organisation", Pattern.compile("Organisation|Organization", Pattern.CASE_INSENSITIVE))
    );

    private record ErrorCounts(int gr, int sp, int wd, int pu) {
    }

    /**
     * Đọc lại mục 0 (chưa tách khỏi {@code rawMarkdown}), TỰ TÍNH lại bảng điểm mục 2 + Tổng kết, không tin
     * % model tự điền — mirror {@code enforceAnchorBound()}. An toàn khi mục 0 thiếu dòng nào: giữ nguyên %
     * model đã ghi ở bảng điểm cho tiêu chí đó (không đoán).
     *
     * @param keyGrammarDictionary Key Grammar (filter 2, bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     *                             2026-09-22) của câu hỏi đang chấm — {@code null} nếu câu hỏi không gắn
     *                             Key Grammar (giữ nguyên hành vi cũ, không áp trần gì thêm).
     * @return markdown đã sửa (chèn dòng "HỆ THỐNG ĐIỀU CHỈNH ĐIỂM: …" trước mục 1 nếu có sửa), hoặc nguyên
     *         văn {@code rawMarkdown} nếu không cần sửa gì.
     */
    public static String enforceScore(String rawMarkdown, WritingV3Grade grade, KeyGrammarDictionary keyGrammarDictionary) {
        AuditSplit split = splitAudit(rawMarkdown);
        if (split.audit().isBlank()) {
            return rawMarkdown;
        }
        String audit = split.audit();
        Matcher capLineM = Pattern.compile("(?m)^.*Trần cứng kích hoạt.*$").matcher(audit);
        String capLine = capLineM.find() ? capLineM.group() : "";

        int nWords = readInt(audit, "N_total:\\s*(\\d+)", -1);
        ErrorCounts errs = readErrorCounts(audit);
        boolean isG6 = "g6".equals(grade.code());

        String grammarCriterionName = grade.rows().stream()
                .filter(r -> r.equals("Grammatical Range & Accuracy") || r.equals("Language"))
                .findFirst().orElse(null);
        KeyGrammarConclusion kgConclusion = keyGrammarDictionary == null ? null
                : parseKeyGrammarConclusion(audit, keyGrammarDictionary.passIfAtLeast());
        Integer kgCap = keyGrammarCapPercent(keyGrammarDictionary, kgConclusion);

        Map<String, Integer> finals = new LinkedHashMap<>();
        List<String> fixes = new ArrayList<>();
        boolean changed = false;

        for (String name : grade.rows()) {
            CheckpointMatch line = findCheckpointLine(audit, name);
            if (line == null) {
                continue;
            }
            int cp = line.cp();
            int modelFinal;
            boolean hasCap;
            if (line.sauTran() != null) {
                modelFinal = line.sauTran();
                hasCap = isCapMentioned(line.trailing()) || (ALIAS.get(name) != null && ALIAS.get(name).matcher(capLine).find());
            } else {
                // "sau trần" không đọc được — lấy nguyên % model đã ghi ở chính bảng điểm mục 2, không đoán.
                Integer fromTable = readScoreTableValue(split.visible(), name);
                modelFinal = fromTable != null ? fromTable : cp;
                hasCap = fromTable != null && fromTable < cp;
            }

            int fin = hasCap ? Math.min(cp, modelFinal) : cp;
            var errFn = ERR_OF.get(name);
            if (errFn != null && nWords >= 0) {
                int e = errFn.apply(errs);
                int ec = errorCap(e, nWords, isG6);
                if (ec < fin) {
                    fixes.add(name + ": trần " + e + " lỗi / " + nWords + " từ → " + ec + "%");
                    fin = ec;
                }
            }
            if (kgCap != null && name.equals(grammarCriterionName) && kgCap < fin) {
                fixes.add(name + ": Key Grammar chưa đạt (" + kgConclusion.correct() + "/" + kgConclusion.attempts() + " lần đúng) → trần " + kgCap + "%");
                fin = kgCap;
            }
            finals.put(name, fin);
            if (fin != modelFinal) {
                changed = true;
            }
        }

        // Trần Tổng kết 35% khi quá nửa động từ chính sai thì/dạng (Khối 7-9; Khối 6 không áp).
        Integer verbCapTotal = null;
        int wrongVerbs = 0;
        Matcher vm = Pattern.compile("N_verb_ok\\s*/\\s*N_verb\\s*:\\s*(\\d+)\\s*/\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(audit);
        if (!isG6 && vm.find()) {
            int ok = Integer.parseInt(vm.group(1));
            int total = Integer.parseInt(vm.group(2));
            if (total > 0 && (total - ok) / (double) total > 0.5) {
                verbCapTotal = total;
                wrongVerbs = total - ok;
                Integer shownTotal = readScoreTableValue(split.visible(), "**Tổng kết**");
                if (shownTotal != null && shownTotal > 35) {
                    changed = true;
                }
            }
        }

        if (!changed) {
            return rawMarkdown;
        }

        String out = rewriteScoreRows(rawMarkdown, finals);

        if (!finals.isEmpty() && finals.size() == grade.rows().size()) {
            double avg = finals.values().stream().mapToInt(Integer::intValue).average().orElse(0);
            int total = (int) (Math.floor(avg / 5 + 1e-9) * 5);
            if (verbCapTotal != null && total > 35) {
                fixes.add("Tổng kết: " + wrongVerbs + "/" + verbCapTotal + " động từ sai (> 50%) → tối đa 35%");
                total = 35;
            }
            out = rewriteTotalRow(out, total);
        }

        String note = "HỆ THỐNG ĐIỀU CHỈNH ĐIỂM (trần số lỗi/động từ, không tin % model tự điền): "
                + (fixes.isEmpty() ? "làm tròn/khớp lại bảng điểm theo checkpoint." : String.join(" · ", fixes));
        return out.replaceFirst("(?m)^(###\\s*1\\.)", Matcher.quoteReplacement(note) + "\n\n$1");
    }

    private static String rewriteScoreRows(String md, Map<String, Integer> finals) {
        StringBuilder out = new StringBuilder();
        String[] lines = md.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (Map.Entry<String, Integer> e : finals.entrySet()) {
                Pattern row = Pattern.compile("^(\\|\\s*(?:\\*\\*)?" + Pattern.quote(e.getKey())
                        + "(?:\\*\\*)?\\s*\\|\\s*)(?:\\*\\*)?\\d+(\\s*%(?:\\*\\*)?\\s*\\|?\\s*)$");
                Matcher m = row.matcher(line);
                if (m.matches()) {
                    line = m.group(1) + e.getValue() + m.group(2);
                    break;
                }
            }
            out.append(line);
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static String rewriteTotalRow(String md, int total) {
        return md.replaceFirst("(\\|\\s*\\*\\*Tổng kết\\*\\*\\s*\\|\\s*\\*\\*)\\d+(%\\s*\\*\\*\\s*\\|?)", "$1" + total + "$2");
    }

    private static boolean isCapMentioned(String trailing) {
        if (trailing == null) {
            return false;
        }
        boolean saysNoCap = Pattern.compile("không\\s*(bị\\s*)?trần", Pattern.CASE_INSENSITIVE).matcher(trailing).find();
        boolean mentionsCap = Pattern.compile("trần|G[1-4]|mỏng|động từ|lỗi", Pattern.CASE_INSENSITIVE).matcher(trailing).find();
        return !saysNoCap && mentionsCap;
    }

    private static Integer readScoreTableValue(String visibleMd, String criterionName) {
        Pattern p = Pattern.compile("(?m)^\\|\\s*(?:\\*\\*)?" + Pattern.quote(criterionName.replace("**", ""))
                + "(?:\\*\\*)?\\s*\\|\\s*(?:\\*\\*)?(\\d+)\\s*%");
        Matcher m = p.matcher(visibleMd);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private static ErrorCounts readErrorCounts(String audit) {
        int gr = readInt(audit, "Số lỗi ngữ pháp\\s*:\\s*\\**\\s*(\\d+)", 0);
        Matcher otherLine = Pattern.compile("(?m)^.*Số lỗi ngôn ngữ khác.*$").matcher(audit);
        String other = otherLine.find() ? otherLine.group() : "";
        int sp = readInt(other, "chính tả\\s*:?\\s*(\\d+)", 0);
        int wd = readInt(other, "dùng từ\\s*:?\\s*(\\d+)", 0);
        int pu = readInt(other, "dấu câu\\s*:?\\s*(\\d+)", 0);
        return new ErrorCounts(gr, sp, wd, pu);
    }

    private static int readInt(String text, String pattern, int fallback) {
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : fallback;
    }

    /** Đọc bảng điểm mục 2 (chỉ phần hiển thị, đã bỏ mục 0) — dùng khi trả kết quả về caller. */
    public static Map<String, Integer> readScoreTable(String visibleMd, List<String> rows) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String name : rows) {
            Integer v = readScoreTableValue(visibleMd, name);
            if (v != null) {
                out.put(name, v);
            }
        }
        return out;
    }

    public static Integer readTotal(String visibleMd) {
        return readScoreTableValue(visibleMd, "**Tổng kết**");
    }
}
