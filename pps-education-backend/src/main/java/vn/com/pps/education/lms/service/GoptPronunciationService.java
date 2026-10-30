package vn.com.pps.education.lms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.com.pps.education.common.pronunciation.PpsHybridScorer;
import vn.com.pps.education.common.pronunciation.PpsV2Result;
import vn.com.pps.education.common.pronunciation.PronunciationInput;
import vn.com.pps.education.common.pronunciation.TargetMerger;
import vn.com.pps.education.lms.dto.PronunciationPhonemeDetail;
import vn.com.pps.education.lms.dto.PronunciationWordDetail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Chấm phát âm bằng GOPT + PPS Hybrid v2, thay Azure Pronunciation Assessment. Trả về cùng khung kết quả
 * ({@link AzurePronunciationClient.Result}) để luồng chấm Speaking không phải đổi cách dùng.
 *
 * <p>Luồng: transcript của Groq → tách từ (số thành chữ) → service suy luận ({@link PronunciationEngineClient}: wav2vec2 IPA, Kaldi
 * gióng hàng, GOPT) → {@link PpsHybridScorer} chấm lại điểm từ, âm, trôi chảy, ngữ điệu → đổi sang thang 0-100 của Azure.
 * Câu mục tiêu theo lab: có bài viết Bước 1 thì ghép bài viết với lời nói ({@link TargetMerger}, chế độ {@code writing_merged}) để từ
 * đọc lệch không bị coi là "nói thêm" rồi chấm cao; không có bài viết (câu không có bước Viết) thì chấm trên chính lời STT nghe được.
 *
 * <p>Trả {@code Result.error(...)} (không có điểm) khi service lỗi, không có tín hiệu tiếng nói, hoặc Kaldi không gióng hàng được
 * (âm thanh quá ngắn, chỉ lẩm bẩm). Đó là điểm "không đo được", không phải 0: luồng chấm xử lý như khi Azure lỗi.
 */
@Service
public class GoptPronunciationService {

    private static final Logger log = LoggerFactory.getLogger(GoptPronunciationService.class);

    /** Từ có điểm dưới mức này (thang 10) gắn nhãn Mispronunciation, tương đương ngưỡng 60/100 hay dùng ở Azure. */
    static final double MISPRONUNCIATION_BELOW = 6.0;
    private static final Pattern HAS_ALNUM = Pattern.compile("[A-Za-z0-9]");

    /** ARPAbet (bảng phone của Kaldi) sang IPA, để bảng phiên âm cho học sinh vẫn là IPA như khi dùng Azure. */
    private static final Map<String, String> ARPABET_TO_IPA = Map.ofEntries(
            Map.entry("AA", "ɑ"), Map.entry("AE", "æ"), Map.entry("AH", "ʌ"), Map.entry("AO", "ɔ"), Map.entry("AW", "aʊ"),
            Map.entry("AY", "aɪ"), Map.entry("B", "b"), Map.entry("CH", "tʃ"), Map.entry("D", "d"), Map.entry("DH", "ð"),
            Map.entry("EH", "ɛ"), Map.entry("ER", "ɝ"), Map.entry("EY", "eɪ"), Map.entry("F", "f"), Map.entry("G", "ɡ"),
            Map.entry("HH", "h"), Map.entry("IH", "ɪ"), Map.entry("IY", "i"), Map.entry("JH", "dʒ"), Map.entry("K", "k"),
            Map.entry("L", "l"), Map.entry("M", "m"), Map.entry("N", "n"), Map.entry("NG", "ŋ"), Map.entry("OW", "oʊ"),
            Map.entry("OY", "ɔɪ"), Map.entry("P", "p"), Map.entry("R", "ɹ"), Map.entry("S", "s"), Map.entry("SH", "ʃ"),
            Map.entry("T", "t"), Map.entry("TH", "θ"), Map.entry("UH", "ʊ"), Map.entry("UW", "u"), Map.entry("V", "v"),
            Map.entry("W", "w"), Map.entry("Y", "j"), Map.entry("Z", "z"), Map.entry("ZH", "ʒ"));

