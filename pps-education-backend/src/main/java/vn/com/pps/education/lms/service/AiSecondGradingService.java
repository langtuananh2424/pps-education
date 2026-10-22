package vn.com.pps.education.lms.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.academic.repository.ClassTeacherRepository;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.auth.repository.UserRepository;
import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.common.ReflexV2Scoring;
import vn.com.pps.education.exception.AnswerNotManuallyGradableException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.lms.domain.ReflexQuestionProgress;
import vn.com.pps.education.lms.domain.ReflexQuestionProgressHistory;
import vn.com.pps.education.lms.dto.ReviewReflexStepRequest;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressHistoryRepository;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.permission.service.PermissionEvaluationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Optional;

/**
 * Chấm lần 2 bài Video phản xạ do AI chấm (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-19;
 * UC-23b). Học sinh phản ánh điểm AI đôi khi chưa đúng nên giáo viên chỉnh điểm chính thức ngay trong popup
 * "Lịch sử chấm AI" của trang Thống kê BTVN theo lớp (xem {@link ReviewVideoReportService#getStudentReflexHistory}):
 * điểm giáo viên ghi thẳng vào writing_score/speaking_score của {@link ReflexQuestionProgress} (ngưỡng đạt,
 * báo cáo, tiến độ BTVN tự dùng điểm mới), điểm + nhận xét gốc của AI giữ ở cột *_ai_* (V216). Điểm chính thức
 * mặc định là lần nộp mới nhất của bước.
 *
 * Lần tốt nhất (V217, đã xác nhận với người dùng 2026-10-20): giáo viên có thể CHỌN lần Viết/Nói tốt nhất làm kết
 * quả chính thức ({@link #selectReflexAttempt}) rồi chỉnh điểm ở lần đó. Điểm + nhận xét giáo viên chốt cho từng
 * lần được lưu cạnh bản AI gốc ở {@link ReflexQuestionProgressHistory} (làm nhãn khi xuất dữ liệu fine-tune), còn
 * {@link ReflexQuestionProgress} luôn phản chiếu kết quả chính thức để mọi chỗ đọc điểm không phải sửa.
 *
 * Phạm vi: giáo viên đứng lớp thật (class_teachers, assignedTo IS NULL) hoặc tài khoản có quyền quản trị
 * {@value #PERM_ADMIN_SCOPE} (vượt rào lớp, V107 — mirror ReviewVideoService#requireAssignedTeacher).
 */
@Service
public class AiSecondGradingService {

    static final String PERM_ADMIN_SCOPE = "lms.review-video.manage";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Hậu tố nhãn band chỉ để tham khảo, không vào điểm mở khoá (xem ReflexV2AiGradingService). */
    private static final String REFERENCE_ONLY_SUFFIX = " (tham khảo)";

    /** Bước của Video phản xạ mà giáo viên chấm lại. */
    public enum ReflexStep { WRITING, SPEAKING }

    private final ClassTeacherRepository classTeacherRepository;
    private final ReflexQuestionProgressRepository reflexQuestionProgressRepository;
    private final ReflexQuestionProgressHistoryRepository reflexQuestionProgressHistoryRepository;
    private final UserRepository userRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    public AiSecondGradingService(ClassTeacherRepository classTeacherRepository,
                                  ReflexQuestionProgressRepository reflexQuestionProgressRepository,
                                  ReflexQuestionProgressHistoryRepository reflexQuestionProgressHistoryRepository,
                                  UserRepository userRepository,
                                  PermissionEvaluationService permissionEvaluationService) {
        this.classTeacherRepository = classTeacherRepository;
        this.reflexQuestionProgressRepository = reflexQuestionProgressRepository;
        this.reflexQuestionProgressHistoryRepository = reflexQuestionProgressHistoryRepository;
        this.userRepository = userRepository;
        this.permissionEvaluationService = permissionEvaluationService;
    }

