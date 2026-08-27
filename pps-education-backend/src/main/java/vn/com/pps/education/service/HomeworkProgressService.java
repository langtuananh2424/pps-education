package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import vn.com.pps.education.domain.Exercise;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.ExerciseAttempt;
import vn.com.pps.education.domain.ReviewVideo;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.ReviewVideoProgress;
import vn.com.pps.education.domain.ReviewVideoQuestion;
import vn.com.pps.education.domain.ReviewVideoSet;
import vn.com.pps.education.repository.ExerciseAttemptRepository;
import vn.com.pps.education.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.repository.ReviewVideoProgressRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionRepository;
import vn.com.pps.education.repository.ReviewVideoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ReflexQuestionProgressRepository reflexQuestionProgressRepository;

    public HomeworkProgressService(ExerciseAttemptRepository exerciseAttemptRepository,
                                    ReviewVideoRepository reviewVideoRepository,
                                    ReviewVideoProgressRepository reviewVideoProgressRepository,
                                    ReviewVideoQuestionRepository reviewVideoQuestionRepository,
                                    ReflexQuestionProgressRepository reflexQuestionProgressRepository) {
        this.exerciseAttemptRepository = exerciseAttemptRepository;
        this.reviewVideoRepository = reviewVideoRepository;
        this.reviewVideoProgressRepository = reviewVideoProgressRepository;
        this.reviewVideoQuestionRepository = reviewVideoQuestionRepository;
        this.reflexQuestionProgressRepository = reflexQuestionProgressRepository;
    }

    /**
     * % bài ngữ pháp online đã giao — "Chưa làm bài"/"Đang chờ chấm" nếu chưa có điểm cuối cùng.
     *
     * V147 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — CHỦ Ý dùng lượt làm MỚI
     * NHẤT theo attemptNumber (không ưu tiên cờ selectedForGrading như ExerciseReportService#
     * selectedOrLatestAttemptByStudent dùng cho "Thống kê BTVN theo lớp") — cột "BTVN buổi trước" ở
     * Nhận xét hàng ngày cần phản ánh đúng trạng thái làm bài HIỆN TẠI của học sinh (kể cả đang làm lại
     * dở dang), khác với trang Thống kê cần hiện đúng điểm CHÍNH THỨC giáo viên đã chốt. Cơ chế chọn
     * điểm chính thức (selectForGrading) vẫn giữ nguyên, chỉ không áp dụng cho cột này.
     */
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
     * V150 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — mirror
     * {@link #grammarProgressLabel(ExerciseAssignment, Long)} nhưng cộng dồn NHIỀU bản giao cùng 1 "Lô
     * giao BTVN theo kỹ năng" (HomeworkSkillBatch, xem StudentCommentService) — % = tổng totalScore / tổng
     * totalPoints của TẤT CẢ Bài trong lô (đúng công thức đã chốt với người dùng khi thiết kế lô, VD 3
     * Bài 8 câu/bài, 20/24 câu ≈ 83%). "Chưa làm bài" nếu KHÔNG Bài nào trong lô có lượt làm; "Đang chờ
     * chấm" nếu có Bài đã làm nhưng còn ít nhất 1 Bài chưa chấm xong.
     */
    public String grammarProgressLabel(List<ExerciseAssignment> assignments, Long studentId) {
        if (assignments == null || assignments.isEmpty()) {
            return null;
        }
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalPoints = BigDecimal.ZERO;
        boolean anyAttempted = false;
        for (ExerciseAssignment assignment : assignments) {
            Exercise exercise = assignment.getExercise();
            List<ExerciseAttempt> attempts = exerciseAttemptRepository
                    .findByExerciseIdAndStudentIdOrderByAttemptNumberDesc(exercise.getId(), studentId);
            if (attempts.isEmpty()) {
                continue;
            }
            anyAttempted = true;
            ExerciseAttempt latest = attempts.get(0);
            if (latest.getTotalScore() == null) {
                return "Đang chờ chấm";
            }
            totalScore = totalScore.add(latest.getTotalScore());
            totalPoints = totalPoints.add(exercise.getTotalPoints() == null ? BigDecimal.ZERO : exercise.getTotalPoints());
        }
        if (!anyAttempted) {
            return "Chưa làm bài";
        }
        if (totalPoints.signum() <= 0) {
            return null;
        }
        int percent = totalScore.divide(totalPoints, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
        return percent + "%";
    }

    /**
     * Mirror {@link #grammarPassed(ExerciseAssignment, Long)} cho 1 Lô (V150) — khác nguồn "đạt": bản lẻ
     * đọc thẳng cờ {@code attempt.passed} (đã tính theo đúng passThresholdPercent CỦA TỪNG Exercise); 1
     * Lô gồm N Exercise có thể khác ngưỡng nhau nên không có 1 cờ chung để đọc lại — so trực tiếp % tổng
     * (từ {@link #grammarProgressLabel(List, Long)}) với ngưỡng 70% cố định đã chốt với người dùng khi
     * thiết kế Lô (VD 3 Bài 8 câu/bài, 20/24 câu ≈ 83% ≥ 70% → Đạt).
     */
    private static final BigDecimal BATCH_PASS_THRESHOLD_PERCENT = new BigDecimal("70.00");

    public boolean grammarPassed(List<ExerciseAssignment> assignments, Long studentId) {
        String label = grammarProgressLabel(assignments, studentId);
        if (label == null || !label.endsWith("%")) {
            return false;
        }
        try {
            return new BigDecimal(label.substring(0, label.length() - 1)).compareTo(BATCH_PASS_THRESHOLD_PERCENT) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
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
                    ? connectionPercent(v, studentId, assignment.getId()) : reflexPercent(v, studentId, assignment.getId());
        }
        return Math.round(total / (float) videos.size()) + "%";
    }

    /**
     * "Đạt" BTVN Ngữ pháp online — bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng 2026-08-06, dùng cho HomeworkAlertTrackingService (cảnh
     * báo thiếu bài theo lộ trình). Tái dùng thẳng cờ {@code passed} đã
     * tính sẵn lúc nộp bài ({@link ExerciseAttemptService#applyPassOutcome}),
     * lượt mới nhất (V147 — chủ ý KHÔNG ưu tiên selectedForGrading, xem Javadoc grammarProgressLabel)
     * — chưa nộp lượt nào coi là chưa đạt.
     */
    public boolean grammarPassed(ExerciseAssignment assignment, Long studentId) {
        if (assignment == null) {
            return false;
        }
        List<ExerciseAttempt> attempts = exerciseAttemptRepository
                .findByExerciseIdAndStudentIdOrderByAttemptNumberDesc(assignment.getExercise().getId(), studentId);
        return !attempts.isEmpty() && Boolean.TRUE.equals(attempts.get(0).getPassed());
    }

    /**
     * "Đạt" BTVN Video ôn tập — bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng 2026-08-06, dùng cho HomeworkAlertTrackingService.
     * CONNECTION: tái dùng thẳng {@link ReviewVideoProgress#isCompleted()}
     * (= viewCount đã đạt requiredViewCount của TỪNG video, đã tính sẵn ở
     * {@code ReviewVideoService#recomputeProgress}) — đạt buổi khi TẤT CẢ
     * video trong bộ đều completed. REFLEX: chưa có cờ "đạt" tương đương
     * có sẵn — tạm so % số câu đã trả lời với ngưỡng cấu hình
     * (`homework_alert.reflex_pass_threshold_percent`).
     */
    public boolean videoPassed(ReviewVideoAssignment assignment, Long studentId, int reflexPassThresholdPercent) {
        if (assignment == null) {
            return false;
        }
        ReviewVideoSet set = assignment.getReviewVideoSet();
        List<ReviewVideo> videos = reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(set.getId());
        if (videos.isEmpty()) {
            return false;
        }
        if (set.getVideoType() == ReviewVideoSet.VideoType.CONNECTION) {
            return videos.stream().allMatch(v -> reviewVideoProgressRepository
                    .findByReviewVideoIdAndStudentIdAndReviewVideoAssignmentId(v.getId(), studentId, assignment.getId())
                    .map(ReviewVideoProgress::isCompleted).orElse(false));
        }
        String label = videoProgressLabel(assignment, studentId);
        if (label == null || !label.endsWith("%")) {
            return false;
        }
        try {
            return Integer.parseInt(label.substring(0, label.length() - 1)) >= reflexPassThresholdPercent;
        } catch (NumberFormatException e) {
            return false;
        }
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
    private int connectionPercent(ReviewVideo v, Long studentId, Long assignmentId) {
        if (v.getRequiredViewCount() <= 0) {
            return 0;
        }
        return reviewVideoProgressRepository.findByReviewVideoIdAndStudentIdAndReviewVideoAssignmentId(v.getId(), studentId, assignmentId)
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
     *
     * V145 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — fix bug thật: vẫn đọc
     * ReviewVideoQuestionSubmission (bảng của luồng CŨ "ghi âm theo mốc, nộp cả loạt cuối video, GV
     * chấm tay") — REFLEX từ V139 đã chuyển hẳn sang ReflexSequentialGradingService/bảng
     * reflex_question_progress, không còn tạo submission kiểu cũ nữa nên "CLIP PHẢN XẠ" ở bảng Nhận xét
     * học viên luôn hiện 0%/"−" dù học sinh đã làm/đạt hết qua luồng mới (cùng gốc bug đã sửa ở
     * AssignmentsTab.tsx/ReviewVideoReportService/ReviewVideoService#toAssignmentStats — xem đó để biết
     * chi tiết). Đổi sang đọc reflex_question_progress — "đã trả lời" = có dòng progress cho câu đó.
     */
    private int reflexPercent(ReviewVideo v, Long studentId, Long assignmentId) {
        List<ReviewVideoQuestion> questions = reviewVideoQuestionRepository.findByReviewVideoIdOrderByDisplayOrder(v.getId());
        if (questions.isEmpty()) {
            return 0;
        }
        Set<Long> answeredQuestionIds = reflexQuestionProgressRepository
                .findByReviewVideoAssignmentIdAndStudentId(assignmentId, studentId).stream()
                .map(p -> p.getReviewVideoQuestion().getId())
                .collect(Collectors.toSet());
        long answeredCount = questions.stream().filter(q -> answeredQuestionIds.contains(q.getId())).count();
        return Math.round(answeredCount * 100f / questions.size());
    }
}
