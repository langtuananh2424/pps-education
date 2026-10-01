package vn.com.pps.education.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.com.pps.education.common.AiTokenUsage;
import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.common.ReflexContentOverlap;
import vn.com.pps.education.common.ReflexV2Scoring;
import vn.com.pps.education.common.ReflexV2Tags;
import vn.com.pps.education.common.ReflexV2Task;
import vn.com.pps.education.common.SpeechMeter;
import vn.com.pps.education.domain.AiGradingTokenUsage;
import vn.com.pps.education.exception.ReflexAudioRejectedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21 (cập nhật theo bản bàn giao 26/9) — UC-23b
 * (Video phản xạ): chấm bằng bộ tiêu chí Speaking v2 (Khối 6-9) do người training bàn giao, thay cặp
 * {@link ReflexWritingGrammarAiGradingService}/{@link ReflexSpeakingContentAiGradingService} CHO ĐÚNG các
 * tổ hợp Khối/track mà {@link ReflexV2Task#forGradeTrack} hỗ trợ (xem {@link ReflexSequentialGradingService}).
 * Mirror {@code grading.js} + {@code prompts.js} trong {@code ma-nguon-tham-chieu/}.
 *
 * Kiến trúc: (1) chấm bài viết → điểm Ngữ pháp Bước 1 (làm SÀN cho bài nói); (2) phiên âm MÙ (không đề,
 * không rubric, không bài viết — biết trước sẽ khiến AI "sửa" từ phát âm sai theo ngữ cảnh); (3) chặn bản
 * ghi bịa / nói khác bài viết TRƯỚC khi chấm (HTTP 422, không tốn lượt chấm thứ ba); (4) chấm bài nói trên
 * transcript cố định + audio gốc. Từ 23/9 Bước 4 chấm lại CẢ Ngữ pháp từ transcript, rồi hệ thống áp các trần
 * đo được từ transcript ({@link ReflexV2Scoring#applyErrorCaps}). Từ 29/9 (cách B, đã xác nhận với người dùng):
 * nếu học sinh nói lại đúng bài đã viết ({@link ReflexV2Scoring#sameAsWritten}) thì điểm Ngữ pháp GIỮ NGUYÊN
 * điểm Bước 1 — điểm AI chấm lại chỉ dùng khi nói khác bài viết; trần 60% chỉ đếm lỗi đỏ NGỮ PHÁP
 * ({@link ReflexV2Scoring#countRed}). Điểm quy đổi làm ở backend ({@link ReflexV2Scoring}), AI chỉ trả checkpoint.
 *
 * Cấu hình bắt buộc theo người training: model {@code gemini-3.6-flash} (KHÔNG model dự phòng — đã kiểm
 * tra ở {@link NineRouterAiClient}), {@code temperature=0}, thinking mức medium (chọn qua tên model
 * {@code ag/gemini-3.6-flash-medium} trên 9Router). Tiêu chí Phát âm CHƯA được kiểm chứng (xem
 * {@code TRANG-THAI-BAN-GIAO.md} mục 2) nên mặc định KHÔNG tính vào điểm mở khoá câu tiếp theo — chỉ hiển
 * thị kèm nhãn "tham khảo"; bật lại bằng {@code app.ai-grading.reflex-v2.unlock-includes-pronunciation}.
 *
 * Lỗi gọi AI/parse trả {@code null} (caller báo "AI chấm lỗi", học sinh nộp lại). Bản ghi bị từ chối
 * ném {@link ReflexAudioRejectedException} (HTTP 422, không trả điểm).
 */
@Service
public class ReflexV2AiGradingService {

    private static final Logger log = LoggerFactory.getLogger(ReflexV2AiGradingService.class);

    static final String MSG_AUDIO_UNREADABLE = "Không đọc được bản ghi âm này. Em hãy ghi âm lại câu trả lời.";
    static final String MSG_SPOKE_DIFFERENT = "Em đã nói khác với bài em đã viết. Hãy nói lại đúng câu trả lời em đã viết ở Bước 1.";

    /** Người nói nhanh nhất cũng không quá ~3,5 từ/giây — quá ngưỡng này là transcript bịa. */
    private static final double MAX_WORDS_PER_SECOND = 3.5;
    private static final int MAX_SUSPECT_WORDS = 40;
    private static final int MAX_AUDIT_DEVIANT_WORDS = 25;

    /** Khoá trong {@code speaking_audit}: lần ghi âm cần giáo viên soát điểm Ngữ pháp (V198). */
    public static final String AUDIT_GRAMMAR_REVIEW_REQUIRED = "grammarReviewRequired";
    /** Khoá trong {@code speaking_audit}: các đoạn transcript bị tô đỏ ngữ pháp (V198). */
    public static final String AUDIT_GRAMMAR_REVIEW_QUOTES = "grammarReviewQuotes";

    private final NineRouterAiClient nineRouterAiClient;
    private final ObjectMapper objectMapper;
    private final ReflexV2Prompts prompts;
    private final AudioTranscoder audioTranscoder;

    @Value("${app.ai-grading.reflex-v2.model:ag/gemini-3.6-flash-medium}")
    private String model;

    @Value("${app.ai-grading.reflex-v2.unlock-includes-pronunciation:false}")
    private boolean unlockIncludesPronunciation;

    public ReflexV2AiGradingService(NineRouterAiClient nineRouterAiClient, ObjectMapper objectMapper,
                                    ReflexV2Prompts prompts, AudioTranscoder audioTranscoder) {
        this.nineRouterAiClient = nineRouterAiClient;
        this.objectMapper = objectMapper;
        this.prompts = prompts;
        this.audioTranscoder = audioTranscoder;
    }

    /**
     * @param step1Percent   điểm Bước 1 = trung bình các tiêu chí chấm ở bước viết (đã áp trần lỗi đỏ và trần theo lỗi đã tô), làm tròn xuống bội 5.
     * @param grammarPercent điểm Ngữ pháp Bước 1 — Bước 2 giữ nguyên khi nói giống bài viết, nói khác thì làm SÀN (nửa điểm này).
     * @param redCount       số lỗi đỏ NGỮ PHÁP trong bài viết ({@link ReflexV2Scoring#countRed}) — căn cứ trần 60%.
     * @param markedText     bài viết gốc đánh dấu lỗi bằng markup {@code {{err}}...{{/err}}} (FE hiện có).
     * @param gateNote       câu giải thích cổng chặn (backend soạn) — rỗng nếu không có cổng nào kích hoạt.
     * @param hint           (bản 30/9, §D.5) cách luyện cho học sinh tự luyện, TÁCH khỏi {@code feedback} — rỗng khi bài không có lỗi.
     * @param usage          (V192) chi phí token của CHÍNH lượt chấm viết này, caller lưu kèm ngữ cảnh học sinh.
     */
    public record WritingResult(int step1Percent, int grammarPercent, int redCount, List<CriteriaScoreItem> criteria,
                                String markedText, String feedback, String hint, String gateNote, List<String> gates,
                                Map<String, Object> audit, AiTokenUsage usage) {
    }

    /** Điểm Ngữ pháp và nội dung bài viết ở Bước 1, làm mốc cho bài nói (sàn điểm, so đọc lệch, cổng nói khác bài viết). */
    public record Step1Anchor(int grammarPercent, String text) {
    }

    /**
     * @param unlockPercent điểm dùng để mở khoá câu tiếp theo (mặc định KHÔNG gồm Phát âm).
     * @param finalPercent  điểm cuối theo công thức của người training (gồm cả Phát âm) — chỉ để tham khảo/audit.
     * @param hint          (bản 30/9, §D.5) cách luyện cho học sinh tự luyện, TÁCH khỏi {@code feedback} — rỗng khi bài không có lỗi.
     */
    public record SpeakingResult(String markedTranscript, List<CriteriaScoreItem> criteria, int unlockPercent,
                                 int finalPercent, String feedback, String hint, List<String> gates, Map<String, Object> audit) {
    }

    /**
     * V192 — nơi nhận chi phí từng lượt gọi AI của bước nói, TÁCH RIÊNG theo bước ({@code TRANSCRIPTION} = phiên
     * âm mù, {@code SPEAKING} = chấm nói) thay vì cộng gộp: cả hai cùng gửi 1 file audio nên nhìn tổng sẽ không
     * biết phần nào do audio, phần nào do rubric dạng chữ — đúng câu hỏi cần trả lời khi tối ưu.
     *
     * Gọi NGAY sau mỗi lượt AI trả về, TRƯỚC mọi bước có thể dừng giữa chừng (từ chối 422 / parse lỗi / trả
     * {@code null}) — token đã tốn thật từ lúc AI trả lời, không phụ thuộc việc chấm có đi hết hay không. Caller
     * (tầng biết học sinh/bài/câu hỏi) ghi qua {@link AiGradingTokenUsageRecorder}.
     */
    @FunctionalInterface
    public interface SpeakingUsageSink {
        void record(AiGradingTokenUsage.Step step, AiTokenUsage usage);
    }

    // ===================== Bước 1: chấm bài viết =====================

    public WritingResult gradeWriting(ReflexV2Task task, String question, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        NineRouterAiClient.AiJsonResponse response = nineRouterAiClient.chatJson(
                prompts.writingSystem(task), prompts.writingUser(question, text), model,
                prompts.gradingSchema(task.writingCriteria()));
        if (response == null) {
            log.warn("ReflexV2AiGradingService: 9Router chấm bài viết thất bại.");
            return null;
        }
        try {
            JsonNode data = parseJson(response.content());
            List<ReflexV2Scoring.Highlight> highlights = ReflexV2Scoring.locateHighlights(text, data.path("highlights"), task.grade());
            int redCount = ReflexV2Scoring.countRed(highlights);
            ReflexV2Scoring.ScoreSet scored = ReflexV2Scoring.applyRedCap(task.grammarCode(),
                    ReflexV2Scoring.computeScores(task, task.writingCriteria(), data), redCount);
            List<ReflexV2Scoring.CriterionScore> capped = ReflexV2Scoring.applyErrorCaps(
                    task, scored.criteria(), highlights, ReflexV2Scoring.ErrorEvidence.NONE);
            int grammarPercent = capped.stream()
                    .filter(c -> c.code().equals(task.grammarCode())).mapToInt(ReflexV2Scoring.CriterionScore::percent)
                    .findFirst().orElse(0);
            List<CriteriaScoreItem> items = new ArrayList<>();
            for (ReflexV2Scoring.CriterionScore c : capped) {
                items.add(new CriteriaScoreItem(ReflexV2Tags.CRITERIA_EN.get(c.code()), c.percent()));
            }
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("taskId", task.id());
            audit.put("rubricFormat", task.rubricFormat());
            audit.put("model", response.model());
            audit.put("gates", scored.gates());
            audit.put("redCount", redCount);
            audit.put("grammarCappedByRedErrors", capped.stream().anyMatch(ReflexV2Scoring.CriterionScore::cappedByRedErrors));
            audit.put("caps", capNotes(capped));
            audit.put("countingNotes", data.path("counting_notes").asText(""));
            return new WritingResult(ReflexV2Scoring.average(capped), grammarPercent, redCount, items,
                    ReflexV2Scoring.toErrMarkup(text, highlights), ReflexV2Scoring.trimFeedback(data.path("feedback").asText(""), highlights),
                    ReflexV2Scoring.trimHint(data.path("hint").asText(""), highlights),
                    ReflexV2Scoring.buildGateNote(task, scored.gates(), ReflexV2Scoring.wordCount(text)), scored.gates(), audit,
                    response.usage());
        } catch (IOException | IllegalStateException e) {
            log.warn("ReflexV2AiGradingService: parse kết quả chấm bài viết thất bại. {}", e.getMessage());
            return null;
        }
    }

    // ===================== Bước 2: phiên âm mù + chấm nói =====================

    /**
     * @param usageSink nhận chi phí từng lượt gọi AI — xem {@link SpeakingUsageSink}.
     * @throws ReflexAudioRejectedException khi bản ghi không đọc được hoặc nói khác bài đã viết (HTTP 422).
     * @return kết quả, hoặc {@code null} nếu gọi AI/parse thất bại.
     */
    public SpeakingResult gradeSpeaking(ReflexV2Task task, String question, byte[] audioBytes, String mimeType,
                                        Step1Anchor anchor, SpeakingUsageSink usageSink) {
        Optional<byte[]> wav = isWav(audioBytes) ? Optional.of(audioBytes) : audioTranscoder.toWav(audioBytes, mimeType);
        Optional<SpeechMeter.Measurement> measured = wav.flatMap(SpeechMeter::measure);
        byte[] audioForModel = wav.orElse(audioBytes);
        String mimeForModel = wav.isPresent() ? "audio/wav" : mimeType;
        if (measured.isEmpty()) {
            log.warn("ReflexV2AiGradingService: KHÔNG đo được tiếng nói (chưa chuyển được sang WAV) — bộ chặn transcript bịa KHÔNG chạy cho lượt này.");
        }
        double durationSec = measured.map(SpeechMeter.Measurement::durationSec).orElse((double) task.seconds());

        // ---- Lượt A: phiên âm MÙ ----
        NineRouterAiClient.AiJsonResponse transcription = nineRouterAiClient.chatWithAudioJson(
                prompts.transcriptionSystem(), prompts.transcriptionUser(durationSec), audioForModel, mimeForModel, model,
                prompts.transcriptionSchema());
        if (transcription == null) {
            log.warn("ReflexV2AiGradingService: 9Router phiên âm thất bại.");
            return null;
        }
        usageSink.record(AiGradingTokenUsage.Step.TRANSCRIPTION, transcription.usage());
        String rawTranscript;
        double aiSpeechSec;
        double aiLongestPause;
        boolean audioQualityInsufficient;
        int wordAuditRows;
        List<String> suspectWords = new ArrayList<>();
        try {
            JsonNode t = parseJson(transcription.content());
            rawTranscript = t.path("transcript").asText("").trim();
            aiSpeechSec = t.path("speech_seconds").asDouble(0);
            aiLongestPause = t.path("longest_pause_seconds").asDouble(0);
            audioQualityInsufficient = t.path("audio_quality_insufficient").asBoolean(false);
            wordAuditRows = t.path("word_audit").size();
            for (JsonNode w : t.path("suspect_words")) {
                if (suspectWords.size() < MAX_SUSPECT_WORDS && !w.asText("").isBlank()) {
                    suspectWords.add(w.asText().trim());
                }
            }
        } catch (IOException e) {
            log.warn("ReflexV2AiGradingService: parse kết quả phiên âm thất bại. {}", e.getMessage());
            return null;
        }

        // Nếu đo được tín hiệu thật thì dùng số đo, không tin ước lượng của AI (số của AI gộp cả im lặng cuối bài).
        double speechSec = measured.map(SpeechMeter.Measurement::speechSec).orElse(aiSpeechSec);
        double longestPause = measured.map(SpeechMeter.Measurement::longestPauseSec).orElse(aiLongestPause);
        int words = rawTranscript.isEmpty() ? 0 : rawTranscript.split("\\s+").length;
        // Chặn transcript bịa: đo trên spanSec (thời gian nói thật) — speechSec chỉ cộng khung có rung thanh
        // nên với mic sạch chỉ bằng ~55% thời gian nói, dùng nó sẽ báo nhầm bài bình thường là bịa.
        if (measured.isPresent()) {
            double rateBase = measured.get().spanSec() > 0 ? measured.get().spanSec() : measured.get().speechSec();
            if (words > Math.max(3, rateBase * MAX_WORDS_PER_SECOND)) {
                log.warn("ReflexV2AiGradingService: transcript nghi bịa: {} từ trong {} giây nói — từ chối.", words, rateBase);
                // Lỗi HỆ THỐNG, không phải lỗi học sinh: chấm tiếp trên transcript rỗng sẽ cho 0% oan.
                // Dừng hẳn và yêu cầu ghi âm lại.
                throw new ReflexAudioRejectedException(MSG_AUDIO_UNREADABLE);
            }
        }
        String transcript = (measured.isPresent() && !measured.get().hasSpeech()) ? "" : rawTranscript;

        // Học sinh phải nói lại bài đã viết; nói khác hẳn → yêu cầu nói lại, không tốn lượt chấm.
        Double overlap = null;
        if (anchor.text() != null && !transcript.isEmpty()) {
            overlap = ReflexContentOverlap.contentOverlap(anchor.text(), transcript);
            if (overlap < ReflexContentOverlap.minOverlapFor(anchor.text())) {
                throw new ReflexAudioRejectedException(MSG_SPOKE_DIFFERENT);
            }
        }

        // So transcript với bài viết Bước 1 — phải tính TRƯỚC khi dựng prompt vì danh sách từ đọc lệch được đưa
        // vào lượt chấm để tô lỗi trong transcript.
        ReflexV2Scoring.Readback readbackRaw = ReflexV2Scoring.writtenVsSpoken(anchor.text(), transcript);
        List<String> deviantWords = readbackRaw == null ? List.of() : readbackRaw.words();

        // ---- Lượt B: chấm bài nói trên transcript cố định (chấm lại CẢ Ngữ pháp, xem Javadoc lớp) ----
        String grammar = task.grammarCode();
        List<String> gradedCodes = task.criteria();
        NineRouterAiClient.AiJsonResponse graded = nineRouterAiClient.chatWithAudioJson(
                prompts.speakingSystem(task),
                prompts.speakingUser(task, question, anchor.text() == null ? "" : anchor.text(),
                        transcript, suspectWords, deviantWords, durationSec, speechSec, longestPause),
                audioForModel, mimeForModel, model, prompts.gradingSchema(gradedCodes));
        if (graded == null) {
            log.warn("ReflexV2AiGradingService: 9Router chấm bài nói thất bại.");
            return null;
        }
        usageSink.record(AiGradingTokenUsage.Step.SPEAKING, graded.usage());
        try {
            JsonNode data = parseJson(graded.content());
            ReflexV2Scoring.ScoreSet scored = ReflexV2Scoring.computeScores(task, gradedCodes, data);

            // Không nói được gì (im lặng / không đủ dữ liệu) thì không có sàn Ngữ pháp, không có bằng chứng phát âm.
            boolean noSpeech = data.path("insufficient_data").asBoolean(false)
                    || transcript.replaceAll("\\.\\.\\.\\d+s|[\\s.,(){}\\[\\]…?-]", "").isEmpty();
            List<ReflexV2Scoring.Highlight> highlights = ReflexV2Scoring.locateHighlights(transcript, data.path("highlights"), task.grade());
            int spokenRed = ReflexV2Scoring.countRed(highlights);
            // Cách B (2026-09-29): nói lại đúng bài đã viết → giữ nguyên điểm Ngữ pháp Bước 1, không để AI chấm
            // lại cùng một câu ra điểm khác; nói khác bài viết mới dùng điểm chấm lại từ transcript.
            ReflexV2Scoring.SpokenMatch spokenMatch = noSpeech ? null : ReflexV2Scoring.sameAsWritten(anchor.text(), transcript);
            boolean keepStep1Grammar = spokenMatch != null && spokenMatch.same();

            List<ReflexV2Scoring.CriterionScore> raw = new ArrayList<>();
            int gradedGrammarPercent = 0;
            for (ReflexV2Scoring.CriterionScore c : scored.criteria()) {
                if (c.code().equals(grammar)) {
                    gradedGrammarPercent = c.percent();
                    raw.add(keepStep1Grammar ? ReflexV2Scoring.keptFromStep1(grammar, anchor.grammarPercent())
                            : ReflexV2Scoring.regradeGrammar(c, spokenRed, anchor.grammarPercent(), noSpeech));
                } else {
                    raw.add(c);
                }
            }
            double spokenSec = measured.map(m -> m.spanSec() > 0 ? m.spanSec() : m.speechSec()).orElse(0.0);
            ReflexV2Scoring.LexicalEvidence lexical = noSpeech ? null : ReflexV2Scoring.lexicalCeiling(transcript, task.seconds());
            ReflexV2Scoring.FluencyEvidence fluency = noSpeech ? null : ReflexV2Scoring.fluencyCeiling(transcript, longestPause);
            ReflexV2Scoring.LengthEvidence length = ReflexV2Scoring.lengthGate(task, transcript, spokenSec);
            ReflexV2Scoring.Readback readback = noSpeech ? null : readbackRaw;
            List<ReflexV2Scoring.CriterionScore> capped = ReflexV2Scoring.applyGrammarFloor(grammar,
                    ReflexV2Scoring.applyErrorCaps(task, raw, highlights,
                            new ReflexV2Scoring.ErrorEvidence(lexical, fluency, length, readback)));
            if (keepStep1Grammar) {
                // Trần theo lỗi đã tô trên transcript không được đụng tới điểm đã giữ từ Bước 1.
                capped = ReflexV2Scoring.keepGrammarFromStep1(grammar, capped, anchor.grammarPercent());
            }
            // Quy trình phòng đào tạo 29/9: chỉ MỘT lượt phiên âm nên vài từ nghe nhầm có thể thành lỗi đỏ ngữ pháp
            // giả. Bài đi nhánh chấm lại mà có ≥2 lỗi đỏ ngữ pháp → điểm Ngữ pháp chỉ là tham khảo, giáo viên soát
            // lại (kể cả khi điểm chấm lại vốn đã ≤60% nên trần không đổi con số — nghe nhầm vẫn có thể đã kéo điểm).
            boolean grammarReviewRequired = !keepStep1Grammar && !noSpeech && spokenRed >= 2;

            List<ReflexV2Scoring.CriterionScore> forUnlock = new ArrayList<>();
            List<CriteriaScoreItem> items = new ArrayList<>();
            for (ReflexV2Scoring.CriterionScore cs : capped) {
                boolean referenceOnly = cs.code().equals(ReflexV2Task.PRONUNCIATION_CODE) && !unlockIncludesPronunciation;
                if (!referenceOnly) {
                    forUnlock.add(cs);
                }
                String label = ReflexV2Tags.CRITERIA_EN.get(cs.code());
                if (referenceOnly) {
                    label += " (tham khảo)";
                }
                items.add(new CriteriaScoreItem(label, cs.percent()));
            }
            int finalPercent = ReflexV2Scoring.average(capped);
            int unlockPercent = ReflexV2Scoring.average(forUnlock);

            String feedback = ReflexV2Scoring.trimFeedback(data.path("feedback").asText(""), highlights);
            // Đếm từ trên transcript CUỐI CÙNG (đã bỏ khi đo được là im lặng), không phải bản thô của AI.
            String gateNote = ReflexV2Scoring.buildGateNote(task, scored.gates(), ReflexV2Scoring.wordCount(transcript), true);
            if (!gateNote.isEmpty()) {
                feedback = feedback.isEmpty() ? gateNote : feedback + " " + gateNote;
            }

            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("taskId", task.id());
            audit.put("rubricFormat", task.rubricFormat());
            audit.put("model", graded.model());
            audit.put("transcribeModel", transcription.model());
            audit.put("suspectWords", suspectWords);
            audit.put("wordAuditRows", wordAuditRows);
            audit.put("speechSeconds", speechSec);
            audit.put("longestPauseSeconds", longestPause);
            audit.put("contentOverlap", overlap);
            audit.put("gates", scored.gates());
            audit.put("grammarStep1Percent", anchor.grammarPercent());
            audit.put("grammarRegradedPercent", gradedGrammarPercent);
            audit.put("grammarKeptFromStep1", keepStep1Grammar);
            audit.put("spokenMatch", toMap(spokenMatch));
            audit.put("spokenRedCount", spokenRed);
            audit.put(AUDIT_GRAMMAR_REVIEW_REQUIRED, grammarReviewRequired);
            audit.put(AUDIT_GRAMMAR_REVIEW_QUOTES, grammarReviewRequired ? ReflexV2Scoring.grammarRedQuotes(transcript, highlights) : List.of());
            audit.put("finalPercentWithPronunciation", finalPercent);
            audit.put("unlockIncludesPronunciation", unlockIncludesPronunciation);
            audit.put("audioMeasured", measured.isPresent());
            audit.put("audioTranscodedToWav", wav.isPresent() && !isWav(audioBytes));
            audit.put("audioQualityInsufficient", audioQualityInsufficient);
            audit.put("lexical", toMap(lexical));
            audit.put("fluency", toMap(fluency));
            audit.put("length", toMap(length));
            audit.put("readback", readbackRaw == null ? null : toMap(new ReflexV2Scoring.Readback(readbackRaw.total(), readbackRaw.count(),
                    readbackRaw.ratio(), readbackRaw.words().stream().limit(MAX_AUDIT_DEVIANT_WORDS).toList())));
            audit.put("caps", capNotes(capped));
            audit.put("countingNotes", data.path("counting_notes").asText(""));
            return new SpeakingResult(ReflexV2Scoring.toErrMarkup(transcript, highlights), items, unlockPercent,
                    finalPercent, feedback, ReflexV2Scoring.trimHint(data.path("hint").asText(""), highlights), scored.gates(), audit);
        } catch (IOException | IllegalStateException e) {
            log.warn("ReflexV2AiGradingService: parse kết quả chấm bài nói thất bại. {}", e.getMessage());
            return null;
        }
    }

    // ===================== Câu đã sửa (V141) =====================

    /**
     * V141 giữ nguyên tính năng "câu trả lời đã sửa lỗi" (hiện từ lần nộp thứ 3 vẫn chưa đạt) nhưng sinh ở
     * LỆNH GỌI RIÊNG — theo người training, bộ mới cấm AI để dạng đúng/cách sửa lọt vào {@code feedback}
     * của lượt chấm. CHỈ sửa lỗi trong chính câu học sinh viết, không viết câu mẫu khác.
     */
    /**
     * V192 — câu sửa mẫu kèm chi phí token của chính lượt gọi sinh ra nó. Đây là lượt gọi AI THỨ TƯ của
     * 1 câu hỏi (sau chấm viết / phiên âm / chấm nói) và chỉ chạy khi học sinh đã trượt từ lần 3 trở đi,
     * nên nếu không đo riêng sẽ không ai ngờ nó tồn tại trong hoá đơn.
     */
    public record CorrectedAnswer(String text, AiTokenUsage usage) {
    }

    public CorrectedAnswer generateCorrectedAnswer(String question, String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return null;
        }
        String system = "Bạn là giáo viên tiếng Anh. Sửa lại CHÍNH câu trả lời của học sinh thành bản đúng ngữ pháp: "
                + "CHỈ sửa lỗi ngữ pháp, chính tả và từ vựng dùng sai; GIỮ NGUYÊN cấu trúc câu và ý tưởng gốc của học sinh — "
                + "TUYỆT ĐỐI KHÔNG viết lại thành câu trả lời khác, KHÔNG thêm ý mới, KHÔNG nâng cấp từ vựng nếu không phải lỗi sai. "
                + "Chỉ trả về đúng câu đã sửa, không giải thích, không đặt trong dấu ngoặc kép.";
        String user = "Câu hỏi: \"" + question + "\"\nCâu trả lời của học sinh: \"" + answerText + "\"";
        NineRouterAiClient.AiTextResponse response = nineRouterAiClient.chatWithUsage(system, user, model);
        if (response == null || response.content() == null || response.content().isBlank()) {
            return null;
        }
        return new CorrectedAnswer(response.content().trim().replaceAll("^\"|\"$", "").trim(), response.usage());
    }

    // ===================== Tiện ích =====================

    /** Chỉ giữ tiêu chí thực sự bị trần/sàn chạm vào — để giáo viên đối chiếu vì sao điểm lệch so với checkpoint. */
    private static Map<String, List<String>> capNotes(List<ReflexV2Scoring.CriterionScore> criteria) {
        Map<String, List<String>> notes = new LinkedHashMap<>();
        for (ReflexV2Scoring.CriterionScore c : criteria) {
            if (!c.caps().isEmpty()) {
                notes.put(c.code(), c.caps());
            }
        }
        return notes;
    }

    private Map<String, Object> toMap(Object value) {
        return value == null ? null : objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {
        });
    }

    private static boolean isWav(byte[] b) {
        return b != null && b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E';
    }

    /** LLM đôi khi bọc JSON trong khối mã hoặc thêm chữ — cắt từ '{' đầu tới '}' cuối. */
    private JsonNode parseJson(String rawText) throws IOException {
        int start = rawText.indexOf('{');
        int end = rawText.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IOException("Model không trả về JSON hợp lệ: " + rawText);
        }
        return objectMapper.readTree(rawText.substring(start, end + 1));
    }
}
