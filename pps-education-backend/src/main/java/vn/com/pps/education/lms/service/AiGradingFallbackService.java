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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    /** Số lần gọi tối đa cho MỖI bước gọi thẳng Claude (HTTP lỗi, nội dung rỗng, JSON hỏng/thiếu mục đều tính) — hết lần mới rơi về 9Router. */
    @Value("${app.ai-grading.direct-max-attempts:3}")
    private int maxAttempts;

    /** Nghỉ giữa các lần (lần k nghỉ k×delay). Test tạo thẳng bằng constructor nên giá trị mặc định 0 = không nghỉ. */
    @Value("${app.ai-grading.direct-retry-delay-ms:1000}")
    private long retryDelayMillis;

    /**
     * Tổng thời gian tối đa (giây) cho các lần thử lại của MỘT bước gọi thẳng — quá mốc này thì thôi, rơi về 9Router.
     * Cần vì timeout 1 lần gọi có thể lên tới claude-direct.timeout-seconds (150s): 3 lần timeout liên tiếp sẽ vượt hạn
     * nginx 180s của đường đồng bộ và còn chưa kể thời gian 9Router dự phòng. 0 = không giới hạn.
     */
    @Value("${app.ai-grading.direct-total-budget-seconds:60}")
    private int totalBudgetSeconds;

    /**
     * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — pool RIÊNG cho 2 lệnh gọi Claude+Azure chạy song
     * song (xem {@link #gradeSpeakingDirectPipeline}). TRƯỚC ĐÂY {@code CompletableFuture.supplyAsync}
     * không chỉ định Executor → mặc định dùng {@code ForkJoinPool.commonPool()}, 1 pool DÙNG CHUNG CHO CẢ
     * JVM với kích thước mặc định = số core CPU - 1 — nhiều học sinh nộp bài nói cùng lúc trên server vật
     * lý ít core sẽ làm các lệnh gọi Azure xếp hàng chờ thay vì chạy song song thật, mất hẳn lợi ích tối
     * ưu độ trễ. Đây là tác vụ CHỜ MẠNG (I/O-bound, thread rảnh phần lớn thời gian chờ response, không tốn
     * CPU thật) nên kích thước pool nên LỚN HƠN HẲN số core CPU — cố định 64 thread, đủ cho nhiều lớp làm
     * bài đồng thời; daemon thread để không chặn JVM tắt.
     */
    private static final ExecutorService AI_PARALLEL_EXECUTOR = Executors.newFixedThreadPool(64, runnable -> {
        Thread t = new Thread(runnable, "ai-grading-parallel");
        t.setDaemon(true);
        return t;
    });

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
                WritingAiGradingService.GradeResult result = withRetries("Essay",
                        () -> writingAiGradingService.grade(essayText, taskPrompt, curriculum, keyGrammarIds, caller));
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
                ReflexV2AiGradingService.WritingResult result = withRetries("Writing câu ngắn",
                        () -> reflexV2AiGradingService.gradeWriting(task, question, text, caller));
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

    // ===================== Speaking (UC-23b Bước 2) — fallback TỪNG BƯỚC =====================

    public ReflexV2AiGradingService.SpeakingResult gradeReflexSpeakingWithFallback(
            ReflexV2Task task, String question, byte[] audioBytes, String mimeType,
            ReflexV2AiGradingService.Step1Anchor anchor, ReflexV2AiGradingService.SpeakingUsageSink usageSink) {
        if (directPipelineEnabled && (claudeAiClient.isConfigured() || groqSpeechClient.isConfigured() || azurePronunciationClient.isConfigured())) {
            try {
                return gradeSpeakingDirectPipeline(task, question, audioBytes, mimeType, anchor, usageSink);
            } catch (ReflexAudioRejectedException e) {
                throw e; // vấn đề audio/nội dung, không phải vấn đề nhà cung cấp AI — không fallback, ném thẳng.
            } catch (NineRouterStepFailedException e) {
                // 9Router (dự phòng của 1 bước) cũng lỗi — chạy lại toàn bộ luồng 9Router chỉ lặp lại đúng lỗi đó.
                log.warn("AiGradingFallbackService: {} — không chấm được tự động.", e.getMessage());
                return null;
            } catch (RuntimeException e) {
                log.warn("AiGradingFallbackService: pipeline trực tiếp chấm Speaking lỗi ngoài dự kiến ({}), rơi về 9Router (cả cụm).", e.getMessage());
            }
        }
        return reflexV2AiGradingService.gradeSpeaking(task, question, audioBytes, mimeType, anchor, usageSink);
    }

    /** Dự phòng 9Router của 1 bước cũng lỗi — không còn đường nào để chấm tự động. */
    private static final class NineRouterStepFailedException extends RuntimeException {
        NineRouterStepFailedException(String message) {
            super(message);
        }
    }

    private static final String SOURCE_DIRECT = "direct";
    private static final String SOURCE_NINE_ROUTER = "9router";

    /**
     * Pipeline MỚI, mỗi bước có nhà cung cấp trực tiếp riêng và DỰ PHÒNG 9Router riêng (không kéo cả cụm):
     * <ul>
     *   <li>Phiên âm: Groq → lỗi/chưa cấu hình thì 9Router phiên âm mù.</li>
     *   <li>2 rào chắn nghiệp vụ (bịa theo tốc độ, nói khác bài viết) — throw thẳng, KHÔNG fallback.</li>
     *   <li>Chấm nội dung: Claude (transcript, không audio) → lỗi thì 9Router chấm trên audio.</li>
     *   <li>Phát âm: Azure (scripted, ReferenceText = transcript) → lỗi thì lấy điểm Phát âm từ lượt 9Router chấm audio
     *       (Claude không nghe được audio nên điểm P của Claude bị bỏ).</li>
     * </ul>
     * Claude và Azure chạy song song. Ném {@link NineRouterStepFailedException} khi dự phòng 9Router của một bước cũng
     * lỗi. Điểm Phát âm của lượt có bước Azure dự phòng là điểm 9Router tự đoán (ghi trong audit.sources).
     */
    private ReflexV2AiGradingService.SpeakingResult gradeSpeakingDirectPipeline(
            ReflexV2Task task, String question, byte[] audioBytes, String mimeType,
            ReflexV2AiGradingService.Step1Anchor anchor, ReflexV2AiGradingService.SpeakingUsageSink usageSink) {

        long pipelineStartedAt = System.currentTimeMillis();
        Optional<byte[]> wav = audioTranscoder.toWav(audioBytes, mimeType);
        Optional<SpeechMeter.Measurement> measured = wav.flatMap(SpeechMeter::measure);
        byte[] audioForModel = wav.orElse(audioBytes);
        String mimeForModel = wav.isPresent() ? "audio/wav" : mimeType;
        double durationSec = measured.map(SpeechMeter.Measurement::durationSec).orElse((double) task.seconds());

        long groqStartedAt = pipelineStartedAt;
        GroqSpeechClient.TranscriptionResult transcription = groqSpeechClient.isConfigured()
                ? groqSpeechClient.transcribe(audioBytes, mimeType, "answer.webm", null) : null;
        String transcriptionSource = SOURCE_DIRECT;
        String transcript;
        String lowConfidenceWarning = null;
        if (transcription != null) {
            log.info("AiGradingFallbackService: Groq STT xong sau {}ms.", System.currentTimeMillis() - groqStartedAt);
            transcript = transcription.text() == null ? "" : transcription.text().trim();
            lowConfidenceWarning = transcription.lowConfidenceWarning();
        } else {
            log.warn("AiGradingFallbackService: Groq STT lỗi/chưa cấu hình, bước phiên âm rơi về 9Router.");
            String viaNineRouter = reflexV2AiGradingService.transcribeBlind(audioForModel, mimeForModel, durationSec, usageSink);
            if (viaNineRouter == null) {
                throw new NineRouterStepFailedException("Phiên âm: Groq và 9Router đều lỗi");
            }
            transcript = viaNineRouter;
            transcriptionSource = SOURCE_NINE_ROUTER;
        }

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
        java.util.concurrent.CompletableFuture<ClaudeSpeakingJson> claudeFuture = !claudeAiClient.isConfigured()
                ? java.util.concurrent.CompletableFuture.completedFuture(null)
                : java.util.concurrent.CompletableFuture.supplyAsync(() -> withRetries("Speaking (Claude)", () -> {
                    NineRouterAiClient.AiJsonResponse response = claudeAiClient.chatJson(systemPrompt, userPrompt, claudeModel, ClaudeAiClient.MAX_TOKENS_LONG_RUBRIC);
                    if (response == null) {
                        return null;
                    }
                    try {
                        return new ClaudeSpeakingJson(response, new com.fasterxml.jackson.databind.ObjectMapper().readTree(extractJson(response.content())));
                    } catch (Exception e) {
                        log.warn("AiGradingFallbackService: parse kết quả Claude (Speaking) thất bại. {}", e.getMessage());
                        return null;
                    }
                }), AI_PARALLEL_EXECUTOR);
        java.util.concurrent.CompletableFuture<AzurePronunciationClient.Result> azureFuture = !azurePronunciationClient.isConfigured()
                ? java.util.concurrent.CompletableFuture.completedFuture(null)
                : java.util.concurrent.CompletableFuture.supplyAsync(() -> azurePronunciationClient.assessSegmented(audioBytes, mimeType, transcript,
                        transcription != null ? transcription.segments() : List.of()), AI_PARALLEL_EXECUTOR);
        ClaudeSpeakingJson claudeResult = claudeFuture.join();
        AzurePronunciationClient.Result pronunciation = azureFuture.join();
        log.info("AiGradingFallbackService: Claude+Azure chạy song song xong sau {}ms.", System.currentTimeMillis() - parallelStartedAt);
        boolean azureOk = pronunciation != null && pronunciation.hasScores();
        String contentSource = claudeResult != null ? SOURCE_DIRECT : SOURCE_NINE_ROUTER;
        String pronunciationSource = azureOk ? SOURCE_DIRECT : SOURCE_NINE_ROUTER;

        // Claude lỗi (chấm nội dung) và/hoặc Azure lỗi (phát âm): MỘT lượt 9Router chấm trên audio phục vụ cả hai —
        // lấy điểm nội dung nếu Claude lỗi, lấy điểm Phát âm nếu Azure lỗi. Chỉ gọi khi thật sự cần.
        JsonNode nineRouterData = null;
        String nineRouterModel = null;
        if (claudeResult == null || !azureOk) {
            log.warn("AiGradingFallbackService: dự phòng 9Router cho Speaking — nội dung={}, phát âm={}.", contentSource, pronunciationSource);
            NineRouterAiClient.AiJsonResponse via = reflexV2AiGradingService.gradeSpeakingOnAudio(task, systemPrompt, userPrompt,
                    audioForModel, mimeForModel, usageSink);
            if (via == null) {
                throw new NineRouterStepFailedException("Chấm nói: dự phòng 9Router lỗi (nội dung=" + contentSource + ", phát âm=" + pronunciationSource + ")");
            }
            try {
                nineRouterData = new com.fasterxml.jackson.databind.ObjectMapper().readTree(extractJson(via.content()));
            } catch (Exception e) {
                throw new NineRouterStepFailedException("Chấm nói: không đọc được kết quả 9Router dự phòng (" + e.getMessage() + ")");
            }
            nineRouterModel = via.model();
        }
        if (claudeResult != null) {
            usageSink.record(AiGradingTokenUsage.Step.SPEAKING, claudeResult.response().usage());
        }

        JsonNode data = claudeResult != null ? claudeResult.data() : nineRouterData;
        String contentModel = claudeResult != null ? claudeResult.response().model() : nineRouterModel;

        // Điểm Phát âm do 9Router chấm (khi Azure lỗi) — lấy từ chính kết quả 9Router, không phải điểm Claude đoán mù.
        ReflexV2Scoring.CriterionScore nineRouterPronunciation = null;
        if (!azureOk && claudeResult != null) {
            nineRouterPronunciation = ReflexV2Scoring.computeScores(task, task.criteria(), nineRouterData).criteria().stream()
                    .filter(c -> c.code().equals(ReflexV2Task.PRONUNCIATION_CODE)).findFirst().orElse(null);
            if (nineRouterPronunciation == null) {
                throw new NineRouterStepFailedException("Chấm nói: 9Router dự phòng không có điểm Phát âm");
            }
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
                if (azureOk) {
                    // Claude không nghe audio nên không có cơ sở chấm P — thay thẳng bằng điểm Azure thật.
                    rawScores.add(new ReflexV2Scoring.CriterionScore(ReflexV2Task.PRONUNCIATION_CODE,
                            pronunciation.overallScore(), false, null, List.of()));
                } else if (nineRouterPronunciation != null) {
                    rawScores.add(nineRouterPronunciation);
                } else {
                    rawScores.add(c); // Claude lỗi: c đã là điểm 9Router chấm trên audio
                }
            } else if (c.code().equals(grammarCode)) {
                rawScores.add(keepStep1Grammar ? ReflexV2Scoring.keptFromStep1(grammarCode, anchor.grammarPercent())
                        : ReflexV2Scoring.regradeGrammar(c, spokenRed, anchor.grammarPercent(), noSpeech));
            } else {
                rawScores.add(c);
            }
        }
        double spokenSec = measured.map(m -> m.spanSec() > 0 ? m.spanSec() : m.speechSec()).orElse(0.0);
        ReflexV2Scoring.LexicalEvidence lexical = noSpeech ? null
                : ReflexV2Scoring.lexicalCeiling(transcript, task.lexicalSeconds(), task.lexicalLowRung());
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
        if (lowConfidenceWarning != null) {
            gateNote = gateNote.isEmpty() ? lowConfidenceWarning : gateNote + " " + lowConfidenceWarning;
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("taskId", task.id());
        audit.put("rubricFormat", task.rubricFormat());
        audit.put("gradedBy", SOURCE_DIRECT.equals(transcriptionSource) && claudeResult != null && azureOk
                ? "claude-direct+groq+azure" : "mixed-direct-9router");
        audit.put("sources", Map.of("transcription", transcriptionSource, "content", contentSource, "pronunciation", pronunciationSource));
        audit.put("model", contentModel);
        audit.put("gates", scored.gates());
        audit.put("spokenRedCount", spokenRed);
        audit.put("grammarKeptFromStep1", keepStep1Grammar);
        audit.put("pronunciationSource", azureOk ? "azure-direct" : "9router-estimate");
        audit.put("pronunciationRaw", azureOk ? pronunciation.rawJson() : null);
        audit.put("pronunciationChunks", azureOk ? pronunciation.chunkCount() : 0);
        // Bổ sung 2026-10-22 (đã xác nhận với người dùng) — chi tiết từng từ/âm vị của Azure (y hệt dữ liệu
        // spike test), lưu vào audit để toResponse() lấy ra trả cho FE hiển thị bảng phiên âm IPA cho học
        // sinh (xem ReflexSequentialGradingService#toResponse + ReflexQuestionProgressResponse#speakingPronunciationWords).
        audit.put("pronunciationWords", azureOk ? pronunciation.words() : List.of());
        audit.put("groqLowConfidenceWarning", lowConfidenceWarning);
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

    private record ClaudeSpeakingJson(NineRouterAiClient.AiJsonResponse response, JsonNode data) {
    }

    /**
     * Gọi {@code attempt} tối đa {@link #maxAttempts} lần cho tới khi trả kết quả khác null (null = HTTP lỗi, nội dung
     * rỗng, JSON hỏng hoặc thiếu mục bắt buộc — các service chấm đều trả null cho mọi trường hợp đó). Ngoại lệ
     * runtime cũng tính là 1 lần lỗi, riêng {@link ReflexAudioRejectedException} (vấn đề audio, không phải nhà cung
     * cấp AI) ném thẳng, không thử lại. Vòng lặp có chặn cứng bởi {@code maxAttempts} (tối thiểu 1) nên không thể
     * lặp vô hạn; hết lần thì trả null (hoặc ném ngoại lệ cuối) để caller rơi về 9Router.
     */
    <T> T withRetries(String label, java.util.function.Supplier<T> attempt) {
        int attempts = Math.max(1, maxAttempts);
        long deadline = totalBudgetSeconds > 0 ? System.currentTimeMillis() + totalBudgetSeconds * 1000L : Long.MAX_VALUE;
        RuntimeException lastError = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                T result = attempt.get();
                if (result != null) {
                    return result;
                }
                lastError = null;
                log.warn("AiGradingFallbackService: {} — Claude trực tiếp trả kết quả không dùng được (lần {}/{}).", label, i, attempts);
            } catch (ReflexAudioRejectedException e) {
                throw e;
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("AiGradingFallbackService: {} — Claude trực tiếp lỗi (lần {}/{}): {}", label, i, attempts, e.getMessage());
            }
            if (System.currentTimeMillis() >= deadline) {
                log.warn("AiGradingFallbackService: {} — quá ngân sách thời gian thử lại ({}s), bỏ qua các lần còn lại.", label, totalBudgetSeconds);
                break;
            }
            if (i < attempts && retryDelayMillis > 0) {
                try {
                    Thread.sleep(retryDelayMillis * i);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        return null;
    }
}
