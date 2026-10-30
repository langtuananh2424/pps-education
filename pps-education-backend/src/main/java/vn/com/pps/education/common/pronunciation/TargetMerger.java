package vn.com.pps.education.common.pronunciation;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ghép câu mục tiêu cho GOPT từ bài viết của học sinh và lời nói được nhận dạng. Port từ {@code target_merge.py} của PPS Speaking
 * Lab (kiểm tra khớp bằng {@code target_merge_golden.json}).
 *
 * <p>Học sinh viết trước rồi nói. Nói thêm ý ngoài bài viết là điều được khuyến khích, nên bài viết không phải kịch bản bắt buộc:
 * <ul>
 *   <li>từ khớp bài viết: giữ chính tả của bài viết (tránh STT nghe sai tên riêng như "Cat Linh" thành "Kathleen");</li>
 *   <li>từ đọc lệch (STT nghe thành từ khác): vẫn giữ từ của bài viết, gắn {@code heardAs}, KHÔNG coi là nói thêm;</li>
 *   <li>từ nói thêm: chỉ nhận khi độ phủ ≥ 75%, nằm ở đầu/cuối câu (hoặc toàn từ nối) và giống từ tiếng Anh;</li>
 *   <li>từ trong bài viết mà không nói: ghi vào {@code omitted}, KHÔNG đưa vào câu mục tiêu để căn chỉnh không bị lệch.</li>
 * </ul>
 * Số ("30", "3.5", "21st") được đổi thành chữ ở cả hai phía, vì học sinh đọc thành tiếng và GOPT cần chấm âm đó.
 */
public final class TargetMerger {

    public static final double EXPAND_MIN_COVERAGE = 0.75;   // độ phủ bài viết tối thiểu để nhận từ nói thêm
    public static final double PLAUSIBLE_BAD_SHARE = 0.30;   // cụm chèn có quá tỉ lệ này là từ không giống tiếng Anh thì bỏ cả cụm
    private static final Set<String> CONNECTORS = Set.of("and", "but", "because", "so", "also", "then", "too", "or", "when",
            "after", "before", "if", "that");
    private static final Pattern WORD = Pattern.compile("[A-Za-z']+|\\d+(?:[.,]\\d+)*(?:st|nd|rd|th)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:[.,]\\d+)*)(st|nd|rd|th)?", Pattern.CASE_INSENSITIVE);

    private static final String[] ONES = ("zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen "
            + "fifteen sixteen seventeen eighteen nineteen").split(" ");
    private static final String[] TENS = "_ _ twenty thirty forty fifty sixty seventy eighty ninety".split(" ");
    private static final Map<String, String> ORDINAL = Map.of("one", "first", "two", "second", "three", "third", "five", "fifth",
            "eight", "eighth", "nine", "ninth", "twelve", "twelfth");

    /** origin: {@code writing} (từ của bài viết) hoặc {@code expanded} (nói thêm). */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MergedWord(String word, String origin, String heardAs, Integer writingIdx) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Omitted(String word, int writingIdx) {
    }

    /** coverage: % từ bài viết đã được nói (0-100), chỉ để ghi chú. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Result(List<MergedWord> words, List<Omitted> omitted, int nWriting, int nExpanded, int nOmitted, int coverage) {
    }

    private TargetMerger() {
    }

    // ---------- số thành chữ ----------

    private static List<String> intWords(long n) {
        List<String> out = new ArrayList<>();
        if (n < 20) {
            out.add(ONES[(int) n]);
        } else if (n < 100) {
            out.add(TENS[(int) (n / 10)]);
            if (n % 10 != 0) {
                out.add(ONES[(int) (n % 10)]);
            }
        } else if (n < 1000) {
            out.add(ONES[(int) (n / 100)]);
            out.add("hundred");
            if (n % 100 != 0) {
                out.addAll(intWords(n % 100));
            }
        } else {
            long[] units = {1_000_000_000L, 1_000_000L, 1000L};
            String[] names = {"billion", "million", "thousand"};
            for (int u = 0; u < units.length; u++) {
                if (n >= units[u]) {
                    out.addAll(intWords(n / units[u]));
                    out.add(names[u]);
                    if (n % units[u] != 0) {
                        out.addAll(intWords(n % units[u]));
                    }
                    break;
                }
            }
        }
        return out;
    }

    /** "30" thành thirty, "3.5" thành three point five, "21st" thành twenty first. */
    private static List<String> numberWords(String tok) {
        Matcher m = NUMBER.matcher(tok);
        if (!m.matches()) {
            return List.of(tok);
        }
        String body = m.group(1).replace(",", "");
        String suffix = m.group(2);
        int dot = body.indexOf('.');
        String whole = dot >= 0 ? body.substring(0, dot) : body;
        String frac = dot >= 0 ? body.substring(dot + 1) : "";
        List<String> out = new ArrayList<>();
        if (whole.length() > 12) {   // quá dài (số điện thoại, mã): đọc từng chữ số
            for (char c : whole.toCharArray()) {
                out.add(ONES[c - '0']);
            }
            return out;
        }
        out.addAll(intWords(Long.parseLong(whole)));
        if (!frac.isEmpty()) {
            out.add("point");
            for (char c : frac.toCharArray()) {
                if (c >= '0' && c <= '9') {   // "1.2.3": bỏ dấu chấm thừa (bản Python lỗi ở ca này)
                    out.add(ONES[c - '0']);
                }
            }
        }
        if (suffix != null) {
            String last = out.get(out.size() - 1);
            String ord = ORDINAL.get(last);
            out.set(out.size() - 1, ord != null ? ord : (last.endsWith("y") ? last.substring(0, last.length() - 1) + "ieth" : last + "th"));
        }
        return out;
    }

