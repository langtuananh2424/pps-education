package vn.com.pps.education.common.pronunciation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import vn.com.pps.education.common.pronunciation.PronunciationInput.GoptPhone;
import vn.com.pps.education.common.pronunciation.PronunciationInput.GoptUtterance;
import vn.com.pps.education.common.pronunciation.PronunciationInput.GoptWord;
import vn.com.pps.education.common.pronunciation.PronunciationInput.Timestamp;
import vn.com.pps.education.common.pronunciation.PronunciationInput.Writing;

/**
 * PPS Hybrid Scorer v2: chấm lại điểm âm vị, từ, trôi chảy, ngữ điệu và điểm câu từ kết quả phân tích một bài (GOPT/Kaldi,
 * wav2vec2 IPA, mốc thời gian STT, bài viết). Port từ {@code pps_hybrid_scorer.py} của PPS Speaking Lab; kết quả được kiểm
 * tra khớp từng số với lab bằng {@code pps_v2_golden.json}.
 *
 * <p>Hàm thuần, không đọc/ghi gì ngoài tham số, an toàn dùng nhiều luồng. Tám bước theo đặc tả
 * {@code pps_speaking_lab_scoring_v2_spec.md}: chuẩn hoá âm vị (2.1), phạt phụ âm cuối (2.2), điểm từ (2.3), trôi chảy và
 * ngữ điệu (3), điểm câu với độ phủ, ba chốt chặn và độ khắt khe theo khối (4).
 *
 * <p>Hai chỗ thêm ngoài công thức của đặc tả để đạt hai ca biên bắt buộc ở mục 5 của đặc tả: bài dùng bài viết thay cho lời
 * nói (fallback) không quá {@code utterance.fallback_cap}, và bài phủ dưới 50% bài viết không quá {@code utterance.low_coverage_cap}.
 */
public final class PpsHybridScorer {

    public static final String MODE = "pps_hybrid_v2";
    public static final int DEFAULT_GRADE = 7;
    private static final Set<Integer> GRADES = Set.of(6, 7, 8);

    /** Phụ âm cuối cần bật rõ; thiếu hoặc sai thì bị trừ điểm từ. */
    private static final Set<Character> ENDING_CONSONANTS = Set.of(
            't', 'd', 's', 'z', 'k', 'g', 'ɡ', 'f', 'v', 'θ', 'ð', 'ʃ', 'ʒ', 'l', 'm', 'n', 'ŋ', 'p', 'b');
    private static final String IPA_MARKS = "ˈˌː ";

    private PpsHybridScorer() {
    }

    /** Khối 6, 7, 8; không xác định được (other, rỗng, khối khác) thì lấy khối 7. */
    public static int gradeOf(Object g) {
        if (g == null) {
            return DEFAULT_GRADE;
        }
        try {
            int v = Integer.parseInt(String.valueOf(g).strip());
            return GRADES.contains(v) ? v : DEFAULT_GRADE;
        } catch (NumberFormatException e) {
            return DEFAULT_GRADE;
        }
    }

    // ---------- 2.1 âm vị ----------

    /** Điểm âm vị đã chuẩn hoá: {thang 0-2, thang 0-10}. Dưới phone.floor là sai hẳn (0 điểm), từ phone.top là đạt tối đa. */
    static double[] phoneScores(double sRaw, PpsConfig cfg) {
        double s = clip(sRaw, 0.0, 2.0);
        double floor = cfg.get("phone.floor");
        double norm = clip((s - floor) / (cfg.get("phone.top") - floor), 0.0, 1.0);
        double curved = Math.pow(norm, cfg.get("phone.exponent"));
        return new double[]{round(2.0 * curved, 3), 10.0 * curved};
    }

    // ---------- 2.2 phụ âm cuối ----------

    /** Từ kết thúc bằng phụ âm cần bật mà wav2vec2 nghe thiếu ('·') hoặc thay bằng âm khác. */
    static boolean endingSoundError(String expectedIpa, String heardIpa, List<String> issues) {
        String exp = stripMarks(expectedIpa);
        String heard = stripMarks(heardIpa);
        if (exp.isEmpty()) {
            return false;
        }
        char last = exp.charAt(exp.length() - 1);
        if (!ENDING_CONSONANTS.contains(last)) {
            return false;
        }
        if (heard.endsWith("·")) {
            return true;
        }
        if (!heard.isEmpty() && heard.charAt(heard.length() - 1) != last) {
            String prefix = "/" + last + "/";
            return issues != null && issues.stream().anyMatch(i -> String.valueOf(i).startsWith(prefix));
        }
        return false;
    }

