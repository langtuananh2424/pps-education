package vn.com.pps.education.common.pronunciation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chấm bài nói tự do (không có bài viết mẫu) cho khối 7 và 8: bốn trụ cột P, F, GV, TR. Port từ {@code spontaneous_scorer.py} của
 * PPS Speaking Lab (kiểm tra khớp bằng {@code spontaneous_golden.json}).
 *
 * <pre>
 * Total = 0.30·P + 0.25·F + 0.25·GV + 0.20·TR, nhân chốt chặn độ dài, kẹp trong 0-10.   (trọng số chỉnh được trong PpsConfig)
 * </pre>
 * P (phát âm) và F (trôi chảy) lấy từ {@link PpsHybridScorer}. GV (ngữ pháp, từ vựng) và TR (đúng đề) do Claude chấm trên transcript
 * và câu hỏi của đề ({@code SpontaneousLlmService}); lớp này chỉ nhận kết quả đó, không gọi mạng.
 *
 * <p>Khác mã mẫu của đặc tả: không có Claude (chưa cấu hình hoặc gọi lỗi) thì KHÔNG bịa GV = TR = 6.0 mà trả
 * {@code status = llm_unavailable}, {@code total = null}, P và F vẫn có; bài quá ngắn tính theo đặc tả (dưới 4 từ hoặc nói dưới 3.0
 * giây); P và F lấy từ engine v2 đủ chốt chặn.
 */
public final class SpontaneousScorer {

    public static final String MODE = "pps_spontaneous_v2";

