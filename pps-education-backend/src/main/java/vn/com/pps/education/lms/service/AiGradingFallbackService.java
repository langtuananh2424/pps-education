package vn.com.pps.education.lms.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.com.pps.education.academic.domain.Curriculum;
import vn.com.pps.education.common.AiTokenUsage;
import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.common.ReflexContentOverlap;
import vn.com.pps.education.common.ReflexV2Scoring;
import vn.com.pps.education.common.ReflexV2Tags;
import vn.com.pps.education.common.ReflexV2Task;
import vn.com.pps.education.common.SpeechMeter;
import vn.com.pps.education.exception.ReflexAudioRejectedException;
import vn.com.pps.education.lms.domain.AiGradingTokenUsage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — điều phối "thử gọi AI trực tiếp (Claude/Groq/Azure)
 * trước, lỗi thì rơi về 9Router" cho 3 luồng chấm thật: Essay (UC-40/41), Writing câu ngắn + Speaking
 * (UC-23b). KHÔNG đổi hành vi của {@link WritingAiGradingService}/{@link ReflexV2AiGradingService} —
 * chỉ gọi 2 overload spike-only đã có sẵn ở đó, cộng 1 pipeline MỚI cho Speaking (xem
 * {@link #gradeReflexSpeakingWithFallback}).
 *
 * Nguyên tắc fallback CẢ CỤM cho Speaking (đã xác nhận với người dùng, KHÔNG fallback từng phần) — luồng
 * mới (Groq+Claude+Azure) và luồng cũ (Gemini/9Router) có kiến trúc khác hẳn nhau (mới: 3 nhà cung cấp
 * độc lập; cũ: Gemini chấm cả nội dung lẫn phát âm trong 1-2 lượt gọi nghe audio trực tiếp) — trộn giữa
 * chừng sẽ ra trạng thái lai không rõ ràng. Lỗi BẤT KỲ bước nào trong luồng mới → bỏ hết, gọi lại TOÀN BỘ
 * {@link ReflexV2AiGradingService#gradeSpeaking} gốc (không override) từ đầu.
 *
 * 2 cảnh báo nghiệp vụ (transcript bịa theo tốc độ nói, nói khác hẳn bài viết) được KIỂM TRA LẠI ở đây
 * bằng chính audio + transcript Groq, và THROW THẲNG {@link ReflexAudioRejectedException} — KHÔNG rơi về
 * fallback, vì đây là vấn đề của bản ghi âm/nội dung, không phải vấn đề của nhà cung cấp AI (luồng cũ gặp
 * cùng audio này nhiều khả năng cũng sẽ từ chối tương tự).
 */
@Service
public class AiGradingFallbackService {

    private static final Logger log = LoggerFactory.getLogger(AiGradingFallbackService.class);

    /** Mirror {@link ReflexV2AiGradingService#MAX_WORDS_PER_SECOND} — không truy cập được (private), định nghĩa lại cùng giá trị đã thống nhất với người training. */
    private static final double MAX_WORDS_PER_SECOND = 3.5;
    private static final String MSG_AUDIO_UNREADABLE = "Không đọc được bản ghi âm này. Em hãy ghi âm lại câu trả lời.";
    private static final String MSG_SPOKE_DIFFERENT = "Em đã nói khác với bài em đã viết. Hãy nói lại đúng câu trả lời em đã viết ở Bước 1.";

    private final WritingAiGradingService writingAiGradingService;
    private final ReflexV2AiGradingService reflexV2AiGradingService;
    private final ReflexV2Prompts reflexV2Prompts;
    private final ClaudeAiClient claudeAiClient;
    private final GroqSpeechClient groqSpeechClient;
    private final AzurePronunciationClient azurePronunciationClient;
    private final AudioTranscoder audioTranscoder;

    @Value("${app.ai-grading.claude-direct.model:claude-haiku-5-5}")
    private String claudeModel;

    @Value("${app.ai-grading.claude-direct.enabled:false}")
    private boolean directPipelineEnabled;

    @Value("${app.ai-grading.reflex-v2.unlock-includes-pronunciation:false}")
    private boolean unlockIncludesPronunciation;

    public AiGradingFallbackService(WritingAiGradingService writingAiGradingService,
                                     ReflexV2AiGradingService reflexV2AiGradingService,
                                     ReflexV2Prompts reflexV2Prompts, ClaudeAiClient claudeAiClient,
                                     GroqSpeechClient groqSpeechClient, AzurePronunciationClient azurePronunciationClient,
                                     AudioTranscoder audioTranscoder) {
        this.writingAiGradingService = writingAiGradingService;
        this.reflexV2AiGradingService = reflexV2AiGradingService;
        this.reflexV2Prompts = reflexV2Prompts;
        this.claudeAiClient = claudeAiClient;
        this.groqSpeechClient = groqSpeechClient;
        this.azurePronunciationClient = azurePronunciationClient;
        this.audioTranscoder = audioTranscoder;
    }

    // ===================== Essay (UC-40/41) =====================

    public WritingAiGradingService.GradeResult gradeEssayWithFallback(String essayText, String taskPrompt,
                                                                        Curriculum curriculum, List<String> keyGrammarIds) {
        if (directPipelineEnabled && claudeAiClient.isConfigured()) {
            try {
                BiFunction<String, String, NineRouterAiClient.ChatResult> caller =
                        (system, user) -> claudeAiClient.chatWithFinishReason(system, user, claudeModel);
                WritingAiGradingService.GradeResult result = writingAiGradingService.grade(essayText, taskPrompt, curriculum, keyGrammarIds, caller);
                if (result != null) {
                    return result;
                }
                log.warn("AiGradingFallbackService: Claude trực tiếp chấm Essay thất bại, rơi về 9Router.");
            } catch (RuntimeException e) {
                log.warn("AiGradingFallbackService: Claude trực tiếp chấm Essay lỗi ({}), rơi về 9Router.", e.getMessage());
            }
        }
        return writingAiGradingService.grade(essayText, taskPrompt, curriculum, keyGrammarIds);
    }

    // ===================== Writing câu ngắn (UC-23b Bước 1) =====================

    public ReflexV2AiGradingService.WritingResult gradeReflexWritingWithFallback(ReflexV2Task task, String question, String text) {
        if (directPipelineEnabled && claudeAiClient.isConfigured()) {
            try {
                BiFunction<String, String, NineRouterAiClient.AiJsonResponse> caller =
                        (system, user) -> claudeAiClient.chatJson(system, user, claudeModel, ClaudeAiClient.MAX_TOKENS_SHORT);
                ReflexV2AiGradingService.WritingResult result = reflexV2AiGradingService.gradeWriting(task, question, text, caller);
                if (result != null) {
                    return result;
                }
                log.warn("AiGradingFallbackService: Claude trực tiếp chấm Writing câu ngắn thất bại, rơi về 9Router.");
            } catch (RuntimeException e) {
                log.warn("AiGradingFallbackService: Claude trực tiếp chấm Writing câu ngắn lỗi ({}), rơi về 9Router.", e.getMessage());
            }
        }
        return reflexV2AiGradingService.gradeWriting(task, question, text);
    }

    // ===================== Speaking (UC-23b Bước 2) — fallback CẢ CỤM =====================

    public ReflexV2AiGradingService.SpeakingResult gradeReflexSpeakingWithFallback(
            ReflexV2Task task, String question, byte[] audioBytes, String mimeType,
            ReflexV2AiGradingService.Step1Anchor anchor, ReflexV2AiGradingService.SpeakingUsageSink usageSink) {
        if (directPipelineEnabled && claudeAiClient.isConfigured() && groqSpeechClient.isConfigured() && azurePronunciationClient.isConfigured()) {
            try {
                ReflexV2AiGradingService.SpeakingResult direct = gradeSpeakingDirectPipeline(task, question, audioBytes, mimeType, anchor, usageSink);
                if (direct != null) {
                    return direct;
                }
                log.warn("AiGradingFallbackService: pipeline trực tiếp (Groq+Claude+Azure) chấm Speaking thất bại, rơi về 9Router (fallback cả cụm).");
            } catch (ReflexAudioRejectedException e) {
                throw e; // vấn đề audio/nội dung, không phải vấn đề nhà cung cấp AI — không fallback, ném thẳng.
            } catch (RuntimeException e) {
                log.warn("AiGradingFallbackService: pipeline trực tiếp chấm Speaking lỗi ({}), rơi về 9Router (fallback cả cụm).", e.getMessage());
            }
        }
        return reflexV2AiGradingService.gradeSpeaking(task, question, audioBytes, mimeType, anchor, usageSink);
    }

    /**
     * Pipeline MỚI: Groq STT (độc lập, không qua 9Router) → 2 rào chắn nghiệp vụ (bịa theo tốc độ, nói
     * khác bài viết — throw thẳng nếu trúng, KHÔNG fallback) → Claude chấm nội dung (FC/LR/GRA hoặc
     * GV/DM từ transcript, không audio) → Azure chấm phát âm riêng (scripted mode, ReferenceText =
     * transcript Groq) → GHÉP điểm Azure vào đúng vị trí tiêu chí Phát âm (P), thay vì để Claude tự đoán
     * (Claude không nghe được audio nên không có cơ sở chấm P). Trả {@code null} nếu bất kỳ bước AI nào
     * (Groq/Claude/Azure) lỗi — caller tự fallback cả cụm, xem {@link #gradeReflexSpeakingWithFallback}.
     */
    private ReflexV2AiGradingService.SpeakingResult gradeSpeakingDirectPipeline(
            ReflexV2Task task, String question, byte[] audioBytes, String mimeType,
            ReflexV2AiGradingService.Step1Anchor anchor, ReflexV2AiGradingService.SpeakingUsageSink usageSink) {

        long pipelineStartedAt = System.currentTimeMillis();
        long groqStartedAt = pipelineStartedAt;
        GroqSpeechClient.TranscriptionResult transcription = groqSpeechClient.transcribe(audioBytes, mimeType, "answer.webm", null);
        log.info("AiGradingFallbackService: Groq STT xong sau {}ms.", System.currentTimeMillis() - groqStartedAt);
        if (transcription == null) {
            return null;
        }
        String transcript = transcription.text() == null ? "" : transcription.text().trim();

        Optional<byte[]> wav = audioTranscoder.toWav(audioBytes, mimeType);
        Optional<SpeechMeter.Measurement> measured = wav.flatMap(SpeechMeter::measure);
        double durationSec = measured.map(SpeechMeter.Measurement::durationSec).orElse((double) task.seconds());
        double speechSec = measured.map(SpeechMeter.Measurement::speechSec).orElse(durationSec);
        double longestPause = measured.map(SpeechMeter.Measurement::longestPauseSec).orElse(0.0);

        // Rào chắn 1: transcript bịa theo tốc độ nói (mirror ReflexV2AiGradingService, xem Javadoc lớp).
        if (measured.isPresent()) {
            double rateBase = measured.get().spanSec() > 0 ? measured.get().spanSec() : measured.get().speechSec();
            int words = transcript.isEmpty() ? 0 : transcript.split("\\s+").length;
            if (words > Math.max(3, rateBase * MAX_WORDS_PER_SECOND)) {
                throw new ReflexAudioRejectedException(MSG_AUDIO_UNREADABLE);
            }
        }
        // Rào chắn 2: nói khác hẳn bài viết Bước 1.
        if (anchor.text() != null && !anchor.text().isBlank() && !transcript.isEmpty()) {
            double overlap = ReflexContentOverlap.contentOverlap(anchor.text(), transcript);
            if (overlap < ReflexContentOverlap.minOverlapFor(anchor.text())) {
                throw new ReflexAudioRejectedException(MSG_SPOKE_DIFFERENT);
            }
        }

        // Bổ sung 2026-10-22 (đã xác nhận với người dùng, tối ưu độ trễ) — Claude (chấm nội dung) và Azure
        // (chấm phát âm) ĐỘC LẬP với nhau, cả 2 chỉ cần transcript Groq vừa có, KHÔNG cần kết quả của
        // nhau — chạy song song thay vì nối tiếp, giảm độ trễ tổng từ (Groq+Claude+Azure) xuống
        // (Groq+max(Claude,Azure)). KHÔNG đổi Azure sang unscripted để chạy song song với Groq — đã verify
        // unscripted kém tin cậy hẳn (xem Javadoc AzurePronunciationClient#assess), đánh đổi đó không đáng.
        String systemPrompt = reflexV2Prompts.speakingSystem(task);
        String userPrompt = reflexV2Prompts.speakingUser(task, question, anchor.text() == null ? "" : anchor.text(),
                transcript, List.of(), List.of(), durationSec, speechSec, longestPause);
        long parallelStartedAt = System.currentTimeMillis();
        java.util.concurrent.CompletableFuture<NineRouterAiClient.AiJsonResponse> claudeFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        claudeAiClient.chatJson(systemPrompt, userPrompt, claudeModel, ClaudeAiClient.MAX_TOKENS_LONG_RUBRIC));
        java.util.concurrent.CompletableFuture<AzurePronunciationClient.Result> azureFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(() -> azurePronunciationClient.assess(audioBytes, mimeType, transcript));
        NineRouterAiClient.AiJsonResponse claudeResponse = claudeFuture.join();
        AzurePronunciationClient.Result pronunciation = azureFuture.join();
        log.info("AiGradingFallbackService: Claude+Azure chạy song song xong sau {}ms.", System.currentTimeMillis() - parallelStartedAt);
        if (claudeResponse == null) {
            return null;
        }
        usageSink.record(AiGradingTokenUsage.Step.SPEAKING, claudeResponse.usage());
        if (!pronunciation.hasScores()) {
            return null;
        }

        JsonNode data;
        try {
            data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(extractJson(claudeResponse.content()));
        } catch (Exception e) {
            log.warn("AiGradingFallbackService: parse kết quả Claude (Speaking) thất bại. {}", e.getMessage());
            return null;
        }

        List<String> gradedCodes = task.criteria();
        ReflexV2Scoring.ScoreSet scored = ReflexV2Scoring.computeScores(task, gradedCodes, data);
        boolean noSpeech = transcript.isEmpty();
        List<ReflexV2Scoring.Highlight> highlights = ReflexV2Scoring.locateHighlights(transcript, data.path("highlights"), task.grade());
        int spokenRed = ReflexV2Scoring.countRed(highlights);
        ReflexV2Scoring.SpokenMatch spokenMatch = noSpeech ? null : ReflexV2Scoring.sameAsWritten(anchor.text(), transcript);
        boolean keepStep1Grammar = spokenMatch != null && spokenMatch.same();

        String grammarCode = task.grammarCode();
        List<ReflexV2Scoring.CriterionScore> rawScores = new ArrayList<>();
        for (ReflexV2Scoring.CriterionScore c : scored.criteria()) {
            if (c.code().equals(ReflexV2Task.PRONUNCIATION_CODE)) {
                // Claude không nghe audio nên không có cơ sở chấm P — thay thẳng bằng điểm Azure thật.
                rawScores.add(new ReflexV2Scoring.CriterionScore(ReflexV2Task.PRONUNCIATION_CODE,
                        pronunciation.overallScore(), false, null, List.of()));
            } else if (c.code().equals(grammarCode)) {
                rawScores.add(keepStep1Grammar ? ReflexV2Scoring.keptFromStep1(grammarCode, anchor.grammarPercent())
                        : ReflexV2Scoring.regradeGrammar(c, spokenRed, anchor.grammarPercent(), noSpeech));
            } else {
                rawScores.add(c);
            }
        }
        double spokenSec = measured.map(m -> m.spanSec() > 0 ? m.spanSec() : m.speechSec()).orElse(0.0);
        ReflexV2Scoring.LexicalEvidence lexical = noSpeech ? null : ReflexV2Scoring.lexicalCeiling(transcript, task.lexicalSeconds());
        ReflexV2Scoring.FluencyEvidence fluency = noSpeech ? null : ReflexV2Scoring.fluencyCeiling(transcript, longestPause);
        ReflexV2Scoring.LengthEvidence length = ReflexV2Scoring.lengthGate(task, transcript, spokenSec);
        ReflexV2Scoring.Readback readback = noSpeech ? null : ReflexV2Scoring.writtenVsSpoken(anchor.text(), transcript);
        List<ReflexV2Scoring.CriterionScore> capped = ReflexV2Scoring.applyGrammarFloor(grammarCode,
                ReflexV2Scoring.applyErrorCaps(task, rawScores, highlights,
                        new ReflexV2Scoring.ErrorEvidence(lexical, fluency, length, readback)));
        if (keepStep1Grammar) {
            capped = ReflexV2Scoring.keepGrammarFromStep1(grammarCode, capped, anchor.grammarPercent());
        }
        // Bổ sung 2026-10-22 (đã xác nhận với người dùng) — cổng "Lạc đề" trước đây chỉ hiện thông báo,
        // không có trần % thật — xem Javadoc ReflexV2Scoring#applySpeakingOffTopicCap.
        capped = ReflexV2Scoring.applySpeakingOffTopicCap(task, capped, scored.gates());
        capped = ReflexV2Scoring.snapToGrid10(capped);

        List<ReflexV2Scoring.CriterionScore> forUnlock = new ArrayList<>();
        List<CriteriaScoreItem> items = new ArrayList<>();
        for (ReflexV2Scoring.CriterionScore cs : capped) {
            boolean referenceOnly = cs.code().equals(ReflexV2Task.PRONUNCIATION_CODE) && !unlockIncludesPronunciation;
            if (!referenceOnly) {
                forUnlock.add(cs);
            }
            String label = ReflexV2Tags.CRITERIA_EN.get(cs.code());
            items.add(new CriteriaScoreItem(referenceOnly ? label + " (tham khảo)" : label, cs.percent()));
        }
        int finalPercent = ReflexV2Scoring.average(capped);
        int unlockPercent = ReflexV2Scoring.average(forUnlock);
        String feedback = ReflexV2Scoring.trimFeedback(data.path("feedback").asText(""), highlights);
        String gateNote = ReflexV2Scoring.buildGateNote(task, scored.gates(), ReflexV2Scoring.wordCount(transcript), true);
        if (transcription.lowConfidenceWarning() != null) {
            gateNote = gateNote.isEmpty() ? transcription.lowConfidenceWarning() : gateNote + " " + transcription.lowConfidenceWarning();
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("taskId", task.id());
        audit.put("rubricFormat", task.rubricFormat());
        audit.put("gradedBy", "claude-direct+groq+azure");
        audit.put("model", claudeResponse.model());
        audit.put("gates", scored.gates());
        audit.put("spokenRedCount", spokenRed);
        audit.put("grammarKeptFromStep1", keepStep1Grammar);
        audit.put("pronunciationSource", "azure-direct");
        audit.put("pronunciationRaw", pronunciation.rawJson());
        // Bổ sung 2026-10-22 (đã xác nhận với người dùng) — chi tiết từng từ/âm vị của Azure (y hệt dữ liệu
        // spike test), lưu vào audit để toResponse() lấy ra trả cho FE hiển thị bảng phiên âm IPA cho học
        // sinh (xem ReflexSequentialGradingService#toResponse + ReflexQuestionProgressResponse#speakingPronunciationWords).
        audit.put("pronunciationWords", pronunciation.words());
        audit.put("groqLowConfidenceWarning", transcription.lowConfidenceWarning());
        long totalMs = System.currentTimeMillis() - pipelineStartedAt;
        audit.put("pipelineTotalMs", totalMs);
        log.info("AiGradingFallbackService: pipeline trực tiếp (Groq+Claude+Azure) chấm Speaking xong sau {}ms.", totalMs);

        return new ReflexV2AiGradingService.SpeakingResult(ReflexV2Scoring.toErrMarkup(transcript, highlights), items,
                unlockPercent, finalPercent, feedback, ReflexV2Scoring.trimHint(data.path("hint").asText(""), highlights),
                scored.gates(), audit);
    }

    private String extractJson(String rawText) {
        int start = rawText.indexOf('{');
        int end = rawText.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Claude không trả về JSON hợp lệ: " + rawText);
        }
        return rawText.substring(start, end + 1);
    }
}
