package vn.com.pps.education.lms.service;

import vn.com.pps.education.academic.domain.Curriculum;
import vn.com.pps.education.common.AiTokenUsage;
import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.common.ReflexContentOverlap;
import vn.com.pps.education.common.ReflexV2Scoring;
import vn.com.pps.education.common.ReflexV2Tags;
import vn.com.pps.education.common.ReflexV2Task;
import vn.com.pps.education.common.SpeechMeter;
import vn.com.pps.education.media.service.MediaStorageService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.lms.dto.PronunciationPhonemeDetail;
import vn.com.pps.education.lms.dto.PronunciationWordDetail;
import vn.com.pps.education.lms.dto.SpeakingAiGradingTestResponse;
import vn.com.pps.education.lms.dto.WritingAiGradingTestResponse;
import vn.com.pps.education.lms.dto.WritingRealRubricTestResponse;
import vn.com.pps.education.lms.dto.WritingReflexRealRubricTestResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SPIKE/TEST riêng — KHÔNG phải business logic của 1 UC đã đặc tả trong docs/uc/. Dựng theo yêu cầu
 * người dùng (2026-08-22) để đánh giá khả thi kỹ thuật + chi phí thật cho hướng "AI chấm Speaking"
 * (dự kiến sau này thay luồng chấm thủ công Video phản xạ hiện có — UC-23b, ReviewVideoQuestion/
 * ReviewVideoQuestionSubmission) — KHÔNG đọc/ghi bảng nghiệp vụ nào, không ảnh hưởng luồng thật.
 *
 * Rubric chấm hiện là PLACEHOLDER (xem {@link #PLACEHOLDER_RUBRIC}) — người dùng sẽ cung cấp file
 * .md thật sau khi chốt tiêu chí với người chấm; PHẢI thay hằng số này trước khi coi kết quả chấm
 * ở đây là đáng tin cho quyết định nghiệp vụ thật (xem .claude/rules/business-fidelity.md).
 *
 * 2 nhà cung cấp AI (chọn qua tham số "provider" từ FE, xem {@link Provider} — bổ sung 2026-08-22, đã
 * xác nhận với người dùng: OpenAI yêu cầu billing dù key hợp lệ (insufficient_quota 429 khi tài khoản
 * chưa nạp tiền), Anthropic yêu cầu nạp tiền trước khi gọi được API — cả 2 chặn test ngay lúc chưa
 * quyết định dùng thật hay không):
 * - OPENAI_CLAUDE: audio -> lưu R2 -> OpenAI gpt-4o-mini-transcribe (Speech-to-Text, Claude KHÔNG
 *   nhận input audio trực tiếp) -> Claude Haiku 4.5 chấm nội dung/ngữ pháp.
 * - GEMINI: audio -> lưu R2 -> Gemini Flash (nhận audio đa phương thức trực tiếp, không cần bước STT
 *   riêng) -> CÙNG Gemini Flash chấm nội dung/ngữ pháp. Free tier, KHÔNG cần thẻ thanh toán (xem
 *   ai.google.dev) — LƯU Ý: dữ liệu gọi qua free tier CÓ THỂ được Google dùng để cải thiện sản phẩm
 *   (khác free tier trả phí) — chỉ chấp nhận được cho test kỹ thuật (voice tự ghi thử), KHÔNG dùng
 *   free tier cho dữ liệu học sinh thật khi lên production.
 * - AZURE_STACK (bổ sung 2026-10-21, đã xác nhận với người dùng — mục tiêu đánh giá khả thi BỎ 9Router,
 *   gọi thẳng API từng nhà cung cấp): audio -> lưu R2 -> 2 nhánh ĐỘC LẬP chạy song song trên CÙNG audio
 *   gốc — (1) Groq Whisper (whisper-large-v3-turbo, gọi thẳng api.groq.com, KHÔNG qua 9Router) ra
 *   transcript -> Claude Haiku 5.5 chấm nội dung/ngữ pháp trên transcript đó; (2) Azure Speech
 *   Pronunciation Assessment chấm phát âm riêng trực tiếp từ audio (xem {@link #callAzurePronunciation}).
 *   Claude KHÔNG nhận input audio nên bắt buộc tách 2 nhánh, khác hẳn nhánh GEMINI/OPENAI_CLAUDE ở trên
 *   vốn gộp STT+chấm nội dung vào 1 lượt gọi. Điểm phát âm trả THÔ thang 100 (Accuracy/Fluency/
 *   Completeness/Prosody), KHÔNG tự quy đổi band IELTS — người dùng tự đối chiếu với điểm giáo viên
 *   chấm tay trước khi quyết định dùng thật (xem .claude/rules/business-fidelity.md).
 * Không truyền "provider" (hoặc "AUTO") thì tự chọn theo key nào đã cấu hình, ưu tiên OPENAI_CLAUDE.
 * AZURE_STACK không nằm trong chuỗi fallback AUTO — phải truyền tường minh vì cần đủ 3 key cùng lúc
 * (Groq + Anthropic + Azure Speech), khác 2 nhánh kia chỉ cần 1-2 key.
 *
 * Gọi thẳng REST API bằng java.net.http.HttpClient (spike, chưa có dấu hiệu cần trừu tượng hoá
 * interface theo OCP như AttendanceMethod — xem .claude/rules/solid.md — cho tới khi hướng này thật
 * sự lên production, lúc đó các nhánh Provider ở đây chính là ứng viên tự nhiên cho pattern đó).
 */
@Service
public class SpeakingAiGradingTestService {

    private static final Logger log = LoggerFactory.getLogger(SpeakingAiGradingTestService.class);

    public enum Provider { AUTO, OPENAI_CLAUDE, GEMINI, AZURE_STACK }

    private static final String PLACEHOLDER_RUBRIC = """
            [PLACEHOLDER - CHUA PHAI RUBRIC THAT, nguoi dung se cung cap file .md sau khi chot voi nguoi cham]
            Cham cau tra loi speaking cua hoc sinh tieng Anh theo thang % (0-100), tham khao khung CEFR:
            - 90-100%: trinh do B2+ - cau phuc, tu vung da dang dung ngu canh.
            - 70-89%: trinh do B1-B2 - tra loi dung trong tam cau hoi, ngu phap co ban dung.
            - 50-69%: trinh do A2-B1 - tra loi duoc nhung cau don gian, co loi ngu phap/tu vung ro.
            - Duoi 50%: chua tra loi dung trong tam cau hoi hoac cau qua ngan/khong ro nghia.
            """;

    /**
     * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — PLACEHOLDER riêng cho {@link #gradeWritingOnly},
     * KHÔNG phải rubric v3 thật đang dùng cho UC-40/41 (xem {@code resources/rubrics-v3/}). Chỉ để đo khả
     * thi kỹ thuật/chi phí Claude Haiku 5.5 trên 4 tiêu chí IELTS Writing dạng text thuần (không audio),
     * PHẢI thay bằng rubric thật trước khi dùng cho quyết định nghiệp vụ (xem .claude/rules/business-fidelity.md).
     */
    private static final String WRITING_PLACEHOLDER_RUBRIC = """
            [PLACEHOLDER - CHUA PHAI RUBRIC THAT, nguoi dung se cung cap rubric that sau khi chot voi nguoi cham]
            Cham bai Writing tieng Anh theo 4 tieu chi IELTS, moi tieu chi thang % (0-100):
            - Task Achievement/Response: bai viet co dung trong tam de bai khong, co du y chinh khong.
            - Coherence & Cohesion: bo cuc ro rang, cac doan/cau lien ket hop ly khong.
            - Lexical Resource: tu vung da dang, dung ngu canh khong.
            - Grammatical Range & Accuracy: cau truc ngu phap da dang, it loi khong.
            Neu khong co de bai (essayPrompt rong) thi bo qua Task Achievement (tra ve null cho truong nay),
            van cham 3 tieu chi con lai binh thuong.
            """;

    private static final String OPENAI_TRANSCRIBE_MODEL = "gpt-4o-mini-transcribe";
    private static final String CLAUDE_GRADING_MODEL = "claude-haiku-4-5-20251001";
    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — đổi từ alias "gemini-flash-latest" (đã âm thầm trỏ sang bản preview quota free tier chỉ 20 request/ngày) sang tên model cụ thể, mirror WritingAiGradingService. */
    private static final String GEMINI_MODEL = "gemini-3.5-flash-lite";
    /** Nhánh AZURE_STACK (2026-10-21) — model chấm riêng, KHÔNG dùng chung {@link #CLAUDE_GRADING_MODEL} cũ (4.5) vì mục tiêu của nhánh này là đánh giá RIÊNG Haiku 5.5 theo quyết định người dùng. */
    private static final String CLAUDE_GRADING_MODEL_V2 = "claude-haiku-5-5";
    /** Groq Whisper gọi thẳng (KHÔNG qua 9Router) — cùng model NineRouterAiClient đang dùng qua combo "groq/whisper-large-v3-turbo", giữ nguyên để so sánh công bằng. */
    private static final String GROQ_TRANSCRIBE_MODEL = "whisper-large-v3-turbo";
    /** Prosody hiện CHỈ hỗ trợ locale này bất kể region Azure nào (xem Javadoc {@link #callAzurePronunciation}). */
    private static final String AZURE_PRONUNCIATION_LANGUAGE = "en-US";

    private final MediaStorageService mediaStorageService;
    private final ObjectMapper objectMapper;
    private final AudioTranscoder audioTranscoder;
    private final WritingAiGradingService writingAiGradingService;
    private final ReflexV2AiGradingService reflexV2AiGradingService;
    private final ReflexV2Prompts reflexV2Prompts;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    /** Hotfix 2026-10-21 — dùng chung NINE_ROUTER_TIMEOUT_SECONDS với NineRouterAiClient (trước đây hardcode 60 giây). */
    @Value("${app.ai-grading.nine-router-timeout-seconds:150}")
    private int requestTimeoutSeconds;

    @Value("${app.ai-grading.openai-api-key:}")
    private String openAiApiKey;

    @Value("${app.ai-grading.anthropic-api-key:}")
    private String anthropicApiKey;

    @Value("${app.ai-grading.gemini-api-key:}")
    private String geminiApiKey;

    @Value("${app.ai-grading.groq-api-key:}")
    private String groqApiKey;

    @Value("${app.ai-grading.azure-speech-key:}")
    private String azureSpeechKey;

    @Value("${app.ai-grading.azure-speech-region:}")
    private String azureSpeechRegion;

    public SpeakingAiGradingTestService(MediaStorageService mediaStorageService, ObjectMapper objectMapper,
                                         AudioTranscoder audioTranscoder, WritingAiGradingService writingAiGradingService,
                                         ReflexV2AiGradingService reflexV2AiGradingService, ReflexV2Prompts reflexV2Prompts) {
        this.mediaStorageService = mediaStorageService;
        this.objectMapper = objectMapper;
        this.audioTranscoder = audioTranscoder;
        this.writingAiGradingService = writingAiGradingService;
        this.reflexV2AiGradingService = reflexV2AiGradingService;
        this.reflexV2Prompts = reflexV2Prompts;
    }

    public SpeakingAiGradingTestResponse grade(MultipartFile audio, String writingText, String providerParam) {
        return grade(audio, writingText, providerParam, null, null, 30, null, 70);
    }

    /**
     * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — 5 tham số cuối CHỈ có tác dụng khi
     * provider=AZURE_STACK VÀ {@code gradeLevel} khác rỗng: chấm thêm nội dung/ngữ pháp bước Nói bằng
     * ĐÚNG checkpoint thật UC-23b (xem {@link #gradeSpeakingReflexContent}) thay vì chỉ placeholder.
     * Không truyền gradeLevel thì giữ nguyên hành vi cũ (chỉ placeholder).
     */
    public SpeakingAiGradingTestResponse grade(MultipartFile audio, String writingText, String providerParam,
                                                String gradeLevelParam, String trackParam, int maxRecordingSeconds,
                                                String question, int step1GrammarPercent) {
        Provider provider = resolveProvider(providerParam);
        String audioUrl = mediaStorageService.store(audio, "REVIEW_VIDEO_SUBMISSION");
        if (provider == Provider.AZURE_STACK) {
            return gradeWithAzureStack(audio, audioUrl, writingText, gradeLevelParam, trackParam, maxRecordingSeconds, question, step1GrammarPercent);
        }
        String transcript = provider == Provider.GEMINI ? transcribeWithGemini(audio) : transcribeWithOpenAi(audio);
        GradeResult content = gradeContent(transcript, provider);
        GradeResult grammar = (writingText == null || writingText.isBlank()) ? null : gradeGrammar(writingText, provider);
        return new SpeakingAiGradingTestResponse(
                audioUrl, transcript,
                content.scorePercent(), content.feedback(),
                grammar == null ? null : grammar.scorePercent(),
                grammar == null ? null : grammar.feedback(),
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Nhánh AZURE_STACK — xem Javadoc class. 2 nhánh (nội dung qua Groq+Claude, phát âm qua Azure) ĐỘC
     * LẬP với nhau có chủ đích (mirror triết lý ReflexV2AiGradingService#gradeSpeaking — gộp chung sẽ
     * không biết phần nào do audio/phát âm, phần nào do nội dung dạng chữ khi so sánh chi phí/độ chính
     * xác). 1 nhánh lỗi không chặn nhánh còn lại — spike nên để NULL thay vì throw, caller (trang test)
     * tự thấy field nào rỗng để biết nhánh nào cần sửa.
     */
    private SpeakingAiGradingTestResponse gradeWithAzureStack(MultipartFile audio, String audioUrl, String writingText,
                                                                String gradeLevelParam, String trackParam, int maxRecordingSeconds,
                                                                String question, int step1GrammarPercent) {
        // Revert 2026-10-22 (đã xác nhận với người dùng) — test thật cho thấy gợi ý bài viết làm Groq LỆCH
        // HƠN ở 1 số trường hợp (ngược với 2 case tôi tự thử tay). Tắt gợi ý, quay lại hành vi an toàn đã
        // biết (không prompt) — giữ nguyên tham số promptHint ở transcribeWithGroq để dễ bật lại/thử nghiệm
        // có kiểm soát sau này, không xoá hẳn code.
        GroqTranscriptionResult transcription = transcribeWithGroq(audio, null);
        String transcript = transcription.text();
        GradeResult content = gradeContent(transcript, Provider.AZURE_STACK);
        GradeResult grammar = (writingText == null || writingText.isBlank()) ? null : gradeGrammar(writingText, Provider.AZURE_STACK);
        AzurePronunciationResult pronunciation = callAzurePronunciation(audio, transcript);

        ReflexSpeakingPreview reflex = null;
        if (gradeLevelParam != null && !gradeLevelParam.isBlank()) {
            try {
                Curriculum.GradeLevel gradeLevel = parseGradeLevel(gradeLevelParam);
                Curriculum.Track track = parseTrack(trackParam);
                java.util.Optional<ReflexV2Task> taskOpt = ReflexV2Task.forGradeTrack(gradeLevel, track, maxRecordingSeconds);
                if (taskOpt.isPresent()) {
                    byte[] audioBytes = audio.getBytes();
                    String mimeType = audio.getContentType() == null ? "audio/webm" : audio.getContentType();
                    ReflexV2AiGradingService.Step1Anchor anchor = new ReflexV2AiGradingService.Step1Anchor(step1GrammarPercent, writingText);
                    reflex = gradeSpeakingReflexContent(taskOpt.get(),
                            question == null || question.isBlank() ? "(không có đề bài)" : question,
                            audioBytes, mimeType, anchor, transcript, resolveWritingModel(null),
                            pronunciation.overallScore());
                }
            } catch (IOException | IllegalArgumentException e) {
                reflex = ReflexSpeakingPreview.error("Không chấm được bằng rubric thật: " + e.getMessage());
            }
        }

        double durationSec = estimateAudioDurationSec(audio);
        double groqCostUsd = audioCostUsd(GROQ_WHISPER_USD_PER_HOUR, durationSec);
        double claudeCostUsd = claudeCostUsd(content.usage()) + claudeCostUsd(grammar == null ? null : grammar.usage())
                + claudeCostUsd(reflex == null ? null : reflex.usage());
        double azureCostUsd = audioCostUsd(AZURE_PRONUNCIATION_USD_PER_HOUR, durationSec);
        double totalCostUsd = groqCostUsd + claudeCostUsd + azureCostUsd;

        return new SpeakingAiGradingTestResponse(
                audioUrl, transcript,
                content.scorePercent(), content.feedback(),
                grammar == null ? null : grammar.scorePercent(),
                grammar == null ? null : grammar.feedback(),
                pronunciation.accuracyScore(), pronunciation.fluencyScore(), pronunciation.completenessScore(),
                pronunciation.prosodyScore(), pronunciation.overallScore(), pronunciation.rawJson(),
                Math.round(durationSec * 10) / 10.0, toVnd(groqCostUsd), toVnd(claudeCostUsd), toVnd(azureCostUsd), toVnd(totalCostUsd),
                reflex == null ? null : reflex.criteria(),
                reflex == null ? null : reflex.unlockPercent(),
                reflex == null ? null : reflex.finalPercent(),
                reflex == null ? null : reflex.feedback(),
                reflex == null ? null : reflex.hint(),
                reflex == null ? null : reflex.gateNote(),
                reflex == null ? null : reflex.markedTranscript(),
                reflex == null ? null : reflex.fabricationWarning(),
                reflex == null ? null : reflex.errorMessage(),
                pronunciation.words(),
                transcription.lowConfidenceWarning());
    }

    /**
     * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — chấm nội dung/ngữ pháp bước NÓI bằng ĐÚNG checkpoint
     * thật UC-23b (lượt B của {@link ReflexV2AiGradingService#gradeSpeaking}), nhưng KHÔNG gọi lại method đó
     * nguyên vẹn vì lượt A thật (phiên âm mù) bắt buộc audio trực tiếp cho Gemini — Claude không nhận được.
     * Thay vào đó: dùng transcript đã có sẵn (Groq) + đo tốc độ nói bằng {@link SpeechMeter} (code thuần,
     * không cần AI, y hệt luồng thật dùng để CHẶN TRANSCRIPT BỊA — xem {@code MAX_WORDS_PER_SECOND}), rồi tái
     * dùng ĐÚNG pipeline tính điểm thật ({@link ReflexV2Scoring}: trần lỗi, sàn ngữ pháp, giữ điểm Bước 1 khi
     * nói giống bài viết, ép lưới 10) — chỉ thay nguồn gọi AI (Claude thay Gemini, KHÔNG gửi audio vì Claude
     * không nhận được, chỉ gửi transcript + số liệu đã đo).
     *
     * CỐ TÌNH BỎ so với bản gốc (chấp nhận được cho spike, không chấp nhận được cho production thật):
     * - Không có "suspect_words" (từ AI tự khai lúc phiên âm) vì không chạy lượt A.
     * - Không throw chặn cứng khi nói khác bài viết quá nhiều — chỉ cảnh báo qua gateNote, vẫn trả điểm.
     * - Không ghi lại {@code AiGradingTokenUsage} vào DB (chỉ trả cost ước tính qua response, xem Javadoc class).
     *
     * Sửa 2026-10-22 (đã xác nhận với người dùng) — TRƯỚC ĐÂY tiêu chí Phát âm (P) chỉ để Claude tự đoán
     * (ghi "tham khảo") và điểm Azure thật chỉ hiện riêng ở khối placeholder bên dưới, không ảnh hưởng
     * "Tổng". Giờ ghép Y HỆT production ({@link AiGradingFallbackService#gradeReflexSpeakingWithFallback}):
     * thay thẳng điểm P bằng {@code azureOverallScore} (null nếu Azure lỗi → giữ nguyên điểm Claude tự đoán,
     * đỡ vỡ cả luồng vì đây là spike).
     */
    private ReflexSpeakingPreview gradeSpeakingReflexContent(ReflexV2Task task, String question, byte[] audioBytes,
                                                              String mimeType, ReflexV2AiGradingService.Step1Anchor anchor,
                                                              String transcript, String model, Integer azureOverallScore) {
        java.util.Optional<byte[]> wav = audioTranscoder.toWav(audioBytes, mimeType);
        java.util.Optional<SpeechMeter.Measurement> measured = wav.flatMap(SpeechMeter::measure);
        double durationSec = measured.map(SpeechMeter.Measurement::durationSec).orElse((double) task.seconds());
        double speechSec = measured.map(SpeechMeter.Measurement::speechSec).orElse(durationSec);
        double longestPause = measured.map(SpeechMeter.Measurement::longestPauseSec).orElse(0.0);

        String fabricationWarning = null;
        if (measured.isPresent()) {
            double rateBase = measured.get().spanSec() > 0 ? measured.get().spanSec() : measured.get().speechSec();
            int words = transcript.isBlank() ? 0 : transcript.split("\\s+").length;
            if (words > Math.max(3, rateBase * MAX_WORDS_PER_SECOND_FABRICATION_CHECK)) {
                fabricationWarning = "Transcript nghi bịa: " + words + " từ trong " + String.format("%.1f", rateBase)
                        + " giây nói — vượt tốc độ nói tối đa con người (~3.5 từ/giây). Kết quả bên dưới KHÔNG đáng tin, nên ghi âm lại.";
            }
        } else {
            fabricationWarning = "Không đo được tốc độ nói (ffmpeg lỗi/thiếu) — không chặn được transcript bịa cho lượt này.";
        }

        ReflexV2Scoring.Readback readbackRaw = ReflexV2Scoring.writtenVsSpoken(anchor.text(), transcript);
        List<String> deviantWords = readbackRaw == null ? List.of() : readbackRaw.words();

        String systemPrompt = reflexV2Prompts.speakingSystem(task);
        String userPrompt = reflexV2Prompts.speakingUser(task, question, anchor.text() == null ? "" : anchor.text(),
                transcript, List.of(), deviantWords, durationSec, speechSec, longestPause);
        ClaudeRawResult raw = callClaudeRawWithUsage(systemPrompt, userPrompt, model, CLAUDE_MAX_TOKENS_LONG_RUBRIC);
        JsonNode data = parseJsonOrThrow(raw.text());

        List<String> gradedCodes = task.criteria();
        ReflexV2Scoring.ScoreSet scored = ReflexV2Scoring.computeScores(task, gradedCodes, data);
        boolean noSpeech = transcript.isBlank();
        List<ReflexV2Scoring.Highlight> highlights = ReflexV2Scoring.locateHighlights(transcript, data.path("highlights"), task.grade());
        int spokenRed = ReflexV2Scoring.countRed(highlights);
        ReflexV2Scoring.SpokenMatch spokenMatch = noSpeech ? null : ReflexV2Scoring.sameAsWritten(anchor.text(), transcript);
        boolean keepStep1Grammar = spokenMatch != null && spokenMatch.same();

        String grammarCode = task.grammarCode();
        List<ReflexV2Scoring.CriterionScore> rawScores = new ArrayList<>();
        for (ReflexV2Scoring.CriterionScore c : scored.criteria()) {
            if (azureOverallScore != null && c.code().equals(ReflexV2Task.PRONUNCIATION_CODE)) {
                // Claude không nghe audio nên không có cơ sở chấm P — thay thẳng bằng điểm Azure thật (mirror production).
                rawScores.add(new ReflexV2Scoring.CriterionScore(ReflexV2Task.PRONUNCIATION_CODE,
                        azureOverallScore, false, null, List.of()));
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
        ReflexV2Scoring.Readback readback = noSpeech ? null : readbackRaw;
        List<ReflexV2Scoring.CriterionScore> capped = ReflexV2Scoring.applyGrammarFloor(grammarCode,
                ReflexV2Scoring.applyErrorCaps(task, rawScores, highlights,
                        new ReflexV2Scoring.ErrorEvidence(lexical, fluency, length, readback)));
        if (keepStep1Grammar) {
            capped = ReflexV2Scoring.keepGrammarFromStep1(grammarCode, capped, anchor.grammarPercent());
        }
        // Bổ sung 2026-10-22 (đã xác nhận với người dùng) — mirror production, xem Javadoc
        // ReflexV2Scoring#applySpeakingOffTopicCap.
        capped = ReflexV2Scoring.applySpeakingOffTopicCap(task, capped, scored.gates());
        capped = ReflexV2Scoring.snapToGrid10(capped);

        List<ReflexV2Scoring.CriterionScore> forUnlock = new ArrayList<>();
        List<CriteriaScoreItem> items = new ArrayList<>();
        for (ReflexV2Scoring.CriterionScore cs : capped) {
            boolean isPronunciation = cs.code().equals(ReflexV2Task.PRONUNCIATION_CODE);
            boolean referenceOnly = isPronunciation; // P không tính vào "mở khoá", chỉ vào "Tổng" (như production).
            if (!referenceOnly) {
                forUnlock.add(cs);
            }
            String label = ReflexV2Tags.CRITERIA_EN.get(cs.code());
            String suffix = isPronunciation
                    ? (azureOverallScore != null ? " (Azure thật)" : " (AI tự đoán — Azure lỗi, xem log)")
                    : "";
            items.add(new CriteriaScoreItem(label + suffix, cs.percent()));
        }
        int finalPercent = ReflexV2Scoring.average(capped);
        int unlockPercent = ReflexV2Scoring.average(forUnlock);
        String feedback = ReflexV2Scoring.trimFeedback(data.path("feedback").asText(""), highlights);
        String gateNote = ReflexV2Scoring.buildGateNote(task, scored.gates(), ReflexV2Scoring.wordCount(transcript), true);
        if (spokenMatch != null && !spokenMatch.same() && anchor.text() != null && !anchor.text().isBlank()) {
            double overlap = ReflexContentOverlap.contentOverlap(anchor.text(), transcript);
            if (overlap < ReflexContentOverlap.minOverlapFor(anchor.text())) {
                String note = "Nói khác khá nhiều so với bài đã viết (overlap " + String.format("%.0f", overlap * 100) + "%) — luồng thật sẽ YÊU CẦU NÓI LẠI, spike này vẫn chấm để bạn xem kết quả.";
                gateNote = gateNote.isEmpty() ? note : gateNote + " " + note;
            }
        }

        return new ReflexSpeakingPreview(items, unlockPercent, finalPercent, feedback,
                ReflexV2Scoring.trimHint(data.path("hint").asText(""), highlights), gateNote,
                ReflexV2Scoring.toErrMarkup(transcript, highlights), fabricationWarning, null, raw.usage());
    }

    /** Người nói nhanh nhất cũng không quá ~3,5 từ/giây — mirror {@code ReflexV2AiGradingService.MAX_WORDS_PER_SECOND}. */
    private static final double MAX_WORDS_PER_SECOND_FABRICATION_CHECK = 3.5;

    private record ReflexSpeakingPreview(List<CriteriaScoreItem> criteria, int unlockPercent, int finalPercent,
                                          String feedback, String hint, String gateNote, String markedTranscript,
                                          String fabricationWarning, String errorMessage, AiTokenUsage usage) {
        static ReflexSpeakingPreview error(String message) {
            return new ReflexSpeakingPreview(null, 0, 0, null, null, null, null, null, message, null);
        }
    }

    /** Dùng lại {@link AudioTranscoder} để đo thời lượng CHÍNH XÁC qua kích thước WAV PCM16 mono 16kHz (byte rate cố định 32000 byte/giây) — rẻ hơn hẳn việc tích hợp thư viện đọc metadata audio riêng cho spike này. Trả 0 nếu convert thất bại (ffmpeg lỗi/thiếu) — chi phí tính ra sẽ thấp hơn thật, không chặn kết quả chấm. */
    private double estimateAudioDurationSec(MultipartFile audio) {
        try {
            String mimeType = audio.getContentType() == null ? "audio/webm" : audio.getContentType();
            Optional<byte[]> wav = audioTranscoder.toWav(audio.getBytes(), mimeType);
            return wav.map(bytes -> Math.max(0, bytes.length - 44) / 32000.0).orElse(0.0);
        } catch (IOException e) {
            return 0.0;
        }
    }

    /**
     * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — chấm Writing ĐỘC LẬP, không cần audio đi kèm
     * (khác {@link #grade}, luôn bắt buộc 1 file audio vì gắn với luồng Speaking). {@code modelParam}
     * nhận "CLAUDE_4_5"/"CLAUDE_5_5"/"GEMINI" (không dùng chung enum {@link Provider} vì enum đó gắn
     * với lựa chọn STT/audio, không áp dụng ở đây). Mặc định CLAUDE_5_5 nếu có ANTHROPIC_API_KEY, không
     * thì GEMINI nếu có GEMINI_API_KEY, không thì báo lỗi thiếu cấu hình — cùng triết lý resolveProvider.
     */
    public WritingAiGradingTestResponse gradeWritingOnly(String essayPrompt, String writingText, String modelParam, String gradeTrack) {
        if (writingText == null || writingText.isBlank()) {
            throw new IllegalArgumentException("writingText không được rỗng.");
        }
        String resolvedModel = resolveWritingModel(modelParam);
        // Bổ sung 2026-10-22 (đã xác nhận với người dùng) — rubric vẫn là PLACEHOLDER chung (không phải
        // rubric riêng từng khối như resources/rubrics-v3/ thật), chỉ truyền NGỮ CẢNH khối vào prompt để
        // model tự điều chỉnh kỳ vọng độ khó theo tuổi/trình độ, KHÔNG thay đổi thang điểm/tiêu chí.
        String gradeContext = (gradeTrack == null || gradeTrack.isBlank())
                ? "Chưa rõ khối lớp — chấm theo mặt bằng chung THCS."
                : "Học sinh khối " + gradeTrack + " (THCS) — cân nhắc kỳ vọng độ khó phù hợp lứa tuổi này khi chấm, "
                        + "không áp kỳ vọng của khối khác.";
        String systemPrompt = "Bạn là giám khảo chấm Writing tiếng Anh cho học sinh trung tâm Anh ngữ. " + gradeContext
                + " Tiêu chí chấm:\n"
                + WRITING_PLACEHOLDER_RUBRIC
                + "\nChỉ trả lời DUY NHẤT 1 JSON hợp lệ, không thêm chữ nào khác, đúng format: "
                + "{\"taskAchievement\": <số nguyên 0-100 hoặc null nếu không có đề bài>, "
                + "\"coherenceCohesion\": <số nguyên 0-100>, \"lexicalResource\": <số nguyên 0-100>, "
                + "\"grammaticalRange\": <số nguyên 0-100>, \"feedback\": \"<nhận xét ngắn gọn tiếng Việt>\"}";
        String userContent = (essayPrompt == null || essayPrompt.isBlank() ? "Đề bài: (không có)\n" : "Đề bài: \"" + essayPrompt + "\"\n")
                + "Bài viết của học sinh: \"" + writingText + "\"";

        WritingGradeResult result = "GEMINI".equals(resolvedModel)
                ? callGeminiForWritingGrade(systemPrompt, userContent)
                : callClaudeForWritingGrade(systemPrompt, userContent, resolvedModel);
        return new WritingAiGradingTestResponse(result.taskAchievement(), result.coherenceCohesion(),
                result.lexicalResource(), result.grammaticalRange(), result.feedback(), resolvedModel,
                gradeTrack == null || gradeTrack.isBlank() ? null : gradeTrack,
                toVnd(claudeCostUsd(result.usage())));
    }

    /**
     * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — chấm Writing bằng ĐÚNG rubric v3 thật, tái dùng
     * {@link WritingAiGradingService#grade(String, String, Curriculum, List, java.util.function.BiFunction)}
     * (overload spike-only) để KHÔNG viết lại prompt/hậu kiểm — chỉ thay nguồn gọi AI bằng Claude/Gemini
     * gọi thẳng thay vì 9Router. {@code Curriculum} dựng TẠM trong bộ nhớ (không lưu DB) vì
     * {@code WritingAiGradingService.grade} chỉ đọc {@code getGradeLevel()}/{@code getTrack()}.
     */
    public WritingRealRubricTestResponse gradeWritingRealRubric(String taskPrompt, String essayText, String gradeLevelParam,
                                                                  String trackParam, String modelParam) {
        if (essayText == null || essayText.isBlank()) {
            throw new IllegalArgumentException("writingText không được rỗng.");
        }
        Curriculum.GradeLevel gradeLevel = parseGradeLevel(gradeLevelParam);
        Curriculum.Track track = parseTrack(trackParam);
        String resolvedModel = resolveWritingModel(modelParam);

        Curriculum curriculum = new Curriculum();
        curriculum.setGradeLevel(gradeLevel);
        curriculum.setTrack(track);

        java.util.function.BiFunction<String, String, NineRouterAiClient.ChatResult> caller = (system, user) -> {
            if ("GEMINI".equals(resolvedModel)) {
                long startedAt = System.currentTimeMillis();
                String raw = callGeminiRaw(system, user);
                return new NineRouterAiClient.ChatResult(raw, "stop", AiTokenUsage.unknown(resolvedModel, false, System.currentTimeMillis() - startedAt));
            }
            ClaudeRawResult claudeResult = callClaudeRawWithUsage(system, user, resolvedModel, CLAUDE_MAX_TOKENS_LONG_RUBRIC);
            return new NineRouterAiClient.ChatResult(claudeResult.text(), "stop", claudeResult.usage());
        };

        WritingAiGradingService.GradeResult result = writingAiGradingService.grade(essayText, taskPrompt, curriculum, null, caller);
        if (result == null) {
            return new WritingRealRubricTestResponse(false, null, null, null, null, resolvedModel, null, null, null);
        }
        AiTokenUsage usage = result.usage();
        return new WritingRealRubricTestResponse(true, result.scorePercent(), result.feedback(), result.markedAnswer(),
                result.criteriaScores(), resolvedModel,
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.cachedTokens(),
                usage == null ? null : usage.completionTokens());
    }

    private Curriculum.GradeLevel parseGradeLevel(String gradeLevelParam) {
        if (gradeLevelParam == null || gradeLevelParam.isBlank()) {
            throw new IllegalArgumentException("Cần chọn khối (gradeLevel) để xác định đúng rubric thật.");
        }
        return switch (gradeLevelParam) {
            case "6", "GRADE_6" -> Curriculum.GradeLevel.GRADE_6;
            case "7", "GRADE_7" -> Curriculum.GradeLevel.GRADE_7;
            case "8", "GRADE_8" -> Curriculum.GradeLevel.GRADE_8;
            case "9", "GRADE_9" -> Curriculum.GradeLevel.GRADE_9;
            default -> throw new IllegalArgumentException("gradeLevel không hợp lệ: " + gradeLevelParam + " (chỉ nhận 6/7/8/9)");
        };
    }

    /** {@code null} hợp lệ (Khối 6 dùng chung rubric, không phân track — xem Javadoc {@link Curriculum.Track}). */
    private Curriculum.Track parseTrack(String trackParam) {
        if (trackParam == null || trackParam.isBlank()) {
            return null;
        }
        return switch (trackParam) {
            case "IELTS" -> Curriculum.Track.IELTS;
            case "CAMBRIDGE" -> Curriculum.Track.CAMBRIDGE;
            default -> throw new IllegalArgumentException("track không hợp lệ: " + trackParam + " (chỉ nhận IELTS/CAMBRIDGE)");
        };
    }

    /**
     * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — chấm bước VIẾT CÂU NGẮN (trước khi mở khoá Nói) bằng
     * ĐÚNG rubric checkpoint thật UC-23b, tái dùng {@link ReflexV2AiGradingService#gradeWriting(ReflexV2Task, String, String, java.util.function.BiFunction)}
     * (overload spike-only) — KHÔNG phải rubric Essay UC-40/41 ({@link #gradeWritingRealRubric}), đúng luồng
     * Viết (ngắn) → Nói mà bạn đang test. {@code maxRecordingSeconds}: 30 = SHORT, 90/120 = PART2 (chỉ ảnh
     * hưởng Khối 8-9 IELTS, các khối khác bỏ qua tham số này — xem Javadoc {@link ReflexV2Task}).
     */
    public WritingReflexRealRubricTestResponse gradeWritingReflexRealRubric(String question, String writingText,
                                                                              String gradeLevelParam, String trackParam,
                                                                              int maxRecordingSeconds, String modelParam) {
        if (writingText == null || writingText.isBlank()) {
            throw new IllegalArgumentException("writingText không được rỗng.");
        }
        Curriculum.GradeLevel gradeLevel = parseGradeLevel(gradeLevelParam);
        Curriculum.Track track = parseTrack(trackParam);
        String resolvedModel = resolveWritingModel(modelParam);

        java.util.Optional<ReflexV2Task> taskOpt = ReflexV2Task.forGradeTrack(gradeLevel, track, maxRecordingSeconds);
        if (taskOpt.isEmpty()) {
            return new WritingReflexRealRubricTestResponse(false, null, null, null, null, null, null, null, null,
                    null, null, null, resolvedModel, null, null, null, null);
        }
        ReflexV2Task task = taskOpt.get();

        java.util.function.BiFunction<String, String, NineRouterAiClient.AiJsonResponse> caller = (system, user) -> {
            if ("GEMINI".equals(resolvedModel)) {
                long startedAt = System.currentTimeMillis();
                String raw = callGeminiRaw(system, user);
                return new NineRouterAiClient.AiJsonResponse(raw, resolvedModel,
                        AiTokenUsage.unknown(resolvedModel, false, System.currentTimeMillis() - startedAt));
            }
            ClaudeRawResult claudeResult = callClaudeRawWithUsage(system, user, resolvedModel, CLAUDE_MAX_TOKENS_LONG_RUBRIC);
            return new NineRouterAiClient.AiJsonResponse(claudeResult.text(), resolvedModel, claudeResult.usage());
        };

        ReflexV2AiGradingService.WritingResult result = reflexV2AiGradingService.gradeWriting(task, question, writingText, caller);
        if (result == null) {
            return new WritingReflexRealRubricTestResponse(false, task.id(), null, null, null, null, null, null,
                    null, null, null, null, resolvedModel, null, null, null, null);
        }
        AiTokenUsage usage = result.usage();
        return new WritingReflexRealRubricTestResponse(true, task.id(), result.step1Percent(), result.grammarPercent(),
                result.redCount(), result.criteria(), result.markedText(), result.feedback(), result.hint(),
                result.gateNote(), result.gates(), result.audit(), resolvedModel,
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.cachedTokens(),
                usage == null ? null : usage.completionTokens(),
                toVnd(claudeCostUsd(usage)));
    }

    /** @return tên model Claude thật, hoặc chuỗi "GEMINI" làm cờ báo nhánh Gemini (không phải tên model Gemini — xem {@link #gradeWritingOnly}). */
    private String resolveWritingModel(String modelParam) {
        String requested = modelParam == null || modelParam.isBlank() ? "AUTO" : modelParam;
        return switch (requested) {
            case "CLAUDE_4_5" -> {
                requireAnthropicKey();
                yield CLAUDE_GRADING_MODEL;
            }
            case "CLAUDE_5_5" -> {
                requireAnthropicKey();
                yield CLAUDE_GRADING_MODEL_V2;
            }
            case "GEMINI" -> {
                if (geminiApiKey.isBlank()) {
                    throw new IllegalArgumentException("Chưa cấu hình GEMINI_API_KEY (xem .env.example).");
                }
                yield "GEMINI";
            }
            case "AUTO" -> {
                if (!anthropicApiKey.isBlank()) {
                    yield CLAUDE_GRADING_MODEL_V2;
                }
                if (!geminiApiKey.isBlank()) {
                    yield "GEMINI";
                }
                throw new IllegalArgumentException("Chưa cấu hình ANTHROPIC_API_KEY hoặc GEMINI_API_KEY (xem .env.example).");
            }
            default -> throw new IllegalArgumentException("model không hợp lệ: " + modelParam + " (chỉ nhận AUTO/CLAUDE_4_5/CLAUDE_5_5/GEMINI)");
        };
    }

    private void requireAnthropicKey() {
        if (anthropicApiKey.isBlank()) {
            throw new IllegalArgumentException("Chưa cấu hình ANTHROPIC_API_KEY (xem .env.example).");
        }
    }

    private Provider resolveProvider(String providerParam) {
        Provider requested;
        try {
            requested = providerParam == null || providerParam.isBlank() ? Provider.AUTO : Provider.valueOf(providerParam);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("provider không hợp lệ: " + providerParam + " (chỉ nhận AUTO/OPENAI_CLAUDE/GEMINI/AZURE_STACK)");
        }
        if (requested == Provider.OPENAI_CLAUDE) {
            if (openAiApiKey.isBlank()) {
                throw new IllegalArgumentException("Chưa cấu hình OPENAI_API_KEY (xem .env.example) — cần key thật để gọi Speech-to-Text.");
            }
            if (anthropicApiKey.isBlank()) {
                throw new IllegalArgumentException("Chưa cấu hình ANTHROPIC_API_KEY (xem .env.example) — cần key thật để chấm bài với Claude.");
            }
            return requested;
        }
        if (requested == Provider.GEMINI) {
            if (geminiApiKey.isBlank()) {
                throw new IllegalArgumentException("Chưa cấu hình GEMINI_API_KEY (xem .env.example) — cần key thật để dùng Gemini.");
            }
            return requested;
        }
        if (requested == Provider.AZURE_STACK) {
            if (groqApiKey.isBlank()) {
                throw new IllegalArgumentException("Chưa cấu hình GROQ_API_KEY (xem .env.example) — cần key thật để gọi Groq Whisper.");
            }
            if (anthropicApiKey.isBlank()) {
                throw new IllegalArgumentException("Chưa cấu hình ANTHROPIC_API_KEY (xem .env.example) — cần key thật để chấm bài với Claude.");
            }
            if (azureSpeechKey.isBlank() || azureSpeechRegion.isBlank()) {
                throw new IllegalArgumentException("Chưa cấu hình AZURE_SPEECH_KEY/AZURE_SPEECH_REGION (xem .env.example) — cần đủ cả 2 để chấm phát âm.");
            }
            return requested;
        }
        // AUTO: ưu tiên OPENAI_CLAUDE nếu đủ cả 2 key, không thì dùng GEMINI nếu có, không thì báo thiếu cấu hình.
        // AZURE_STACK KHÔNG nằm trong chuỗi fallback này — phải truyền tường minh (xem Javadoc class).
        if (!openAiApiKey.isBlank() && !anthropicApiKey.isBlank()) {
            return Provider.OPENAI_CLAUDE;
        }
        if (!geminiApiKey.isBlank()) {
            return Provider.GEMINI;
        }
        throw new IllegalArgumentException("Chưa cấu hình đủ key cho nhà cung cấp AI nào (xem .env.example) — "
                + "cần OPENAI_API_KEY+ANTHROPIC_API_KEY, hoặc GEMINI_API_KEY, hoặc truyền provider=AZURE_STACK với đủ 3 key riêng.");
    }

    /** Ngưỡng cảnh báo độ tin cậy Groq — mốc kinh nghiệm phổ biến của họ Whisper (avg_logprob rất âm/compression_ratio cao = dấu hiệu bịa/lặp từ), KHÔNG phải số Groq công bố chính thức, cần tinh chỉnh khi có nhiều dữ liệu thật hơn. */
    private static final double GROQ_LOW_CONFIDENCE_AVG_LOGPROB = -1.0;
    private static final double GROQ_HIGH_NO_SPEECH_PROB = 0.5;
    private static final double GROQ_HIGH_COMPRESSION_RATIO = 2.4;

    private record GroqTranscriptionResult(String text, String lowConfidenceWarning) {
    }

    /**
     * Bổ sung 2026-10-22 (đã xác nhận với người dùng, phát hiện khi test thật — Groq từng "bịa" thêm từ
     * không có trong audio) — chuyển sang {@code response_format=verbose_json} để đọc 3 tín hiệu độ tin
     * cậy Groq tự báo theo từng đoạn: {@code avg_logprob} (càng âm nhiều càng không chắc), {@code
     * no_speech_prob} (gần 1 = có thể là im lặng/nhiễu bị nhận nhầm thành chữ), {@code compression_ratio}
     * (vượt ngưỡng = dấu hiệu lặp từ/bịa theo khuôn, lỗi hallucination kinh điển của họ Whisper). Không
     * chặn cứng (vẫn trả transcript để chấm tiếp) — chỉ đính kèm cảnh báo để người dùng/giáo viên biết cần
     * nghe lại, tinh thần giống {@code SpeechMeter} chặn bịa bằng tốc độ nói nhưng bắt được KIỂU bịa khác
     * (sai nội dung thay vì sai tốc độ).
     *
     * Fix 2026-10-22 lần 2 (đã xác nhận với người dùng, phát hiện khi test thật — case "housework" bị
     * nghe nhầm "our work" dù avg_logprob RẤT tốt, tức cờ cảnh báo ở trên KHÔNG bắt được kiểu lỗi "model tự
     * tin nhưng sai") — thêm {@code promptHint} (bài viết Bước 1, nếu có) vào field {@code prompt} chuẩn
     * Whisper/Groq để THIÊN HƯỚNG từ vựng, KHÔNG ép buộc. Đã verify gọi tay 2 chiều trước khi áp dụng:
     * (1) gợi ý ĐÚNG ngữ cảnh ("...with housework.") sửa đúng lỗi, avg_logprob còn TỐT HƠN (-0.13 so với
     * -0.20 không gợi ý); (2) gợi ý SAI HẲN chủ đề (về thời tiết/biển) KHÔNG đánh lừa được Groq — vẫn ra
     * đúng audio thật ("our work" như không có gợi ý) — chứng minh {@code prompt} chỉ thiên hướng, không
     * phải ép Groq chép lại bất kể audio nói gì.
     */
    private GroqTranscriptionResult transcribeWithGroq(MultipartFile audio, String promptHint) {
        try {
            String boundary = "----ppsSpikeGroq" + UUID.randomUUID();
            byte[] body = buildGroqMultipartBody(boundary, audio, promptHint);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/audio/transcriptions"))
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalArgumentException("Groq transcription lỗi (HTTP " + response.statusCode() + "): " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            String text = json.path("text").asText("");
            List<String> warnings = new ArrayList<>();
            for (JsonNode seg : json.path("segments")) {
                double avgLogprob = seg.path("avg_logprob").asDouble(0);
                double noSpeechProb = seg.path("no_speech_prob").asDouble(0);
                double compressionRatio = seg.path("compression_ratio").asDouble(0);
                if (avgLogprob < GROQ_LOW_CONFIDENCE_AVG_LOGPROB || noSpeechProb > GROQ_HIGH_NO_SPEECH_PROB
                        || compressionRatio > GROQ_HIGH_COMPRESSION_RATIO) {
                    warnings.add(String.format("\"%s\" (avg_logprob=%.2f, no_speech_prob=%.2f, compression_ratio=%.2f)",
                            seg.path("text").asText("").trim(), avgLogprob, noSpeechProb, compressionRatio));
                }
            }
            String warning = warnings.isEmpty() ? null
                    : "Groq không chắc chắn ở đoạn: " + String.join("; ", warnings) + " — nên nghe lại audio gốc để xác nhận, đừng tin tuyệt đối transcript.";
            return new GroqTranscriptionResult(text, warning);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalArgumentException("Không gọi được Groq transcription: " + e.getMessage(), e);
        }
    }

    /** Groq (giống OpenAI Whisper) giới hạn field {@code prompt} ~224 token — cắt thô theo ký tự cho an toàn, bài viết Bước 1 luôn rất ngắn nên hiếm khi chạm ngưỡng này. */
    private static final int GROQ_PROMPT_MAX_CHARS = 800;

    private byte[] buildGroqMultipartBody(String boundary, MultipartFile audio, String promptHint) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String prefix = "--" + boundary + "\r\n";
        out.write((prefix + "Content-Disposition: form-data; name=\"model\"\r\n\r\n" + GROQ_TRANSCRIBE_MODEL + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write((prefix + "Content-Disposition: form-data; name=\"response_format\"\r\n\r\nverbose_json\r\n")
                .getBytes(StandardCharsets.UTF_8));
        if (promptHint != null && !promptHint.isBlank()) {
            String trimmedHint = promptHint.length() > GROQ_PROMPT_MAX_CHARS ? promptHint.substring(0, GROQ_PROMPT_MAX_CHARS) : promptHint;
            out.write((prefix + "Content-Disposition: form-data; name=\"prompt\"\r\n\r\n" + trimmedHint + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        String filename = audio.getOriginalFilename() == null ? "audio.webm" : audio.getOriginalFilename();
        String contentType = audio.getContentType() == null ? "application/octet-stream" : audio.getContentType();
        out.write((prefix + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(audio.getBytes());
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private String transcribeWithOpenAi(MultipartFile audio) {
        try {
            String boundary = "----ppsSpike" + UUID.randomUUID();
            byte[] body = buildMultipartBody(boundary, audio);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/audio/transcriptions"))
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalArgumentException("OpenAI transcription lỗi (HTTP " + response.statusCode() + "): " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            return json.path("text").asText("");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalArgumentException("Không gọi được OpenAI transcription: " + e.getMessage(), e);
        }
    }

    private byte[] buildMultipartBody(String boundary, MultipartFile audio) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String prefix = "--" + boundary + "\r\n";
        out.write((prefix + "Content-Disposition: form-data; name=\"model\"\r\n\r\n" + OPENAI_TRANSCRIBE_MODEL + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
        String filename = audio.getOriginalFilename() == null ? "audio.webm" : audio.getOriginalFilename();
        String contentType = audio.getContentType() == null ? "application/octet-stream" : audio.getContentType();
        out.write((prefix + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(audio.getBytes());
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** Gemini nhận audio trực tiếp (đa phương thức, khác Claude/GPT text-only) — không cần bước STT riêng như nhánh OpenAI. */
    private String transcribeWithGemini(MultipartFile audio) {
        try {
            String base64Audio = Base64.getEncoder().encodeToString(audio.getBytes());
            String mimeType = audio.getContentType() == null ? "audio/webm" : audio.getContentType();
            ObjectNode payload = objectMapper.createObjectNode();
            ArrayNode contents = payload.putArray("contents");
            ObjectNode contentEntry = contents.addObject();
            ArrayNode parts = contentEntry.putArray("parts");
            parts.addObject().put("text",
                    "Transcribe chính xác đoạn audio sau sang chữ (tiếng Anh nếu học sinh nói tiếng Anh). "
                            + "Chỉ trả về DUY NHẤT phần chữ đã transcribe, không thêm chú thích/nhận xét gì khác.");
            ObjectNode inlineData = parts.addObject().putObject("inline_data");
            inlineData.put("mime_type", mimeType);
            inlineData.put("data", base64Audio);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent"))
                    .header("x-goog-api-key", geminiApiKey)
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalArgumentException("Gemini transcription lỗi (HTTP " + response.statusCode() + "): " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            return json.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("").trim();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalArgumentException("Không gọi được Gemini transcription: " + e.getMessage(), e);
        }
    }

    private GradeResult gradeContent(String transcript, Provider provider) {
        String systemPrompt = "Bạn là giám khảo chấm Speaking tiếng Anh cho học sinh trung tâm Anh ngữ. Tiêu chí chấm:\n"
                + PLACEHOLDER_RUBRIC
                + "\nChỉ trả lời DUY NHẤT 1 JSON hợp lệ, không thêm chữ nào khác, đúng format: "
                + "{\"scorePercent\": <số nguyên 0-100>, \"feedback\": \"<nhận xét ngắn gọn tiếng Việt>\"}";
        String userContent = "Câu trả lời của học sinh (đã chuyển từ voice sang chữ, có thể có lỗi nhận dạng giọng nói): \""
                + transcript + "\"";
        return callGradingModel(systemPrompt, userContent, provider);
    }

    private GradeResult gradeGrammar(String writingText, Provider provider) {
        String systemPrompt = "Bạn là giáo viên chấm ngữ pháp tiếng Anh cho học sinh trung tâm Anh ngữ. "
                + "Chấm % dựa trên số lỗi ngữ pháp/chính tả so với độ dài bài viết (nhiều lỗi = điểm thấp). "
                + "Chỉ trả lời DUY NHẤT 1 JSON hợp lệ, không thêm chữ nào khác, đúng format: "
                + "{\"scorePercent\": <số nguyên 0-100>, \"feedback\": \"<liệt kê lỗi chính, tiếng Việt>\"}";
        String userContent = "Bài viết của học sinh: \"" + writingText + "\"";
        return callGradingModel(systemPrompt, userContent, provider);
    }

    private GradeResult callGradingModel(String systemPrompt, String userContent, Provider provider) {
        if (provider == Provider.GEMINI) {
            return callGeminiForGrade(systemPrompt, userContent);
        }
        String model = provider == Provider.AZURE_STACK ? CLAUDE_GRADING_MODEL_V2 : CLAUDE_GRADING_MODEL;
        return callClaudeForGrade(systemPrompt, userContent, model);
    }

    private GradeResult callClaudeForGrade(String systemPrompt, String userContent, String model) {
        ClaudeRawResult raw = callClaudeRawWithUsage(systemPrompt, userContent, model, CLAUDE_MAX_TOKENS_SHORT_JSON);
        JsonNode parsed = parseJsonOrThrow(raw.text());
        return new GradeResult(parsed.path("scorePercent").asInt(0), parsed.path("feedback").asText(""), raw.usage());
    }

    /** Gemini API (generativelanguage.googleapis.com) — free tier, không cần thẻ thanh toán. Chưa đo token/chi phí (xem {@link #estimateClaudeCostUsd} — phần chi phí ước tính coi Gemini = 0, chỉ tính phần Groq/Azure/Claude thật). */
    private GradeResult callGeminiForGrade(String systemPrompt, String userContent) {
        JsonNode parsed = parseJsonOrThrow(callGeminiRaw(systemPrompt, userContent));
        return new GradeResult(parsed.path("scorePercent").asInt(0), parsed.path("feedback").asText(""), null);
    }

    /** Như {@link #callClaudeForGrade} nhưng parse 4 tiêu chí IELTS Writing thay vì 1 điểm tổng — dùng cho {@link #gradeWritingOnly}. */
    private WritingGradeResult callClaudeForWritingGrade(String systemPrompt, String userContent, String model) {
        ClaudeRawResult raw = callClaudeRawWithUsage(systemPrompt, userContent, model, CLAUDE_MAX_TOKENS_SHORT_JSON);
        return parseWritingGradeResult(parseJsonOrThrow(raw.text()), raw.usage());
    }

    private WritingGradeResult callGeminiForWritingGrade(String systemPrompt, String userContent) {
        return parseWritingGradeResult(parseJsonOrThrow(callGeminiRaw(systemPrompt, userContent)), null);
    }

    /** {@link ObjectMapper#readTree(String)} khai báo {@code throws JsonProcessingException} (checked) — bọc lại thành unchecked để không phải rải try/catch ở mọi wrapper gọi hàm này. */
    private JsonNode parseJsonOrThrow(String rawText) {
        try {
            return objectMapper.readTree(extractJson(rawText));
        } catch (IOException e) {
            throw new IllegalArgumentException("Model chấm bài không trả về JSON hợp lệ: " + rawText, e);
        }
    }

    private WritingGradeResult parseWritingGradeResult(JsonNode parsed, AiTokenUsage usage) {
        return new WritingGradeResult(
                nullableInt(parsed, "taskAchievement"),
                nullableInt(parsed, "coherenceCohesion"),
                nullableInt(parsed, "lexicalResource"),
                nullableInt(parsed, "grammaticalRange"),
                parsed.path("feedback").asText(""), usage);
    }

    private Integer nullableInt(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asInt();
    }

    /** Ngân sách mặc định cho 1 JSON ngắn (điểm + feedback) — xem {@link #callClaudeRaw(String, String, String)}. */
    private static final int CLAUDE_MAX_TOKENS_SHORT_JSON = 2048;
    /**
     * Fix 2026-10-22 (phát hiện khi test rubric v3 thật) — output rubric v3 KHÔNG phải 1 JSON ngắn, mà là
     * cả bài đã đánh dấu lỗi inline + bảng điểm + mục "0. Kiểm đếm" (bằng chứng từng checkpoint) — dài hơn
     * nhiều lần so với placeholder. 2048 từng đủ cho JSON ngắn nhưng KHÔNG đủ cho output này: Claude Haiku
     * 5.5 ngốn hết ngân sách vào "thinking" ẩn, KHÔNG còn chỗ sinh text -> content rỗng, gradeV3 coi là
     * "chấm thất bại". Luồng thật qua 9Router (NineRouterAiClient) không set max_tokens nên không gặp giới
     * hạn này — đây là giới hạn riêng của code spike, không phải giới hạn thật của Claude/Anthropic.
     *
     * Fix 2026-10-22 lần 2 (vẫn test thật) — tăng lên 8192 CHƯA đủ: log cho thấy CẢ 2 lần thử đều
     * {@code outputTokens=8192} (đúng bằng trần) nhưng content vẫn rỗng — nghĩa là "thinking" ẩn ăn hết
     * 100% ngân sách output, không chừa 1 token nào cho phần markdown thật (khác hẳn case JSON ngắn trước
     * đó, nơi thinking chỉ tốn ~150/2048). Việc rubric v3 bắt model tự liệt kê bằng chứng cho TỪNG
     * checkpoint trước khi chấm (mục 0) khiến effort suy nghĩ tự đẩy lên rất cao. Sửa bằng
     * {@code "effort": "low"} ở {@link #callClaudeRawWithUsage} + tăng trần lên 16384 làm lưới an toàn.
     */
    private static final int CLAUDE_MAX_TOKENS_LONG_RUBRIC = 16384;

    private String callClaudeRaw(String systemPrompt, String userContent, String model) {
        return callClaudeRaw(systemPrompt, userContent, model, CLAUDE_MAX_TOKENS_SHORT_JSON);
    }

    /**
     * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — prompt caching Anthropic (gọi thẳng API, KHÔNG
     * qua 9Router). Gửi {@code system} dạng MẢNG 1 block kèm {@code cache_control: {type: ephemeral}}
     * thay vì chuỗi thường — bật cache phía Anthropic cho đúng phần system prompt (rubric cố định theo
     * Khối/Track, giống hệt giữa nhiều học sinh cùng lớp). Khác hẳn 9Router: Javadoc
     * {@link NineRouterAiClient#extractCachedTokens} đã xác nhận bằng gọi tay 9Router KHÔNG BAO GIỜ trả
     * field cache qua {@code /v1/chat/completions} dù cache có xảy ra hay không — gọi thẳng Anthropic thì
     * đọc được thật (field {@code cache_read_input_tokens}/{@code cache_creation_input_tokens}).
     * Cache có hiệu lực ~5 phút kể từ lần ghi gần nhất (mặc định "ephemeral" của Anthropic), chỉ có lợi
     * khi prompt đủ dài (rubric v3 — ~ngàn token) và được gọi lại nhiều lần liên tiếp (nhiều bài cùng
     * khối/track trong 1 đợt chấm) — prompt ngắn (placeholder) gửi cache_control không hại gì nhưng khó
     * đạt ngưỡng tối thiểu để cache thật sự kích hoạt.
     */
    private String callClaudeRaw(String systemPrompt, String userContent, String model, int maxTokens) {
        return callClaudeRawWithUsage(systemPrompt, userContent, model, maxTokens).text();
    }

    private record ClaudeRawResult(String text, AiTokenUsage usage) {
    }

    /** Như {@link #callClaudeRaw(String, String, String, int)} nhưng trả kèm usage THẬT (gồm số token cache) — dùng khi caller cần hiển thị bằng chứng cache cho người dùng, không chỉ ghi log. */
    private ClaudeRawResult callClaudeRawWithUsage(String systemPrompt, String userContent, String model, int maxTokens) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", model);
            payload.put("max_tokens", maxTokens);
            // Fix 2026-10-22 lần 3 (đã xác nhận với người dùng) — "effort" KHÔNG phải field hợp lệ của
            // Anthropic Messages API (gọi tay xác nhận HTTP 400 "Extra inputs are not permitted" — nhớ
            // nhầm tên field). Field đúng để tắt "thinking" ẩn là {@code thinking: {type: "disabled"}} (đã
            // verify gọi tay: thinking_tokens=0, content chỉ còn đúng 1 block "text", không còn block
            // "thinking" rỗng phải lọc). Rubric v3 đã liệt kê sẵn checklist cụ thể cho từng checkpoint
            // trong prompt — không cần model tự suy luận thêm, tắt hẳn để KHÔNG tốn token vào việc này.
            payload.putObject("thinking").put("type", "disabled");
            ArrayNode systemBlocks = payload.putArray("system");
            ObjectNode systemBlock = systemBlocks.addObject();
            systemBlock.put("type", "text");
            systemBlock.put("text", systemPrompt);
            systemBlock.putObject("cache_control").put("type", "ephemeral");
            ArrayNode messages = payload.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userContent);

            long startedAt = System.currentTimeMillis();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("x-api-key", anthropicApiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalArgumentException("Claude API lỗi (HTTP " + response.statusCode() + "): " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            logClaudeCacheUsage(json);
            return new ClaudeRawResult(extractClaudeTextBlock(json), buildUsageFromClaude(json, model, System.currentTimeMillis() - startedAt));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalArgumentException("Không gọi được Claude API: " + e.getMessage(), e);
        }
    }

    /** {@code cachedTokens} (xem {@link AiTokenUsage}) ghi {@code cache_read_input_tokens} — phần THỰC SỰ được giảm giá do cache hit, không phải {@code cache_creation_input_tokens} (lần ghi, không được giảm giá, còn tốn thêm ~25%). */
    private AiTokenUsage buildUsageFromClaude(JsonNode json, String requestedModel, long elapsedMs) {
        JsonNode usage = json.path("usage");
        String servedModel = json.path("model").asText(requestedModel);
        if (usage.isMissingNode()) {
            return AiTokenUsage.unknown(servedModel, false, elapsedMs);
        }
        return new AiTokenUsage(servedModel, false,
                usage.path("input_tokens").asInt(0),
                usage.path("cache_read_input_tokens").asInt(0),
                usage.path("output_tokens").asInt(0),
                0, elapsedMs);
    }

    /**
     * Đo cache prompt THẬT (không phỏng đoán) — mirror triết lý {@code NineRouterAiClient#logUsage}.
     * {@code cache_creation_input_tokens > 0} = lần này VỪA GHI cache (lần gọi đầu với prompt đó, tốn thêm
     * ~25% giá input cho phần ghi); {@code cache_read_input_tokens > 0} = lần này ĐỌC ĐƯỢC cache (giảm ~90%
     * giá phần đó) — 2 số đều 0 nghĩa là cache KHÔNG kích hoạt (prompt dưới ngưỡng tối thiểu, hoặc đã hết
     * hạn ~5 phút, hoặc đổi model/organization). Mức INFO vì cần thấy ngay khi test thủ công, không phải
     * log gỡ lỗi bật tạm.
     */
    private void logClaudeCacheUsage(JsonNode json) {
        JsonNode usage = json.path("usage");
        if (usage.isMissingNode()) {
            return;
        }
        log.info("SpeakingAiGradingTestService (Claude cache): model={} cacheCreationTokens={} cacheReadTokens={} inputTokens={} outputTokens={}",
                json.path("model").asText("?"),
                usage.path("cache_creation_input_tokens").asInt(0),
                usage.path("cache_read_input_tokens").asInt(0),
                usage.path("input_tokens").asInt(0),
                usage.path("output_tokens").asInt(0));
    }

    /**
     * Fix 2026-10-22 (phát hiện khi test thật, đã xác nhận với người dùng) — Claude Haiku 5.5 trả
     * {@code content} là MẢNG nhiều block khi model tự bật extended thinking, VD
     * {@code [{"type":"thinking","text":""},{"type":"text","text":"{...json...}"}]}. Lấy cứng
     * {@code content[0]} (như code cũ, đúng cho Haiku 4.5 không có thinking block) sẽ luôn ra block
     * thinking RỖNG, khiến {@link #extractJson} ném lỗi "không trả về JSON hợp lệ" dù API trả 200 OK.
     * Phải duyệt mảng tìm đúng block {@code type=="text"}.
     */
    private String extractClaudeTextBlock(JsonNode json) {
        for (JsonNode block : json.path("content")) {
            if ("text".equals(block.path("type").asText(""))) {
                return block.path("text").asText("");
            }
        }
        return "";
    }

    /** @return text đã trích JSON (chưa parse) từ response Gemini — xem {@link #callClaudeRaw}. */
    private String callGeminiRaw(String systemPrompt, String userContent) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            ObjectNode systemInstruction = payload.putObject("system_instruction");
            systemInstruction.putArray("parts").addObject().put("text", systemPrompt);
            ArrayNode contents = payload.putArray("contents");
            ObjectNode contentEntry = contents.addObject();
            contentEntry.putArray("parts").addObject().put("text", userContent);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent"))
                    .header("x-goog-api-key", geminiApiKey)
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalArgumentException("Gemini API lỗi (HTTP " + response.statusCode() + "): " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            return json.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalArgumentException("Không gọi được Gemini API: " + e.getMessage(), e);
        }
    }

    /** LLM đôi khi bọc thêm text/markdown quanh JSON dù đã dặn "chỉ trả JSON" — cắt từ '{' đầu tới '}' cuối cho an toàn. */
    private String extractJson(String rawText) {
        int start = rawText.indexOf('{');
        int end = rawText.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Model chấm bài không trả về JSON hợp lệ: " + rawText);
        }
        return rawText.substring(start, end + 1);
    }

    /**
     * Azure Speech Pronunciation Assessment, chế độ KHÔNG văn bản mẫu (unscripted — {@code ReferenceText}
     * rỗng), đúng bối cảnh speaking tự do (không đọc lại 1 câu cho sẵn). Dùng REST "short audio" endpoint
     * ({@code /speech/recognition/conversation/...}) thay vì Speech SDK (SDK cần native lib, không hợp
     * backend Java thuần spike này) — audio PHẢI là WAV PCM16 mono 16kHz nên tái dùng {@link AudioTranscoder}
     * đã có sẵn (cùng converter {@code ReflexV2AiGradingService} đang dùng cho {@code SpeechMeter}).
     *
     * Fix 2026-10-22 (đã xác nhận với người dùng, phát hiện khi test thật) — {@code ReferenceText} rỗng
     * (unscripted) khiến Azure phải TỰ DO đoán cả câu, đã verify bằng gọi tay: Azure đoán sai hẳn nguyên
     * cụm ("We had dinner too late" → "We had been not related") dù phần đầu câu nghe đúng gần tuyệt đối.
     * Sửa bằng {@code ReferenceText = transcript Groq} (ép Azure "forced-align" âm thanh vào đúng chữ đã
     * biết, thay vì tự đoán) — đã verify gọi tay: cùng file audio, cùng kết quả sai ở chế độ rỗng, ra ĐÚNG
     * 100% ở chế độ có ReferenceText, còn tự phát hiện đúng từ "dinner" phát âm yếu (56%,
     * ErrorType=Mispronunciation) thay vì đoán nhầm cả cụm.
     *
     * KHÔNG dùng bài viết Bước 1 làm ReferenceText (dù cũng đã nghĩ tới) — học sinh có thể nói KHÁC bài
     * viết, ép khớp vào bài viết sẽ chấm phát âm cho từ chưa chắc đã được nói. Dùng transcript Groq (đã
     * verify chính xác qua 2 lượt test thật) làm ReferenceText thay vì bài viết — luôn khớp đúng NHỮNG GÌ
     * THỰC SỰ ĐƯỢC NÓI, bất kể có giống bài viết hay không. {@code EnableMiscue=true} để Azure tự báo
     * thêm Insertion/Omission nếu phát âm thực tế lệch khỏi reference (hiếm khi xảy ra vì reference lấy
     * từ chính STT của audio này, nhưng vẫn bật phòng Azure+Groq nghe lệch nhau ở biên câu).
     */
    private AzurePronunciationResult callAzurePronunciation(MultipartFile audio, String referenceText) {
        try {
            byte[] rawBytes = audio.getBytes();
            String mimeType = audio.getContentType() == null ? "audio/webm" : audio.getContentType();
            Optional<byte[]> wav = audioTranscoder.toWav(rawBytes, mimeType);
            if (wav.isEmpty()) {
                return AzurePronunciationResult.error("Không convert được audio sang WAV 16kHz (ffmpeg lỗi/thiếu) — xem log AudioTranscoder.");
            }

            ObjectNode assessmentConfig = objectMapper.createObjectNode();
            assessmentConfig.put("ReferenceText", referenceText == null ? "" : referenceText);
            assessmentConfig.put("GradingSystem", "HundredMark");
            assessmentConfig.put("Granularity", "Phoneme");
            assessmentConfig.put("Dimension", "Comprehensive");
            assessmentConfig.put("EnableMiscue", referenceText != null && !referenceText.isBlank());
            assessmentConfig.put("EnableProsodyAssessment", true);
            String pronunciationHeader = Base64.getEncoder()
                    .encodeToString(objectMapper.writeValueAsString(assessmentConfig).getBytes(StandardCharsets.UTF_8));

            String url = "https://" + azureSpeechRegion + ".stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1"
                    + "?language=" + AZURE_PRONUNCIATION_LANGUAGE + "&format=detailed";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Ocp-Apim-Subscription-Key", azureSpeechKey)
                    .header("Content-Type", "audio/wav; codecs=audio/pcm; samplerate=16000")
                    .header("Accept", "application/json")
                    .header("Pronunciation-Assessment", pronunciationHeader)
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(wav.get()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                return AzurePronunciationResult.error("Azure Pronunciation Assessment lỗi (HTTP " + response.statusCode() + "): " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            // Fix 2026-10-22 (phát hiện khi test thật, đã xác nhận với người dùng) — nhớ SAI shape Azure:
            // AccuracyScore/FluencyScore/ProsodyScore/CompletenessScore/PronScore nằm NGANG HÀNG ngay trong
            // NBest[0] (không lồng thêm 1 object con "PronunciationAssessment" ở mức câu — object con đó CHỈ
            // có ở từng phần tử NBest[0].Words[].PronunciationAssessment, mức TỪ, khác hẳn mức câu). Gọi tay
            // xác nhận: response thật có đủ field, chỉ là code tìm sai đường dẫn nên luôn ra null.
            JsonNode assessment = json.path("NBest").path(0);
            if (assessment.isMissingNode() || assessment.path("AccuracyScore").isMissingNode()) {
                return AzurePronunciationResult.error("Azure trả về nhưng không có điểm phát âm ở NBest[0] — có thể audio quá ngắn/im lặng: " + response.body());
            }
            return new AzurePronunciationResult(
                    assessment.path("AccuracyScore").isMissingNode() ? null : assessment.path("AccuracyScore").asInt(),
                    assessment.path("FluencyScore").isMissingNode() ? null : assessment.path("FluencyScore").asInt(),
                    assessment.path("CompletenessScore").isMissingNode() ? null : assessment.path("CompletenessScore").asInt(),
                    assessment.path("ProsodyScore").isMissingNode() ? null : assessment.path("ProsodyScore").asInt(),
                    assessment.path("PronScore").isMissingNode() ? null : assessment.path("PronScore").asInt(),
                    response.body(), parseAzureWords(assessment));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return AzurePronunciationResult.error("Không gọi được Azure Pronunciation Assessment: " + e.getMessage());
        }
    }

    private record GradeResult(int scorePercent, String feedback, AiTokenUsage usage) {
    }

    private record WritingGradeResult(Integer taskAchievement, Integer coherenceCohesion, Integer lexicalResource,
                                       Integer grammaticalRange, String feedback, AiTokenUsage usage) {
    }

    private record AzurePronunciationResult(Integer accuracyScore, Integer fluencyScore, Integer completenessScore,
                                             Integer prosodyScore, Integer overallScore, String rawJson,
                                             List<PronunciationWordDetail> words) {
        static AzurePronunciationResult error(String message) {
            return new AzurePronunciationResult(null, null, null, null, null, message, null);
        }
    }

    /**
     * Fix 2026-10-22 lần 2 (đã xác nhận với người dùng, phát hiện khi test thật với response Azure đầy đủ)
     * — nhớ SAI shape lần nữa: {@code Words[].AccuracyScore}/{@code ErrorType} và
     * {@code Words[].Phonemes[].AccuracyScore} đều NẰM PHẲNG trực tiếp trong object, KHÔNG lồng qua
     * {@code PronunciationAssessment} ở CẢ 2 mức (từ lẫn âm vị) — khác hẳn giả định trước (nhầm với tài
     * liệu SDK mô tả 1 phiên bản API khác). Bằng chứng: response thật có {@code "Word":"not",...,
     * "AccuracyScore":100.0,"ErrorType":"None"} không có field {@code PronunciationAssessment} nào bọc
     * ngoài. Đọc phẳng trực tiếp, không còn dò qua object con nữa.
     */
    private List<PronunciationWordDetail> parseAzureWords(JsonNode assessment) {
        List<PronunciationWordDetail> words = new ArrayList<>();
        for (JsonNode w : assessment.path("Words")) {
            Double wordAccuracy = w.path("AccuracyScore").isMissingNode() ? null : w.path("AccuracyScore").asDouble();
            String errorType = w.path("ErrorType").isMissingNode() ? null : w.path("ErrorType").asText();
            List<PronunciationPhonemeDetail> phonemes = new ArrayList<>();
            for (JsonNode p : w.path("Phonemes")) {
                Double phAccuracy = p.path("AccuracyScore").isMissingNode() ? null : p.path("AccuracyScore").asDouble();
                phonemes.add(new PronunciationPhonemeDetail(p.path("Phoneme").asText(""), phAccuracy));
            }
            words.add(new PronunciationWordDetail(w.path("Word").asText(""), wordAccuracy, errorType, phonemes));
        }
        return words;
    }

    // ===================== Ước tính chi phí (2026-10-22, đã xác nhận với người dùng) =====================
    // Đơn giá lấy ĐÚNG theo con số đã trao đổi trong tài liệu chat-ai-cham-writing-speaking.md — ƯỚC TÍNH
    // THÔ, KHÔNG phải hoá đơn thật (Azure/Groq/Anthropic đổi giá theo thời gian, theo region). Chỉ dùng để
    // có cảm giác về độ lớn chi phí khi so sánh 3 phương án (OPENAI_CLAUDE/GEMINI/AZURE_STACK), KHÔNG dùng
    // số này để chốt ngân sách thật — phải tra lại pricing calculator chính thức trước khi quyết định.

    private static final double USD_TO_VND = 26000;
    private static final double GROQ_WHISPER_USD_PER_HOUR = 0.04;
    /** 1 nguồn trong tài liệu ghi $1.32/giờ (nguồn khác ghi $1 + $0.30 phụ phí) — lấy mốc cao hơn cho an toàn khi ước tính. */
    private static final double AZURE_PRONUNCIATION_USD_PER_HOUR = 1.32;
    private static final double CLAUDE_HAIKU_INPUT_USD_PER_MILLION = 0.10;
    private static final double CLAUDE_HAIKU_OUTPUT_USD_PER_MILLION = 0.50;
    /** Anthropic: phần input phục vụ từ cache tính ~10% giá input gốc (không tính phụ phí ghi cache lần đầu — xem Javadoc {@link #callClaudeRawWithUsage}). */
    private static final double CLAUDE_CACHE_READ_DISCOUNT = 0.1;

    private double claudeCostUsd(AiTokenUsage usage) {
        if (usage == null) {
            return 0;
        }
        double cachedTokens = usage.cachedTokens();
        double nonCachedInputTokens = Math.max(0, usage.promptTokens() - cachedTokens);
        double inputCost = (nonCachedInputTokens * CLAUDE_HAIKU_INPUT_USD_PER_MILLION
                + cachedTokens * CLAUDE_HAIKU_INPUT_USD_PER_MILLION * CLAUDE_CACHE_READ_DISCOUNT) / 1_000_000.0;
        double outputCost = usage.completionTokens() * CLAUDE_HAIKU_OUTPUT_USD_PER_MILLION / 1_000_000.0;
        return inputCost + outputCost;
    }

    private double audioCostUsd(double usdPerHour, double durationSec) {
        return durationSec / 3600.0 * usdPerHour;
    }

    private long toVnd(double usd) {
        return Math.round(usd * USD_TO_VND);
    }
}
