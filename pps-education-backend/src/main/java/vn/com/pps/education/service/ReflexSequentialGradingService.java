package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ReflexQuestionProgress;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.ReviewVideoQuestion;
import vn.com.pps.education.domain.ReviewVideoSet;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.dto.ReflexQuestionProgressResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.SubmissionPastDeadlineException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.repository.ReviewVideoAssignmentRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionRepository;
import vn.com.pps.education.repository.StudentRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * UC-23b V2 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22) — "Video phản xạ" đổi
 * hướng: thay luồng cũ "xem 1 video liên tục, ghi âm mỗi câu theo mốc thời gian, nộp cả loạt cuối
 * video, GV chấm tay" (vẫn còn nguyên trong {@link ReviewVideoService}/{@link ReviewVideoGradingPanel}
 * — KHÔNG xoá, chỉ không còn dùng cho video REFLEX theo luồng mới) bằng luồng TUẦN TỰ theo từng câu
 * hỏi: viết trước → {@link ReflexWritingGrammarAiGradingService} chấm ngữ pháp → đạt 70% mới mở khoá
 * ghi âm → {@link ReflexSpeakingContentAiGradingService} chấm nội dung (transcribe + chấm, Gemini) →
 * đạt 70% mới mở khoá câu tiếp theo. KHÔNG giới hạn số lần thử lại (đã xác nhận với người dùng) — nộp
 * lại chỉ SỬA ĐÈ dòng {@link ReflexQuestionProgress} hiện có, không tạo bản ghi lịch sử mới.
 *
 * Tách THÀNH SERVICE RIÊNG (không thêm vào {@link ReviewVideoService}, dù cùng UC-23b) vì
 * {@link ReviewVideoService} đã rất lớn (nhiều nhóm nghiệp vụ: CONNECTION/REFLEX cũ/thống kê/lịch sử)
 * — mirror lại (KHÔNG gọi lại) {@code resolveStudentAccessForAssignment}/{@code requireNotPastDeadline}
 * /{@code getQuestionOrThrow} của ReviewVideoService (private, không expose được) để không phải sửa
 * sâu vào 1 file rất lớn đã có nhiều lịch sử quyết định phức tạp — xem .claude/rules/solid.md.
 */
@Service
public class ReflexSequentialGradingService {

    /** Ngưỡng đạt 70% (đã xác nhận với người dùng 2026-08-22) — CỐ ĐỊNH, ReviewVideoSet chưa có field
     * pass_threshold_percent riêng như Exercise (ngoài phạm vi thay đổi lần này). */
    private static final int PASS_THRESHOLD_PERCENT = 70;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String AI_GRADING_FAILED_FEEDBACK = "Không chấm được tự động — vui lòng thử nộp lại.";

    private final ReviewVideoQuestionRepository reviewVideoQuestionRepository;
    private final ReviewVideoAssignmentRepository reviewVideoAssignmentRepository;
    private final ReflexQuestionProgressRepository reflexQuestionProgressRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final MediaStorageService mediaStorageService;
    private final ReflexWritingGrammarAiGradingService writingGradingService;
    private final ReflexSpeakingContentAiGradingService speakingGradingService;

    public ReflexSequentialGradingService(ReviewVideoQuestionRepository reviewVideoQuestionRepository,
                                           ReviewVideoAssignmentRepository reviewVideoAssignmentRepository,
                                           ReflexQuestionProgressRepository reflexQuestionProgressRepository,
                                           ClassEnrollmentRepository classEnrollmentRepository,
                                           StudentRepository studentRepository,
                                           MediaStorageService mediaStorageService,
                                           ReflexWritingGrammarAiGradingService writingGradingService,
                                           ReflexSpeakingContentAiGradingService speakingGradingService) {
        this.reviewVideoQuestionRepository = reviewVideoQuestionRepository;
        this.reviewVideoAssignmentRepository = reviewVideoAssignmentRepository;
        this.reflexQuestionProgressRepository = reflexQuestionProgressRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.mediaStorageService = mediaStorageService;
        this.writingGradingService = writingGradingService;
        this.speakingGradingService = speakingGradingService;
    }

    /** Bước 1: nộp câu trả lời viết, AI chấm ngữ pháp ngay. */
    @Transactional
    public ReflexQuestionProgressResponse submitWrittenAnswer(Long questionId, Long assignmentId, String answerText, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        StudentAccess access = resolveStudentAccessForAssignment(question.getReviewVideo().getReviewVideoSet(), assignmentId, actorUserId);
        requireNotPastDeadline(access.assignment());
        requireReflexVideo(question);

        ReflexQuestionProgress progress = findOrCreate(question, access.student(), access.assignment());
        progress.setAnswerText(answerText);
        progress.setWritingAttemptCount(progress.getWritingAttemptCount() + 1);

        ReflexWritingGrammarAiGradingService.GradeResult result =
                writingGradingService.grade(answerText, question.getReviewVideo().getReviewVideoSet().getCurriculum());
        applyWritingResult(progress, result);
        progress = reflexQuestionProgressRepository.save(progress);
        return toResponse(progress);
    }

