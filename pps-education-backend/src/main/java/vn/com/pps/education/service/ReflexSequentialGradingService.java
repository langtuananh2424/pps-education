package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.common.ReflexV2Task;
import vn.com.pps.education.domain.AiGradingTokenUsage;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.ReflexQuestionProgress;
import vn.com.pps.education.domain.ReflexQuestionProgressHistory;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.ReviewVideoQuestion;
import vn.com.pps.education.domain.ReviewVideoSet;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.dto.ReflexQuestionProgressResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.SubmissionPastDeadlineException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ReflexQuestionProgressHistoryRepository;
import vn.com.pps.education.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.repository.ReviewVideoAssignmentRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionRepository;
import vn.com.pps.education.repository.StudentRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * UC-23b V2 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22) — "Video phản xạ" đổi
 * hướng: thay luồng cũ "xem 1 video liên tục, ghi âm mỗi câu theo mốc thời gian, nộp cả loạt cuối
 * video, GV chấm tay" (vẫn còn nguyên trong {@link ReviewVideoService}/{@link ReviewVideoGradingPanel}
 * — KHÔNG xoá, chỉ không còn dùng cho video REFLEX theo luồng mới) bằng luồng TUẦN TỰ theo từng câu
 * hỏi: viết trước → {@link ReflexWritingGrammarAiGradingService} chấm ngữ pháp → đạt ngưỡng % mới mở
 * khoá ghi âm → {@link ReflexSpeakingContentAiGradingService} chấm nội dung (transcribe + chấm,
 * Gemini) → đạt ngưỡng % mới mở khoá câu tiếp theo. Ngưỡng % (mặc định 70, đã xác nhận với người dùng
 * 2026-08-22) cấu hình được theo từng video từ V168 (đã xác nhận với người dùng 2026-09-08, trước đó
 * hardcode cố định — xem {@link #passThresholdPercent}). KHÔNG giới hạn số lần thử lại (đã xác nhận
 * với người dùng) — nộp lại chỉ SỬA ĐÈ dòng {@link ReflexQuestionProgress} hiện có, không tạo bản ghi
 * lịch sử mới.
 *
 * Tách THÀNH SERVICE RIÊNG (không thêm vào {@link ReviewVideoService}, dù cùng UC-23b) vì
 * {@link ReviewVideoService} đã rất lớn (nhiều nhóm nghiệp vụ: CONNECTION/REFLEX cũ/thống kê/lịch sử)
 * — mirror lại (KHÔNG gọi lại) {@code resolveStudentAccessForAssignment}/{@code requireNotPastDeadline}
 * /{@code getQuestionOrThrow} của ReviewVideoService (private, không expose được) để không phải sửa
 * sâu vào 1 file rất lớn đã có nhiều lịch sử quyết định phức tạp — xem .claude/rules/solid.md.
 */
@Service
public class ReflexSequentialGradingService {

    private static final Logger log = LoggerFactory.getLogger(ReflexSequentialGradingService.class);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String AI_GRADING_FAILED_FEEDBACK = "Không chấm được tự động — vui lòng thử nộp lại.";

    private final ReviewVideoQuestionRepository reviewVideoQuestionRepository;
    private final ReviewVideoAssignmentRepository reviewVideoAssignmentRepository;
    private final ReflexQuestionProgressRepository reflexQuestionProgressRepository;
    private final ReflexQuestionProgressHistoryRepository reflexQuestionProgressHistoryRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final MediaStorageService mediaStorageService;
    private final ReflexWritingGrammarAiGradingService writingGradingService;
    private final ReflexSpeakingContentAiGradingService speakingGradingService;
    private final ReflexV2AiGradingService reflexV2GradingService;
    private final AiGradingTokenUsageRecorder tokenUsageRecorder;

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21 — bật luồng chấm bộ tiêu chí Speaking v2
     * (Khối 6-7, xem {@link ReflexV2AiGradingService}). MẶC ĐỊNH TẮT: bật bằng REFLEX_V2_ENABLED=true sau khi
     * đã kiểm chứng 9Router (model 3.6-medium, ffmpeg trong image). Khi tắt, hoặc Khối/track chưa có bộ v2
     * (Khối 8/9, Khối 7 thiếu track), hành vi y hệt luồng cũ.
     */
    @Value("${app.ai-grading.reflex-v2.enabled:false}")
    private boolean reflexV2Enabled;

    public ReflexSequentialGradingService(ReviewVideoQuestionRepository reviewVideoQuestionRepository,
                                           ReviewVideoAssignmentRepository reviewVideoAssignmentRepository,
                                           ReflexQuestionProgressRepository reflexQuestionProgressRepository,
                                           ReflexQuestionProgressHistoryRepository reflexQuestionProgressHistoryRepository,
                                           ClassEnrollmentRepository classEnrollmentRepository,
                                           StudentRepository studentRepository,
                                           MediaStorageService mediaStorageService,
                                           ReflexWritingGrammarAiGradingService writingGradingService,
                                           ReflexSpeakingContentAiGradingService speakingGradingService,
                                           ReflexV2AiGradingService reflexV2GradingService,
                                           AiGradingTokenUsageRecorder tokenUsageRecorder) {
        this.reviewVideoQuestionRepository = reviewVideoQuestionRepository;
        this.reviewVideoAssignmentRepository = reviewVideoAssignmentRepository;
        this.reflexQuestionProgressRepository = reflexQuestionProgressRepository;
        this.reflexQuestionProgressHistoryRepository = reflexQuestionProgressHistoryRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.mediaStorageService = mediaStorageService;
        this.writingGradingService = writingGradingService;
        this.speakingGradingService = speakingGradingService;
        this.reflexV2GradingService = reflexV2GradingService;
        this.tokenUsageRecorder = tokenUsageRecorder;
    }

    /** Bước 1: nộp câu trả lời viết, AI chấm ngữ pháp ngay. */
    @Transactional
    public ReflexQuestionProgressResponse submitWrittenAnswer(Long questionId, Long assignmentId, String answerText, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        StudentAccess access = resolveStudentAccessForAssignment(question.getReviewVideo().getReviewVideoSet(), assignmentId, actorUserId);
        boolean late = requireNotPastDeadline(access.assignment());
        requireReflexVideo(question);

        ReflexQuestionProgress progress = findOrCreate(question, access.student(), access.assignment());
        if (late) {
            progress.setLateSubmission(true);
        }
        progress.setAnswerText(answerText);
        progress.setWritingAttemptCount(progress.getWritingAttemptCount() + 1);

        Curriculum curriculum = question.getReviewVideo().getReviewVideoSet().getCurriculum();
        Optional<ReflexV2Task> v2Task = reflexV2Task(curriculum, question, reflexV2Enabled);
        if (v2Task.isPresent()) {
            ReflexV2AiGradingService.WritingResult v2Result =
                    reflexV2GradingService.gradeWriting(v2Task.get(), question.getPrompt(), answerText);
            recordUsage(AiGradingTokenUsage.Step.WRITING, "chatJson",
                    v2Result == null ? null : v2Result.usage(), progress);
            applyWritingResultV2(progress, v2Result, question);
        } else {
            progress.setRubricVersion(null);
            ReflexWritingGrammarAiGradingService.GradeResult result =
                    writingGradingService.grade(answerText, question.getPrompt(), curriculum);
            recordUsage(AiGradingTokenUsage.Step.WRITING, "chat", result == null ? null : result.usage(), progress);
            applyWritingResult(progress, result);
        }
        progress = reflexQuestionProgressRepository.save(progress);
        recordWritingHistory(progress);
        return toResponse(progress);
    }

    /** Bước 2 — CHỈ chấp nhận khi bước 1 đã đạt: nộp audio, AI transcribe + chấm nội dung ngay. */
    @Transactional
    public ReflexQuestionProgressResponse submitSpokenAnswer(Long questionId, Long assignmentId, String audioUrl, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        StudentAccess access = resolveStudentAccessForAssignment(question.getReviewVideo().getReviewVideoSet(), assignmentId, actorUserId);
        boolean late = requireNotPastDeadline(access.assignment());
        requireReflexVideo(question);

        ReflexQuestionProgress progress = findOrCreate(question, access.student(), access.assignment());
        if (!isWritingPassed(progress)) {
            throw new IllegalArgumentException("Phải đạt phần viết trước khi ghi âm câu hỏi này.");
        }
        if (late) {
            progress.setLateSubmission(true);
        }
        progress.setAudioUrl(audioUrl);
        progress.setSpeakingAttemptCount(progress.getSpeakingAttemptCount() + 1);

        // V147 (2026-08-25, xác nhận với người dùng) — fix bug thật: TRƯỚC ĐÂY hardcode "audio/webm" cho
        // MỌI audio bất kể định dạng thật do trình duyệt ghi ra (Chrome/Android thường "audio/webm",
        // Safari/iOS thường "audio/mp4") — lấy đúng content-type đã lưu ở R2 lúc upload thay vì đoán
        // (xem MediaStorageService#downloadWithContentType).
        MediaStorageService.DownloadedFile audioFile;
        try {
            audioFile = mediaStorageService.downloadWithContentType(audioUrl);
        } catch (Exception e) {
            audioFile = null;
        }
        Curriculum curriculum = question.getReviewVideo().getReviewVideoSet().getCurriculum();
        // Định tuyến theo CHÍNH rubricVersion của dòng (không theo cờ hiện tại): 1 câu đã chấm viết bằng v2 thì
        // bước nói cũng phải v2 (cần điểm Ngữ pháp khoá + số lỗi đỏ) dù cờ có bị đổi giữa chừng.
        Optional<ReflexV2Task> v2Task = "v2".equals(progress.getRubricVersion()) ? reflexV2Task(curriculum, question, true) : Optional.empty();
        if (v2Task.isPresent() && progress.getWritingLockedGrammarPercent() != null) {
            ReflexV2AiGradingService.LockedGrammar locked = new ReflexV2AiGradingService.LockedGrammar(
                    progress.getWritingLockedGrammarPercent().intValue(),
                    progress.getWritingRedErrorCount() == null ? 0 : progress.getWritingRedErrorCount(),
                    progress.getAnswerText());
            // ReflexAudioRejectedException (bản ghi không đọc được / nói khác bài viết) ném thẳng ra → HTTP 422,
            // giao dịch rollback nên KHÔNG tính lượt nộp và KHÔNG ghi điểm. Chi phí từng lượt AI được ghi qua sink
            // ngay khi AI trả về — kể cả khi sau đó bị từ chối / parse lỗi (recorder chạy REQUIRES_NEW nên không
            // bị rollback theo).
            ReflexQuestionProgress usageContext = progress;
            ReflexV2AiGradingService.SpeakingResult result = audioFile == null ? null
                    : reflexV2GradingService.gradeSpeaking(v2Task.get(), question.getPrompt(), audioFile.bytes(), audioFile.contentType(), locked,
                            (step, usage) -> recordUsage(step, "chatWithAudioJson", usage, usageContext));
            applySpeakingResultV2(progress, result);
        } else {
            ReflexSpeakingContentAiGradingService.GradeResult result =
                    audioFile == null ? null : speakingGradingService.grade(audioFile.bytes(), audioFile.contentType(), question.getPrompt(), curriculum);
            recordUsage(AiGradingTokenUsage.Step.SPEAKING, "chatWithAudio", result == null ? null : result.usage(), progress);
            applySpeakingResult(progress, result);
        }
        progress = reflexQuestionProgressRepository.save(progress);
        recordSpeakingHistory(progress);
        return toResponse(progress);
    }

    /** Học sinh xem tiến trình mọi câu hỏi của video này (để dựng đúng trạng thái khoá/mở khi tải lại trang). */
    @Transactional(readOnly = true)
    public List<ReflexQuestionProgressResponse> listMyProgress(Long assignmentId, Long actorUserId) {
        ReviewVideoAssignment assignment = reviewVideoAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideo.setNotFound", new Object[]{assignmentId}, "Không tìm thấy lần giao id=" + assignmentId));
        Student student = studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideo.setNotFound", new Object[]{assignmentId}, "Không tìm thấy lần giao id=" + assignmentId));
        return reflexQuestionProgressRepository.findByReviewVideoAssignmentIdAndStudentId(assignmentId, student.getId())
                .stream().map(this::toResponse).toList();
    }

    // ===================== Helpers =====================

    /**
     * V181 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16) — chấm thành công KHÔNG còn
     * ghi feedback văn xuôi dài dòng nữa (writingFeedback để null) — thay bằng writingMarkedAnswer
     * (chính câu trả lời của học sinh, đánh dấu lỗi bằng markup). writingFeedback nay CHỈ còn dùng cho
     * thông báo khi AI chấm thất bại (nhánh else bên dưới).
     *
     * V184 (2026-09-16, phát hiện qua test thật trên staging, xác nhận với người dùng) — V181 làm học
     * sinh không biết vì sao điểm thấp khi Cổng chặn kích hoạt (VD "Quá ngắn") mà không có lỗi ngữ pháp
     * nào bị đánh dấu (vì thực sự không sai) — writingFeedback = null tuyệt đối nên chẳng có gì giải
     * thích. Sửa: tái dùng CHÍNH writingFeedback để chứa {@code result.gateNote()} khi cổng chặn có kích
     * hoạt (rỗng thì vẫn null như cũ) — field này giờ dùng chung cho 2 trường hợp "cần giải thích ngắn
     * gọn vì sao chưa tốt" (cổng chặn HOẶC AI chấm lỗi), không phải feedback 7 mục dài dòng như trước V181.
     */
    private void applyWritingResult(ReflexQuestionProgress progress, ReflexWritingGrammarAiGradingService.GradeResult result) {
        if (result != null) {
            progress.setWritingScore(BigDecimal.valueOf(result.scorePercent()));
            progress.setWritingMaxScore(HUNDRED);
            progress.setWritingFeedback(result.gateNote() == null || result.gateNote().isBlank() ? null : result.gateNote());
            progress.setWritingMarkedAnswer(result.markedAnswer());
            progress.setWritingGradedAt(OffsetDateTime.now());
            // V141 — chỉ có ý nghĩa khi CHƯA đạt (đạt rồi thì không cần gợi ý sửa nữa) — không set khi đạt
            // để tránh FE lỡ hiện gợi ý sửa cho 1 câu đã đúng.
            progress.setWritingCorrectedAnswer(result.scorePercent() >= passThresholdPercent(progress) ? null : result.correctedAnswer());
        } else {
            progress.setWritingScore(null);
            progress.setWritingMaxScore(null);
            progress.setWritingFeedback(AI_GRADING_FAILED_FEEDBACK);
            progress.setWritingMarkedAnswer(null);
            progress.setWritingGradedAt(null);
            progress.setWritingCorrectedAnswer(null);
        }
    }

    private Optional<ReflexV2Task> reflexV2Task(Curriculum curriculum, ReviewVideoQuestion question, boolean enabled) {
        if (!enabled || curriculum == null) {
            return Optional.empty();
        }
        Optional<ReflexV2Task> task = ReflexV2Task.forGradeTrack(curriculum.getGradeLevel(), curriculum.getTrack(),
                question.getMaxRecordingSeconds());
        // Ngưỡng của rubric được hiệu chuẩn theo đúng thời lượng ghi âm của dạng bài (20/25/30/60/90 giây) —
        // giáo viên đặt lệch thì ngưỡng đếm từ/ý/chỗ ngắt sai; chỉ cảnh báo, không chặn.
        task.filter(t -> t.seconds() != question.getMaxRecordingSeconds()).ifPresent(t ->
                log.warn("ReflexSequentialGradingService: câu hỏi {} ghi âm tối đa {}s nhưng rubric {} hiệu chuẩn cho {}s — điểm có thể lệch.",
                        question.getId(), question.getMaxRecordingSeconds(), t.id(), t.seconds()));
        return task;
    }

    /**
     * Luồng v2 (2026-09-21, đã xác nhận với người dùng) — điểm Bước 1 = trung bình các tiêu chí chấm ở bước
     * viết; điểm Ngữ pháp KHOÁ + số lỗi đỏ lưu lại cho bước nói. {@code writingFeedback} chứa nhận xét 2 câu
     * của AI cộng câu giải thích cổng chặn (backend soạn). Câu đã sửa (V141) sinh ở lệnh gọi RIÊNG và chỉ
     * khi đã nộp từ lần thứ 3 mà vẫn chưa đạt — đúng điều kiện FE mới hiện.
     */
    private void applyWritingResultV2(ReflexQuestionProgress progress, ReflexV2AiGradingService.WritingResult result,
                                      ReviewVideoQuestion question) {
        progress.setRubricVersion("v2");
        if (result == null) {
            progress.setWritingScore(null);
            progress.setWritingMaxScore(null);
            progress.setWritingFeedback(AI_GRADING_FAILED_FEEDBACK);
            progress.setWritingMarkedAnswer(null);
            progress.setWritingGradedAt(null);
            progress.setWritingCorrectedAnswer(null);
            progress.setWritingLockedGrammarPercent(null);
            progress.setWritingRedErrorCount(null);
            progress.setWritingAudit(null);
            return;
        }
        progress.setWritingScore(BigDecimal.valueOf(result.step1Percent()));
        progress.setWritingMaxScore(HUNDRED);
        StringBuilder feedback = new StringBuilder(result.feedback() == null ? "" : result.feedback());
        if (result.gateNote() != null && !result.gateNote().isBlank()) {
            if (feedback.length() > 0) {
                feedback.append(' ');
            }
            feedback.append(result.gateNote());
        }
        progress.setWritingFeedback(feedback.length() == 0 ? null : feedback.toString());
        progress.setWritingMarkedAnswer(result.markedText());
        progress.setWritingGradedAt(OffsetDateTime.now());
        progress.setWritingLockedGrammarPercent(BigDecimal.valueOf(result.grammarPercent()));
        progress.setWritingRedErrorCount(result.redCount());
        progress.setWritingAudit(result.audit());
        boolean passed = result.step1Percent() >= passThresholdPercent(progress);
        if (passed || progress.getWritingAttemptCount() < 3) {
            progress.setWritingCorrectedAnswer(null);
        } else {
            ReflexV2AiGradingService.CorrectedAnswer corrected =
                    reflexV2GradingService.generateCorrectedAnswer(question.getPrompt(), progress.getAnswerText());
            progress.setWritingCorrectedAnswer(corrected == null ? null : corrected.text());
            recordUsage(AiGradingTokenUsage.Step.CORRECTED_ANSWER, "chat",
                    corrected == null ? null : corrected.usage(), progress);
        }
    }

    /**
     * Luồng v2 — {@code speakingScore} là điểm MỞ KHOÁ câu tiếp theo (mặc định KHÔNG gồm Phát âm, xem
     * {@link ReflexV2AiGradingService}); điểm cuối gồm Phát âm nằm trong {@code speakingAudit}.
     */
    private void applySpeakingResultV2(ReflexQuestionProgress progress, ReflexV2AiGradingService.SpeakingResult result) {
        if (result == null) {
            progress.setSpeakingScore(null);
            progress.setSpeakingMaxScore(null);
            progress.setSpeakingFeedback(AI_GRADING_FAILED_FEEDBACK);
            progress.setSpeakingTranscript(null);
            progress.setSpeakingCriteriaScores(null);
            progress.setSpeakingGradedAt(null);
            progress.setSpeakingAudit(null);
            return;
        }
        progress.setSpeakingScore(BigDecimal.valueOf(result.unlockPercent()));
        progress.setSpeakingMaxScore(HUNDRED);
        progress.setSpeakingFeedback(result.feedback());
        progress.setSpeakingTranscript(result.markedTranscript());
        progress.setSpeakingCriteriaScores(result.criteria());
        progress.setSpeakingGradedAt(OffsetDateTime.now());
        progress.setSpeakingAudit(result.audit());
    }

    private void applySpeakingResult(ReflexQuestionProgress progress, ReflexSpeakingContentAiGradingService.GradeResult result) {
        if (result != null) {
            progress.setSpeakingScore(BigDecimal.valueOf(result.scorePercent()));
            progress.setSpeakingMaxScore(HUNDRED);
            progress.setSpeakingFeedback(result.feedback());
            progress.setSpeakingTranscript(result.transcript());
            progress.setSpeakingCriteriaScores(result.criteriaScores());
            progress.setSpeakingGradedAt(OffsetDateTime.now());
        } else {
            progress.setSpeakingScore(null);
            progress.setSpeakingMaxScore(null);
            progress.setSpeakingFeedback(AI_GRADING_FAILED_FEEDBACK);
            progress.setSpeakingTranscript(null);
            progress.setSpeakingCriteriaScores(null);
            progress.setSpeakingGradedAt(null);
        }
    }

    /**
     * V191 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21) — ghi 1 dòng snapshot CHỈ-THÊM
     * vào {@link ReflexQuestionProgressHistory} mỗi khi AI chấm phần viết xong, vì {@link ReflexQuestionProgress}
     * ghi đè tại chỗ nên không tự giữ lịch sử — phục vụ giáo viên xem/xuất lịch sử từng lần làm.
     */
    private void recordWritingHistory(ReflexQuestionProgress progress) {
        ReflexQuestionProgressHistory h = new ReflexQuestionProgressHistory();
        h.setReflexQuestionProgress(progress);
        h.setReviewVideoQuestion(progress.getReviewVideoQuestion());
        h.setStudent(progress.getStudent());
        h.setReviewVideoAssignment(progress.getReviewVideoAssignment());
        h.setAttemptType(ReflexQuestionProgressHistory.AttemptType.WRITING);
        h.setAttemptNumber(progress.getWritingAttemptCount());
        h.setAnswerText(progress.getAnswerText());
        h.setScore(progress.getWritingScore());
        h.setMaxScore(progress.getWritingMaxScore());
        h.setFeedback(progress.getWritingFeedback());
        h.setMarkedAnswer(progress.getWritingMarkedAnswer());
        h.setGradedAt(progress.getWritingGradedAt());
        reflexQuestionProgressHistoryRepository.save(h);
    }

    /** V191 — như {@link #recordWritingHistory}, cho bước ghi âm (có audioUrl/transcript/criteriaScores). */
    private void recordSpeakingHistory(ReflexQuestionProgress progress) {
        ReflexQuestionProgressHistory h = new ReflexQuestionProgressHistory();
        h.setReflexQuestionProgress(progress);
        h.setReviewVideoQuestion(progress.getReviewVideoQuestion());
        h.setStudent(progress.getStudent());
        h.setReviewVideoAssignment(progress.getReviewVideoAssignment());
        h.setAttemptType(ReflexQuestionProgressHistory.AttemptType.SPEAKING);
        h.setAttemptNumber(progress.getSpeakingAttemptCount());
        h.setAudioUrl(progress.getAudioUrl());
        h.setScore(progress.getSpeakingScore());
        h.setMaxScore(progress.getSpeakingMaxScore());
        h.setFeedback(progress.getSpeakingFeedback());
        h.setTranscript(progress.getSpeakingTranscript());
        h.setCriteriaScores(progress.getSpeakingCriteriaScores());
        h.setGradedAt(progress.getSpeakingGradedAt());
        reflexQuestionProgressHistoryRepository.save(h);
    }

    /**
     * V192 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — ghi chi phí token của 1 lượt
     * gọi AI kèm ngữ cảnh học sinh/bài/câu hỏi, cho trang Quản trị hệ thống → Sử dụng token AI. Đây là
     * tầng DUY NHẤT biết đủ cả 3 thứ: {@link NineRouterAiClient} cố tình không biết học sinh là ai, còn
     * {@link ReflexV2AiGradingService} không biết dòng tiến trình nào.
     *
     * {@link AiGradingTokenUsageRecorder} chạy giao dịch RIÊNG (REQUIRES_NEW) nên dòng chi phí vẫn còn
     * kể cả khi giao dịch chấm bị rollback vì {@code ReflexAudioRejectedException} — lượt phiên âm mù đã
     * tốn tiền thật trước khi bản ghi bị từ chối, xem Javadoc của recorder.
     */
    private void recordUsage(AiGradingTokenUsage.Step step, String operation,
                             vn.com.pps.education.common.AiTokenUsage usage, ReflexQuestionProgress progress) {
        tokenUsageRecorder.record(step, operation, null, usage, progress.getStudent(),
                progress.getReviewVideoAssignment(), progress.getReviewVideoQuestion());
    }

    private boolean isWritingPassed(ReflexQuestionProgress progress) {
        return progress.getWritingScore() != null && progress.getWritingScore().compareTo(BigDecimal.valueOf(passThresholdPercent(progress))) >= 0;
    }

    private boolean isSpeakingPassed(ReflexQuestionProgress progress) {
        return progress.getSpeakingScore() != null && progress.getSpeakingScore().compareTo(BigDecimal.valueOf(passThresholdPercent(progress))) >= 0;
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-08 — ngưỡng % đạt (viết VÀ nói) mỗi
     * câu, đọc từ cấu hình của chính video (trước đây hardcode 70 cố định, xem V168). Cấu hình được
     * theo từng video REFLEX qua {@link vn.com.pps.education.domain.ReviewVideo#getCompletionThresholdPercent()}
     * (field này với CONNECTION lại mang nghĩa khác — % pass điểm trắc nghiệm, xem ReviewVideoService).
     */
    private int passThresholdPercent(ReflexQuestionProgress progress) {
        return progress.getReviewVideoQuestion().getReviewVideo().getCompletionThresholdPercent();
    }

    private ReflexQuestionProgress findOrCreate(ReviewVideoQuestion question, Student student, ReviewVideoAssignment assignment) {
        return reflexQuestionProgressRepository
                .findByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentId(question.getId(), student.getId(), assignment.getId())
                .orElseGet(() -> {
                    ReflexQuestionProgress p = new ReflexQuestionProgress();
                    p.setReviewVideoQuestion(question);
                    p.setStudent(student);
                    p.setReviewVideoAssignment(assignment);
                    return p;
                });
    }

    private void requireReflexVideo(ReviewVideoQuestion question) {
        if (question.getReviewVideo().getReviewVideoSet().getVideoType() != ReviewVideoSet.VideoType.REFLEX) {
            throw new IllegalArgumentException("Video này không phải loại Video phản xạ (REFLEX) — không áp dụng luồng viết/nói tuần tự.");
        }
    }

    private ReflexQuestionProgressResponse toResponse(ReflexQuestionProgress p) {
        boolean writingPassed = isWritingPassed(p);
        boolean speakingPassed = isSpeakingPassed(p);
        return new ReflexQuestionProgressResponse(
                p.getReviewVideoQuestion().getId(),
                p.getAnswerText(),
                p.getWritingScore() == null ? null : p.getWritingScore().intValue(),
                p.getWritingFeedback(),
                p.getWritingMarkedAnswer(),
                writingPassed,
                p.getWritingAttemptCount(),
                p.getWritingCorrectedAnswer(),
                p.getAudioUrl(),
                p.getSpeakingScore() == null ? null : p.getSpeakingScore().intValue(),
                p.getSpeakingFeedback(),
                p.getSpeakingTranscript(),
                p.getSpeakingCriteriaScores(),
                speakingPassed,
                p.getSpeakingAttemptCount(),
                writingPassed && speakingPassed,
                p.getUpdatedAt());
    }

    // ---- Mirror ReviewVideoService (private ở đó, không expose được) — xem Javadoc lớp. ----

    private record StudentAccess(Student student, ReviewVideoAssignment assignment) {
    }

    private ReviewVideoQuestion getQuestionOrThrow(Long id) {
        return reviewVideoQuestionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideo.questionNotFound", new Object[]{id}, "Không tìm thấy câu hỏi id=" + id));
    }

    private StudentAccess resolveStudentAccessForAssignment(ReviewVideoSet set, Long assignmentId, Long actorUserId) {
        if (set.getStatus() != ReviewVideoSet.Status.PUBLISHED) {
            throw new ResourceNotFoundException("error.reviewVideo.setNotFound", new Object[]{set.getId()}, "Không tìm thấy bộ video id=" + set.getId());
        }
        Student student = studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideo.setNotFound", new Object[]{set.getId()}, "Không tìm thấy bộ video id=" + set.getId()));
        ReviewVideoAssignment assignment = reviewVideoAssignmentRepository.findById(assignmentId)
                .filter(a -> a.getReviewVideoSet().getId().equals(set.getId()))
                .filter(a -> a.getStatus() == ReviewVideoAssignment.Status.ACTIVE)
                .filter(a -> a.getTargetStudentIds() == null || a.getTargetStudentIds().contains(student.getId()))
                .filter(a -> classEnrollmentRepository
                        .findBySchoolClassIdAndStudentIdAndStatus(a.getSchoolClass().getId(), student.getId(), ClassEnrollment.Status.ACTIVE)
                        .isPresent())
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideo.setNotFound", new Object[]{set.getId()}, "Không tìm thấy bộ video id=" + set.getId()));
        return new StudentAccess(student, assignment);
    }

    /**
     * V165 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07) — đảo ngược quyết định
     * 2026-07-30: chỉ chặn cứng khi {@link ReviewVideoAssignment#isLateSubmissionAllowed()} tắt. Trả về
     * {@code true} nếu đã quá hạn nhưng vẫn được cho qua (nộp muộn) — caller dùng để đánh dấu
     * {@link ReflexQuestionProgress#setLateSubmission}, mirror {@code ExerciseAttemptService#submitAttempt}.
     */
    private boolean requireNotPastDeadline(ReviewVideoAssignment assignment) {
        if (assignment.getStatus() != ReviewVideoAssignment.Status.ACTIVE) {
            throw new SubmissionPastDeadlineException("Bản giao Video Ôn tập này đã bị thay thế hoặc hủy, không thể ghi nhận thêm.");
        }
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — "trễ hạn" (đánh dấu
        // lateSubmission, trả về true) vẫn tính từ dueAt GỐC; CHỈ điều kiện CHẶN HẲN mới xét thêm
        // lateSubmissionDeadline (isPastEffectiveDeadline), mirror ExerciseAttemptService#submitAttempt.
        boolean pastDue = assignment.getDueAt() != null && OffsetDateTime.now().isAfter(assignment.getDueAt());
        if (assignment.isPastEffectiveDeadline()) {
            throw new SubmissionPastDeadlineException(
                    "error.submissionPastDeadline.reviewVideo", new Object[]{assignment.getDueAt()},
                    "Bản giao Video Ôn tập này đã quá hạn nộp (" + assignment.getDueAt() + ").");
        }
        return pastDue;
    }
}
