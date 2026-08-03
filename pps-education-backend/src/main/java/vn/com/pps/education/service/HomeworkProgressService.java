package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import vn.com.pps.education.domain.Exercise;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.ExerciseAttempt;
import vn.com.pps.education.domain.ReviewVideo;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.ReviewVideoQuestion;
import vn.com.pps.education.domain.ReviewVideoQuestionSubmission;
import vn.com.pps.education.domain.ReviewVideoSet;
import vn.com.pps.education.repository.ExerciseAttemptRepository;
import vn.com.pps.education.repository.ReviewVideoProgressRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionSubmissionRepository;
import vn.com.pps.education.repository.ReviewVideoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Tính % tiến độ hoàn thành BTVN đã giao (Ngữ pháp online / Video Kết nối
 * + Phản xạ) — tách từ StudentCommentService (đã dùng cho nhận xét/Excel)
 * để ParentPortalService (Cổng phụ huynh xem tiến độ BTVN của con) dùng
 * chung, không lặp lại logic (bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-07-29).
 */
@Service
public class HomeworkProgressService {

    private final ExerciseAttemptRepository exerciseAttemptRepository;
    private final ReviewVideoRepository reviewVideoRepository;
    private final ReviewVideoProgressRepository reviewVideoProgressRepository;
    private final ReviewVideoQuestionRepository reviewVideoQuestionRepository;
    private final ReviewVideoQuestionSubmissionRepository reviewVideoQuestionSubmissionRepository;

    public HomeworkProgressService(ExerciseAttemptRepository exerciseAttemptRepository,
                                    ReviewVideoRepository reviewVideoRepository,
                                    ReviewVideoProgressRepository reviewVideoProgressRepository,
                                    ReviewVideoQuestionRepository reviewVideoQuestionRepository,
                                    ReviewVideoQuestionSubmissionRepository reviewVideoQuestionSubmissionRepository) {
        this.exerciseAttemptRepository = exerciseAttemptRepository;
        this.reviewVideoRepository = reviewVideoRepository;
        this.reviewVideoProgressRepository = reviewVideoProgressRepository;
        this.reviewVideoQuestionRepository = reviewVideoQuestionRepository;
        this.reviewVideoQuestionSubmissionRepository = reviewVideoQuestionSubmissionRepository;
    }

    /** % bài ngữ pháp online đã giao — "Chưa làm bài"/"Đang chờ chấm" nếu chưa có điểm cuối cùng. */
    public String grammarProgressLabel(ExerciseAssignment assignment, Long studentId) {
        if (assignment == null) {
            return null;
        }
        Exercise exercise = assignment.getExercise();
        List<ExerciseAttempt> attempts = exerciseAttemptRepository
                .findByExerciseIdAndStudentIdOrderByAttemptNumberDesc(exercise.getId(), studentId);
        if (attempts.isEmpty()) {
            return "Chưa làm bài";
        }
        ExerciseAttempt latest = attempts.get(0);
        if (latest.getTotalScore() == null) {
            return "Đang chờ chấm";
        }
        if (exercise.getTotalPoints() == null || exercise.getTotalPoints().signum() <= 0) {
            return null;
        }
        int percent = latest.getTotalScore().divide(exercise.getTotalPoints(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
        return percent + "%";
    }

    /**
     * % video ôn tập đã giao — trung bình % từng video trong bộ (số lượt
     * xem ĐẠT/số lượt yêu cầu cho CONNECTION, số câu ĐÃ TRẢ LỜI/tổng số
     * câu cho REFLEX — xem Javadoc connectionPercent/reflexPercent). V65
     * (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): nhận
     * {@link ReviewVideoAssignment} thay vì {@code ReviewVideoSet} trực
     * tiếp — chỉ cần {@code getReviewVideoSet()} bên trong, cách tính %
     * không đổi.
     */
    public String videoProgressLabel(ReviewVideoAssignment assignment, Long studentId) {
        if (assignment == null) {
            return null;
        }
        ReviewVideoSet set = assignment.getReviewVideoSet();
        List<ReviewVideo> videos = reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(set.getId());
        if (videos.isEmpty()) {
            return null;
        }
        int total = 0;
        for (ReviewVideo v : videos) {
            total += set.getVideoType() == ReviewVideoSet.VideoType.CONNECTION
                    ? connectionPercent(v, studentId) : reflexPercent(v, studentId, assignment.getId());
        }
        return Math.round(total / (float) videos.size()) + "%";
    }

    /**
     * V71 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-03,
     * sửa lại công thức tính % — trước đây dùng watchedSeconds/duration,
     * SAI vì không phản ánh đúng khái niệm nghiệp vụ "lượt xem": xem 99%
     * thời lượng ở 1 lượt duy nhất vẫn chỉ tính là 1/{@code requiredViewCount}
     * lượt ĐẠT, không phải gần 100%). % = số lượt xem ĐÃ ĐẠT ngưỡng
     * (viewCount, chỉ đếm lượt qualified — xem ReviewVideoService#reportProgress)
     * chia cho số lượt YÊU CẦU của video (requiredViewCount). VD yêu cầu 3
     * lượt, mới đạt 1 lượt → 33%, không phải % thời lượng đã xem.
     */
    private int connectionPercent(ReviewVideo v, Long studentId) {
        if (v.getRequiredViewCount() <= 0) {
            return 0;
        }
        return reviewVideoProgressRepository.findByReviewVideoIdAndStudentId(v.getId(), studentId)
                .map(p -> Math.min(100, Math.round(p.getViewCount() * 100f / v.getRequiredViewCount())))
                .orElse(0);
    }

    /**
     * V57: video REFLEX nay có nhiều câu hỏi. V71 (bổ sung ngoài SDD gốc,
     * đã xác nhận với người dùng 2026-08-03, sửa lại công thức tính % —
     * trước đây lấy TRUNG BÌNH ĐIỂM (score/maxScore) từng câu đã chấm,
     * SAI vì trộn lẫn 2 khái niệm khác nhau "đã làm bao nhiêu %" và "làm
     * đúng bao nhiêu %"): % = số câu ĐÃ TRẢ LỜI (có ít nhất 1 submission,
     * không quan tâm điểm chấm cao hay thấp) chia cho tổng số câu. VD 5
     * câu, trả lời 3 → 60%. V69 (bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng 2026-07-31): chỉ tính submission trong phạm vi ĐÚNG lần
     * giao (assignmentId) đang báo cáo — không tính lịch sử của lần giao
     * TRƯỚC (khác lần giao = "làm lại từ đầu", xem Javadoc
     * ReviewVideoService.submitQuestionAudio).
     */
    private int reflexPercent(ReviewVideo v, Long studentId, Long assignmentId) {
        List<ReviewVideoQuestion> questions = reviewVideoQuestionRepository.findByReviewVideoIdOrderByDisplayOrder(v.getId());
        if (questions.isEmpty()) {
            return 0;
        }
        int answeredCount = 0;
        for (ReviewVideoQuestion q : questions) {
            List<ReviewVideoQuestionSubmission> submissions = reviewVideoQuestionSubmissionRepository
                    .findByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentIdOrderByAttemptNumberDesc(
                            q.getId(), studentId, assignmentId);
            if (!submissions.isEmpty()) {
                answeredCount++;
            }
        }
        return Math.round(answeredCount * 100f / questions.size());
    }
}