    /** Bước 2 — CHỈ chấp nhận khi bước 1 đã đạt: nộp audio, AI transcribe + chấm nội dung ngay. */
    @Transactional
    public ReflexQuestionProgressResponse submitSpokenAnswer(Long questionId, Long assignmentId, String audioUrl, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        StudentAccess access = resolveStudentAccessForAssignment(question.getReviewVideo().getReviewVideoSet(), assignmentId, actorUserId);
        requireNotPastDeadline(access.assignment());
        requireReflexVideo(question);

        ReflexQuestionProgress progress = findOrCreate(question, access.student(), access.assignment());
        if (!isWritingPassed(progress)) {
            throw new IllegalArgumentException("Phải đạt phần viết trước khi ghi âm câu hỏi này.");
        }
        progress.setAudioUrl(audioUrl);
        progress.setSpeakingAttemptCount(progress.getSpeakingAttemptCount() + 1);

        byte[] audioBytes;
        try {
            audioBytes = mediaStorageService.download(audioUrl);
        } catch (Exception e) {
            audioBytes = null;
        }
        ReflexSpeakingContentAiGradingService.GradeResult result =
                audioBytes == null ? null : speakingGradingService.grade(audioBytes, "audio/webm", question.getReviewVideo().getReviewVideoSet().getCurriculum());
        applySpeakingResult(progress, result);
        progress = reflexQuestionProgressRepository.save(progress);
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

    private void applyWritingResult(ReflexQuestionProgress progress, ReflexWritingGrammarAiGradingService.GradeResult result) {
        if (result != null) {
            progress.setWritingScore(BigDecimal.valueOf(result.scorePercent()));
            progress.setWritingMaxScore(HUNDRED);
            progress.setWritingFeedback(result.feedback());
            progress.setWritingGradedAt(OffsetDateTime.now());
        } else {
            progress.setWritingScore(null);
            progress.setWritingMaxScore(null);
            progress.setWritingFeedback(AI_GRADING_FAILED_FEEDBACK);
            progress.setWritingGradedAt(null);
        }
    }

    private void applySpeakingResult(ReflexQuestionProgress progress, ReflexSpeakingContentAiGradingService.GradeResult result) {
        if (result != null) {
            progress.setSpeakingScore(BigDecimal.valueOf(result.scorePercent()));
            progress.setSpeakingMaxScore(HUNDRED);
            progress.setSpeakingFeedback(result.feedback());
            progress.setSpeakingGradedAt(OffsetDateTime.now());
        } else {
            progress.setSpeakingScore(null);
            progress.setSpeakingMaxScore(null);
            progress.setSpeakingFeedback(AI_GRADING_FAILED_FEEDBACK);
            progress.setSpeakingGradedAt(null);
        }
    }

    private boolean isWritingPassed(ReflexQuestionProgress progress) {
        return progress.getWritingScore() != null && progress.getWritingScore().compareTo(BigDecimal.valueOf(PASS_THRESHOLD_PERCENT)) >= 0;
    }

    private boolean isSpeakingPassed(ReflexQuestionProgress progress) {
        return progress.getSpeakingScore() != null && progress.getSpeakingScore().compareTo(BigDecimal.valueOf(PASS_THRESHOLD_PERCENT)) >= 0;
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
                writingPassed,
                p.getWritingAttemptCount(),
                p.getAudioUrl(),
                p.getSpeakingScore() == null ? null : p.getSpeakingScore().intValue(),
                p.getSpeakingFeedback(),
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

    private void requireNotPastDeadline(ReviewVideoAssignment assignment) {
        if (assignment.getStatus() != ReviewVideoAssignment.Status.ACTIVE) {
            throw new SubmissionPastDeadlineException("Bản giao Video Ôn tập này đã bị thay thế hoặc hủy, không thể ghi nhận thêm.");
        }
        if (assignment.getDueAt() != null && OffsetDateTime.now().isAfter(assignment.getDueAt())) {
            throw new SubmissionPastDeadlineException(
                    "error.submissionPastDeadline.reviewVideo", new Object[]{assignment.getDueAt()},
                    "Bản giao Video Ôn tập này đã quá hạn nộp (" + assignment.getDueAt() + ").");
        }
    }
}
