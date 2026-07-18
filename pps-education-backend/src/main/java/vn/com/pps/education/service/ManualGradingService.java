package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ExerciseAttempt;
import vn.com.pps.education.domain.ExerciseAttemptHistory;
import vn.com.pps.education.domain.StudentAnswer;
import vn.com.pps.education.domain.StudentAnswerGrading;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.GradeAnswerRequest;
import vn.com.pps.education.dto.PendingGradingResponse;
import vn.com.pps.education.dto.StudentAnswerGradingResponse;
import vn.com.pps.education.exception.AnswerNotManuallyGradableException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ExerciseAttemptHistoryRepository;
import vn.com.pps.education.repository.ExerciseAttemptRepository;
import vn.com.pps.education.repository.StudentAnswerGradingRepository;
import vn.com.pps.education.repository.StudentAnswerRepository;
import vn.com.pps.education.repository.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-41: Chấm bài thủ công (FR-LMS-11). Xem docs/uc/phan-he-07-lms-portal.md.
 * Chấm từng câu tự luận/Nói của {@link StudentAnswer} do
 * {@link ExerciseAttemptService} chuyển sang chờ chấm (autoGradable=false).
 * Điểm tổng kết (total_score) chỉ tính xong khi TẤT CẢ câu trong đề đã có
 * điểm (tự động hoặc thủ công) — Main Flow bước 3, 5.
 *
 * Authorization qua @PreAuthorize("hasPermission(null,'lms.grading.manage')")
 * ở ManualGradingController (Hybrid PBAC — V28), không còn role-check trong Service.
 */
@Service
public class ManualGradingService {

    private final StudentAnswerRepository studentAnswerRepository;
    private final StudentAnswerGradingRepository studentAnswerGradingRepository;
    private final ExerciseAttemptRepository exerciseAttemptRepository;
    private final ExerciseAttemptHistoryRepository exerciseAttemptHistoryRepository;
    private final UserRepository userRepository;

    public ManualGradingService(StudentAnswerRepository studentAnswerRepository,
                                 StudentAnswerGradingRepository studentAnswerGradingRepository,
                                 ExerciseAttemptRepository exerciseAttemptRepository,
                                 ExerciseAttemptHistoryRepository exerciseAttemptHistoryRepository,
                                 UserRepository userRepository) {
        this.studentAnswerRepository = studentAnswerRepository;
        this.studentAnswerGradingRepository = studentAnswerGradingRepository;
        this.exerciseAttemptRepository = exerciseAttemptRepository;
        this.exerciseAttemptHistoryRepository = exerciseAttemptHistoryRepository;
        this.userRepository = userRepository;
    }

    /** Main Flow bước 1, A1 (nhiều bài chờ chấm): danh sách câu tự luận/Nói đã nộp, chưa chấm. */
    @Transactional(readOnly = true)
    public List<PendingGradingResponse> listPendingGrading(Long actorUserId) {
        return studentAnswerRepository.findPendingManualGrading().stream().map(this::toPendingResponse).toList();
    }

    /** Main Flow bước 2-5: chấm 1 câu, cập nhật điểm hiển thị ngay cho HS; tự tính total_score khi đã chấm hết. */
    @Transactional
    public StudentAnswerGradingResponse gradeAnswer(Long studentAnswerId, GradeAnswerRequest request, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);
        StudentAnswer answer = studentAnswerRepository.findById(studentAnswerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy câu trả lời id=" + studentAnswerId));
        if (answer.isAutoGradable()) {
            throw new AnswerNotManuallyGradableException(
                    "Câu trả lời id=" + studentAnswerId + " thuộc câu hỏi tự chấm được — không chấm thủ công.");
        }

        // saveAndFlush bắt buộc — nếu chỉ save(), Hibernate có thể flush INSERT bản ghi
        // mới trước UPDATE bản cũ (thứ tự flush mặc định: insert trước update), vi phạm
        // tạm thời unique partial index idx_student_answer_grading_current (is_final=TRUE).
        studentAnswerGradingRepository.findByStudentAnswerIdAndLatestIsTrue(studentAnswerId).ifPresent(previous -> {
            previous.setLatest(false);
            studentAnswerGradingRepository.saveAndFlush(previous);
        });

        StudentAnswerGrading grading = new StudentAnswerGrading();
        grading.setStudentAnswer(answer);
        grading.setGrader(actor);
        grading.setScore(request.score());
        grading.setMaxScore(request.maxScore());
        grading.setFeedback(request.feedback());
        grading.setGradedAt(OffsetDateTime.now());
        grading.setLatest(true);
        grading = studentAnswerGradingRepository.save(grading);

        recomputeAttemptTotals(answer.getExerciseAttempt(), actor);
        return toResponse(grading);
    }

    // ===================== Helpers =====================

    private void recomputeAttemptTotals(ExerciseAttempt attempt, User actor) {
        List<StudentAnswer> answers = studentAnswerRepository.findByExerciseAttemptId(attempt.getId());
        BigDecimal manualGradeScore = BigDecimal.ZERO;
        boolean allGraded = true;
        for (StudentAnswer answer : answers) {
            if (answer.isAutoGradable()) {
                continue;
            }
            var latest = studentAnswerGradingRepository.findByStudentAnswerIdAndLatestIsTrue(answer.getId());
            if (latest.isEmpty()) {
                allGraded = false;
                continue;
            }
            manualGradeScore = manualGradeScore.add(latest.get().getScore());
        }

        attempt.setManualGradeScore(manualGradeScore);
        if (allGraded) {
            BigDecimal autoGradeScore = attempt.getAutoGradeScore() == null ? BigDecimal.ZERO : attempt.getAutoGradeScore();
            attempt.setTotalScore(autoGradeScore.add(manualGradeScore));
            attempt.setStatus(ExerciseAttempt.Status.FULLY_GRADED);
        }
        exerciseAttemptRepository.save(attempt);

        ExerciseAttemptHistory history = new ExerciseAttemptHistory();
        history.setExerciseAttempt(attempt);
        history.setChangedBy(actor);
        history.setAction(ExerciseAttemptHistory.Action.UPDATED);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("manualGradeScore", manualGradeScore);
        snapshot.put("status", attempt.getStatus().name());
        history.setDetails(snapshot);
        exerciseAttemptHistoryRepository.save(history);
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private PendingGradingResponse toPendingResponse(StudentAnswer a) {
        ExerciseAttempt attempt = a.getExerciseAttempt();
        return new PendingGradingResponse(
                a.getId(), attempt.getId(), attempt.getExercise().getId(), attempt.getExercise().getTitle(),
                attempt.getStudent().getId(), attempt.getStudent().getUser().getFullName(),
                a.getQuestion().getId(), a.getQuestion().getQuestionType().name(), a.getQuestion().getContent(),
                a.getAnswerText(), a.getAudioAnswerUrl());
    }

    private StudentAnswerGradingResponse toResponse(StudentAnswerGrading g) {
        return new StudentAnswerGradingResponse(
                g.getId(), g.getStudentAnswer().getId(), g.getGrader().getId(), g.getScore(), g.getMaxScore(),
                g.getFeedback(), g.getGradedAt());
    }
}