    private static String stripMarks(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            if (IPA_MARKS.indexOf(s.charAt(i)) < 0) {
                sb.append(s.charAt(i));
            }
        }
        return sb.toString();
    }

    // ---------- 2.3 từ ----------

    static PpsV2Result.Word scoreWord(PronunciationInput.Word w, GoptWord gw, boolean fallback, PpsConfig cfg) {
        List<PpsV2Result.Phone> phones = new ArrayList<>();
        List<Double> p10 = new ArrayList<>();
        if (gw != null && gw.phones() != null) {
            for (GoptPhone ph : gw.phones()) {
                double[] sc = phoneScores(ph.s(), cfg);
                phones.add(new PpsV2Result.Phone(ph.p(), round(ph.s(), 3), sc[0]));
                p10.add(sc[1]);
            }
        }
        double ipaW10 = (w.score() == null ? 0 : w.score()) / 10.0;
        double goptWNorm = p10.isEmpty() ? ipaW10 : mean(p10);
        double ipaShare = ipaW10 < goptWNorm ? cfg.get("word.ipa_weight_low") : cfg.get("word.ipa_weight_high");   // wav2vec2 thấy sai rõ hơn GOPT: ưu tiên IPA
        double base = (1 - ipaShare) * goptWNorm + ipaShare * ipaW10;

        boolean endErr = endingSoundError(w.expectedIpa(), w.heardIpa(), w.issues());
        base = Math.max(0.0, base - (endErr ? cfg.get("word.ending_penalty") : 0.0));

        String status = fallback ? "unverified" : (w.whisper() == null || w.whisper().isEmpty() ? "ok" : w.whisper());
        if (status.equals("missed")) {   // nói có câu mẫu: lời nói không có từ này, coi như đọc lệch
            status = "diff";
        }
        double mult = whisperParam("whisper.mult.", status, 1.0, cfg);
        double cap = whisperParam("whisper.cap.", status, 10.0, cfg);
        double acc = Math.min(cap, Math.max(0.0, base * mult));

        double rawStress = clip(gw != null && gw.stress() != null ? gw.stress() : 10.0, 0.0, 10.0);
        double stressNorm = 4.0 + 6.0 * clip((rawStress - 8.5) / 1.4, 0.0, 1.0);
        double stress = clip(0.50 * stressNorm + 0.50 * acc, 0.0, 10.0);
        double share = cfg.get("word.stress_share");
        double total = (1 - share) * acc + share * stress;
        return new PpsV2Result.Word(w.word() == null ? "" : w.word(), status, round(acc, 2), round(stress, 2), round(total, 2), endErr, phones);
    }

    private static double whisperParam(String prefix, String status, double otherwise, PpsConfig cfg) {
        return switch (status) {
            case "extra", "diff", "unverified" -> cfg.get(prefix + status);
            default -> otherwise;
        };
    }

    // ---------- 3 trôi chảy và ngữ điệu ----------

    private static double lenBonus(int nPhones) {
        return Math.min(0.90, Math.max(0.0, (nPhones - 28) * 0.022));
    }

    /** {trôi chảy, ngữ điệu}, thang 0-10. */
    static double[] fluencyProsody(GoptUtterance gu, List<Timestamp> ts, List<Double> wordScoresCal, int nPhones,
                                   Double fluencyHeuristic, int grade, Double targetWpmOverride, PpsConfig cfg) {
        double bonus = lenBonus(nPhones);
        double fluLen = Math.min(10.0, gu.fluency() + 0.60 * bonus);
        double proLen = Math.min(10.0, gu.prosodic() + 0.60 * bonus);
        double fluNorm = 1.5 + 7.7 * Math.pow(clip((fluLen - 3.2) / 5.4, 0.0, 1.0), 1.2);
        double proNorm = 1.5 + 7.5 * Math.pow(clip((proLen - 3.0) / 5.5, 0.0, 1.0), 1.2);

        double targetWpm = targetWpmOverride != null && targetWpmOverride != 0 ? targetWpmOverride : cfg.byGrade("fluency.target_wpm", grade);
        double tsFlu;
        if (ts.size() >= 3) {
            double span = Math.max(0.5, ts.get(ts.size() - 1).end() - ts.get(0).start());
            double wpm = ts.size() / span * 60.0;
            double rate;
            if (wpm >= targetWpm) {
                rate = Math.min(9.2, 7.5 + (wpm - targetWpm) * 0.03);
            } else if (wpm >= targetWpm - 30.0) {
                rate = 5.0 + (wpm - (targetWpm - 30.0)) * (2.5 / 30.0);
            } else {
                rate = Math.max(1.8, 1.8 + Math.max(0.0, wpm - 35.0) * (3.2 / Math.max(1.0, targetWpm - 65.0)));
            }
            double shortS = cfg.get("fluency.short_pause_s");
            double longS = cfg.get("fluency.long_pause_s");
            int nShort = 0;
            int nLong = 0;
            for (int i = 1; i < ts.size(); i++) {
                double gap = Math.max(0.0, ts.get(i).start() - ts.get(i - 1).end());
                if (gap > shortS && gap <= longS) {   // đọc rời rạc từng từ
                    nShort++;
                } else if (gap > longS) {             // ngập ngừng, quên từ
                    nLong++;
                }
            }
            double penalty = (cfg.get("fluency.short_pause_penalty") * nShort + cfg.get("fluency.long_pause_penalty") * nLong)
                    * (8.0 / Math.max(ts.size(), 4));
            tsFlu = Math.min(9.5, Math.max(1.0, rate - penalty));
        } else {
            double heuristic = fluencyHeuristic == null || fluencyHeuristic == 0 ? 60.0 : fluencyHeuristic;
            tsFlu = (heuristic / 10.0) * 0.65;
        }

        double meanAcc = wordScoresCal.isEmpty() ? 4.0 : mean(wordScoresCal);
        double share = cfg.get("fluency.gopt_share");
        double flu = clip(share * fluNorm + (1 - share) * tsFlu, 0.0, 10.0);
        double pro = clip(0.50 * proNorm + 0.30 * flu + 0.20 * meanAcc, 0.0, 10.0);
        return new double[]{flu, pro};
    }

    // ---------- 4 điểm câu ----------

    static PpsV2Result.Utterance utteranceScores(GoptUtterance gu, List<Double> wordScoresCal, double fluencyCal, double prosodyCal,
                                                 int nPhones, Writing wi, int grade, PpsConfig cfg) {
        double bonus = lenBonus(nPhones);
        double accLen = Math.min(10.0, gu.accuracy() + bonus);
        double accNorm = 1.5 + 7.7 * Math.pow(clip((accLen - 3.0) / 5.5, 0.0, 1.0), 1.15);

        double meanAcc = wordScoresCal.isEmpty() ? 4.0 : mean(wordScoresCal);
        double threshold = cfg.get("utterance.bad_word_threshold");
        long bad = wordScoresCal.stream().filter(s -> s < threshold).count();
        double badRatio = (double) bad / Math.max(wordScoresCal.size(), 1);
        double wordShare = cfg.get("utterance.word_share");
        double rawAcc = clip(wordShare * meanAcc + (1 - wordShare) * accNorm - cfg.get("utterance.bad_word_penalty") * badRatio, 0.0, 10.0);

        int nWr = Math.max(1, firstNonZero(wi == null ? null : wi.nWriting(), wordScoresCal.size(), 1));
        int nOm = wi == null || wi.nOmitted() == null ? 0 : wi.nOmitted();
        int nEx = wi == null || wi.nExpanded() == null ? 0 : wi.nExpanded();
        boolean fallback = wi != null && Boolean.TRUE.equals(wi.fallback());
        int nSubst = Math.min(nOm, nEx);
        double effCov = clip((nWr - nOm - 0.35 * nSubst) / nWr, 0.0, 1.0);
        double completeness = round(10.0 * effCov, 2);

        double cg = cfg.get("utterance.coverage_gate_from");
        double covGate = effCov >= cg ? 1.0 : 0.35 + 0.65 * (effCov / cg);
        double minPhones = cfg.get("utterance.min_phones");
        double lenGate = nPhones >= minPhones ? 1.0 : 0.65 + 0.35 * (nPhones / minPhones);
        double unvGate = fallback ? cfg.get("utterance.fallback_gate") : 1.0;
        double totalGate = covGate * lenGate * unvGate;
        double shift = cfg.byGrade("utterance.grade_shift", grade);

        double acc = clip((rawAcc + shift) * covGate * unvGate, 0.0, 10.0);
        double flu = clip((fluencyCal + shift) * lenGate * unvGate, 0.0, 10.0);
        double pro = clip((prosodyCal + shift) * lenGate * unvGate, 0.0, 10.0);
        double total = clip((cfg.get("utterance.weights.accuracy") * rawAcc + cfg.get("utterance.weights.fluency") * fluencyCal
                + cfg.get("utterance.weights.prosody") * prosodyCal + shift) * totalGate, 0.0, 10.0);

        if (fallback) {
            double cap = cfg.get("utterance.fallback_cap");
            acc = Math.min(cap, acc);
            flu = Math.min(cap, flu);
            pro = Math.min(cap, pro);
            total = Math.min(cap, total);
        }
        if (effCov < 0.50) {
            total = Math.min(cfg.get("utterance.low_coverage_cap"), total);
        }
        return new PpsV2Result.Utterance(round(acc, 2), round(flu, 2), round(pro, 2), completeness, round(total, 2), round(meanAcc, 2));
    }

    // ---------- tổng hợp một bài ----------

    /**
     * Điểm v2 của một bài. Rỗng nếu bài không chạy GOPT hoặc không có tiếng nói (không có dữ liệu để chấm lại).
     * GOPT chạy mà Kaldi không gióng hàng được thì trả điểm 0.0 với status = no_alignment.
     *
     * @param grade      khối (6, 7, 8; giá trị khác tính như 7)
     * @param targetWpm  tốc độ chuẩn riêng cho bài này (null: lấy theo khối trong cấu hình)
     */
    public static Optional<PpsV2Result> score(PronunciationInput res, Object grade, Double targetWpm, PpsConfig cfg) {
        PpsConfig c = cfg != null ? cfg : PpsConfig.defaults();
        PronunciationInput.Gopt gopt = res.gopt();
        List<PronunciationInput.Word> words = res.words();
        if (gopt == null || gopt.isEmpty() || words == null || words.isEmpty() || Boolean.TRUE.equals(res.noSpeech())) {
            return Optional.empty();
        }
        List<GoptWord> gws = gopt.words();
        if (gopt.error() != null || gopt.utterance() == null || gws == null || gws.isEmpty()) {
            String reason = gopt.error() != null ? gopt.error() : "Kaldi không trả kết quả gióng hàng";
            return Optional.of(noAlignment(words, grade, reason, c));
        }
        int g = gradeOf(grade);
        Writing writing = res.writing();
        boolean fallback = writing != null && Boolean.TRUE.equals(writing.fallback());

        List<PpsV2Result.Word> scored = new ArrayList<>();
        List<Double> accs = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            PpsV2Result.Word sw = scoreWord(words.get(i), i < gws.size() ? gws.get(i) : null, fallback, c);
            scored.add(sw);
            accs.add(sw.wordAccuracyCalibrated());
        }
        int nPhones = 0;
        for (GoptWord gw : gws) {
            nPhones += gw.phones() == null ? 0 : gw.phones().size();
        }
        List<Timestamp> ts = res.wordTimestamps() == null ? List.of() : res.wordTimestamps();
        Double heuristic = res.scores() == null ? null : res.scores().fluencyHeuristic();
        double[] fp = fluencyProsody(gopt.utterance(), ts, accs, nPhones, heuristic, g, targetWpm, c);
        PpsV2Result.Utterance utt = utteranceScores(gopt.utterance(), accs, fp[0], fp[1], nPhones, writing, g, c);
        return Optional.of(new PpsV2Result(MODE, "ok", null, g, c.get("word.ending_penalty"), utt, scored, calibrationMeta(g, c)));
    }

    /**
     * Kaldi không gióng hàng được (âm thanh quá ngắn, chỉ lẩm bẩm, lỗi ép gióng): không có điểm âm học nào để chấm lại, nên mọi
     * điểm đặt 0.0 và đánh dấu status = no_alignment để người xem biết đây không phải điểm đo được.
     */
    static PpsV2Result noAlignment(List<PronunciationInput.Word> words, Object grade, String reason, PpsConfig cfg) {
        int g = gradeOf(grade);
        PpsV2Result.Utterance zero = new PpsV2Result.Utterance(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        List<PpsV2Result.Word> ws = new ArrayList<>();
        for (PronunciationInput.Word w : words) {
            String st = w.whisper() == null || w.whisper().isEmpty() ? "ok" : w.whisper();
            ws.add(new PpsV2Result.Word(w.word() == null ? "" : w.word(), st, 0.0, 0.0, 0.0, false, List.of()));
        }
        return new PpsV2Result(MODE, "no_alignment", reason, g, cfg.get("word.ending_penalty"), zero, ws, calibrationMeta(g, cfg));
    }

    public static PpsV2Result.CalibrationConfig calibrationMeta(Object grade, PpsConfig cfg) {
        return new PpsV2Result.CalibrationConfig(MODE, gradeOf(grade), cfg.get("word.ending_penalty"), cfg.changed());
    }

    // ---------- tiện ích ----------

    private static double clip(double x, double lo, double hi) {
        return Math.min(hi, Math.max(lo, x));
    }

    /** Trung bình như {@code statistics.fmean} của Python: cộng chính xác rồi mới làm tròn, để điểm làm tròn khớp lab ở các ca hoà. */
    private static double mean(List<Double> xs) {
        BigDecimal s = BigDecimal.ZERO;
        for (double x : xs) {
            s = s.add(new BigDecimal(x));
        }
        return s.doubleValue() / xs.size();
    }

    private static int firstNonZero(Integer a, int b, int c) {
        if (a != null && a != 0) {
            return a;
        }
        return b != 0 ? b : c;
    }

    /** Làm tròn như {@code round(x, n)} của Python: theo giá trị nhị phân chính xác của x, hoà thì về số chẵn. */
    static double round(double x, int digits) {
        return new BigDecimal(x).setScale(digits, RoundingMode.HALF_EVEN).doubleValue();
    }
}
