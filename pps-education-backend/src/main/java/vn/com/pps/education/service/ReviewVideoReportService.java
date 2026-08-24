package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ReflexQuestionProgress;
import vn.com.pps.education.domain.ReviewVideo;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.ReviewVideoConnectionAnswer;
import vn.com.pps.education.domain.ReviewVideoConnectionQuestion;
import vn.com.pps.education.domain.ReviewVideoProgress;
import vn.com.pps.education.domain.ReviewVideoQuestion;
import vn.com.pps.education.domain.ReviewVideoSet;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.dto.ReviewVideoAssignmentQuestionStatsResponse;
import vn.com.pps.education.dto.ReviewVideoAssignmentStatsResponse;
import vn.com.pps.education.dto.ReviewVideoAssignmentStudentStatsResponse;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.repository.ReviewVideoAssignmentRepository;
import vn.com.pps.education.repository.ReviewVideoConnectionAnswerRepository;
import vn.com.pps.education.repository.ReviewVideoConnectionQuestionRepository;
import vn.com.pps.education.repository.ReviewVideoProgressRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionRepository;
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
 * có sẵn dữ liệu đúng/sai thật.
 *
 * V145 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — REFLEX từ V139 đã chuyển
 * hẳn sang {@link ReflexSequentialGradingService} (AI tự chấm viết+nói tuần tự, KHÔNG còn chấm tay
 * bằng audio như ghi chú cũ ở đây) — bảng tổng hợp giờ đọc {@code reflex_question_progress} thay vì
 * {@code ReviewVideoQuestionSubmission} (bảng của luồng cũ, không còn dữ liệu mới). "Hoàn thành" của
 * REFLEX cũng đổi sang tiêu chí "mọi câu đã đạt cả 2 bước" thay vì % thời lượng đã xem (không còn
 * được cập nhật bởi luồng phát video mới).
 */
@Service
public class ReviewVideoReportService {

    private final ReviewVideoAssignmentRepository reviewVideoAssignmentRepository;
    private final ReviewVideoService reviewVideoService;
    private final ReviewVideoRepository reviewVideoRepository;
    private final ReviewVideoProgressRepository reviewVideoProgressRepository;
    private final ReviewVideoQuestionRepository reviewVideoQuestionRepository;
    private final ReflexQuestionProgressRepository reflexQuestionProgressRepository;
    private final ReviewVideoConnectionQuestionRepository reviewVideoConnectionQuestionRepository;
    private final ReviewVideoConnectionAnswerRepository reviewVideoConnectionAnswerRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    private static final String PERM_REVIEW_VIDEO_MANAGE = "lms.review-video.manage";

    /** V145 — phải khớp ReflexSequentialGradingService.PASS_THRESHOLD_PERCENT (private ở đó, không expose được). */
    private static final int REFLEX_PASS_THRESHOLD_PERCENT = 70;

    private boolean isReflexQuestionPassed(ReflexQuestionProgress p) {
        return p.getWritingScore() != null && p.getWritingScore().compareTo(BigDecimal.valueOf(REFLEX_PASS_THRESHOLD_PERCENT)) >= 0
                && p.getSpeakingScore() != null && p.getSpeakingScore().compareTo(BigDecimal.valueOf(REFLEX_PASS_THRESHOLD_PERCENT)) >= 0;
    }