    /**
     * Chấm lần 2 một bước (Viết/Nói). Chấm lại lần nữa thì chỉ đổi điểm giáo viên, điểm AI gốc (đã lưu ở lần
     * đầu) được giữ nguyên.
     */
    @Transactional
    public void reviewReflexStep(Long progressId, ReflexStep step, ReviewReflexStepRequest request, Long actorUserId) {
        ReflexQuestionProgress progress = getProgressOrThrow(progressId);
        requireAssignedTeacher(progress.getReviewVideoAssignment().getSchoolClass().getId(), actorUserId);
        User actor = getUserOrThrow(actorUserId);

        String feedback = request.feedback() == null || request.feedback().isBlank() ? null : request.feedback().trim();
        OffsetDateTime now = OffsetDateTime.now();
        boolean writing = step == ReflexStep.WRITING;
        if (writing ? progress.getWritingScore() == null || progress.getWritingGradedAt() == null
                : progress.getSpeakingScore() == null || progress.getSpeakingGradedAt() == null) {
            throw notAiGraded();
        }
        Optional<ReflexQuestionProgressHistory> official = officialAttempt(attemptsOf(progress, step));
        // Điểm từng band (2026-10-22): điểm tổng do server tính lại từ band, không tin điểm tổng client gửi kèm.
        List<CriteriaScoreItem> bands = null;
        BigDecimal score = request.score();
        if (request.criteriaScores() != null && !request.criteriaScores().isEmpty()) {
            List<CriteriaScoreItem> aiBands = official.map(ReflexQuestionProgressHistory::getCriteriaScores).orElse(null);
            bands = resolveBands(aiBands, request.criteriaScores());
            score = BigDecimal.valueOf(totalFromBands(progress.getRubricVersion(), bands));
        } else if (score == null) {
            throw new AnswerNotManuallyGradableException("error.aiSecondGrading.scoreRequired", new Object[]{},
                    "Cần nhập điểm tổng hoặc điểm từng band.");
        }
        // Sửa lại chỉ điểm tổng thì band hiển thị quay về bản AI gốc (band không còn khớp điểm tổng giáo viên chốt).
        List<CriteriaScoreItem> shownBands = bands != null ? bands : official.map(ReflexQuestionProgressHistory::getCriteriaScores).orElse(null);
        if (writing) {
            if (progress.getWritingReviewedAt() == null) {
                progress.setWritingAiScore(progress.getWritingScore());
                progress.setWritingAiFeedback(progress.getWritingFeedback());
            }
            progress.setWritingScore(score);
            progress.setWritingMaxScore(HUNDRED);
            progress.setWritingFeedback(feedback);
            progress.setWritingCriteriaScores(shownBands);
            // Câu sửa gợi ý do AI sinh theo điểm AI cũ — điểm GV đổi thì không còn khớp, bỏ để khỏi hiện sai.
            progress.setWritingCorrectedAnswer(null);
            progress.setWritingReviewedBy(actor);
            progress.setWritingReviewedAt(now);
        } else {
            if (progress.getSpeakingReviewedAt() == null) {
                progress.setSpeakingAiScore(progress.getSpeakingScore());
                progress.setSpeakingAiFeedback(progress.getSpeakingFeedback());
            }
            progress.setSpeakingScore(score);
            progress.setSpeakingMaxScore(HUNDRED);
            progress.setSpeakingFeedback(feedback);
            progress.setSpeakingCriteriaScores(shownBands);
            progress.setSpeakingReviewedBy(actor);
            progress.setSpeakingReviewedAt(now);
        }
        reflexQuestionProgressRepository.save(progress);

        // V217 — lưu điểm/nhận xét giáo viên lên đúng lần đang là kết quả chính thức (lần được chọn, hoặc lần mới nhất).
        BigDecimal savedScore = score;
        List<CriteriaScoreItem> savedBands = bands;
        official.ifPresent(attempt -> {
            attempt.setTeacherScore(savedScore);
            attempt.setTeacherFeedback(feedback);
            attempt.setTeacherCriteriaScores(savedBands);
            attempt.setTeacherBy(actor);
            attempt.setTeacherAt(now);
            reflexQuestionProgressHistoryRepository.save(attempt);
        });
    }

    /**
     * Ghép band giáo viên sửa lên danh sách band AI gốc của lần chính thức: phải đủ và khớp tên từng band (giữ nguyên
     * thứ tự + nhãn của AI, kể cả hậu tố "(tham khảo)"), thiếu/thừa/trùng band là lỗi — không cho lưu một nửa.
     */
    static List<CriteriaScoreItem> resolveBands(List<CriteriaScoreItem> aiBands, List<ReviewReflexStepRequest.BandScore> edited) {
        if (aiBands == null || aiBands.isEmpty()) {
            throw new AnswerNotManuallyGradableException("error.aiSecondGrading.noBands", new Object[]{},
                    "Lần làm này không có điểm từng band — chỉ chỉnh được điểm tổng.");
        }
        Map<String, Integer> byName = new HashMap<>();
        for (ReviewReflexStepRequest.BandScore band : edited) {
            if (byName.put(band.criterion(), band.percent()) != null) {
                throw bandsMismatch();
            }
        }
        if (byName.size() != aiBands.size()) {
            throw bandsMismatch();
        }
        List<CriteriaScoreItem> out = new ArrayList<>();
        for (CriteriaScoreItem ai : aiBands) {
            Integer percent = byName.get(ai.criterion());
            if (percent == null) {
                throw bandsMismatch();
            }
            out.add(new CriteriaScoreItem(ai.criterion(), percent));
        }
        return out;
    }