    private final PronunciationEngineClient engine;
    private final PpsConfigHolder configHolder;
    private final ObjectMapper objectMapper;

    public GoptPronunciationService(PronunciationEngineClient engine, PpsConfigHolder configHolder, ObjectMapper objectMapper) {
        this.engine = engine;
        this.configHolder = configHolder;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return engine.isConfigured();
    }

    /** Chấm không có bài viết: câu mục tiêu là transcript (câu không có bước Viết). */
    public AzurePronunciationClient.Result assess(byte[] audioBytes, String mimeType, String transcript,
                                                  List<GroqSpeechClient.Word> wordStamps, int grade) {
        return assess(audioBytes, mimeType, transcript, wordStamps, grade, null);
    }

    /**
     * @param wordStamps  mốc thời gian từng từ của STT (rỗng thì trôi chảy tính theo heuristic)
     * @param grade       khối 6-9 của đề (khối 9 tính như khối 7 vì v2 mới hiệu chuẩn cho khối 6-8)
     * @param writtenText bài viết Bước 1; trống hoặc null nếu câu không có bước Viết
     */
    public AzurePronunciationClient.Result assess(byte[] audioBytes, String mimeType, String transcript,
                                                  List<GroqSpeechClient.Word> wordStamps, int grade, String writtenText) {
        boolean guided = writtenText != null && !TargetMerger.wordsOf(writtenText).isEmpty();
        List<String> said = saidWords(wordStamps, transcript);
        TargetMerger.Result merge = null;
        boolean fallback = false;
        List<String> words;
        if (guided) {
            merge = TargetMerger.merge(writtenText, said);
            words = merge.words().stream().map(TargetMerger.MergedWord::word).toList();
            if (words.isEmpty()) {
                return AzurePronunciationClient.Result.error("GOPT: STT không nghe ra từ nào để chấm");
            }
            if (garbled(said, merge)) {
                // STT nghe ra chữ vô nghĩa dù có thể vẫn có tiếng nói: tạm dùng bài viết làm câu mẫu, sau khi engine đo xong âm thanh có rõ
                // và có tiếng đủ không (như lab) mới giữ; các từ bị đánh dấu unverified nên điểm bị chặn trần và trừ chốt chặn.
                fallback = true;
                words = TargetMerger.wordsOf(writtenText);
                merge = new TargetMerger.Result(words.stream().map(w -> new TargetMerger.MergedWord(w, "writing", null, null)).toList(),
                        List.of(), words.size(), 0, 0, 0);
            }
        } else {
            words = TargetMerger.wordsOf(transcript);
            if (words.isEmpty()) {
                return AzurePronunciationClient.Result.error("GOPT: transcript không có từ nào để chấm phát âm");
            }
        }
        PronunciationEngineClient.Analysis analysis = engine.analyze(audioBytes, mimeType, "answer.webm", words);
        if (analysis == null) {
            return AzurePronunciationClient.Result.error("GOPT: không gọi được service suy luận phát âm");
        }
        if (Boolean.TRUE.equals(analysis.noSignal())) {
            return AzurePronunciationClient.Result.error("GOPT: âm thanh gần như không có tín hiệu");
        }
        if (analysis.ipa() == null || analysis.ipa().words() == null || analysis.ipa().words().size() != words.size()) {
            return AzurePronunciationClient.Result.error("GOPT: số từ IPA không khớp câu mục tiêu");
        }
        if (fallback && !clearEnoughForFallback(analysis.audio())) {
            return AzurePronunciationClient.Result.error("GOPT: STT không nhận ra lời nói và âm thanh không đủ rõ để dùng bài viết thay thế");
        }
        double seconds = analysis.audio() == null ? 0.0 : analysis.audio().seconds();
        List<PronunciationInput.Timestamp> stamps = timestamps(wordStamps);
        List<PronunciationInput.Word> inputWords = guided
                ? toGuidedInputWords(analysis.ipa().words(), merge.words(), fallback) : toInputWords(analysis.ipa().words());
        PronunciationInput.Writing writing = guided
                ? new PronunciationInput.Writing(merge.nWriting(), merge.nOmitted(), merge.nExpanded(), fallback) : null;
        PronunciationInput input = new PronunciationInput(
                inputWords, stamps, writing,
                new PronunciationInput.Scores((double) fluencyHeuristic(stamps, seconds)), analysis.gopt(), false);

        Optional<PpsV2Result> scored = PpsHybridScorer.score(input, grade, null, configHolder.get());
        if (scored.isEmpty()) {
            return AzurePronunciationClient.Result.error("GOPT: service không trả điểm âm vị");
        }
        PpsV2Result v2 = scored.get();
        if ("no_alignment".equals(v2.status())) {
            log.warn("GoptPronunciationService: Kaldi không gióng hàng được — {}", v2.reason());
            return AzurePronunciationClient.Result.error("GOPT: không gióng hàng được âm thanh (" + v2.reason() + ")");
        }
        PpsV2Result.Utterance u = v2.utterance();
        return new AzurePronunciationClient.Result(toPercent(u.accuracyCalibrated()), toPercent(u.fluencyCalibrated()),
                toPercent(u.completenessCalibrated()), toPercent(u.prosodicCalibrated()), toPercent(u.totalCalibrated()),
                toWordDetails(v2.words()), rawJson(v2, analysis), 1, AzurePronunciationClient.Result.ENGINE_GOPT);
    }

