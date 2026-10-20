package vn.com.pps.education.lms.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.academic.domain.ClassTeacher;
import vn.com.pps.education.academic.domain.SchoolClass;
import vn.com.pps.education.academic.repository.ClassTeacherRepository;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.auth.repository.UserRepository;
import vn.com.pps.education.exception.AnswerNotManuallyGradableException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.lms.domain.ReflexQuestionProgress;
import vn.com.pps.education.lms.domain.StudentAnswer;
import vn.com.pps.education.lms.domain.StudentAnswerGrading;
import vn.com.pps.education.lms.dto.AiGradedClassSummaryResponse;
import vn.com.pps.education.lms.dto.AiGradedItemResponse;
import vn.com.pps.education.lms.dto.ReviewReflexStepRequest;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.lms.repository.StudentAnswerGradingRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Chấm lần 2 các bài do AI chấm (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-19; liên quan
 * UC-23b Video phản xạ và UC-40/41 bài tập Writing). Học sinh phản ánh điểm AI đôi khi chưa đúng nên giáo
 * viên được xem lại mọi bài AI đã chấm trong "Hàng chờ chấm bài" và chấm đè — điểm giáo viên là điểm chính
 * thức, điểm gốc của AI vẫn được giữ để đối chiếu:
 * <ul>
 *   <li>Video phản xạ (Viết/Nói): {@link #reviewReflexStep} ghi điểm GV vào writing_score/speaking_score của
 *       {@link ReflexQuestionProgress} (ngưỡng đạt, báo cáo, tiến độ BTVN tự dùng điểm mới), điểm AI gốc ở
 *       cột *_ai_* (V216).</li>
 *   <li>Bài tập Writing (ESSAY): dùng lại {@link ManualGradingService#gradeAnswer} — bản chấm AI chuyển
 *       latest=false (còn nguyên làm lịch sử), bản của GV thành bản hiện hành.</li>
 * </ul>
 * Luyện Nói (listening practice) không có AI chấm nên không thuộc phạm vi này.
 *
 * Phạm vi giáo viên: lớp mình đang đứng tên thật (class_teachers, assignedTo IS NULL) — mirror
 * ReviewVideoService#getPendingGradingSummaryForTeacher. Authorization quyền ở controller
 * (lms.grading.manage).
 */
@Service
public class AiSecondGradingService {

    static final String TYPE_REFLEX_WRITING = "REFLEX_WRITING";
    static final String TYPE_REFLEX_SPEAKING = "REFLEX_SPEAKING";
    static final String TYPE_ESSAY = "ESSAY";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Bước của Video phản xạ mà giáo viên chấm lại. */
    public enum ReflexStep { WRITING, SPEAKING }

    private final ClassTeacherRepository classTeacherRepository;
    private final ReflexQuestionProgressRepository reflexQuestionProgressRepository;
    private final StudentAnswerGradingRepository studentAnswerGradingRepository;
    private final UserRepository userRepository;

    public AiSecondGradingService(ClassTeacherRepository classTeacherRepository,
                                  ReflexQuestionProgressRepository reflexQuestionProgressRepository,
                                  StudentAnswerGradingRepository studentAnswerGradingRepository,
                                  UserRepository userRepository) {
        this.classTeacherRepository = classTeacherRepository;
        this.reflexQuestionProgressRepository = reflexQuestionProgressRepository;
        this.studentAnswerGradingRepository = studentAnswerGradingRepository;
        this.userRepository = userRepository;
    }

    /** Landing: các lớp giáo viên đang đứng lớp thật có bài AI đã chấm, kèm số bài chưa chấm lại. */
    @Transactional(readOnly = true)
    public List<AiGradedClassSummaryResponse> listClasses(Long actorUserId) {
        List<SchoolClass> myClasses = classTeacherRepository.findByTeacherIdAndAssignedToIsNull(actorUserId).stream()
                .map(ClassTeacher::getSchoolClass)
                .distinct()
                .toList();
        List<AiGradedClassSummaryResponse> result = new ArrayList<>();
        for (SchoolClass schoolClass : myClasses) {
            List<AiGradedItemResponse> items = collectItems(schoolClass.getId());
            if (items.isEmpty()) {
                continue;
            }
            int awaiting = (int) items.stream().filter(i -> !i.reviewed()).count();
            result.add(new AiGradedClassSummaryResponse(
                    schoolClass.getId(), schoolClass.getClassCode(), schoolClass.getName(), awaiting, items.size()));
        }
        result.sort(Comparator.comparing(AiGradedClassSummaryResponse::className, Comparator.nullsLast(String::compareTo)));
        return result;
    }

    /** Các bài AI đã chấm của 1 lớp — mặc định chỉ bài chưa chấm lại, {@code includeReviewed} để xem cả bài đã chấm lại. */
    @Transactional(readOnly = true)
    public List<AiGradedItemResponse> listItems(Long classId, boolean includeReviewed, Long actorUserId) {
        requireAssignedTeacher(classId, actorUserId);
        return collectItems(classId).stream().filter(i -> includeReviewed || !i.reviewed()).toList();
    }

    /**
     * Chấm lần 2 một bước (Viết/Nói) của Video phản xạ. Chấm lại lần nữa thì chỉ đổi điểm GV, điểm AI gốc
     * (đã lưu ở lần đầu) được giữ nguyên.
     */
    @Transactional
    public AiGradedItemResponse reviewReflexStep(Long progressId, ReflexStep step, ReviewReflexStepRequest request, Long actorUserId) {
        ReflexQuestionProgress progress = reflexQuestionProgressRepository.findById(progressId)
                .orElseThrow(() -> new ResourceNotFoundException("error.aiSecondGrading.progressNotFound",
                        new Object[]{progressId}, "Không tìm thấy tiến trình bài id=" + progressId));
        requireAssignedTeacher(progress.getReviewVideoAssignment().getSchoolClass().getId(), actorUserId);
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.manualGrading.userNotFound",
                        new Object[]{actorUserId}, "Không tìm thấy tài khoản id=" + actorUserId));

        String feedback = request.feedback() == null || request.feedback().isBlank() ? null : request.feedback().trim();
        OffsetDateTime now = OffsetDateTime.now();
        if (step == ReflexStep.WRITING) {
            if (progress.getWritingScore() == null || progress.getWritingGradedAt() == null) {
                throw notAiGraded();
            }
            if (progress.getWritingReviewedAt() == null) {
                progress.setWritingAiScore(progress.getWritingScore());
                progress.setWritingAiFeedback(progress.getWritingFeedback());
            }
            progress.setWritingScore(request.score());
            progress.setWritingMaxScore(HUNDRED);
            progress.setWritingFeedback(feedback);
            // Câu sửa gợi ý do AI sinh theo điểm AI cũ — điểm GV đổi thì không còn khớp, bỏ để khỏi hiện sai.
            progress.setWritingCorrectedAnswer(null);
            progress.setWritingReviewedBy(actor);
            progress.setWritingReviewedAt(now);
        } else {
            if (progress.getSpeakingScore() == null || progress.getSpeakingGradedAt() == null) {
                throw notAiGraded();
            }
            if (progress.getSpeakingReviewedAt() == null) {
                progress.setSpeakingAiScore(progress.getSpeakingScore());
                progress.setSpeakingAiFeedback(progress.getSpeakingFeedback());
            }
            progress.setSpeakingScore(request.score());
            progress.setSpeakingMaxScore(HUNDRED);
            progress.setSpeakingFeedback(feedback);
            progress.setSpeakingReviewedBy(actor);
            progress.setSpeakingReviewedAt(now);
        }
        progress = reflexQuestionProgressRepository.save(progress);
        return reflexItem(progress, step);
    }

    // ===================== Helpers =====================

    private AnswerNotManuallyGradableException notAiGraded() {
        return new AnswerNotManuallyGradableException("error.aiSecondGrading.notAiGraded", new Object[]{},
                "Bước này chưa được AI chấm — không thể chấm lần 2.");
    }

    private void requireAssignedTeacher(Long classId, Long actorUserId) {
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "error.notAssignedTeacherForClass.default", new Object[]{}, "Bạn không được phân công giảng dạy lớp này.");
        }
    }

    private List<AiGradedItemResponse> collectItems(Long classId) {
        List<AiGradedItemResponse> items = new ArrayList<>();
        for (ReflexQuestionProgress p : reflexQuestionProgressRepository.findAiGradedByClassId(classId)) {
            if (p.getWritingScore() != null && p.getWritingGradedAt() != null) {
                items.add(reflexItem(p, ReflexStep.WRITING));
            }
            if (p.getSpeakingScore() != null && p.getSpeakingGradedAt() != null) {
                items.add(reflexItem(p, ReflexStep.SPEAKING));
            }
        }
        items.addAll(essayItems(classId));
        // Chưa chấm lại lên trước, trong mỗi nhóm bài AI chấm mới nhất lên trước.
        items.sort(Comparator.comparing(AiGradedItemResponse::reviewed)
                .thenComparing(AiGradedItemResponse::aiGradedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    private AiGradedItemResponse reflexItem(ReflexQuestionProgress p, ReflexStep step) {
        boolean writing = step == ReflexStep.WRITING;
        OffsetDateTime reviewedAt = writing ? p.getWritingReviewedAt() : p.getSpeakingReviewedAt();
        boolean reviewed = reviewedAt != null;
        BigDecimal currentScore = writing ? p.getWritingScore() : p.getSpeakingScore();
        String currentFeedback = writing ? p.getWritingFeedback() : p.getSpeakingFeedback();
        BigDecimal aiScore = reviewed ? (writing ? p.getWritingAiScore() : p.getSpeakingAiScore()) : currentScore;
        String aiFeedback = reviewed ? (writing ? p.getWritingAiFeedback() : p.getSpeakingAiFeedback()) : currentFeedback;
        User reviewer = writing ? p.getWritingReviewedBy() : p.getSpeakingReviewedBy();
        var question = p.getReviewVideoQuestion();
        var video = question.getReviewVideo();
        return new AiGradedItemResponse(
                writing ? TYPE_REFLEX_WRITING : TYPE_REFLEX_SPEAKING,
                p.getId(),
                p.getStudent().getId(),
                p.getStudent().getUser().getFullName(),
                video.getReviewVideoSet().getTitle() + " — " + video.getTitle(),
                question.getPrompt(),
                writing ? p.getAnswerText() : null,
                writing ? p.getWritingMarkedAnswer() : p.getSpeakingTranscript(),
                writing ? null : p.getAudioUrl(),
                aiScore,
                HUNDRED,
                aiFeedback,
                writing ? p.getWritingGradedAt() : p.getSpeakingGradedAt(),
                reviewed,
                reviewed ? currentScore : null,
                reviewed ? currentFeedback : null,
                reviewedAt,
                reviewer == null ? null : reviewer.getFullName());
    }

    /** ESSAY: mỗi câu trả lời 1 dòng, lấy bản chấm AI mới nhất; "đã chấm lại" khi bản hiện hành là của GV. */
    private List<AiGradedItemResponse> essayItems(Long classId) {
        Map<Long, StudentAnswerGrading> latestAiByAnswer = new LinkedHashMap<>();
        for (StudentAnswerGrading g : studentAnswerGradingRepository
                .findByGradingSourceAndClassId(StudentAnswerGrading.GradingSource.AI, classId)) {
            latestAiByAnswer.putIfAbsent(g.getStudentAnswer().getId(), g);
        }
        if (latestAiByAnswer.isEmpty()) {
            return List.of();
        }
        Map<Long, StudentAnswerGrading> currentByAnswer = studentAnswerGradingRepository
                .findByStudentAnswerIdInAndLatestIsTrue(latestAiByAnswer.keySet()).stream()
                .collect(Collectors.toMap(g -> g.getStudentAnswer().getId(), g -> g));
        List<AiGradedItemResponse> items = new ArrayList<>();
        latestAiByAnswer.forEach((answerId, ai) -> {
            StudentAnswerGrading current = currentByAnswer.get(answerId);
            boolean reviewed = current != null && current.getGradingSource() == StudentAnswerGrading.GradingSource.HUMAN;
            StudentAnswer answer = ai.getStudentAnswer();
            items.add(new AiGradedItemResponse(
                    TYPE_ESSAY,
                    answerId,
                    answer.getExerciseAttempt().getStudent().getId(),
                    answer.getExerciseAttempt().getStudent().getUser().getFullName(),
                    answer.getExerciseAttempt().getExercise().getTitle(),
                    answer.getQuestion().getContent(),
                    answer.getAnswerText(),
                    ai.getMarkedAnswer(),
                    null,
                    ai.getScore(),
                    ai.getMaxScore(),
                    ai.getFeedback(),
                    ai.getGradedAt(),
                    reviewed,
                    reviewed ? current.getScore() : null,
                    reviewed ? current.getFeedback() : null,
                    reviewed ? current.getGradedAt() : null,
                    reviewed ? current.getGrader().getFullName() : null));
        });
        return items;
    }
}