    /**
     * Điểm tổng từ band, cùng công thức lúc AI chấm: rubric v2 = trung bình các band (bỏ band chỉ "tham khảo", tức Phát
     * âm khi không tính vào mở khoá) làm tròn XUỐNG bội 5 ({@link ReflexV2Scoring#average}); luồng cũ (v1) AI tự cho điểm
     * tổng nên dùng trung bình cộng làm tròn gần nhất.
     */
    static int totalFromBands(String rubricVersion, List<CriteriaScoreItem> bands) {
        if (rubricVersion == null) {
            double avg = bands.stream().mapToInt(CriteriaScoreItem::percent).average().orElse(0);
            return BigDecimal.valueOf(avg).setScale(0, RoundingMode.HALF_UP).intValue();
        }
        double avg = bands.stream().filter(b -> !b.criterion().endsWith(REFERENCE_ONLY_SUFFIX))
                .mapToInt(CriteriaScoreItem::percent).average().orElse(0);
        return ReflexV2Scoring.floorTo5(avg);
    }

    private static AnswerNotManuallyGradableException bandsMismatch() {
        return new AnswerNotManuallyGradableException("error.aiSecondGrading.bandsMismatch", new Object[]{},
                "Danh sách band không khớp với các band AI đã chấm — vui lòng tải lại rồi sửa.");
    }

    /**
     * Chọn lần Viết/Nói tốt nhất làm kết quả chính thức của bước (V217). Điểm + nhận xét của lần đó (bản giáo viên đã
     * chốt trước đó nếu có, không thì điểm AI gốc — chọn = xác nhận) được đưa lên {@link ReflexQuestionProgress}, nên
     * ngưỡng đạt, mở khoá bước Nói, báo cáo và tiến độ BTVN tự dùng điểm mới. Học sinh nộp lại bước đó thì lần mới
     * thay thế và lựa chọn bị xoá (xem ReflexSequentialGradingService#clearTeacherSelection).
     */
    @Transactional
    public void selectReflexAttempt(Long progressId, ReflexStep step, int attemptNumber, Long actorUserId) {
        ReflexQuestionProgress progress = getProgressOrThrow(progressId);
        requireAssignedTeacher(progress.getReviewVideoAssignment().getSchoolClass().getId(), actorUserId);
        User actor = getUserOrThrow(actorUserId);

        List<ReflexQuestionProgressHistory> attempts = attemptsOf(progress, step);
        ReflexQuestionProgressHistory chosen = attempts.stream().filter(a -> a.getAttemptNumber() == attemptNumber).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("error.aiSecondGrading.attemptNotFound",
                        new Object[]{attemptNumber}, "Không tìm thấy lần làm #" + attemptNumber + " của bước này."));
        if (chosen.getScore() == null) {
            throw notAiGraded();
        }
        OffsetDateTime now = OffsetDateTime.now();
        // Bỏ cờ lần đang được chọn trước (flush) rồi mới đặt cờ lần mới — unique index chỉ cho 1 lần/bước.
        for (ReflexQuestionProgressHistory other : attempts) {
            if (other != chosen && other.isTeacherSelected()) {
                other.setTeacherSelected(false);
                reflexQuestionProgressHistoryRepository.saveAndFlush(other);
            }
        }
        if (chosen.getTeacherScore() == null) {
            chosen.setTeacherScore(chosen.getScore());
            chosen.setTeacherFeedback(chosen.getFeedback());
        }
        chosen.setTeacherSelected(true);
        chosen.setTeacherBy(actor);
        chosen.setTeacherAt(now);
        reflexQuestionProgressHistoryRepository.save(chosen);

