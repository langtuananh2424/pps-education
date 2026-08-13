package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ReviewVideo;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.ReviewVideoConnectionAnswer;
import vn.com.pps.education.domain.ReviewVideoConnectionQuestion;
import vn.com.pps.education.domain.ReviewVideoProgress;
import vn.com.pps.education.domain.ReviewVideoQuestion;
import vn.com.pps.education.domain.ReviewVideoQuestionSubmission;
import vn.com.pps.education.domain.ReviewVideoSet;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.dto.ReviewVideoAssignmentQuestionStatsResponse;
import vn.com.pps.education.dto.ReviewVideoAssignmentStatsResponse;
import vn.com.pps.education.dto.ReviewVideoAssignmentStudentStatsResponse;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.ReviewVideoAssignmentRepository;
import vn.com.pps.education.repository.ReviewVideoConnectionAnswerRepository;
import vn.com.pps.education.repository.ReviewVideoConnectionQuestionRepository;
import vn.com.pps.education.repository.ReviewVideoProgressRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionSubmissionRepository;
import vn.com.pps.education.repository.ReviewVideoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UC-66 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — trang "Xem chi tiết" 1
 * {@link ReviewVideoAssignment} cụ thể (mirror {@code ExerciseReportService}), dùng cho nút "Xem chi
 * tiết" ở hàng Video Ôn tập trong "Thống kê BTVN theo lớp". Khác {@link ReviewVideoService} (CRUD +
 * chấm bài + theo dõi tiến độ học sinh) — Service này THUẦN ĐỌC/BÁO CÁO, tách riêng theo đúng SRP
 * (1 Service = 1 nhóm nghiệp vụ chặt, xem .claude/rules/solid.md) — chỉ tái dùng
 * {@link ReviewVideoService#toAssignmentStats} cho phần header (đã có sẵn, không tự viết lại).
 *
 * REFLEX chỉ có bảng tổng hợp mỗi học sinh (đã nộp bao nhiêu câu/điểm TB) — KHÔNG có tab phân tích
 * câu hỏi hay chấm bài ở đây (việc chấm vẫn làm ở ExamsPage/ReviewVideoGradingPanel như hiện tại,
 * đã xác nhận với người dùng 2026-08-12). CONNECTION có đủ bảng tổng hợp + phân tích câu hỏi vì đã
 * có sẵn dữ liệu đúng/sai thật (khác REFLEX chấm tay bằng audio).
 */
@Service
public class ReviewVideoReportService {

    private final ReviewVideoAssignmentRepository reviewVideoAssignmentRepository;
    private final ReviewVideoService reviewVideoService;
    private final ReviewVideoRepository reviewVideoRepository;
    private final ReviewVideoProgressRepository reviewVideoProgressRepository;
    private final ReviewVideoQuestionRepository reviewVideoQuestionRepository;
    private final ReviewVideoQuestionSubmissionRepository reviewVideoQuestionSubmissionRepository;
    private final ReviewVideoConnectionQuestionRepository reviewVideoConnectionQuestionRepository;
    private final ReviewVideoConnectionAnswerRepository reviewVideoConnectionAnswerRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    private static final String PERM_REVIEW_VIDEO_MANAGE = "lms.review-video.manage";

    public ReviewVideoReportService(ReviewVideoAssignmentRepository reviewVideoAssignmentRepository,
                                     ReviewVideoService reviewVideoService,
                                     ReviewVideoRepository reviewVideoRepository,
                                     ReviewVideoProgressRepository reviewVideoProgressRepository,
                                     ReviewVideoQuestionRepository reviewVideoQuestionRepository,
                                     ReviewVideoQuestionSubmissionRepository reviewVideoQuestionSubmissionRepository,
                                     ReviewVideoConnectionQuestionRepository reviewVideoConnectionQuestionRepository,
                                     ReviewVideoConnectionAnswerRepository reviewVideoConnectionAnswerRepository,
                                     ClassEnrollmentRepository classEnrollmentRepository,
                                     ClassTeacherRepository classTeacherRepository,
                                     PermissionEvaluationService permissionEvaluationService) {
        this.reviewVideoAssignmentRepository = reviewVideoAssignmentRepository;
        this.reviewVideoService = reviewVideoService;
        this.reviewVideoRepository = reviewVideoRepository;
        this.reviewVideoProgressRepository = reviewVideoProgressRepository;
        this.reviewVideoQuestionRepository = reviewVideoQuestionRepository;
        this.reviewVideoQuestionSubmissionRepository = reviewVideoQuestionSubmissionRepository;
        this.reviewVideoConnectionQuestionRepository = reviewVideoConnectionQuestionRepository;
        this.reviewVideoConnectionAnswerRepository = reviewVideoConnectionAnswerRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.permissionEvaluationService = permissionEvaluationService;
    }

    @Transactional(readOnly = true)
    public ReviewVideoAssignmentStudentStatsResponse getStudentStats(Long assignmentId, Long actorUserId) {
        ReviewVideoAssignment assignment = getAssignmentOrThrow(assignmentId);
        requireAssignedTeacher(assignment.getSchoolClass().getId(), actorUserId);

        List<ClassEnrollment> roster = classEnrollmentRepository.findBySchoolClassIdAndStatus(
                assignment.getSchoolClass().getId(), ClassEnrollment.Status.ACTIVE);
        ReviewVideoAssignmentStatsResponse header = reviewVideoService.toAssignmentStats(assignment, roster);

        List<Long> scopedStudentIds = assignment.getTargetStudentIds() != null
                ? assignment.getTargetStudentIds()
                : roster.stream().map(e -> e.getStudent().getId()).toList();
        Map<Long, ClassEnrollment> enrollmentByStudentId = roster.stream()
                .collect(Collectors.toMap(e -> e.getStudent().getId(), e -> e, (a, b) -> a));

        ReviewVideoSet set = assignment.getReviewVideoSet();
        List<ReviewVideo> videos = reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(set.getId());
        List<Long> videoIds = videos.stream().map(ReviewVideo::getId).toList();

        List<ReviewVideoAssignmentStudentStatsResponse.StudentRow> rows = set.getVideoType() == ReviewVideoSet.VideoType.CONNECTION
                ? buildConnectionStudentRows(scopedStudentIds, enrollmentByStudentId, videos, videoIds)
                : buildReflexStudentRows(scopedStudentIds, enrollmentByStudentId, videos, videoIds);

        return new ReviewVideoAssignmentStudentStatsResponse(header, rows);
    }

    @Transactional(readOnly = true)
    public ReviewVideoAssignmentQuestionStatsResponse getQuestionStats(Long assignmentId, Long actorUserId) {
        ReviewVideoAssignment assignment = getAssignmentOrThrow(assignmentId);
        requireAssignedTeacher(assignment.getSchoolClass().getId(), actorUserId);

        ReviewVideoSet set = assignment.getReviewVideoSet();
        if (set.getVideoType() != ReviewVideoSet.VideoType.CONNECTION) {
            // REFLEX không có khái niệm đúng/sai tự chấm — FE chỉ gọi hàm này khi videoType=CONNECTION,
            // trả rỗng thay vì lỗi cho gọn (không cần FE tự chặn trước khi gọi).
            return new ReviewVideoAssignmentQuestionStatsResponse(List.of());
        }

        List<ReviewVideo> videos = reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(set.getId());
        List<Long> videoIds = videos.stream().map(ReviewVideo::getId).toList();
        Map<Long, ReviewVideo> videoById = videos.stream().collect(Collectors.toMap(ReviewVideo::getId, v -> v));

        List<ReviewVideoConnectionQuestion> questions = videoIds.isEmpty()
                ? List.of() : reviewVideoConnectionQuestionRepository.findByReviewVideoIdIn(videoIds);
        Map<String, ReviewVideoConnectionAnswer> latestAnswerByStudentAndQuestion = latestConnectionAnswerByStudentAndQuestion(videoIds);

        List<Long> scopedStudentIds = scopedStudentIdsFor(assignment);
        Map<Long, Student> studentByEnrollmentStudentId = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(assignment.getSchoolClass().getId(), ClassEnrollment.Status.ACTIVE)
                .stream().collect(Collectors.toMap(e -> e.getStudent().getId(), ClassEnrollment::getStudent));

        List<ReviewVideoAssignmentQuestionStatsResponse.QuestionRow> rows = questions.stream()
                .sorted((a, b) -> {
                    int byVideo = a.getReviewVideo().getId().compareTo(b.getReviewVideo().getId());
                    return byVideo != 0 ? byVideo : Integer.compare(a.getDisplayOrder(), b.getDisplayOrder());
                })
                .map(q -> toQuestionRow(q, videoById.get(q.getReviewVideo().getId()), scopedStudentIds,
                        studentByEnrollmentStudentId, latestAnswerByStudentAndQuestion))
                .toList();

        return new ReviewVideoAssignmentQuestionStatsResponse(rows);
    }

    // ===================== CONNECTION =====================

    private List<ReviewVideoAssignmentStudentStatsResponse.StudentRow> buildConnectionStudentRows(
            List<Long> scopedStudentIds, Map<Long, ClassEnrollment> enrollmentByStudentId,
            List<ReviewVideo> videos, List<Long> videoIds) {
        Map<String, ReviewVideoProgress> progressByKey = videoIds.isEmpty()
                ? Map.of()
                : reviewVideoProgressRepository.findByReviewVideoIdIn(videoIds).stream()
                    .collect(Collectors.toMap(p -> p.getReviewVideo().getId() + ":" + p.getStudent().getId(), p -> p));
        List<ReviewVideoConnectionQuestion> questions = videoIds.isEmpty()
                ? List.of() : reviewVideoConnectionQuestionRepository.findByReviewVideoIdIn(videoIds);
        Map<Long, List<ReviewVideoConnectionQuestion>> questionsByVideoId = questions.stream()
                .collect(Collectors.groupingBy(q -> q.getReviewVideo().getId()));
        Map<String, ReviewVideoConnectionAnswer> latestAnswerByStudentAndQuestion = latestConnectionAnswerByStudentAndQuestion(videoIds);

        return scopedStudentIds.stream().map(studentId -> {
            ClassEnrollment enrollment = enrollmentByStudentId.get(studentId);
            if (enrollment == null) return null;
            Student student = enrollment.getStudent();

            int viewCount = 0;
            int requiredViewCount = 0;
            boolean allVideosCompleted = !videos.isEmpty();
            int correctCount = 0;
            int totalQuestions = 0;
            boolean allVideosPassed = !videos.isEmpty();
            for (ReviewVideo video : videos) {
                ReviewVideoProgress progress = progressByKey.get(video.getId() + ":" + studentId);
                viewCount += progress == null ? 0 : progress.getViewCount();
                requiredViewCount += video.getRequiredViewCount();
                if (progress == null || !progress.isCompleted()) allVideosCompleted = false;

                List<ReviewVideoConnectionQuestion> videoQuestions = questionsByVideoId.getOrDefault(video.getId(), List.of());
                if (videoQuestions.isEmpty()) continue;
                int videoCorrect = 0;
                for (ReviewVideoConnectionQuestion q : videoQuestions) {
                    ReviewVideoConnectionAnswer ans = latestAnswerByStudentAndQuestion.get(studentId + ":" + q.getId());
                    if (ans != null && ans.isCorrect()) videoCorrect++;
                }
                totalQuestions += videoQuestions.size();
                correctCount += videoCorrect;
                double percent = videoCorrect * 100.0 / videoQuestions.size();
                if (percent < video.getCompletionThresholdPercent()) allVideosPassed = false;
            }

            return new ReviewVideoAssignmentStudentStatsResponse.StudentRow(
                    studentId, student.getStudentCode(), student.getUser().getFullName(),
                    viewCount, requiredViewCount, allVideosCompleted,
                    correctCount, totalQuestions, allVideosPassed,
                    null, null, null, null);
        }).filter(java.util.Objects::nonNull).toList();
    }

    /** Bản MỚI NHẤT (answeredAt) mỗi cặp (studentId, questionId), key = "studentId:questionId" — mirror ReviewVideoService#isConnectionVideoPassed nhưng bulk cho CẢ LỚP thay vì 1 học sinh. */
    private Map<String, ReviewVideoConnectionAnswer> latestConnectionAnswerByStudentAndQuestion(List<Long> videoIds) {
        if (videoIds.isEmpty()) return Map.of();
        return reviewVideoConnectionAnswerRepository.findByReviewVideoConnectionQuestion_ReviewVideoIdIn(videoIds).stream()
                .collect(Collectors.toMap(
                        a -> a.getStudent().getId() + ":" + a.getReviewVideoConnectionQuestion().getId(),
                        a -> a,
                        (a, b) -> a.getAnsweredAt().isAfter(b.getAnsweredAt()) ? a : b));
    }

    private ReviewVideoAssignmentQuestionStatsResponse.QuestionRow toQuestionRow(
            ReviewVideoConnectionQuestion question, ReviewVideo video, List<Long> scopedStudentIds,
            Map<Long, Student> studentByStudentId, Map<String, ReviewVideoConnectionAnswer> latestAnswerByStudentAndQuestion) {
        int answered = 0;
        int wrong = 0;
        List<ReviewVideoAssignmentQuestionStatsResponse.WrongStudent> wrongStudents = new java.util.ArrayList<>();
        for (Long studentId : scopedStudentIds) {
            ReviewVideoConnectionAnswer ans = latestAnswerByStudentAndQuestion.get(studentId + ":" + question.getId());
            if (ans == null) continue;
            answered++;
            if (!ans.isCorrect()) {
                wrong++;
                Student student = studentByStudentId.get(studentId);
                if (student != null) {
                    wrongStudents.add(new ReviewVideoAssignmentQuestionStatsResponse.WrongStudent(
                            studentId, student.getStudentCode(), student.getUser().getFullName()));
                }
            }
        }
        BigDecimal wrongRate = answered == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(wrong * 100.0 / answered).setScale(1, RoundingMode.HALF_UP);
        return new ReviewVideoAssignmentQuestionStatsResponse.QuestionRow(
                question.getId(), question.getReviewVideo().getId(), video == null ? "" : video.getTitle(),
                question.getDisplayOrder(), question.getPrompt(), answered, wrong, wrongRate, wrongStudents);
    }

    // ===================== REFLEX =====================

    private List<ReviewVideoAssignmentStudentStatsResponse.StudentRow> buildReflexStudentRows(
            List<Long> scopedStudentIds, Map<Long, ClassEnrollment> enrollmentByStudentId,
            List<ReviewVideo> videos, List<Long> videoIds) {
        Map<String, ReviewVideoProgress> progressByKey = videoIds.isEmpty()
                ? Map.of()
                : reviewVideoProgressRepository.findByReviewVideoIdIn(videoIds).stream()
                    .collect(Collectors.toMap(p -> p.getReviewVideo().getId() + ":" + p.getStudent().getId(), p -> p));

        List<ReviewVideoQuestion> questions = videoIds.stream()
                .flatMap(id -> reviewVideoQuestionRepository.findByReviewVideoIdOrderByDisplayOrder(id).stream())
                .toList();
        List<Long> questionIds = questions.stream().map(ReviewVideoQuestion::getId).toList();
        int totalReflexQuestions = questions.size();

        List<ReviewVideoQuestionSubmission> submissions = questionIds.isEmpty() || scopedStudentIds.isEmpty()
                ? List.of()
                : reviewVideoQuestionSubmissionRepository.findByReviewVideoQuestionIdInAndStudentIdIn(questionIds, scopedStudentIds);
        // Bản MỚI NHẤT (attemptNumber cao nhất) mỗi cặp (câu hỏi, học sinh) — mirror ReviewVideoService#listSubmissionsForTeacher.
        Map<String, ReviewVideoQuestionSubmission> latestByQuestionAndStudent = submissions.stream()
                .collect(Collectors.toMap(
                        s -> s.getReviewVideoQuestion().getId() + ":" + s.getStudent().getId(),
                        s -> s,
                        (a, b) -> a.getAttemptNumber() >= b.getAttemptNumber() ? a : b));

        return scopedStudentIds.stream().map(studentId -> {
            ClassEnrollment enrollment = enrollmentByStudentId.get(studentId);
            if (enrollment == null) return null;
            Student student = enrollment.getStudent();

            int viewCount = 0;
            int requiredViewCount = 0;
            boolean allVideosCompleted = !videos.isEmpty();
            for (ReviewVideo video : videos) {
                ReviewVideoProgress progress = progressByKey.get(video.getId() + ":" + studentId);
                viewCount += progress == null ? 0 : progress.getViewCount();
                requiredViewCount += video.getRequiredViewCount();
                if (progress == null || !progress.isCompleted()) allVideosCompleted = false;
            }

            int answered = 0;
            BigDecimal scoreSum = BigDecimal.ZERO;
            BigDecimal maxScoreSum = BigDecimal.ZERO;
            int gradedCount = 0;
            for (ReviewVideoQuestion q : questions) {
                ReviewVideoQuestionSubmission submission = latestByQuestionAndStudent.get(q.getId() + ":" + studentId);
                if (submission == null) continue;
                answered++;
                if (submission.getScore() != null) {
                    scoreSum = scoreSum.add(submission.getScore());
                    maxScoreSum = maxScoreSum.add(submission.getMaxScore() == null ? BigDecimal.ZERO : submission.getMaxScore());
                    gradedCount++;
                }
            }
            BigDecimal averageScore = gradedCount == 0 ? null : scoreSum.divide(BigDecimal.valueOf(gradedCount), 2, RoundingMode.HALF_UP);
            BigDecimal averageMaxScore = gradedCount == 0 ? null : maxScoreSum.divide(BigDecimal.valueOf(gradedCount), 2, RoundingMode.HALF_UP);

            return new ReviewVideoAssignmentStudentStatsResponse.StudentRow(
                    studentId, student.getStudentCode(), student.getUser().getFullName(),
                    viewCount, requiredViewCount, allVideosCompleted,
                    null, null, null,
                    answered, totalReflexQuestions, averageScore, averageMaxScore);
        }).filter(java.util.Objects::nonNull).toList();
    }

    // ===================== Helpers =====================

    private List<Long> scopedStudentIdsFor(ReviewVideoAssignment assignment) {
        if (assignment.getTargetStudentIds() != null) return assignment.getTargetStudentIds();
        return classEnrollmentRepository.findBySchoolClassIdAndStatus(assignment.getSchoolClass().getId(), ClassEnrollment.Status.ACTIVE)
                .stream().map(e -> e.getStudent().getId()).toList();
    }

    private ReviewVideoAssignment getAssignmentOrThrow(Long assignmentId) {
        return reviewVideoAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lần giao BTVN Video Ôn tập id=" + assignmentId));
    }

    /** Quyền lms.review-video.manage vượt rào — quản trị viên xem báo cáo lớp bất kỳ (mirror ReviewVideoService#requireAssignedTeacher). */
    private void requireAssignedTeacher(Long classId, Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, PERM_REVIEW_VIDEO_MANAGE)) {
            return;
        }
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "Tài khoản id=" + actorUserId + " không được phân công giảng dạy lớp id=" + classId + ".");
        }
    }
}