    // ---------- ghép bài viết với lời nói (chế độ writing_merged của lab) ----------

    /** Các từ STT nghe được theo thứ tự: mốc từ nếu có, không thì tách từ transcript (STT dự phòng không trả mốc). */
    static List<String> saidWords(List<GroqSpeechClient.Word> stamps, String transcript) {
        List<String> out = new ArrayList<>();
        if (stamps != null) {
            for (GroqSpeechClient.Word w : stamps) {
                if (w.word() != null) {
                    out.add(w.word());
                }
            }
        }
        return out.isEmpty() ? TargetMerger.wordsOf(transcript) : out;
    }

    private static final Pattern FOREIGN_LETTER = Pattern.compile("[\\p{L}&&[^A-Za-z]]");

    /**
     * STT nghe thành chữ ngoại ngữ (ký tự có dấu: lời nghe được là rác) hoặc gần như không nghe ra gì, và không có từ bài viết nào khớp:
     * port điều kiện {@code garbled} của lab. Khi đó không thể tin STT để dựng câu mục tiêu.
     */
    static boolean garbled(List<String> said, TargetMerger.Result merge) {
        long heard = said.stream().filter(w -> HAS_ALNUM.matcher(w).find()).count();
        long foreign = said.stream().filter(w -> FOREIGN_LETTER.matcher(w).find()).count();
        long matched = merge.words().stream().filter(m -> "writing".equals(m.origin()) && m.heardAs() == null).count();
        boolean anyWriting = merge.words().stream().anyMatch(m -> "writing".equals(m.origin()));
        boolean mostlyForeign = !said.isEmpty() && (double) foreign / said.size() >= 0.2 && matched < 0.5 * Math.max(1, merge.nWriting());
        return mostlyForeign || (heard <= 3 && !anyWriting);
    }

    /** Lab chỉ dùng bài viết thay STT khi âm thanh rõ (chênh tiếng/ồn từ 20 dB) và có ít nhất 1,5 giây tiếng. */
    static boolean clearEnoughForFallback(PronunciationEngineClient.AudioInfo audio) {
        return audio != null && audio.snrDb() >= 20.0 && audio.activeSeconds() >= 1.5;
    }

    /**
     * Trạng thái từng từ so với lời STT, đúng lab: unverified (dùng bài viết thay STT), extra (nói thêm), diff (STT nghe thành từ khác),
     * ok. Chặn trần điểm từ theo trạng thái nằm ở {@link PpsHybridScorer}.
     */
    static List<PronunciationInput.Word> toGuidedInputWords(List<PronunciationEngineClient.IpaWord> ipaWords,
                                                            List<TargetMerger.MergedWord> merged, boolean fallback) {
        List<PronunciationInput.Word> out = new ArrayList<>();
        for (int i = 0; i < ipaWords.size(); i++) {
            PronunciationEngineClient.IpaWord w = ipaWords.get(i);
            TargetMerger.MergedWord m = merged.get(i);
            String status = fallback ? "unverified" : "expanded".equals(m.origin()) ? "extra" : m.heardAs() != null ? "diff" : "ok";
            out.add(new PronunciationInput.Word(w.word(), w.expectedIpa(), w.heardIpa(), w.issues(), w.score() == null ? 0 : w.score(), status));
        }
        return out;
    }