    /** Tách từ; số được đổi thành chữ để không mất âm học sinh đọc. */
    public static List<String> wordsOf(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = WORD.matcher(text == null ? "" : text);
        while (m.find()) {
            String tok = m.group();
            if (Character.isDigit(tok.charAt(0))) {
                out.addAll(numberWords(tok));
            } else {
                out.add(tok);
            }
        }
        return out;
    }

    /**
     * Từ chèn có giống một từ tiếng Anh không: toàn chữ cái ASCII, có nguyên âm (hoặc y), dài từ 2 ký tự trừ "a" và "I", không lặp
     * một ký tự (hmm, aaa). Chỉ lọc mảnh rác STT nghe ảo, không kiểm tra chính tả.
     */
    public static boolean plausibleWord(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        int s = 0;
        int e = w.length();
        while (s < e && w.charAt(s) == '\'') {
            s++;
        }
        while (e > s && w.charAt(e - 1) == '\'') {
            e--;
        }
        w = w.substring(s, e);
        if (w.equals("a") || w.equals("i")) {
            return true;
        }
        if (w.length() < 2) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasVowel = false;
        for (char c : w.toCharArray()) {
            if (c == '\'') {
                continue;
            }
            if (c < 'a' || c > 'z') {
                return false;   // không phải chữ cái ASCII
            }
            hasLetter = true;
            hasVowel |= "aeiouy".indexOf(c) >= 0;
        }
        if (!hasLetter || !hasVowel) {
            return false;
        }
        return w.chars().distinct().count() > 1;
    }

    // ---------- ghép ----------

    /**
     * Gióng hàng chặt (PPS Hybrid v2, mô-đun 1).
     * <ul>
     *   <li>equal: giữ từ của bài viết;</li>
     *   <li>replace: KHÔNG tách thành bỏ sót + mở rộng. Luôn giữ từ của bài viết để GOPT và wav2vec2 chấm với từ học sinh đáng lẽ
     *       đọc, gắn heardAs (lời STT nghe được) cho mọi từ trong đoạn;</li>
     *   <li>delete: từ bài viết không có âm tương ứng, ghi vào omitted (tính độ phủ);</li>
     *   <li>insert: chỉ nhận làm từ mở rộng khi độ phủ ≥ 75% và cụm nằm ở đầu hoặc cuối câu (hoặc toàn từ nối); ngược lại bỏ,
     *       vì thường là STT nghe ảo.</li>
     * </ul>
     */
    public static Result merge(String written, List<String> saidWords) {
        List<String> wr = wordsOf(written);
        List<String> said = new ArrayList<>();
        for (String w : saidWords) {
            said.addAll(wordsOf(w));
        }
        List<String> wl = wr.stream().map(x -> x.toLowerCase(Locale.ROOT)).toList();
        List<String> sl = said.stream().map(x -> x.toLowerCase(Locale.ROOT)).toList();
        List<SequenceDiff.Op> ops = SequenceDiff.opcodes(wl, sl);

        int nWr = wr.size();
        int nDel = 0;
        for (SequenceDiff.Op op : ops) {
            if (op.tag().equals("delete")) {
                nDel += op.i2() - op.i1();
            }
        }
        double coverage = nWr > 0 ? (double) (nWr - nDel) / nWr : 1.0;

        List<MergedWord> target = new ArrayList<>();
        List<Omitted> omitted = new ArrayList<>();
        for (SequenceDiff.Op op : ops) {
            switch (op.tag()) {
                case "equal" -> {
                    for (int i = op.i1(); i < op.i2(); i++) {
                        target.add(new MergedWord(wr.get(i), "writing", null, i));
                    }
                }
                case "delete" -> {
                    for (int i = op.i1(); i < op.i2(); i++) {
                        omitted.add(new Omitted(wr.get(i), i));
                    }
                }
                case "insert" -> {
                    List<String> group = said.subList(op.j1(), op.j2());
                    boolean atEdge = op.i1() == 0 || op.i1() >= nWr;
                    long bad = group.stream().filter(w -> !plausibleWord(w)).count();
                    double badShare = (double) bad / Math.max(group.size(), 1);
                    boolean allConnectors = group.stream().allMatch(w -> CONNECTORS.contains(w.toLowerCase(Locale.ROOT)));
                    if (coverage >= EXPAND_MIN_COVERAGE && badShare <= PLAUSIBLE_BAD_SHARE && (atEdge || allConnectors)) {
                        for (String w : group) {
                            target.add(new MergedWord(w, "expanded", null, null));
                        }
                    }
                }
                default -> {   // replace: giữ nguyên từ bài viết, chỉ ghi lại STT nghe thành gì
                    String heard = String.join(" ", said.subList(op.j1(), op.j2()));
                    for (int i = op.i1(); i < op.i2(); i++) {
                        target.add(new MergedWord(wr.get(i), "writing", heard, i));
                    }
                }
            }
        }
        int nExp = (int) target.stream().filter(t -> t.origin().equals("expanded")).count();
        int cov = nWr > 0 ? (int) Math.rint(100.0 * (nWr - omitted.size()) / nWr) : 100;   // rint = làm tròn về số chẵn như round() của Python
        return new Result(target, omitted, nWr, nExp, omitted.size(), cov);
    }
}