    public ReviewVideoReportService(ReviewVideoAssignmentRepository reviewVideoAssignmentRepository,
                                     ReviewVideoService reviewVideoService,
                                     ReviewVideoRepository reviewVideoRepository,
                                     ReviewVideoProgressRepository reviewVideoProgressRepository,
                                     ReviewVideoQuestionRepository reviewVideoQuestionRepository,
                                     ReflexQuestionProgressRepository reflexQuestionProgressRepository,
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
        this.reflexQuestionProgressRepository = reflexQuestionProgressRepository;
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
                ? buildConnectionStudentRows(scopedStudentIds, enrollmentByStudentId, videos, videoIds, assignmentId)
                : buildReflexStudentRows(scopedStudentIds, enrollmentByStudentId, videos, videoIds, assignmentId);

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
            List<ReviewVideo> videos, List<Long> videoIds, Long assignmentId) {
        // V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — lọc đúng lần giao đang
        // xem báo cáo, tránh trộn tiến độ của lần giao KHÁC cùng bộ+lớp vào chung báo cáo này.
        Map<String, ReviewVideoProgress> progressByKey = videoIds.isEmpty()
                ? Map.of()
                : reviewVideoProgressRepository.findByReviewVideoIdInAndReviewVideoAssignmentId(videoIds, assignmentId).stream()
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
            List<ReviewVideo> videos, List<Long> videoIds, Long assignmentId) {
        Map<String, ReviewVideoProgress> progressByKey = videoIds.isEmpty()
                ? Map.of()
                : reviewVideoProgressRepository.findByReviewVideoIdInAndReviewVideoAssignmentId(videoIds, assignmentId).stream()
                    .collect(Collectors.toMap(p -> p.getReviewVideo().getId() + ":" + p.getStudent().getId(), p -> p));

        List<ReviewVideoQuestion> questions = videoIds.stream()
                .flatMap(id -> reviewVideoQuestionRepository.findByReviewVideoIdOrderByDisplayOrder(id).stream())
                .toList();
        int totalReflexQuestions = questions.size();

        // V145 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — fix bug thật: dòng này
        // vẫn đọc ReviewVideoQuestionSubmission (bảng của luồng CŨ "ghi âm theo mốc, nộp cả loạt cuối
        // video, GV chấm tay") — REFLEX từ V139 đã chuyển hẳn sang ReflexSequentialGradingService/bảng
        // reflex_question_progress, không còn tạo submission kiểu cũ nữa nên báo cáo GV luôn hiện "0/N,
        // chưa hoàn thành" dù học sinh đã làm/đạt hết qua luồng mới (mirror đúng bug đã sửa ở FE Portal
        // — AssignmentsTab.tsx — cho badge "Đã nộp X/Y câu"). Đổi sang đọc reflex_question_progress.
        List<ReflexQuestionProgress> progresses = scopedStudentIds.isEmpty()
                ? List.of()
                : reflexQuestionProgressRepository.findByReviewVideoAssignmentIdAndStudentIdIn(assignmentId, scopedStudentIds);
        Map<String, ReflexQuestionProgress> progressByQuestionAndStudent = progresses.stream()
                .collect(Collectors.toMap(
                        p -> p.getReviewVideoQuestion().getId() + ":" + p.getStudent().getId(),
                        p -> p,
                        (a, b) -> a));
        // V145 — "HOÀN THÀNH" của REFLEX cũng bị ảnh hưởng bởi cùng bug: dựa vào ReviewVideoProgress
        // (xem/nghe hết % thời lượng), NHƯNG luồng phát video REFLEX mới (ReflexVideoTaskPage.tsx,
        // "nhảy thẳng theo mốc câu hỏi") KHÔNG CÒN gọi API cập nhật ReviewVideoProgress nữa — cột này
        // sẽ mãi mãi 0/CHƯA HOÀN THÀNH bất kể học sinh làm gì. Đổi định nghĩa "hoàn thành" cho REFLEX
        // sang đúng tiêu chí thực tế UC-23b V2 đang dùng ở FE (ReflexVideoTaskPage.tsx#allQuestionsPassed)
        // — MỌI câu hỏi của video đó đã questionPassed=true (đạt cả viết lẫn nói).
        Map<Long, List<ReviewVideoQuestion>> questionsByVideoId = questions.stream()
                .collect(Collectors.groupingBy(q -> q.getReviewVideo().getId()));

        return scopedStudentIds.stream().map(studentId -> {
            ClassEnrollment enrollment = enrollmentByStudentId.get(studentId);
            if (enrollment == null) return null;
            Student student = enrollment.getStudent();

            int viewCount = 0;
            int requiredViewCount = 0;
            for (ReviewVideo video : videos) {
                ReviewVideoProgress progress = progressByKey.get(video.getId() + ":" + studentId);
                viewCount += progress == null ? 0 : progress.getViewCount();
                requiredViewCount += video.getRequiredViewCount();
            }
            boolean allVideosCompleted = !videos.isEmpty() && videos.stream().allMatch(video -> {
                List<ReviewVideoQuestion> videoQuestions = questionsByVideoId.getOrDefault(video.getId(), List.of());
                return !videoQuestions.isEmpty() && videoQuestions.stream().allMatch(q -> {
                    ReflexQuestionProgress p = progressByQuestionAndStudent.get(q.getId() + ":" + studentId);
                    return p != null && isReflexQuestionPassed(p);
                });
            });

            int answered = 0;
            BigDecimal scoreSum = BigDecimal.ZERO;
            BigDecimal maxScoreSum = BigDecimal.ZERO;
            int gradedCount = 0;
            for (ReviewVideoQuestion q : questions) {
                ReflexQuestionProgress progress = progressByQuestionAndStudent.get(q.getId() + ":" + studentId);
                if (progress == null) continue;
                answered++;
                // Viết và nói được AI chấm ĐỘC LẬP (2 lượt riêng, xem ReflexSequentialGradingService) —
                // gộp cả 2 vào chung 1 tổng "điểm TB" theo % thay vì chỉ 1 điểm/câu như luồng cũ.
                if (progress.getWritingScore() != null) {
                    scoreSum = scoreSum.add(progress.getWritingScore());
                    maxScoreSum = maxScoreSum.add(progress.getWritingMaxScore() == null ? BigDecimal.ZERO : progress.getWritingMaxScore());
                    gradedCount++;
                }
                if (progress.getSpeakingScore() != null) {
                    scoreSum = scoreSum.add(progress.getSpeakingScore());
                    maxScoreSum = maxScoreSum.add(progress.getSpeakingMaxScore() == null ? BigDecimal.ZERO : progress.getSpeakingMaxScore());
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
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideoReport.assignmentNotFound",
                        new Object[]{assignmentId}, "Không tìm thấy lần giao BTVN Video Ôn tập id=" + assignmentId));
    }

    /** Quyền lms.review-video.manage vượt rào — quản trị viên xem báo cáo lớp bất kỳ (mirror ReviewVideoService#requireAssignedTeacher). */
    private void requireAssignedTeacher(Long classId, Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, PERM_REVIEW_VIDEO_MANAGE)) {
            return;
        }
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "error.notAssignedTeacherForClass.default", new Object[]{}, "Bạn không được phân công giảng dạy lớp này.");
        }
    }
}