    // ---------- ánh xạ ----------

    /** Một từ mà wav2vec2 không nghe thấy âm nào khớp (điểm IPA ≤ 20, từ ≥ 3 âm): STT "tự sửa" nên chặn trần điểm từ (như lab). */
    static List<PronunciationInput.Word> toInputWords(List<PronunciationEngineClient.IpaWord> ipaWords) {
        List<PronunciationInput.Word> out = new ArrayList<>();
        for (PronunciationEngineClient.IpaWord w : ipaWords) {
            int score = w.score() == null ? 0 : w.score();
            long phones = w.expectedIpa() == null ? 0 : w.expectedIpa().chars().filter(c -> "ˈˌː ".indexOf(c) < 0).count();
            String status = phones >= 3 && score <= 20 ? "diff" : "ok";
            out.add(new PronunciationInput.Word(w.word(), w.expectedIpa(), w.heardIpa(), w.issues(), score, status));
        }
        return out;
    }

    static List<PronunciationInput.Timestamp> timestamps(List<GroqSpeechClient.Word> stamps) {
        List<PronunciationInput.Timestamp> out = new ArrayList<>();
        if (stamps != null) {
            for (GroqSpeechClient.Word w : stamps) {
                if (w.word() != null && HAS_ALNUM.matcher(w.word()).find()) {
                    out.add(new PronunciationInput.Timestamp(w.word().trim(), w.startSec(), w.endSec()));
                }
            }
        }
        return out;
    }

    /** Chỉ số trôi chảy thô theo tốc độ nói và số lần ngắt dài (dùng khi thiếu mốc từ). Port từ {@code fluency()} của lab. */
    static int fluencyHeuristic(List<PronunciationInput.Timestamp> stamps, double durationSec) {
        if (stamps.isEmpty() || durationSec <= 0) {
            return 0;
        }
        double wpm = stamps.size() / durationSec * 60;
        int pauses = 0;
        for (int i = 1; i < stamps.size(); i++) {
            if (stamps.get(i).start() - stamps.get(i - 1).end() > 0.6) {
                pauses++;
            }
        }
        double s = 100 - Math.abs(wpm - 110) * 0.4 - pauses * 8;
        return (int) Math.max(0, Math.min(100, Math.rint(s)));
    }

    static int toPercent(double score10) {
        return (int) Math.max(0, Math.min(100, Math.round(score10 * 10)));
    }

    static List<PronunciationWordDetail> toWordDetails(List<PpsV2Result.Word> words) {
        List<PronunciationWordDetail> out = new ArrayList<>();
        for (PpsV2Result.Word w : words) {
            List<PronunciationPhonemeDetail> phonemes = new ArrayList<>();
            for (PpsV2Result.Phone p : w.phones()) {
                phonemes.add(new PronunciationPhonemeDetail(ARPABET_TO_IPA.getOrDefault(p.p(), p.p().toLowerCase()),
                        Math.round(p.phoneScoreCalibrated() / 2.0 * 1000) / 10.0));   // thang 0-2 sang 0-100, 1 chữ số thập phân
            }
            String errorType = w.wordAccuracyCalibrated() < MISPRONUNCIATION_BELOW ? "Mispronunciation" : "None";
            out.add(new PronunciationWordDetail(w.word(), Math.round(w.wordAccuracyCalibrated() * 100) / 10.0, errorType, phonemes));
        }
        return out;
    }

    private String rawJson(PpsV2Result v2, PronunciationEngineClient.Analysis analysis) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("engine", "gopt-pps-v2");
        raw.put("v2", v2);
        raw.put("audio", analysis.audio());
        raw.put("latencySeconds", analysis.latencySeconds());
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            return "{\"engine\":\"gopt-pps-v2\"}";
        }
    }
}
