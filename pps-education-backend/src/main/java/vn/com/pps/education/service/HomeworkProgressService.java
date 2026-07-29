package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import vn.com.pps.education.domain.Exercise;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.ExerciseAttempt;
import vn.com.pps.education.domain.ReviewVideo;
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

    /** % video ôn tập đã giao — trung bình % từng video trong bộ (watched% cho CONNECTION, score/maxScore cho REFLEX). */
    public String videoProgressLabel(ReviewVideoSet set, Long studentId) {
        if (set == null) {
            return null;
        }
        List<ReviewVideo> videos = reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(set.getId());
        if (videos.isEmpty()) {
            return null;
        }
        int total = 0;
        for (ReviewVideo v : videos) {
            total += set.getVideoType() == ReviewVideoSet.VideoType.CONNECTION
                    ? connectionPercent(v, studentId) : reflexPercent(v, studentId);
        }
        return Math.round(total / (float) videos.size()) + "%";
    }

    private int connectionPercent(ReviewVideo v, Long studentId) {
        return reviewVideoProgressRepository.findByReviewVideoIdAndStudentId(v.getId(), studentId)
                .map(p -> Math.min(100, Math.round(p.getWatchedSeconds() * 100f / v.getDurationSeconds())))
                .orElse(0);
    }

    /** V57: video REFLEX nay có nhiều câu hỏi — % là trung bình % (điểm/điểm tối đa của attempt MỚI NHẤT) trên từng câu hỏi trong video. */
    private int reflexPercent(ReviewVideo v, Long studentId) {
        List<ReviewVideoQuestion> questions = reviewVideoQuestionRepository.findByReviewVideoIdOrderByDisplayOrder(v.getId());
        if (questions.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ReviewVideoQuestion q : questions) {
            List<ReviewVideoQuestionSubmission> attempts = reviewVideoQuestionSubmissionRepository
                    .findByReviewVideoQuestionIdAndStudentIdOrderByAttemptNumberDesc(q.getId(), studentId);
            total += attempts.isEmpty() ? 0 : latestAttemptPercent(attempts.get(0));
        }
        return Math.round(total / (float) questions.size());
    }

    private int latestAttemptPercent(ReviewVideoQuestionSubmission latest) {
        if (latest.getScore() == null || latest.getMaxScore() == null || latest.getMaxScore().signum() <= 0) {
            return 0;
        }
        return latest.getScore().divide(latest.getMaxScore(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).intValue();
    }
}
