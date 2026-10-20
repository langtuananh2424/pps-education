package vn.com.pps.education.lms.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.lms.domain.ExerciseAssignment;
import vn.com.pps.education.lms.domain.ExerciseAttempt;
import vn.com.pps.education.lms.domain.StudentAnswer;
import vn.com.pps.education.lms.domain.StudentAnswerGrading;
import vn.com.pps.education.lms.dto.EssayGradingHistoryResponse;
import vn.com.pps.education.lms.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.lms.repository.ExerciseAttemptRepository;
import vn.com.pps.education.lms.repository.ExerciseQuestionRepository;
import vn.com.pps.education.lms.repository.StudentAnswerGradingRepository;
import vn.com.pps.education.lms.repository.StudentAnswerRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Popup "Lịch sử chấm AI" cho câu ESSAY của Bài tập Writing ở trang Thống kê BTVN theo lớp (bổ sung ngoài SDD gốc,
 * xác nhận 2026-10-20; UC-41 mở rộng, xem docs/uc/phan-he-07-lms-portal.md mục V216). Chỉ đọc: giáo viên chấm lại bằng
 * {@link ManualGradingService#gradeAnswer} (bản AI chuyển is_final=false, điểm giáo viên là điểm chính thức).
 * Chỉ trả câu trả lời từng được AI chấm; phạm vi như {@link AiSecondGradingService#requireAssignedTeacher}.
 */
@Service
public class EssayGradingHistoryService {

    private final ExerciseAssignmentRepository exerciseAssignmentRepository;
    private final ExerciseAttemptRepository exerciseAttemptRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final StudentAnswerGradingRepository studentAnswerGradingRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final AiSecondGradingService aiSecondGradingService;

    public EssayGradingHistoryService(ExerciseAssignmentRepository exerciseAssignmentRepository,
                                      ExerciseAttemptRepository exerciseAttemptRepository,
                                      StudentAnswerRepository studentAnswerRepository,
                                      StudentAnswerGradingRepository studentAnswerGradingRepository,
                                      ExerciseQuestionRepository exerciseQuestionRepository,
                                      AiSecondGradingService aiSecondGradingService) {
        this.exerciseAssignmentRepository = exerciseAssignmentRepository;
        this.exerciseAttemptRepository = exerciseAttemptRepository;
        this.studentAnswerRepository = studentAnswerRepository;
        this.studentAnswerGradingRepository = studentAnswerGradingRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.aiSecondGradingService = aiSecondGradingService;
    }

    @Transactional(readOnly = true)
    public List<EssayGradingHistoryResponse> getStudentEssayHistory(Long assignmentId, Long studentId, Long actorUserId) {
        ExerciseAssignment assignment = exerciseAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseReport.assignmentNotFound",
                        new Object[]{assignmentId}, "Không tìm thấy bản giao BTVN id=" + assignmentId));
        aiSecondGradingService.requireAssignedTeacher(assignment.getSchoolClass().getId(), actorUserId);

        List<ExerciseAttempt> attempts = exerciseAttemptRepository
                .findByExerciseAssignmentIdAndStudentIdOrderByAttemptNumberDesc(assignmentId, studentId);
        if (attempts.isEmpty()) {
            return List.of();
        }
        Map<Long, ExerciseAttempt> attemptById = attempts.stream().collect(Collectors.toMap(ExerciseAttempt::getId, a -> a));
        List<StudentAnswer> essayAnswers = studentAnswerRepository.findByExerciseAttemptIdIn(List.copyOf(attemptById.keySet())).stream()
                .filter(a -> !a.isAutoGradable())
                .toList();
        if (essayAnswers.isEmpty()) {
            return List.of();
        }
        Map<Long, List<StudentAnswerGrading>> gradingsByAnswer = studentAnswerGradingRepository
                .findHistoryByStudentAnswerIds(essayAnswers.stream().map(StudentAnswer::getId).toList()).stream()
                .collect(Collectors.groupingBy(g -> g.getStudentAnswer().getId()));
        Map<Long, Integer> displayOrderByQuestion = new HashMap<>();
        exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(assignment.getExercise().getId())
                .forEach(eq -> displayOrderByQuestion.put(eq.getQuestion().getId(), eq.getDisplayOrder()));

        return essayAnswers.stream()
                .filter(a -> gradingsByAnswer.getOrDefault(a.getId(), List.of()).stream()
                        .anyMatch(g -> g.getGradingSource() == StudentAnswerGrading.GradingSource.AI))
                .map(a -> toResponse(a, attemptById.get(a.getExerciseAttempt().getId()),
                        displayOrderByQuestion.getOrDefault(a.getQuestion().getId(), 0), gradingsByAnswer.get(a.getId())))
                .sorted(Comparator.comparingInt(EssayGradingHistoryResponse::questionDisplayOrder)
                        .thenComparing(Comparator.comparingInt(EssayGradingHistoryResponse::attemptNumber).reversed()))
                .toList();
    }

    private EssayGradingHistoryResponse toResponse(StudentAnswer answer, ExerciseAttempt attempt, int displayOrder,
                                                   List<StudentAnswerGrading> gradings) {
        List<EssayGradingHistoryResponse.Version> versions = gradings.stream()
                .map(g -> new EssayGradingHistoryResponse.Version(g.getId(), g.getScore(), g.getMaxScore(), g.getFeedback(),
                        g.getMarkedAnswer(), g.getCriteriaScores(), g.getGradedAt(), g.getGradingSource().name(),
                        g.getGrader().getFullName(), g.isLatest()))
                .toList();
        return new EssayGradingHistoryResponse(answer.getId(), attempt.getId(), attempt.getAttemptNumber(), attempt.getSubmittedAt(),
                answer.getQuestion().getId(), displayOrder, answer.getQuestion().getContent(), answer.getAnswerText(), versions);
    }
}
