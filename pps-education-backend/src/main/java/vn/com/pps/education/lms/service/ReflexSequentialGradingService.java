package vn.com.pps.education.lms.service;

import vn.com.pps.education.media.service.MediaStorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.Hibernate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.com.pps.education.common.ReflexQuestionFormat;
import vn.com.pps.education.common.ReflexV2Task;
import vn.com.pps.education.lms.domain.AiGradingTokenUsage;
import vn.com.pps.education.academic.domain.ClassEnrollment;
import vn.com.pps.education.academic.domain.Curriculum;
import vn.com.pps.education.lms.domain.ReflexGradingJob;
import vn.com.pps.education.lms.domain.ReflexGradingStatus;
import vn.com.pps.education.lms.domain.ReflexQuestionProgress;
import vn.com.pps.education.lms.domain.ReflexQuestionProgressHistory;
import vn.com.pps.education.lms.domain.ReviewVideoAssignment;
import vn.com.pps.education.lms.domain.ReviewVideoQuestion;
import vn.com.pps.education.lms.domain.ReviewVideoSet;
import vn.com.pps.education.student.domain.Student;
import vn.com.pps.education.lms.dto.PronunciationWordDetail;
import vn.com.pps.education.lms.dto.ReflexQuestionProgressResponse;
import vn.com.pps.education.exception.ReflexAudioRejectedException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RetakeNotAllowedException;
import vn.com.pps.education.exception.SubmissionPastDeadlineException;
import vn.com.pps.education.academic.repository.ClassEnrollmentRepository;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressHistoryRepository;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.lms.repository.ReviewVideoAssignmentRepository;
import vn.com.pps.education.lms.repository.ReviewVideoQuestionRepository;
import vn.com.pps.education.student.repository.StudentRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
 * hardcode cố định — xem {@link #passThresholdPercent}). Nộp lại chỉ SỬA ĐÈ dòng
 * {@link ReflexQuestionProgress} hiện có, không tạo bản ghi lịch sử mới.
 *
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07 — ĐẢO NGƯỢC quyết định cũ
 * ở trên ("KHÔNG giới hạn số lần thử lại"): nay giới hạn tối đa {@link #MAX_STEP_ATTEMPTS} lần nộp mỗi
 * BƯỚC (viết riêng, nói riêng) cho 1 câu hỏi/1 lần giao — xem {@link #isWritingUnlocked}/
 * {@link #isSpeakingFinalized}. Hết lượt mà CHƯA đạt ngưỡng %: bước VIẾT tự mở khoá ghi âm (dùng điểm/
 * nhận xét của lần nộp cuối làm kết quả cuối cùng của bước viết, không chặn học sinh lại); bước NÓI tự
 * đóng câu — coi là KHÔNG đạt, dùng điểm/nhận xét lần nộp cuối, tự mở khoá câu tiếp theo. Đạt trước khi
 * hết lượt thì mở khoá ngay như cũ, không cần dùng hết {@link #MAX_STEP_ATTEMPTS} lần. Mỗi lần giao lại
 * ({@link ReviewVideoAssignment} mới) thì bộ đếm reset về 0 (tiến trình scope theo assignment, xem
 * {@link #findOrCreate}).
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
    /** Chỉ dùng để đọc lại {@code pronunciationWords} từ {@code speakingAudit} (jsonb) — xem {@link #extractPronunciationWords}. */
    private static final ObjectMapper AUDIT_OBJECT_MAPPER = new ObjectMapper();

    /** (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — số lần nộp tối đa mỗi BƯỚC (viết/nói), xem Javadoc lớp. */
    static final int MAX_STEP_ATTEMPTS = 3;

    private static final String GRADING_IN_PROGRESS_MESSAGE = "Bài của con đang được chấm — vui lòng đợi kết quả rồi hãy nộp lại.";

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
    private final AiGradingFallbackService aiGradingFallbackService;
    private final AiGradingTokenUsageRecorder tokenUsageRecorder;
    private final ReflexQueueSettings queueSettings;
    private final GradingJobQueue jobQueue;
    private final TransactionTemplate tx;

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
                                           AiGradingFallbackService aiGradingFallbackService,
                                           AiGradingTokenUsageRecorder tokenUsageRecorder,
                                           ReflexQueueSettings queueSettings,
                                           GradingJobQueue jobQueue,
                                           PlatformTransactionManager transactionManager) {
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
        this.aiGradingFallbackService = aiGradingFallbackService;
        this.tokenUsageRecorder = tokenUsageRecorder;
        this.queueSettings = queueSettings;
        this.jobQueue = jobQueue;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Bước 1: nộp câu trả lời viết, AI chấm ngữ pháp ngay.
     *
     * Hotfix 2026-10-21 — KHÔNG còn bọc cả hàm trong 1 giao dịch: AI chấm mất 35–90 giây, giữ connection DB suốt
     * thời gian đó làm cạn pool Hikari (log "Apparent connection leak") và kéo theo /auth/refresh, đăng nhập của học
     * sinh khác trả 500 → bị văng. Chia 3 pha: (1) giao dịch ngắn kiểm tra + tăng lượt, (2) gọi AI KHÔNG giữ
     * connection, (3) giao dịch ngắn ghi kết quả. Lượt đã được tính từ pha 1 (như bước nói) nên nếu AI nổ ngoại lệ
     * bất ngờ thì lượt vẫn tính, không rollback.
     */
    public ReflexQuestionProgressResponse submitWrittenAnswer(Long questionId, Long assignmentId, String answerText, Long actorUserId) {
        if (queueSettings.isEnabled()) {
            return enqueueWrittenAnswer(questionId, assignmentId, answerText, actorUserId);
        }
        WritingPrep prep = tx.execute(status -> prepareWrittenAnswer(questionId, assignmentId, answerText, actorUserId));
        ReflexQuestionProgress progress = prep.progress();

        // ---- Pha 2: gọi AI, không giữ connection DB ----
        WritingAiOutput output = callWritingAi(prep);

        // ---- Pha 3: ghi kết quả ----
        return tx.execute(status -> {
            applyWritingOutput(progress, prep, output);
            ReflexQuestionProgress saved = saveProgressOrReject(progress);
            recordWritingHistory(saved);
            return toResponse(saved);
        });
    }

    /**
     * Lưu dòng tiến trình; 2 lần nộp gần như đồng thời cho CÙNG câu chưa có dòng tiến trình vi phạm unique
     * (review_video_question_id, student_id, assignment) → báo rõ thay vì 500 (hotfix 2026-10-21).
     */
    private ReflexQuestionProgress saveProgressOrReject(ReflexQuestionProgress progress) {
        try {
            return reflexQuestionProgressRepository.save(progress);
        } catch (DataIntegrityViolationException e) {
            // Hotfix 2026-10-21 — 2 lần nộp gần như đồng thời cho CÙNG câu chưa có dòng tiến trình (VD nộp lại khi
            // lượt đầu đang chấm 40-90 giây): lượt đến sau vi phạm unique (review_video_question_id, student_id,
            // assignment) và trước đây trả 500. Báo rõ để học sinh tải lại xem kết quả của lượt đầu.
            throw new RetakeNotAllowedException("Câu trả lời của con đang được chấm ở một lần nộp khác — vui lòng đợi rồi tải lại trang để xem kết quả.");
        }
    }

    /** Kết quả gọi AI chấm viết; {@code v2}/{@code corrected} khi dùng bộ v2, {@code v1} khi dùng luồng cũ. */
    private record WritingAiOutput(ReflexV2AiGradingService.WritingResult v2,
                                   ReflexV2AiGradingService.CorrectedAnswer corrected,
                                   ReflexWritingGrammarAiGradingService.GradeResult v1) {
        boolean failed(boolean v2Used) {
            return v2Used ? v2 == null : v1 == null;
        }
    }

    /**
     * ĐIỂM GỌI AI DUY NHẤT của bước viết (cả luồng đồng bộ lẫn hàng đợi) — đổi nhà cung cấp AI chỉ sửa quanh đây
     * và các service chấm được gọi ở đây. Không giữ transaction/connection DB.
     */
    private WritingAiOutput callWritingAi(WritingPrep prep) {
        ReflexQuestionProgress progress = prep.progress();
        ReviewVideoQuestion question = prep.question();
        String answerText = progress.getAnswerText();
        if (prep.v2Task().isPresent()) {
            ReflexV2Task task = prep.v2Task().get();
            // Bổ sung 2026-10-22 (đã xác nhận với người dùng) — qua AiGradingFallbackService: thử Claude
            // trực tiếp trước (chỉ khi app.ai-grading.claude-direct.enabled=true), lỗi thì tự rơi về
            // 9Router — KHÔNG đổi hành vi khi cờ tắt (mặc định).
            ReflexV2AiGradingService.WritingResult v2Result = aiGradingFallbackService.gradeReflexWritingWithFallback(task, gradingQuestionText(question, task), answerText);
            recordUsage(AiGradingTokenUsage.Step.WRITING, "chatJson", v2Result == null ? null : v2Result.usage(), progress);
            // Câu đã sửa (V141) sinh ở lệnh gọi AI RIÊNG, chỉ khi nộp từ lần thứ 3 mà vẫn chưa đạt.
            ReflexV2AiGradingService.CorrectedAnswer corrected = null;
            if (v2Result != null && v2Result.step1Percent() < passThresholdPercent(progress) && progress.getWritingAttemptCount() >= 3) {
                corrected = reflexV2GradingService.generateCorrectedAnswer(question.getPrompt(), answerText);
                recordUsage(AiGradingTokenUsage.Step.CORRECTED_ANSWER, "chat", corrected == null ? null : corrected.usage(), progress);
            }
            return new WritingAiOutput(v2Result, corrected, null);
        }
        ReflexWritingGrammarAiGradingService.GradeResult v1Result = writingGradingService.grade(answerText, question.getPrompt(), prep.curriculum());
        recordUsage(AiGradingTokenUsage.Step.WRITING, "chat", v1Result == null ? null : v1Result.usage(), progress);
        return new WritingAiOutput(null, null, v1Result);
    }

    private void applyWritingOutput(ReflexQuestionProgress progress, WritingPrep prep, WritingAiOutput output) {
        if (prep.v2Task().isPresent()) {
            applyWritingResultV2(progress, output.v2(), prep.v2Task().get(), output.corrected());
        } else {
            progress.setRubricVersion(null);
            applyWritingResult(progress, output.v1());
        }
    }

    /** Dữ liệu pha 1 → pha 2/3 của bước viết; mọi quan hệ lazy cần dùng đã được nạp sẵn (không còn session mở). */
    private record WritingPrep(ReflexQuestionProgress progress, ReviewVideoQuestion question, Curriculum curriculum,
                               Optional<ReflexV2Task> v2Task) {
    }

    private WritingPrep prepareWrittenAnswer(Long questionId, Long assignmentId, String answerText, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        StudentAccess access = resolveStudentAccessForAssignment(question.getReviewVideo().getReviewVideoSet(), assignmentId, actorUserId);
        boolean late = requireNotPastDeadline(access.assignment());
        requireReflexVideo(question);

        ReflexQuestionProgress progress = findOrCreate(question, access.student(), access.assignment());
        if (progress.getWritingGradingStatus() == ReflexGradingStatus.PENDING) {
            throw new RetakeNotAllowedException(GRADING_IN_PROGRESS_MESSAGE);
        }
        // FAILED (AI lỗi, đã hoàn lượt) KHÔNG chặn nộp lại — chỉ đạt thật/hết lượt/bỏ bước viết mới chặn.
        if (isWritingSkipped(progress) || isWritingPassed(progress) || isWritingExhausted(progress)) {
            throw new RetakeNotAllowedException("error.retakeNotAllowed.reflexWriting", new Object[]{MAX_STEP_ATTEMPTS},
                    "Bước viết của câu hỏi này đã hết lượt nộp (tối đa " + MAX_STEP_ATTEMPTS + ").");
        }
        // Vá phòng ngừa race condition (2026-10-08, đã xác nhận với người dùng, chưa có bằng chứng thật đã xảy
        // ra — chỉ là rủi ro lý thuyết phát hiện qua review code): check Java ngay trên dựa vào bản đọc CÓ THỂ
        // CŨ của progress, 2 request gần như đồng thời cho CÙNG 1 dòng có thể cùng qua được check rồi cùng tăng,
        // vượt MAX_STEP_ATTEMPTS. UPDATE có điều kiện dưới đây mới là cổng chặn THẬT (atomic ở DB) — dòng đã có
        // sẵn (progress.getId() != null) thì phải qua được UPDATE này mới coi là 1 lượt hợp lệ; dòng mới toanh
        // (chưa từng lưu) thì không có gì để tranh chấp, bỏ qua bước này. Xem Javadoc
        // ReflexQuestionProgressRepository#incrementWritingAttemptCountIfBelowLimit.
        if (progress.getId() != null
                && reflexQuestionProgressRepository.incrementWritingAttemptCountIfBelowLimit(progress.getId(), MAX_STEP_ATTEMPTS) == 0) {
            throw new RetakeNotAllowedException("error.retakeNotAllowed.reflexWriting", new Object[]{MAX_STEP_ATTEMPTS},
                    "Bước viết của câu hỏi này đã hết lượt nộp (tối đa " + MAX_STEP_ATTEMPTS + ").");
        }
        if (late) {
            progress.setLateSubmission(true);
        }
        if (progress.getWritingGradingStatus() == ReflexGradingStatus.FAILED) {
            progress.setWritingGradingStatus(ReflexGradingStatus.NONE); // lượt mới thay lượt AI lỗi
        }
        progress.setAnswerText(answerText);
        progress.setWritingAttemptCount(progress.getWritingAttemptCount() + 1);

        Curriculum curriculum = question.getReviewVideo().getReviewVideoSet().getCurriculum();
        Hibernate.initialize(curriculum); // pha 2 chạy ngoài session — graders đọc gradeLevel/track
        Optional<ReflexV2Task> v2Task = reflexV2Task(curriculum, question, reflexV2Enabled, ReflexV2Task.CURRENT_RUBRIC_VERSION);
        return new WritingPrep(progress, question, curriculum, v2Task);
    }

    /**
     * Bước 2 — CHỈ chấp nhận khi bước 1 đã đạt: nộp audio, AI transcribe + chấm nội dung ngay.
     *
     * Hotfix 2026-10-21 — chia 3 pha như {@link #submitWrittenAnswer}: (1) giao dịch ngắn kiểm tra + tăng lượt,
     * (2) tải audio + gọi AI KHÔNG giữ connection DB (chấm nói có thể mất cả phút), (3) giao dịch ngắn ghi kết quả.
     * {@code ReflexAudioRejectedException} ném ở pha 2 nên chưa ghi gì vào bản ghi tiến trình (như rollback trước
     * đây); lượt vẫn tính nhờ lệnh tăng REQUIRES_NEW ở pha 1.
     *
     * @param recordingFilter V199 — bản ghi đã qua bộ lọc thu âm của FE hay chưa (null = FE cũ); chỉ lưu lại để so
     *                        sánh hai chế độ, không ảnh hưởng cách chấm.
     */
    public ReflexQuestionProgressResponse submitSpokenAnswer(Long questionId, Long assignmentId, String audioUrl,
                                                             Boolean recordingFilter, Long actorUserId) {
        // Chống SSRF: audioUrl do học sinh gửi lên, server sẽ tự tải file này để chấm AI -> chỉ nhận URL
        // do chính hệ thống sinh ra lúc upload (xem MediaStorageService#requireStoredUrl).
        mediaStorageService.requireStoredUrl(audioUrl);
        if (queueSettings.isEnabled()) {
            return enqueueSpokenAnswer(questionId, assignmentId, audioUrl, recordingFilter, actorUserId);
        }
        SpeakingPrep prep = tx.execute(status -> prepareSpokenAnswer(questionId, assignmentId, audioUrl, actorUserId));
        ReflexQuestionProgress progress = prep.progress();

        // ReflexAudioRejectedException (bản ghi không đọc được / nói khác bài viết) ném thẳng ra → HTTP 422,
        // KHÔNG ghi điểm/feedback của lượt này. Lượt vẫn ĐƯỢC TÍNH vào giới hạn MAX_STEP_ATTEMPTS (xem
        // incrementSpeakingAttemptCountInNewTransactionIfBelowLimit, đổi từ 2026-10-07 — trước đó KHÔNG tính,
        // nhưng học sinh có thể né giới hạn bằng cách gửi bản ghi im lặng liên tục).
        // ---- Pha 2: tải audio + gọi AI, không giữ connection DB ----
        SpeakingAiOutput output = callSpeakingAi(prep, questionId, audioUrl);

        // ---- Pha 3: ghi kết quả ----
        return tx.execute(status -> {
            applySpeakingOutput(progress, output, recordingFilter);
            ReflexQuestionProgress saved = reflexQuestionProgressRepository.save(progress);
            recordSpeakingHistory(saved, recordingFilter);
            return toResponse(saved);
        });
    }

    /** Kết quả gọi AI chấm nói; {@code v2Used} cho biết dùng bộ v2 (định tuyến theo rubricVersion của dòng) hay luồng cũ. */
    private record SpeakingAiOutput(ReflexV2AiGradingService.SpeakingResult v2,
                                    ReflexSpeakingContentAiGradingService.GradeResult v1, boolean v2Used) {
        boolean failed() {
            return v2Used ? v2 == null : v1 == null;
        }
    }

    /**
     * ĐIỂM GỌI AI DUY NHẤT của bước nói (cả luồng đồng bộ lẫn hàng đợi) — xem {@link #callWritingAi}. Không giữ
     * transaction/connection DB. {@link ReflexAudioRejectedException} ném thẳng ra cho caller xử lý.
     */
    private SpeakingAiOutput callSpeakingAi(SpeakingPrep prep, Long questionId, String audioUrl) {
        ReflexQuestionProgress progress = prep.progress();
        ReviewVideoQuestion question = prep.question();
        Curriculum curriculum = prep.curriculum();

        // V147 (2026-08-25, xác nhận với người dùng) — fix bug thật: TRƯỚC ĐÂY hardcode "audio/webm" cho
        // MỌI audio bất kể định dạng thật do trình duyệt ghi ra (Chrome/Android thường "audio/webm",
        // Safari/iOS thường "audio/mp4") — lấy đúng content-type đã lưu ở R2 lúc upload thay vì đoán
        // (xem MediaStorageService#downloadWithContentType).
        MediaStorageService.DownloadedFile audioFile;
        try {
            audioFile = mediaStorageService.downloadWithContentType(audioUrl);
        } catch (Exception e) {
            // Trước đây nuốt lỗi không log → học sinh thấy "Không chấm được tự động" mà 9Router không nhận request nào,
            // không có dấu vết để chẩn đoán (R2 sai khóa/bucket, URL không khớp publicBaseUrl...).
            log.warn("ReflexSequentialGradingService: không tải được bản ghi để chấm (câu {}, url={}): {}", questionId, audioUrl, e.toString());
            audioFile = null;
        }
        // Bổ sung 2026-10-28, đã xác nhận với người dùng — câu PART2 không có bước Viết (xem isWritingSkipped)
        // nên progress.getRubricVersion() KHÔNG BAO GIỜ được gán (chỉ applyWritingResultV2 gán) — tự dùng
        // version hiện hành thay vì đọc từ dòng tiến trình, chỉ cho riêng trường hợp này.
        boolean writingSkipped = isWritingSkipped(progress);
        // Định tuyến theo CHÍNH rubricVersion của dòng (không theo cờ hiện tại): câu đã chấm viết bằng v2/v3 thì
        // bước nói cũng dùng đúng bộ đó (cần điểm Ngữ pháp Bước 1 + bài viết làm mốc so đọc lệch) dù cờ hay
        // version hiện hành có bị đổi giữa chừng — câu chấm viết bằng v2 trước khi lên v3 vẫn chấm nói bằng v2.
        Optional<ReflexV2Task> v2Task = reflexV2Task(curriculum, question, true,
                writingSkipped ? ReflexV2Task.CURRENT_RUBRIC_VERSION : progress.getRubricVersion());
        if (v2Task.isPresent() && (writingSkipped || progress.getWritingLockedGrammarPercent() != null)) {
            // Bổ sung 2026-10-28 — PART2 không có bài viết Bước 1 để làm sàn/đối chiếu (xem Javadoc
            // isWritingSkipped): Step1Anchor(0, null) là mốc "rỗng" — ReflexV2AiGradingService#gradeSpeaking
            // VÀ ReflexV2Scoring (writtenVsSpoken/sameAsWritten/regradeGrammar) đã tự xử lý null/0 đúng ý
            // nghĩa "không có Bước 1": bỏ qua cổng "nói khác bài viết", bỏ qua bằng chứng đọc lệch, LUÔN
            // chấm lại Ngữ pháp từ transcript (keepStep1Grammar luôn false vì sameAsWritten(null,...)=null),
            // không áp sàn (floor = nửa của 0 = 0, không bao giờ nâng điểm lên) — không cần sửa thêm gì ở đó.
            ReflexV2AiGradingService.Step1Anchor locked = writingSkipped
                    ? new ReflexV2AiGradingService.Step1Anchor(0, null)
                    : new ReflexV2AiGradingService.Step1Anchor(progress.getWritingLockedGrammarPercent().intValue(), progress.getAnswerText());
            if (writingSkipped) {
                progress.setRubricVersion(v2Task.get().rubricVersion());
            }
            // Chi phí từng lượt AI được ghi qua sink ngay khi AI trả về — kể cả khi sau đó bị từ chối / parse
            // lỗi (recorder chạy REQUIRES_NEW nên không bị rollback theo, cùng lý do với lượt nộp ở trên).
            // Bổ sung 2026-10-22 (đã xác nhận với người dùng) — qua AiGradingFallbackService: thử pipeline
            // trực tiếp Groq+Claude+Azure trước (chỉ khi app.ai-grading.claude-direct.enabled=true), lỗi
            // BẤT KỲ bước nào thì rơi về TOÀN BỘ luồng 9Router/Gemini gốc (fallback cả cụm, không trộn giữa
            // chừng — xem Javadoc AiGradingFallbackService). ReflexAudioRejectedException vẫn ném thẳng y
            // hệt trước (không đổi hành vi 422), KHÔNG đổi hành vi khi cờ tắt (mặc định).
            ReflexV2AiGradingService.SpeakingResult result = audioFile == null ? null
                    : aiGradingFallbackService.gradeReflexSpeakingWithFallback(v2Task.get(), gradingQuestionText(question, v2Task.get()), audioFile.bytes(), audioFile.contentType(), locked,
                            (step, usage) -> recordUsage(step, "chatWithAudioJson", usage, progress));
            return new SpeakingAiOutput(result, null, true);
        }
        ReflexSpeakingContentAiGradingService.GradeResult result =
                audioFile == null ? null : speakingGradingService.grade(audioFile.bytes(), audioFile.contentType(), question.getPrompt(), curriculum);
        recordUsage(AiGradingTokenUsage.Step.SPEAKING, "chatWithAudio", result == null ? null : result.usage(), progress);
        return new SpeakingAiOutput(null, result, false);
    }

    private void applySpeakingOutput(ReflexQuestionProgress progress, SpeakingAiOutput output, Boolean recordingFilter) {
        if (output.v2Used()) {
            applySpeakingResultV2(progress, output.v2());
            if (progress.getSpeakingAudit() != null && recordingFilter != null) {
                progress.getSpeakingAudit().put("recordingFilter", recordingFilter);
            }
        } else {
            applySpeakingResult(progress, output.v1());
        }
    }

    /** Dữ liệu pha 1 → pha 2/3 của bước nói; mọi quan hệ lazy cần dùng đã được nạp sẵn (không còn session mở). */
    private record SpeakingPrep(ReflexQuestionProgress progress, ReviewVideoQuestion question, Curriculum curriculum) {
    }

    private SpeakingPrep prepareSpokenAnswer(Long questionId, Long assignmentId, String audioUrl, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        StudentAccess access = resolveStudentAccessForAssignment(question.getReviewVideo().getReviewVideoSet(), assignmentId, actorUserId);
        boolean late = requireNotPastDeadline(access.assignment());
        requireReflexVideo(question);

        ReflexQuestionProgress progress = findOrCreate(question, access.student(), access.assignment());
        if (!isWritingUnlocked(progress)) {
            throw new IllegalArgumentException("Phải đạt phần viết trước khi ghi âm câu hỏi này.");
        }
        if (progress.getSpeakingGradingStatus() == ReflexGradingStatus.PENDING) {
            throw new RetakeNotAllowedException(GRADING_IN_PROGRESS_MESSAGE);
        }
        // FAILED (AI lỗi, đã hoàn lượt) KHÔNG chặn nộp lại — nếu không học sinh thấy "hết lượt" dù 0/3 lần.
        if (isSpeakingPassed(progress) || isSpeakingExhausted(progress)) {
            throw new RetakeNotAllowedException("error.retakeNotAllowed.reflexSpeaking", new Object[]{MAX_STEP_ATTEMPTS},
                    "Bước ghi âm của câu hỏi này đã hết lượt nộp (tối đa " + MAX_STEP_ATTEMPTS + ").");
        }
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07 — tăng ĐỘC LẬP (REQUIRES_NEW)
        // TRƯỚC khi gọi AI: nếu AI từ chối bản ghi (xem comment ReflexAudioRejectedException bên dưới)
        // làm rollback cả giao dịch này, lượt vẫn phải tính vào giới hạn MAX_STEP_ATTEMPTS — xem Javadoc
        // ReflexQuestionProgressRepository#incrementSpeakingAttemptCountInNewTransactionIfBelowLimit.
        //
        // Vá phòng ngừa race condition (2026-10-08, đã xác nhận với người dùng, chưa có bằng chứng thật
        // đã xảy ra — chỉ là rủi ro lý thuyết phát hiện qua review code): check isSpeakingFinalized ở
        // Java phía trên dựa vào bản đọc CÓ THỂ CŨ, 2 request gần như đồng thời cho CÙNG 1 dòng có thể
        // cùng qua được rồi cùng tăng, vượt MAX_STEP_ATTEMPTS. UPDATE có điều kiện này mới là cổng chặn
        // THẬT (atomic ở DB) — 0 dòng bị ảnh hưởng nghĩa là đã đủ giới hạn, chặn NGAY không gọi AI.
        int updated = reflexQuestionProgressRepository
                .incrementSpeakingAttemptCountInNewTransactionIfBelowLimit(progress.getId(), MAX_STEP_ATTEMPTS);
        if (updated == 0) {
            throw new RetakeNotAllowedException("error.retakeNotAllowed.reflexSpeaking", new Object[]{MAX_STEP_ATTEMPTS},
                    "Bước ghi âm của câu hỏi này đã hết lượt nộp (tối đa " + MAX_STEP_ATTEMPTS + ").");
        }
        if (late) {
            progress.setLateSubmission(true);
        }
        if (progress.getSpeakingGradingStatus() == ReflexGradingStatus.FAILED) {
            progress.setSpeakingGradingStatus(ReflexGradingStatus.NONE); // lượt mới thay lượt AI lỗi
        }
        progress.setAudioUrl(audioUrl);
        progress.setSpeakingAttemptCount(progress.getSpeakingAttemptCount() + 1);

        Curriculum curriculum = question.getReviewVideo().getReviewVideoSet().getCurriculum();
        Hibernate.initialize(curriculum); // pha 2 chạy ngoài session — graders đọc gradeLevel/track
        return new SpeakingPrep(progress, question, curriculum);
    }

    // ===================== Hàng đợi chấm AI (V222, app.reflex-queue.enabled) =====================

    /**
     * Bật hàng đợi: bước viết KHÔNG gọi AI trong request — chỉ ghi câu trả lời, đánh dấu PENDING và xếp job vào làn
     * WRITING (1 giao dịch ngắn, xong là trả về). Worker chấm sau; FE hỏi lại tiến trình đến khi hết PENDING.
     */
    private ReflexQuestionProgressResponse enqueueWrittenAnswer(Long questionId, Long assignmentId, String answerText, Long actorUserId) {
        return tx.execute(status -> {
            WritingPrep prep = prepareWrittenAnswer(questionId, assignmentId, answerText, actorUserId);
            ReflexQuestionProgress progress = prep.progress();
            progress.setWritingGradingStatus(ReflexGradingStatus.PENDING);
            ReflexQuestionProgress saved = saveProgressOrReject(progress);
            jobQueue.enqueue(saved.getId(), ReflexGradingJob.Lane.WRITING, saved.getWritingAttemptCount(), null);
            return toResponse(saved);
        });
    }

    /** Như {@link #enqueueWrittenAnswer}, cho bước nói (làn SPEAKING). */
    private ReflexQuestionProgressResponse enqueueSpokenAnswer(Long questionId, Long assignmentId, String audioUrl,
                                                               Boolean recordingFilter, Long actorUserId) {
        return tx.execute(status -> {
            SpeakingPrep prep = prepareSpokenAnswer(questionId, assignmentId, audioUrl, actorUserId);
            ReflexQuestionProgress progress = prep.progress();
            progress.setSpeakingGradingStatus(ReflexGradingStatus.PENDING);
            ReflexQuestionProgress saved = reflexQuestionProgressRepository.save(progress);
            jobQueue.enqueue(saved.getId(), ReflexGradingJob.Lane.SPEAKING, saved.getSpeakingAttemptCount(), recordingFilter);
            return toResponse(saved);
        });
    }

    /** Ngữ cảnh pha 1 của job: chỉ 1 trong {@code writing}/{@code speaking} khác null theo làn. */
    private record JobContext(Long jobId, Long progressId, ReflexGradingJob.Lane lane, int attemptNumber, int tryCount,
                              Boolean recordingFilter, WritingPrep writing, SpeakingPrep speaking) {
    }

    /**
     * Worker gọi cho 1 job đã nhận (RUNNING). Cùng mẫu 3 pha như luồng đồng bộ: (1) giao dịch ngắn đọc bài,
     * (2) gọi AI KHÔNG giữ connection, (3) giao dịch ngắn ghi điểm + lịch sử + đóng job.
     *
     * AI lỗi/null/ném ngoại lệ: thử lại tối đa {@code max-tries} lần (lượt nộp của học sinh KHÔNG bị tính trong lúc
     * thử lại); hết số lần → job FAILED, HOÀN lượt, bước đánh dấu FAILED để giáo viên chấm tay. Bản ghi bị từ chối
     * (im lặng/lạc đề) → REJECTED, không thử lại, vẫn tính lượt.
     */
    public void processQueuedJob(Long jobId) {
        JobContext ctx = tx.execute(status -> loadJobContext(jobId));
        if (ctx == null) {
            return;
        }
        if (ctx.lane() == ReflexGradingJob.Lane.WRITING) {
            WritingAiOutput output;
            String error = null;
            try {
                output = callWritingAi(ctx.writing());
            } catch (RuntimeException e) {
                log.warn("ReflexSequentialGradingService: job {} (viết) lỗi khi gọi AI: {}", jobId, e.toString());
                output = new WritingAiOutput(null, null, null);
                error = e.toString();
            }
            WritingAiOutput finalOutput = output;
            String finalError = error;
            tx.executeWithoutResult(status -> finishWritingJob(ctx, finalOutput, finalError));
        } else {
            SpeakingAiOutput output;
            String error = null;
            String rejected = null;
            try {
                output = callSpeakingAi(ctx.speaking(), ctx.speaking().question().getId(), ctx.speaking().progress().getAudioUrl());
            } catch (ReflexAudioRejectedException e) {
                output = null;
                rejected = e.getMessage();
            } catch (RuntimeException e) {
                log.warn("ReflexSequentialGradingService: job {} (nói) lỗi khi gọi AI: {}", jobId, e.toString());
                output = new SpeakingAiOutput(null, null, false);
                error = e.toString();
            }
            SpeakingAiOutput finalOutput = output;
            String finalError = error;
            String finalRejected = rejected;
            tx.executeWithoutResult(status -> finishSpeakingJob(ctx, finalOutput, finalError, finalRejected));
        }
    }

    /** Pha 1 của job. Trả {@code null} (và đóng job) nếu job không còn hợp lệ — bản ghi đã sang lượt nộp khác. */
    private JobContext loadJobContext(Long jobId) {
        ReflexGradingJob job = jobQueue.find(jobId).orElse(null);
        if (job == null || job.getStatus() != ReflexGradingJob.Status.RUNNING) {
            return null;
        }
        ReflexQuestionProgress progress = reflexQuestionProgressRepository.findById(job.getProgressId()).orElse(null);
        if (progress == null || isStale(progress, job.getLane(), job.getAttemptNumber())) {
            jobQueue.close(jobId, ReflexGradingJob.Status.DONE, "bỏ qua: bản ghi tiến trình đã sang lượt nộp khác");
            return null;
        }
        ReviewVideoQuestion question = progress.getReviewVideoQuestion();
        Hibernate.initialize(question);
        Hibernate.initialize(question.getReviewVideo()); // passThresholdPercent đọc ở pha 2
        Curriculum curriculum = question.getReviewVideo().getReviewVideoSet().getCurriculum();
        Hibernate.initialize(curriculum); // pha 2 chạy ngoài session — graders đọc gradeLevel/track
        if (job.getLane() == ReflexGradingJob.Lane.WRITING) {
            Optional<ReflexV2Task> v2Task = reflexV2Task(curriculum, question, reflexV2Enabled, ReflexV2Task.CURRENT_RUBRIC_VERSION);
            return new JobContext(jobId, progress.getId(), job.getLane(), job.getAttemptNumber(), job.getTryCount(), null,
                    new WritingPrep(progress, question, curriculum, v2Task), null);
        }
        return new JobContext(jobId, progress.getId(), job.getLane(), job.getAttemptNumber(), job.getTryCount(),
                job.getRecordingFilter(), null, new SpeakingPrep(progress, question, curriculum));
    }

    /** Bước còn PENDING và đúng lượt nộp mà job được xếp cho — nếu không, job là của lượt cũ. */
    private static boolean isStale(ReflexQuestionProgress progress, ReflexGradingJob.Lane lane, int attemptNumber) {
        return lane == ReflexGradingJob.Lane.WRITING
                ? progress.getWritingGradingStatus() != ReflexGradingStatus.PENDING || progress.getWritingAttemptCount() != attemptNumber
                : progress.getSpeakingGradingStatus() != ReflexGradingStatus.PENDING || progress.getSpeakingAttemptCount() != attemptNumber;
    }

    /** Pha 3 của job viết. Ghi lên bản ghi MỚI đọc trong giao dịch này (không merge bản detached của pha 1). */
    private void finishWritingJob(JobContext ctx, WritingAiOutput output, String error) {
        ReflexQuestionProgress fresh = reflexQuestionProgressRepository.findById(ctx.progressId()).orElse(null);
        if (fresh == null || isStale(fresh, ReflexGradingJob.Lane.WRITING, ctx.attemptNumber())) {
            jobQueue.close(ctx.jobId(), ReflexGradingJob.Status.DONE, "bỏ qua: bản ghi tiến trình đã sang lượt nộp khác");
            return;
        }
        boolean v2Used = ctx.writing().v2Task().isPresent();
        if (!output.failed(v2Used)) {
            applyWritingOutput(fresh, ctx.writing(), output);
            fresh.setWritingGradingStatus(ReflexGradingStatus.GRADED);
            recordWritingHistory(reflexQuestionProgressRepository.save(fresh));
            jobQueue.close(ctx.jobId(), ReflexGradingJob.Status.DONE, null);
        } else if (ctx.tryCount() < queueSettings.maxTries()) {
            jobQueue.scheduleRetry(ctx.jobId(), error != null ? error : "AI không trả kết quả chấm");
        } else {
            applyWritingOutput(fresh, ctx.writing(), output); // đặt điểm/nhận xét về trạng thái "không chấm được"
            fresh.setWritingGradingStatus(ReflexGradingStatus.FAILED);
            fresh.setWritingAttemptCount(Math.max(0, fresh.getWritingAttemptCount() - 1)); // hoàn lượt
            reflexQuestionProgressRepository.save(fresh);
            jobQueue.close(ctx.jobId(), ReflexGradingJob.Status.FAILED, error != null ? error : "AI không trả kết quả chấm");
            log.warn("ReflexSequentialGradingService: job {} (viết) hết {} lần thử — đã hoàn lượt, chờ giáo viên chấm tay (tiến trình {})",
                    ctx.jobId(), queueSettings.maxTries(), ctx.progressId());
        }
    }

    /** Pha 3 của job nói; {@code rejectedMessage} khác null = bản ghi bị từ chối (không thử lại, vẫn tính lượt). */
    private void finishSpeakingJob(JobContext ctx, SpeakingAiOutput output, String error, String rejectedMessage) {
        ReflexQuestionProgress fresh = reflexQuestionProgressRepository.findById(ctx.progressId()).orElse(null);
        if (fresh == null || isStale(fresh, ReflexGradingJob.Lane.SPEAKING, ctx.attemptNumber())) {
            jobQueue.close(ctx.jobId(), ReflexGradingJob.Status.DONE, "bỏ qua: bản ghi tiến trình đã sang lượt nộp khác");
            return;
        }
        if (rejectedMessage != null) {
            applySpeakingResult(fresh, null);
            fresh.setSpeakingFeedback(rejectedMessage);
            fresh.setSpeakingGradingStatus(ReflexGradingStatus.REJECTED);
            reflexQuestionProgressRepository.save(fresh);
            jobQueue.close(ctx.jobId(), ReflexGradingJob.Status.REJECTED, rejectedMessage);
        } else if (!output.failed()) {
            applySpeakingOutput(fresh, output, ctx.recordingFilter());
            fresh.setSpeakingGradingStatus(ReflexGradingStatus.GRADED);
            recordSpeakingHistory(reflexQuestionProgressRepository.save(fresh), ctx.recordingFilter());
            jobQueue.close(ctx.jobId(), ReflexGradingJob.Status.DONE, null);
        } else if (ctx.tryCount() < queueSettings.maxTries()) {
            jobQueue.scheduleRetry(ctx.jobId(), error != null ? error : "AI không trả kết quả chấm");
        } else {
            applySpeakingOutput(fresh, output, ctx.recordingFilter()); // đặt điểm/nhận xét về trạng thái "không chấm được"
            fresh.setSpeakingGradingStatus(ReflexGradingStatus.FAILED);
            fresh.setSpeakingAttemptCount(Math.max(0, fresh.getSpeakingAttemptCount() - 1)); // hoàn lượt
            reflexQuestionProgressRepository.save(fresh);
            jobQueue.close(ctx.jobId(), ReflexGradingJob.Status.FAILED, error != null ? error : "AI không trả kết quả chấm");
            log.warn("ReflexSequentialGradingService: job {} (nói) hết {} lần thử — đã hoàn lượt, chờ giáo viên chấm tay (tiến trình {})",
                    ctx.jobId(), queueSettings.maxTries(), ctx.progressId());
        }
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

    /**
     * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — proxy tải lại CHÍNH audio đã ghi âm của học
     * sinh qua backend, phục vụ vẽ dạng sóng âm (Web Audio API cần đọc được byte audio, nhưng bucket R2
     * public-base-url KHÔNG bật CORS cho fetch() — chỉ ổn cho {@code <audio src>} phát trực tiếp, không
     * đọc được byte qua JS). Audio phát qua thẻ {@code <audio>} vẫn trỏ thẳng audioUrl như cũ, KHÔNG đổi
     * — chỉ FE gọi thêm endpoint này riêng cho việc vẽ waveform. Dùng lại đúng {@link #resolveStudentAccessForAssignment}
     * để đảm bảo chỉ xem được audio của CHÍNH học sinh đang đăng nhập, không phải audio bất kỳ.
     */
    @Transactional(readOnly = true)
    public MediaStorageService.DownloadedFile downloadSpeakingAudio(Long questionId, Long assignmentId, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        StudentAccess access = resolveStudentAccessForAssignment(question.getReviewVideo().getReviewVideoSet(), assignmentId, actorUserId);
        ReflexQuestionProgress progress = reflexQuestionProgressRepository
                .findByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentId(questionId, access.student().getId(), assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideo.questionNotFound", new Object[]{questionId}, "Không tìm thấy tiến trình câu hỏi id=" + questionId));
        if (progress.getAudioUrl() == null) {
            throw new ResourceNotFoundException("error.reviewVideo.questionNotFound", new Object[]{questionId}, "Câu hỏi id=" + questionId + " chưa có bản ghi âm.");
        }
        return mediaStorageService.downloadWithContentType(progress.getAudioUrl());
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
        progress.clearWritingReview();
        progress.setWritingCriteriaScores(null);
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

    /** Câu lệnh mặc định của dạng tả tranh khi giáo viên để trống đề (mirror kho đề PET Task 2 của mã tham chiếu). */
    static final String DEFAULT_PICTURE_PROMPT = "Look at the photo. Describe what you can see.";

    /**
     * V200 — đề gửi vào lượt chấm VIẾT và chấm NÓI. Dạng tả tranh: kèm mô tả tranh giáo viên đã duyệt, CHỈ để xét
     * đúng/lạc đề (rubric §C1 dạng PICTURE) — mirror {@code bank.gradingText} của mã tham chiếu. Lượt phiên âm không
     * nhận đề nên mô tả không bao giờ lọt vào đó; học sinh cũng không thấy mô tả (xem ReviewVideoService#listQuestions).
     */
    static String gradingQuestionText(ReviewVideoQuestion question, ReflexV2Task task) {
        boolean picture = "PICTURE".equals(task.rubricFormat());
        String prompt = question.getPrompt() == null || question.getPrompt().isBlank()
                ? (picture ? DEFAULT_PICTURE_PROMPT : "") : question.getPrompt().trim();
        String brief = question.getPictureBrief();
        if (!picture || brief == null || brief.isBlank()) {
            return prompt;
        }
        StringBuilder sb = new StringBuilder(prompt)
                .append("\n\nMÔ TẢ ẢNH (giáo viên nhập — chỉ dùng để xét đúng/lạc đề, học sinh không thấy):\n");
        for (String line : brief.split("\\R")) {
            String l = line.strip().replaceFirst("^[-•*]\\s*", "");
            if (!l.isEmpty()) {
                sb.append("- ").append(l).append("\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    /** @param rubricVersion version rubric ({@code null}/không hỗ trợ → luồng cũ). */
    private Optional<ReflexV2Task> reflexV2Task(Curriculum curriculum, ReviewVideoQuestion question, boolean enabled,
                                                String rubricVersion) {
        if (!enabled || curriculum == null) {
            return Optional.empty();
        }
        // V200 — dạng đề giáo viên chọn (nếu có) quyết định dạng bài; câu hỏi cũ chưa chọn thì suy theo thời lượng.
        Optional<ReflexV2Task> task = ReflexV2Task.forQuestion(curriculum.getGradeLevel(), curriculum.getTrack(),
                question.getMaxRecordingSeconds(), question.getQuestionFormat(), rubricVersion);
        // Ngưỡng của rubric được hiệu chuẩn theo đúng thời lượng ghi âm của dạng bài (20/25/30/60/90 giây) —
        // giáo viên đặt lệch thì ngưỡng đếm từ/ý/chỗ ngắt sai; chỉ cảnh báo, không chặn.
        task.filter(t -> t.seconds() != question.getMaxRecordingSeconds()).ifPresent(t ->
                log.warn("ReflexSequentialGradingService: câu hỏi {} ghi âm tối đa {}s nhưng rubric {} hiệu chuẩn cho {}s — điểm có thể lệch.",
                        question.getId(), question.getMaxRecordingSeconds(), t.id(), t.seconds()));
        return task;
    }

    /**
     * Luồng v2 (2026-09-21, đã xác nhận với người dùng) — điểm Bước 1 = trung bình các tiêu chí chấm ở bước
     * viết; điểm Ngữ pháp Bước 1 + số lỗi đỏ ngữ pháp lưu lại cho bước nói (giữ nguyên khi nói giống bài viết,
     * nói khác thì làm sàn điểm chấm lại — xem {@link ReflexV2AiGradingService}). {@code rubricVersion} ghi
     * version của dạng bài vừa chấm để bước nói dùng đúng bộ đó. {@code writingFeedback} chứa nhận xét 2 câu
     * của AI cộng câu giải thích cổng chặn (backend soạn). Câu đã sửa (V141) sinh ở lệnh gọi RIÊNG và chỉ
     * khi đã nộp từ lần thứ 3 mà vẫn chưa đạt — đúng điều kiện FE mới hiện.
     */
    private void applyWritingResultV2(ReflexQuestionProgress progress, ReflexV2AiGradingService.WritingResult result,
                                      ReflexV2Task task, ReflexV2AiGradingService.CorrectedAnswer corrected) {
        progress.clearWritingReview();
        progress.setRubricVersion(task.rubricVersion());
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
            progress.setWritingCriteriaScores(null);
            progress.setWritingHint(null);
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
        progress.setWritingHint(result.hint() == null || result.hint().isBlank() ? null : result.hint());
        progress.setWritingGradedAt(OffsetDateTime.now());
        progress.setWritingLockedGrammarPercent(BigDecimal.valueOf(result.grammarPercent()));
        progress.setWritingRedErrorCount(result.redCount());
        progress.setWritingAudit(result.audit());
        progress.setWritingCriteriaScores(result.criteria());
        boolean passed = result.step1Percent() >= passThresholdPercent(progress);
        if (passed || progress.getWritingAttemptCount() < 3) {
            progress.setWritingCorrectedAnswer(null);
        } else {
            // corrected do pha 2 của submitWrittenAnswer sinh sẵn (lệnh AI không được chạy trong giao dịch ghi).
            progress.setWritingCorrectedAnswer(corrected == null ? null : corrected.text());
        }
    }

    /**
     * Luồng v2 — {@code speakingScore} là điểm MỞ KHOÁ câu tiếp theo (mặc định KHÔNG gồm Phát âm, xem
     * {@link ReflexV2AiGradingService}); điểm cuối gồm Phát âm nằm trong {@code speakingAudit}.
     */
    private void applySpeakingResultV2(ReflexQuestionProgress progress, ReflexV2AiGradingService.SpeakingResult result) {
        progress.clearSpeakingReview();
        if (result == null) {
            progress.setSpeakingScore(null);
            progress.setSpeakingMaxScore(null);
            progress.setSpeakingFeedback(AI_GRADING_FAILED_FEEDBACK);
            progress.setSpeakingTranscript(null);
            progress.setSpeakingCriteriaScores(null);
            progress.setSpeakingGradedAt(null);
            progress.setSpeakingAudit(null);
            progress.setSpeakingHint(null);
            return;
        }
        progress.setSpeakingScore(BigDecimal.valueOf(result.unlockPercent()));
        progress.setSpeakingMaxScore(HUNDRED);
        progress.setSpeakingFeedback(result.feedback());
        progress.setSpeakingTranscript(result.markedTranscript());
        progress.setSpeakingCriteriaScores(result.criteria());
        progress.setSpeakingGradedAt(OffsetDateTime.now());
        progress.setSpeakingAudit(result.audit());
        progress.setSpeakingHint(result.hint() == null || result.hint().isBlank() ? null : result.hint());
    }

    private void applySpeakingResult(ReflexQuestionProgress progress, ReflexSpeakingContentAiGradingService.GradeResult result) {
        progress.clearSpeakingReview();
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
        clearTeacherSelection(progress, ReflexQuestionProgressHistory.AttemptType.WRITING);
        h.setAttemptNumber(progress.getWritingAttemptCount());
        h.setAnswerText(progress.getAnswerText());
        h.setScore(progress.getWritingScore());
        h.setMaxScore(progress.getWritingMaxScore());
        h.setFeedback(progress.getWritingFeedback());
        h.setMarkedAnswer(progress.getWritingMarkedAnswer());
        h.setHint(progress.getWritingHint());
        h.setCriteriaScores(progress.getWritingCriteriaScores());
        h.setGradedAt(progress.getWritingGradedAt());
        reflexQuestionProgressHistoryRepository.save(h);
    }

    /**
     * V217 — học sinh nộp lại bước: lần mới trở thành kết quả chính thức nên bỏ cờ "giáo viên chọn" của lần cũ (điểm
     * giáo viên đã chốt ở lần cũ vẫn giữ làm nhãn). Bước này của {@link ReflexQuestionProgress} đã bị AI chấm mới ghi đè.
     */
    private void clearTeacherSelection(ReflexQuestionProgress progress, ReflexQuestionProgressHistory.AttemptType type) {
        if (progress.getId() == null) {
            return;
        }
        reflexQuestionProgressHistoryRepository
                .findByReflexQuestionProgressIdAndAttemptTypeOrderByAttemptNumberAsc(progress.getId(), type).stream()
                .filter(ReflexQuestionProgressHistory::isTeacherSelected)
                .forEach(old -> {
                    old.setTeacherSelected(false);
                    reflexQuestionProgressHistoryRepository.save(old);
                });
    }

    /** V191 — như {@link #recordWritingHistory}, cho bước ghi âm (có audioUrl/transcript/criteriaScores). */
    private void recordSpeakingHistory(ReflexQuestionProgress progress, Boolean recordingFilter) {
        ReflexQuestionProgressHistory h = new ReflexQuestionProgressHistory();
        h.setReflexQuestionProgress(progress);
        h.setReviewVideoQuestion(progress.getReviewVideoQuestion());
        h.setStudent(progress.getStudent());
        h.setReviewVideoAssignment(progress.getReviewVideoAssignment());
        h.setAttemptType(ReflexQuestionProgressHistory.AttemptType.SPEAKING);
        clearTeacherSelection(progress, ReflexQuestionProgressHistory.AttemptType.SPEAKING);
        h.setAttemptNumber(progress.getSpeakingAttemptCount());
        h.setAudioUrl(progress.getAudioUrl());
        h.setScore(progress.getSpeakingScore());
        h.setMaxScore(progress.getSpeakingMaxScore());
        h.setFeedback(progress.getSpeakingFeedback());
        h.setTranscript(progress.getSpeakingTranscript());
        h.setCriteriaScores(progress.getSpeakingCriteriaScores());
        h.setHint(progress.getSpeakingHint());
        h.setGradedAt(progress.getSpeakingGradedAt());
        h.setRecordingFilter(recordingFilter);
        // V198 — cờ "cần giáo viên soát Ngữ pháp" lấy từ speaking_audit của CHÍNH lần chấm này (luồng cũ: không có).
        Map<String, Object> audit = progress.getSpeakingAudit();
        if (audit != null && Boolean.TRUE.equals(audit.get(ReflexV2AiGradingService.AUDIT_GRAMMAR_REVIEW_REQUIRED))) {
            h.setGrammarReviewRequired(true);
            Object quotes = audit.get(ReflexV2AiGradingService.AUDIT_GRAMMAR_REVIEW_QUOTES);
            h.setGrammarReviewQuotes(quotes instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of());
        }
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

    /** V222 — bước đang chờ hàng đợi chấm: chưa có kết quả nào hợp lệ (điểm cũ của lượt trước không được tính). */
    private static boolean isWritingPending(ReflexQuestionProgress progress) {
        return progress.getWritingGradingStatus() == ReflexGradingStatus.PENDING;
    }

    private static boolean isSpeakingPending(ReflexQuestionProgress progress) {
        return progress.getSpeakingGradingStatus() == ReflexGradingStatus.PENDING;
    }

    private boolean isWritingPassed(ReflexQuestionProgress progress) {
        return !isWritingPending(progress) && progress.getWritingScore() != null && progress.getWritingScore().compareTo(BigDecimal.valueOf(passThresholdPercent(progress))) >= 0;
    }

    private boolean isSpeakingPassed(ReflexQuestionProgress progress) {
        return !isSpeakingPending(progress) && progress.getSpeakingScore() != null && progress.getSpeakingScore().compareTo(BigDecimal.valueOf(passThresholdPercent(progress))) >= 0;
    }

    /** (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — đã dùng hết {@link #MAX_STEP_ATTEMPTS} lần nộp bước viết mà vẫn chưa đạt. */
    private boolean isWritingExhausted(ReflexQuestionProgress progress) {
        return !isWritingPending(progress) && !isWritingPassed(progress) && progress.getWritingAttemptCount() >= MAX_STEP_ATTEMPTS;
    }

    /**
     * (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — bước viết đã "xong" (đạt thật HOẶC hết lượt mà vẫn chưa đạt) — dùng để
     * mở khoá bước ghi âm VÀ để chặn nộp lại bước viết (2 vai trò gộp 1 vì cùng điều kiện, xem Javadoc
     * lớp).
     */
    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-28 — câu hỏi dạng PART2 (IELTS Speaking
     * Part 2, Khối 8-9) BỎ HẲN bước Viết: vào thẳng ghi âm khi video chạm mốc, giống thi IELTS Part 2
     * thật (không viết trước, không có bài viết Bước 1 làm mốc). Điểm Ngữ pháp của bước Nói chấm thẳng
     * từ transcript — không có "Cách B" (giữ nguyên/làm sàn theo bài viết, xem {@link #callSpeakingAi})
     * vì không có gì để đối chiếu.
     *
     * Mở rộng cùng ngày (lần 2, đã xác nhận với người dùng) — PET4 (Khối 8, PET Speaking Part 4) và
     * PICTURE (Khối 7-8, PET Speaking Task 2 tả tranh) cũng BỎ bước Viết, cùng lý do: thi PET Speaking
     * thật không viết trước. Dùng chung {@code Step1Anchor(0, null)} ở {@link #callSpeakingAi} — GV chấm
     * thẳng từ transcript theo bảng checkpoint G1-G5/D1-D5/P1-P5 của rubric khối tương ứng.
     */
    private boolean isWritingSkipped(ReflexQuestionProgress progress) {
        ReflexQuestionFormat format = progress.getReviewVideoQuestion().getQuestionFormat();
        return format == ReflexQuestionFormat.PART2 || format == ReflexQuestionFormat.PET4
                || format == ReflexQuestionFormat.PICTURE;
    }

    private boolean isWritingUnlocked(ReflexQuestionProgress progress) {
        // V222 — AI lỗi hết số lần thử (FAILED, đã hoàn lượt): không chặn học sinh, mở khoá bước nói, giáo viên chấm tay sau.
        return isWritingSkipped(progress)
                || progress.getWritingGradingStatus() == ReflexGradingStatus.FAILED || isWritingPassed(progress) || isWritingExhausted(progress);
    }

    /** (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — đã dùng hết {@link #MAX_STEP_ATTEMPTS} lần nộp bước nói mà vẫn chưa đạt. */
    private boolean isSpeakingExhausted(ReflexQuestionProgress progress) {
        return !isSpeakingPending(progress) && !isSpeakingPassed(progress) && progress.getSpeakingAttemptCount() >= MAX_STEP_ATTEMPTS;
    }

    /** (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — bước nói đã "xong" (đạt thật HOẶC hết lượt) — chặn nộp lại bước nói. */
    private boolean isSpeakingFinalized(ReflexQuestionProgress progress) {
        return progress.getSpeakingGradingStatus() == ReflexGradingStatus.FAILED || isSpeakingPassed(progress) || isSpeakingExhausted(progress);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-08 — ngưỡng % đạt (viết VÀ nói) mỗi
     * câu, đọc từ cấu hình của chính video (trước đây hardcode 70 cố định, xem V168). Cấu hình được
     * theo từng video REFLEX qua {@link vn.com.pps.education.lms.domain.ReviewVideo#getCompletionThresholdPercent()}
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
        boolean writingExhausted = isWritingExhausted(p);
        boolean speakingExhausted = isSpeakingExhausted(p);
        // Đổi 2026-10-28 (người dùng quyết định, phản hồi học sinh Huyền): câu ĐẠT khi bước NÓI đạt ngưỡng. Bước viết vẫn
        // là cổng mở khoá bước nói (đạt thật hoặc hết lượt) và điểm Ngữ pháp bước viết vẫn làm mốc/sàn khi chấm nói,
        // nhưng viết chưa đạt (hết lượt) không còn kéo câu xuống "Chưa đạt" khi nói đã đạt. Trước đó: writingPassed && speakingPassed.
        // Bổ sung cùng ngày (câu PART2 — xem isWritingSkipped) — rule này đã TỰ ĐỘNG đúng luôn cho câu
        // PART2 (không có bước Viết, writingPassed luôn false) vì questionPassed giờ không còn phụ thuộc
        // writingPassed nữa, không cần ngoại lệ riêng như bản trước merge.
        boolean questionPassed = speakingPassed;
        // Fix bug thật (2026-10-08, phát hiện qua test thật trên staging, đã xác nhận với người dùng) —
        // công thức cũ "questionPassed || speakingExhausted" bỏ sót trường hợp bước VIẾT hết lượt (chưa
        // đạt thật) NHƯNG bước NÓI sau đó đạt THẬT (không hết lượt): questionPassed luôn false (viết
        // chưa đạt thật) và speakingExhausted cũng false (nói đã đạt, không phải hết lượt) → kẹt học
        // sinh ở câu đó vĩnh viễn, đúng điều fix 2026-10-07 muốn tránh. submitSpokenAnswer đã chặn không
        // cho nói trước khi writingUnlocked, nên speakingPassed=true LUÔN kéo theo writing đã unlock (đạt
        // thật hoặc hết lượt) — không cần xét lại writingPassed ở đây, mirror đúng isSpeakingFinalized.
        boolean questionFinalized = isSpeakingFinalized(p);
        // V222 — bước đang chờ hàng đợi: kết quả còn trong dòng tiến trình là của LẦN NỘP TRƯỚC, không trả ra (FE sẽ hiện
        // điểm/transcript cũ như thể là kết quả của bài vừa nộp). Lịch sử các lần vẫn nằm ở reflex_question_progress_history.
        boolean wPending = isWritingPending(p);
        boolean sPending = isSpeakingPending(p);
        return new ReflexQuestionProgressResponse(
                p.getReviewVideoQuestion().getId(),
                p.getAnswerText(),
                wPending || p.getWritingScore() == null ? null : p.getWritingScore().intValue(),
                wPending ? null : p.getWritingFeedback(),
                wPending ? null : p.getWritingMarkedAnswer(),
                wPending ? null : p.getWritingHint(),
                writingPassed,
                p.getWritingAttemptCount(),
                wPending ? null : p.getWritingCorrectedAnswer(),
                p.getAudioUrl(),
                sPending || p.getSpeakingScore() == null ? null : p.getSpeakingScore().intValue(),
                sPending ? null : p.getSpeakingFeedback(),
                sPending ? null : p.getSpeakingTranscript(),
                sPending ? null : p.getSpeakingHint(),
                sPending ? null : p.getSpeakingCriteriaScores(),
                sPending ? null : extractPronunciationWords(p.getSpeakingAudit()),
                speakingPassed,
                p.getSpeakingAttemptCount(),
                questionPassed,
                writingExhausted,
                MAX_STEP_ATTEMPTS,
                isWritingUnlocked(p),
                speakingExhausted,
                MAX_STEP_ATTEMPTS,
                questionFinalized,
                p.getUpdatedAt(),
                p.getWritingGradingStatus().name(),
                p.getSpeakingGradingStatus().name());
    }

    /**
     * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — đọc lại {@code pronunciationWords} (Azure, từng từ/
     * âm vị) đã ghi vào {@code speaking_audit} ở {@link AiGradingFallbackService#gradeSpeakingDirectPipeline}.
     * Hibernate đọc jsonb thành {@code List<LinkedHashMap>} (không giữ kiểu record gốc) nên phải convert lại
     * bằng Jackson — null an toàn khi chưa chấm/chấm bằng luồng cũ (không có key này)/parse lỗi.
     */
    private List<PronunciationWordDetail> extractPronunciationWords(Map<String, Object> speakingAudit) {
        if (speakingAudit == null) {
            return null;
        }
        Object raw = speakingAudit.get("pronunciationWords");
        if (raw == null) {
            return null;
        }
        try {
            return AUDIT_OBJECT_MAPPER.convertValue(raw,
                    AUDIT_OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, PronunciationWordDetail.class));
        } catch (Exception e) {
            log.warn("ReflexSequentialGradingService: parse pronunciationWords từ speakingAudit thất bại. {}", e.getMessage());
            return null;
        }
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
