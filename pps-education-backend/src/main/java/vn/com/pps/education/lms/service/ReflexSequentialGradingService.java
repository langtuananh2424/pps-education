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
import vn.com.pps.education.common.ReflexV2Task;
import vn.com.pps.education.lms.domain.AiGradingTokenUsage;
import vn.com.pps.education.academic.domain.ClassEnrollment;
import vn.com.pps.education.academic.domain.Curriculum;
import vn.com.pps.education.lms.domain.ReflexQuestionProgress;
import vn.com.pps.education.lms.domain.ReflexQuestionProgressHistory;
import vn.com.pps.education.lms.domain.ReviewVideoAssignment;
import vn.com.pps.education.lms.domain.ReviewVideoQuestion;
import vn.com.pps.education.lms.domain.ReviewVideoSet;
import vn.com.pps.education.student.domain.Student;
import vn.com.pps.education.lms.dto.ReflexQuestionProgressResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RetakeNotAllowedException;
import vn.com.pps.education.exception.SubmissionPastDeadlineException;
import vn.com.pps.education.academic.repository.ClassEnrollmentRepository;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressHistoryRepository;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.lms.repository.ReviewVideoAssignmentRepository;
import vn.com.pps.education.lms.repository.ReviewVideoQuestionRepository;
import vn.com.pps.education.student.repository.StudentRepository;

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

    /** (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — số lần nộp tối đa mỗi BƯỚC (viết/nói), xem Javadoc lớp. */
    static final int MAX_STEP_ATTEMPTS = 3;

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
                                           AiGradingTokenUsageRecorder tokenUsageRecorder,
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
        this.tokenUsageRecorder = tokenUsageRecorder;
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
        WritingPrep prep = tx.execute(status -> prepareWrittenAnswer(questionId, assignmentId, answerText, actorUserId));
        ReflexQuestionProgress progress = prep.progress();
        ReviewVideoQuestion question = prep.question();
        Curriculum curriculum = prep.curriculum();

        // ---- Pha 2: gọi AI, không giữ connection DB ----
        ReflexV2AiGradingService.WritingResult v2Result = null;
        ReflexV2AiGradingService.CorrectedAnswer corrected = null;
        ReflexWritingGrammarAiGradingService.GradeResult v1Result = null;
        if (prep.v2Task().isPresent()) {
            ReflexV2Task task = prep.v2Task().get();
            v2Result = reflexV2GradingService.gradeWriting(task, gradingQuestionText(question, task), answerText);
            recordUsage(AiGradingTokenUsage.Step.WRITING, "chatJson", v2Result == null ? null : v2Result.usage(), progress);
            // Câu đã sửa (V141) sinh ở lệnh gọi AI RIÊNG, chỉ khi nộp từ lần thứ 3 mà vẫn chưa đạt.
            if (v2Result != null && v2Result.step1Percent() < passThresholdPercent(progress) && progress.getWritingAttemptCount() >= 3) {
                corrected = reflexV2GradingService.generateCorrectedAnswer(question.getPrompt(), answerText);
                recordUsage(AiGradingTokenUsage.Step.CORRECTED_ANSWER, "chat", corrected == null ? null : corrected.usage(), progress);
            }
        } else {
            v1Result = writingGradingService.grade(answerText, question.getPrompt(), curriculum);
            recordUsage(AiGradingTokenUsage.Step.WRITING, "chat", v1Result == null ? null : v1Result.usage(), progress);
        }

        // ---- Pha 3: ghi kết quả ----
        ReflexV2AiGradingService.WritingResult finalV2 = v2Result;
        ReflexV2AiGradingService.CorrectedAnswer finalCorrected = corrected;
        ReflexWritingGrammarAiGradingService.GradeResult finalV1 = v1Result;
        return tx.execute(status -> {
            if (prep.v2Task().isPresent()) {
                applyWritingResultV2(progress, finalV2, prep.v2Task().get(), finalCorrected);
            } else {
                progress.setRubricVersion(null);
                applyWritingResult(progress, finalV1);
            }
            ReflexQuestionProgress saved;
            try {
                saved = reflexQuestionProgressRepository.save(progress);
            } catch (DataIntegrityViolationException e) {
                // Hotfix 2026-10-21 — 2 lần nộp gần như đồng thời cho CÙNG câu chưa có dòng tiến trình (VD nộp lại khi
                // lượt đầu đang chấm 40-90 giây): lượt đến sau vi phạm unique (review_video_question_id, student_id,
                // assignment) và trước đây trả 500. Báo rõ để học sinh tải lại xem kết quả của lượt đầu.
                throw new RetakeNotAllowedException("Câu trả lời của con đang được chấm ở một lần nộp khác — vui lòng đợi rồi tải lại trang để xem kết quả.");
            }
            recordWritingHistory(saved);
            return toResponse(saved);
        });
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
        if (isWritingUnlocked(progress)) {
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
        SpeakingPrep prep = tx.execute(status -> prepareSpokenAnswer(questionId, assignmentId, audioUrl, actorUserId));
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
        // Định tuyến theo CHÍNH rubricVersion của dòng (không theo cờ hiện tại): câu đã chấm viết bằng v2/v3 thì
        // bước nói cũng dùng đúng bộ đó (cần điểm Ngữ pháp Bước 1 + bài viết làm mốc so đọc lệch) dù cờ hay
        // version hiện hành có bị đổi giữa chừng — câu chấm viết bằng v2 trước khi lên v3 vẫn chấm nói bằng v2.
        ReflexV2AiGradingService.SpeakingResult v2Speaking = null;
        ReflexSpeakingContentAiGradingService.GradeResult v1Speaking = null;
        Optional<ReflexV2Task> v2Task = reflexV2Task(curriculum, question, true, progress.getRubricVersion());
        if (v2Task.isPresent() && progress.getWritingLockedGrammarPercent() != null) {
            ReflexV2AiGradingService.Step1Anchor locked = new ReflexV2AiGradingService.Step1Anchor(
                    progress.getWritingLockedGrammarPercent().intValue(), progress.getAnswerText());
            // ReflexAudioRejectedException (bản ghi không đọc được / nói khác bài viết) ném thẳng ra → HTTP 422,
            // giao dịch rollback nên KHÔNG ghi điểm/feedback của lượt này. Lượt vẫn ĐƯỢC TÍNH vào giới hạn
            // MAX_STEP_ATTEMPTS (xem incrementSpeakingAttemptCountInNewTransactionIfBelowLimit ở trên, đổi từ 2026-10-07 —
            // trước đó KHÔNG tính, nhưng học sinh có thể né giới hạn bằng cách gửi bản ghi im lặng liên tục).
            // Chi phí từng lượt AI được ghi qua sink ngay khi AI trả về — kể cả khi sau đó bị từ chối / parse
            // lỗi (recorder chạy REQUIRES_NEW nên không bị rollback theo, cùng lý do với lượt nộp ở trên).
            ReflexQuestionProgress usageContext = progress;
            ReflexV2AiGradingService.SpeakingResult result = audioFile == null ? null
                    : reflexV2GradingService.gradeSpeaking(v2Task.get(), gradingQuestionText(question, v2Task.get()), audioFile.bytes(), audioFile.contentType(), locked,
                            (step, usage) -> recordUsage(step, "chatWithAudioJson", usage, usageContext));
            v2Speaking = result;
        } else {
            ReflexSpeakingContentAiGradingService.GradeResult result =
                    audioFile == null ? null : speakingGradingService.grade(audioFile.bytes(), audioFile.contentType(), question.getPrompt(), curriculum);
            recordUsage(AiGradingTokenUsage.Step.SPEAKING, "chatWithAudio", result == null ? null : result.usage(), progress);
            v1Speaking = result;
        }
        // ---- Pha 3: ghi kết quả ----
        ReflexV2AiGradingService.SpeakingResult finalV2 = v2Speaking;
        ReflexSpeakingContentAiGradingService.GradeResult finalV1 = v1Speaking;
        boolean v2Used = v2Task.isPresent() && progress.getWritingLockedGrammarPercent() != null;
        return tx.execute(status -> {
            if (v2Used) {
                applySpeakingResultV2(progress, finalV2);
                if (progress.getSpeakingAudit() != null && recordingFilter != null) {
                    progress.getSpeakingAudit().put("recordingFilter", recordingFilter);
                }
            } else {
                applySpeakingResult(progress, finalV1);
            }
            ReflexQuestionProgress saved = reflexQuestionProgressRepository.save(progress);
            recordSpeakingHistory(saved, recordingFilter);
            return toResponse(saved);
        });
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
        if (isSpeakingFinalized(progress)) {
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
        progress.setAudioUrl(audioUrl);
        progress.setSpeakingAttemptCount(progress.getSpeakingAttemptCount() + 1);

        Curriculum curriculum = question.getReviewVideo().getReviewVideoSet().getCurriculum();
        Hibernate.initialize(curriculum); // pha 2 chạy ngoài session — graders đọc gradeLevel/track
        return new SpeakingPrep(progress, question, curriculum);
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

    private boolean isWritingPassed(ReflexQuestionProgress progress) {
        return progress.getWritingScore() != null && progress.getWritingScore().compareTo(BigDecimal.valueOf(passThresholdPercent(progress))) >= 0;
    }

    private boolean isSpeakingPassed(ReflexQuestionProgress progress) {
        return progress.getSpeakingScore() != null && progress.getSpeakingScore().compareTo(BigDecimal.valueOf(passThresholdPercent(progress))) >= 0;
    }

    /** (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — đã dùng hết {@link #MAX_STEP_ATTEMPTS} lần nộp bước viết mà vẫn chưa đạt. */
    private boolean isWritingExhausted(ReflexQuestionProgress progress) {
        return !isWritingPassed(progress) && progress.getWritingAttemptCount() >= MAX_STEP_ATTEMPTS;
    }

    /**
     * (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — bước viết đã "xong" (đạt thật HOẶC hết lượt mà vẫn chưa đạt) — dùng để
     * mở khoá bước ghi âm VÀ để chặn nộp lại bước viết (2 vai trò gộp 1 vì cùng điều kiện, xem Javadoc
     * lớp).
     */
    private boolean isWritingUnlocked(ReflexQuestionProgress progress) {
        return isWritingPassed(progress) || isWritingExhausted(progress);
    }

    /** (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — đã dùng hết {@link #MAX_STEP_ATTEMPTS} lần nộp bước nói mà vẫn chưa đạt. */
    private boolean isSpeakingExhausted(ReflexQuestionProgress progress) {
        return !isSpeakingPassed(progress) && progress.getSpeakingAttemptCount() >= MAX_STEP_ATTEMPTS;
    }

    /** (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07) — bước nói đã "xong" (đạt thật HOẶC hết lượt) — chặn nộp lại bước nói. */
    private boolean isSpeakingFinalized(ReflexQuestionProgress progress) {
        return isSpeakingPassed(progress) || isSpeakingExhausted(progress);
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
        boolean questionPassed = writingPassed && speakingPassed;
        // Fix bug thật (2026-10-08, phát hiện qua test thật trên staging, đã xác nhận với người dùng) —
        // công thức cũ "questionPassed || speakingExhausted" bỏ sót trường hợp bước VIẾT hết lượt (chưa
        // đạt thật) NHƯNG bước NÓI sau đó đạt THẬT (không hết lượt): questionPassed luôn false (viết
        // chưa đạt thật) và speakingExhausted cũng false (nói đã đạt, không phải hết lượt) → kẹt học
        // sinh ở câu đó vĩnh viễn, đúng điều fix 2026-10-07 muốn tránh. submitSpokenAnswer đã chặn không
        // cho nói trước khi writingUnlocked, nên speakingPassed=true LUÔN kéo theo writing đã unlock (đạt
        // thật hoặc hết lượt) — không cần xét lại writingPassed ở đây, mirror đúng isSpeakingFinalized.
        boolean questionFinalized = speakingPassed || speakingExhausted;
        return new ReflexQuestionProgressResponse(
                p.getReviewVideoQuestion().getId(),
                p.getAnswerText(),
                p.getWritingScore() == null ? null : p.getWritingScore().intValue(),
                p.getWritingFeedback(),
                p.getWritingMarkedAnswer(),
                p.getWritingHint(),
                writingPassed,
                p.getWritingAttemptCount(),
                p.getWritingCorrectedAnswer(),
                p.getAudioUrl(),
                p.getSpeakingScore() == null ? null : p.getSpeakingScore().intValue(),
                p.getSpeakingFeedback(),
                p.getSpeakingTranscript(),
                p.getSpeakingHint(),
                p.getSpeakingCriteriaScores(),
                speakingPassed,
                p.getSpeakingAttemptCount(),
                questionPassed,
                writingExhausted,
                MAX_STEP_ATTEMPTS,
                writingPassed || writingExhausted,
                speakingExhausted,
                MAX_STEP_ATTEMPTS,
                questionFinalized,
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