        boolean latest = chosen.getAttemptNumber()
                == attempts.stream().mapToInt(ReflexQuestionProgressHistory::getAttemptNumber).max().orElse(0);
        boolean edited = chosen.getTeacherScore().compareTo(chosen.getScore()) != 0
                || !Objects.equals(chosen.getTeacherFeedback(), chosen.getFeedback())
                || chosen.getTeacherCriteriaScores() != null;
        mirrorToProgress(progress, step, chosen, actor, now, !latest || edited);
        reflexQuestionProgressRepository.save(progress);
    }

    /** Đưa lần được chọn lên {@link ReflexQuestionProgress}: nội dung bài làm + điểm/nhận xét chính thức; AI gốc giữ ở *_ai_*. */
    private void mirrorToProgress(ReflexQuestionProgress progress, ReflexStep step, ReflexQuestionProgressHistory chosen,
                                  User actor, OffsetDateTime now, boolean markReviewed) {
        BigDecimal maxScore = chosen.getMaxScore() == null ? HUNDRED : chosen.getMaxScore();
        if (step == ReflexStep.WRITING) {
            progress.setAnswerText(chosen.getAnswerText());
            progress.setWritingScore(chosen.getTeacherScore());
            progress.setWritingMaxScore(maxScore);
            progress.setWritingFeedback(chosen.getTeacherFeedback());
            progress.setWritingMarkedAnswer(chosen.getMarkedAnswer());
            progress.setWritingHint(chosen.getHint());
            progress.setWritingCriteriaScores(shownBands(chosen));
            progress.setWritingCorrectedAnswer(null);
            if (markReviewed) {
                progress.setWritingAiScore(chosen.getScore());
                progress.setWritingAiFeedback(chosen.getFeedback());
                progress.setWritingReviewedBy(actor);
                progress.setWritingReviewedAt(now);
            } else {
                progress.clearWritingReview();
            }
        } else {
            progress.setAudioUrl(chosen.getAudioUrl());
            progress.setSpeakingScore(chosen.getTeacherScore());
            progress.setSpeakingMaxScore(maxScore);
            progress.setSpeakingFeedback(chosen.getTeacherFeedback());
            progress.setSpeakingTranscript(chosen.getTranscript());
            progress.setSpeakingCriteriaScores(shownBands(chosen));
            progress.setSpeakingHint(chosen.getHint());
            if (markReviewed) {
                progress.setSpeakingAiScore(chosen.getScore());
                progress.setSpeakingAiFeedback(chosen.getFeedback());
                progress.setSpeakingReviewedBy(actor);
                progress.setSpeakingReviewedAt(now);
            } else {
                progress.clearSpeakingReview();
            }
        }
    }

    /** Band hiển thị của một lần: bản giáo viên đã sửa nếu có, không thì bản AI gốc. */
    private static List<CriteriaScoreItem> shownBands(ReflexQuestionProgressHistory attempt) {
        return attempt.getTeacherCriteriaScores() != null ? attempt.getTeacherCriteriaScores() : attempt.getCriteriaScores();
    }

    private List<ReflexQuestionProgressHistory> attemptsOf(ReflexQuestionProgress progress, ReflexStep step) {
        return reflexQuestionProgressHistoryRepository.findByReflexQuestionProgressIdAndAttemptTypeOrderByAttemptNumberAsc(
                progress.getId(), ReflexQuestionProgressHistory.AttemptType.valueOf(step.name()));
    }

    /** Lần chính thức của bước: lần giáo viên chọn, không có thì lần nộp mới nhất. */
    static Optional<ReflexQuestionProgressHistory> officialAttempt(List<ReflexQuestionProgressHistory> attempts) {
        return attempts.stream().filter(ReflexQuestionProgressHistory::isTeacherSelected).findFirst()
                .or(() -> attempts.stream().max(Comparator.comparingInt(ReflexQuestionProgressHistory::getAttemptNumber)));
    }

    private ReflexQuestionProgress getProgressOrThrow(Long progressId) {
        return reflexQuestionProgressRepository.findById(progressId)
                .orElseThrow(() -> new ResourceNotFoundException("error.aiSecondGrading.progressNotFound",
                        new Object[]{progressId}, "Không tìm thấy tiến trình bài id=" + progressId));
    }

    private User getUserOrThrow(Long actorUserId) {
        return userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.manualGrading.userNotFound",
                        new Object[]{actorUserId}, "Không tìm thấy tài khoản id=" + actorUserId));
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
