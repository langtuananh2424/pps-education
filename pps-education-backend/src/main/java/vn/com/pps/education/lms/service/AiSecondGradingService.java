package vn.com.pps.education.lms.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.academic.repository.ClassTeacherRepository;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.auth.repository.UserRepository;
import vn.com.pps.education.exception.AnswerNotManuallyGradableException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.lms.domain.ReflexQuestionProgress;
import vn.com.pps.education.lms.dto.ReviewReflexStepRequest;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.permission.service.PermissionEvaluationService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Chấm lần 2 bài Video phản xạ do AI chấm (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-19;
 * UC-23b). Học sinh phản ánh điểm AI đôi khi chưa đúng nên giáo viên chỉnh điểm chính thức ngay trong popup
 * "Lịch sử chấm AI" của trang Thống kê BTVN theo lớp (xem {@link ReviewVideoReportService#getStudentReflexHistory}):
 * điểm giáo viên ghi thẳng vào writing_score/speaking_score của {@link ReflexQuestionProgress} (ngưỡng đạt,
 * báo cáo, tiến độ BTVN tự dùng điểm mới), điểm + nhận xét gốc của AI giữ ở cột *_ai_* (V216). Điểm chính thức
 * luôn là lần nộp mới nhất của bước, nên chỉ lần đó chỉnh được.
 *
 * Phạm vi: giáo viên đứng lớp thật (class_teachers, assignedTo IS NULL) hoặc tài khoản có quyền quản trị
 * {@value #PERM_ADMIN_SCOPE} (vượt rào lớp, V107 — mirror ReviewVideoService#requireAssignedTeacher).
 */
@Service
public class AiSecondGradingService {

    static final String PERM_ADMIN_SCOPE = "lms.review-video.manage";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Bước của Video phản xạ mà giáo viên chấm lại. */
    public enum ReflexStep { WRITING, SPEAKING }

    private final ClassTeacherRepository classTeacherRepository;
    private final ReflexQuestionProgressRepository reflexQuestionProgressRepository;
    private final UserRepository userRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    public AiSecondGradingService(ClassTeacherRepository classTeacherRepository,
                                  ReflexQuestionProgressRepository reflexQuestionProgressRepository,
                                  UserRepository userRepository,
                                  PermissionEvaluationService permissionEvaluationService) {
        this.classTeacherRepository = classTeacherRepository;
        this.reflexQuestionProgressRepository = reflexQuestionProgressRepository;
        this.userRepository = userRepository;
        this.permissionEvaluationService = permissionEvaluationService;
    }

    /**
     * Chấm lần 2 một bước (Viết/Nói). Chấm lại lần nữa thì chỉ đổi điểm giáo viên, điểm AI gốc (đã lưu ở lần
     * đầu) được giữ nguyên.
     */
    @Transactional
    public void reviewReflexStep(Long progressId, ReflexStep step, ReviewReflexStepRequest request, Long actorUserId) {
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
        reflexQuestionProgressRepository.save(progress);
    }

    private AnswerNotManuallyGradableException notAiGraded() {
        return new AnswerNotManuallyGradableException("error.aiSecondGrading.notAiGraded", new Object[]{},
                "Bước này chưa được AI chấm — không thể chấm lần 2.");
    }

    /** Phạm vi chấm lần 2: giáo viên đứng lớp thật hoặc quyền {@value #PERM_ADMIN_SCOPE}; dùng chung với {@link EssayGradingHistoryService}. */
    public void requireAssignedTeacher(Long classId, Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, PERM_ADMIN_SCOPE)) {
            return;
        }
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "error.notAssignedTeacherForClass.default", new Object[]{}, "Bạn không được phân công giảng dạy lớp này.");
        }
    }
}