    private static final Pattern VIETNAMESE = Pattern.compile(
            "[ăâđêôơưĂÂĐÊÔƠƯ]|[ạảãàáấầẩẫậắằẳẵặẹẻẽèéếềểễệịỉĩìíọỏõòóốồổỗộớờởỡợụủũùúứừửữựỳỵỷỹý]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern REFUSE = Pattern.compile(
            "\\b(i\\s+(do\\s*n'?t|don'?t|do\\s+not)\\s+know|no\\s+idea|i\\s+have\\s+no\\s+idea|not\\s+sure|i\\s+can'?t\\s+(say|answer))\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern WORD = Pattern.compile("[A-Za-z']+");

    /**
     * Điểm GV và TR do Claude chấm (hoặc {@code error} khi không chấm được). Lưu cùng kết quả bài để tính lại điểm khi đổi tham số
     * mà không phải gọi lại Claude.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Llm(Double taskResponse, Double grammarVocab, List<String> grammarErrors, List<String> goodPoints, String feedbackVi,
                      String model, String error) {

        public static Llm failed(String error) {
            return new Llm(null, null, List.of(), List.of(), "", null, error);
        }

        boolean usable() {
            return (error == null || error.isEmpty()) && taskResponse != null && grammarVocab != null;
        }
    }

    /** Điểm bốn trụ cột thang 0-10. {@code total} null khi chưa có điểm GV/TR. Các trường chỉ có ở bài chấm đủ thì null ở bài thoát sớm. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Result(String mode, String status, int grade, List<Double> weights, Double total, double pronunciation, double fluency,
                         Double grammarVocab, Double taskResponse, String feedback, List<String> grammarErrors, List<String> goodPoints,
                         Double wpm, Double wordMeanAccuracy, Integer endingSoundErrors, Double lengthGate, Integer nWords) {
    }

    private SpontaneousScorer() {
    }

    // ---------- tiện ích ----------

    static List<Double> weights(PpsConfig cfg) {
        return List.of(cfg.get("spontaneous.weights.pronunciation"), cfg.get("spontaneous.weights.fluency"),
                cfg.get("spontaneous.weights.grammar_vocab"), cfg.get("spontaneous.weights.task_response"));
    }

    /** Khối 8 dùng chuẩn khối 8; mọi khối khác (6, 7, other, rỗng) dùng chuẩn khối 7. */
    public static int gradeKey(Object grade) {
        return PpsHybridScorer.gradeOf(grade) == 8 ? 8 : 7;
    }

    static int wordCount(String text) {
        Matcher m = WORD.matcher(text == null ? "" : text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** Thời lượng nói thực tế: từ đầu từ đầu tiên đến cuối từ cuối cùng; không có mốc thì dùng độ dài file. */
    static double speechSeconds(List<PronunciationInput.Timestamp> ts, double audioSeconds) {
        if (ts.size() >= 2) {
            return Math.max(0.0, ts.get(ts.size() - 1).end() - ts.get(0).start());
        }
        if (ts.size() == 1) {
            return Math.max(0.0, ts.get(0).end() - ts.get(0).start());
        }
        return audioSeconds;
    }

    /** Tốc độ nói (từ/phút); dưới 3 mốc từ thì lấy 60 (không đo được). */
    public static double wpmOf(List<PronunciationInput.Timestamp> ts) {
        if (ts.size() < 3) {
            return 60.0;
        }
        double span = Math.max(0.5, ts.get(ts.size() - 1).end() - ts.get(0).start());
        return ts.size() / span * 60.0;
    }

    public static boolean isSpontaneous(PronunciationInput res) {
        return PronunciationInput.MODE_UNSCRIPTED.equals(res.mode());
    }

    private static String transcript(PronunciationInput res) {
        return res.whisperTranscript() == null ? "" : res.whisperTranscript().strip();
    }

    private static List<PronunciationInput.Timestamp> stamps(PronunciationInput res) {
        return res.wordTimestamps() == null ? List.of() : res.wordTimestamps();
    }

    private static boolean isRefusal(String text, int n) {
        return REFUSE.matcher(text).find() && n <= 6;
    }

    private static double clip10(double x) {
        return PpsHybridScorer.round(Math.min(10.0, Math.max(0.0, x)), 2);
    }

    private static Result early(String status, double total, double p, double f, double gv, double tr, String feedback, int g, PpsConfig cfg) {
        return new Result(MODE, status, g, weights(cfg), total, p, f, gv, tr, feedback, List.of(), List.of(), null, null, null, null, null);
    }

    // ---------- cổng thoát sớm ----------

    /** Thoát sớm trước khi chấm: sai ngôn ngữ, quá ngắn. Rỗng là bài đủ điều kiện chấm (kể cả câu từ chối, xử lý ở {@link #score}). */
    public static Optional<Result> preGate(PronunciationInput res, Object grade, PpsConfig cfg) {
        PpsConfig c = cfg != null ? cfg : PpsConfig.defaults();
        int g = gradeKey(grade);
        String text = transcript(res);
        int n = wordCount(text);
        if (VIETNAMESE.matcher(text).find()) {
            return Optional.of(early("wrong_language", 1.0, 1.0, 1.0, 1.0, 0.0, "Vui lòng trả lời hoàn toàn bằng tiếng Anh.", g, c));
        }
        if (isRefusal(text, n)) {
            return Optional.empty();   // câu từ chối: vẫn chấm P và F, GV và TR đặt cố định ở score()
        }
        double minHard = c.get("spontaneous.min_words_hard");
        double audio = res.audioSeconds() == null ? 0.0 : res.audioSeconds();
        if (n < minHard || speechSeconds(stamps(res), audio) < c.get("spontaneous.min_speech_seconds")) {
            double total = PpsHybridScorer.round(1.5 + 0.5 * Math.min(1.0, n / minHard), 2);
            return Optional.of(early("too_short", total, 2.0, 2.0, 1.0, 1.0, "Câu trả lời quá ngắn, chưa đủ ý để đánh giá.", g, c));
        }
        return Optional.empty();
    }

    /** Bài có cần hỏi Claude không: có transcript đủ điều kiện và không phải câu từ chối. */
    public static boolean needsLlm(PronunciationInput res, Object grade, PpsConfig cfg) {
        if (preGate(res, grade, cfg).isPresent()) {
            return false;
        }
        String text = transcript(res);
        return !isRefusal(text, wordCount(text));
    }

    // ---------- chấm điểm ----------

    /**
     * Điểm bốn trụ cột của một bài nói tự do. Rỗng nếu không phải bài nói tự do, không có tiếng nói, hoặc chưa có điểm GOPT.
     *
     * @param llm kết quả chấm GV/TR của Claude, hoặc null / có {@code error} khi chưa có
     */
    public static Optional<Result> score(PronunciationInput res, Object grade, Llm llm, PpsConfig cfg) {
        if (!isSpontaneous(res) || Boolean.TRUE.equals(res.noSpeech())) {
            return Optional.empty();
        }
        PpsConfig c = cfg != null ? cfg : PpsConfig.defaults();
        int g = gradeKey(grade);
        Optional<Result> gate = preGate(res, g, c);
        if (gate.isPresent()) {
            return gate;
        }
        Optional<PpsV2Result> v2opt = PpsHybridScorer.score(res, g, c.get("spontaneous.target_wpm." + g), c);
        if (v2opt.isEmpty()) {
            return Optional.empty();
        }
        PpsV2Result v2 = v2opt.get();
        double p = v2.utterance().accuracyCalibrated();
        double f = v2.utterance().fluencyCalibrated();
        String text = transcript(res);
        int n = wordCount(text);

        String status = "ok";
        Double gv = null;
        Double tr = null;
        String feedback = "";
        List<String> errors = new ArrayList<>();
        List<String> good = new ArrayList<>();
        if (isRefusal(text, n)) {
            status = "refused";
            gv = 2.0;
            tr = 0.0;
            feedback = "Em chưa trả lời câu hỏi. Hãy thử nói một câu đơn giản bằng tiếng Anh, ví dụ \"I think ...\".";
        } else if (llm != null && llm.usable()) {
            gv = clip10(llm.grammarVocab());
            tr = clip10(llm.taskResponse());
            feedback = llm.feedbackVi() == null ? "" : llm.feedbackVi();
            errors.addAll(llm.grammarErrors() == null ? List.of() : llm.grammarErrors());
            good.addAll(llm.goodPoints() == null ? List.of() : llm.goodPoints());
        } else {
            status = "llm_unavailable";
            String why = llm != null && llm.error() != null && !llm.error().isEmpty() ? llm.error() : "chưa gọi Haiku";
            feedback = "Chưa chấm được ngữ pháp, từ vựng và đúng đề: " + why;
        }

        if (v2.status().equals("no_alignment") && status.equals("ok")) {
            status = "no_alignment";
        }
        double minWords = c.get("spontaneous.min_words." + g);
        double gateLen = n >= minWords ? 1.0 : (g == 7 ? 0.50 + 0.50 * n / minWords : 0.40 + 0.60 * n / minWords);
        Double total = null;
        List<Double> w = weights(c);
        if (gv != null) {
            double raw = w.get(0) * p + w.get(1) * f + w.get(2) * gv + w.get(3) * tr;
            total = PpsHybridScorer.round(Math.min(10.0, Math.max(0.0, raw * gateLen)), 2);
        }
        int endingErrors = (int) v2.words().stream().filter(PpsV2Result.Word::endingSoundError).count();
        return Optional.of(new Result(MODE, status, g, w, total, p, f, gv, tr, feedback, errors, good,
                PpsHybridScorer.round(wpmOf(stamps(res)), 1), v2.utterance().wordMeanAccuracyCalibrated(), endingErrors,
                PpsHybridScorer.round(gateLen, 3), n));
    }
}
