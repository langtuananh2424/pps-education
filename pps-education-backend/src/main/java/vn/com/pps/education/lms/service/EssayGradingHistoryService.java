package vn.com.pps.education.lms.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.common.ExcelExportHelper;
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

import java.util.ArrayList;
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

    /**
     * Xuất Excel bài viết + nhận xét của AI + điểm/nhận xét giáo viên chấm lại cho mọi câu ESSAY từng được AI chấm của
     * 1 bản giao BTVN (mọi học sinh, mọi lượt). Mỗi dòng = 1 câu trả lời; "điểm chính thức" là bản hiện hành
     * (is_final), cột AI/giáo viên là bản mới nhất của từng nguồn. Phạm vi như {@link #getStudentEssayHistory}.
     */
    @Transactional(readOnly = true)
    public byte[] exportAssignmentEssays(Long assignmentId, Long actorUserId) {
        ExerciseAssignment assignment = exerciseAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseReport.assignmentNotFound",
                        new Object[]{assignmentId}, "Không tìm thấy bản giao BTVN id=" + assignmentId));
        aiSecondGradingService.requireAssignedTeacher(assignment.getSchoolClass().getId(), actorUserId);

        List<String> headers = List.of("Mã học sinh", "Họ tên", "Lượt làm", "Nộp lúc", "Câu số", "Đề bài", "Bài làm",
                "Điểm chính thức", "Điểm tối đa", "Nguồn điểm chính thức", "Điểm AI", "Nhận xét AI", "Điểm theo tiêu chí (AI)",
                "Điểm giáo viên", "Nhận xét giáo viên", "Giáo viên chấm", "Giáo viên chấm lúc");
        List<List<Object>> rows = new ArrayList<>();

        List<ExerciseAttempt> attempts = exerciseAttemptRepository.findByExerciseAssignmentIdOrderByAttemptNumberAsc(assignmentId);
        if (!attempts.isEmpty()) {
            Map<Long, ExerciseAttempt> attemptById = attempts.stream().collect(Collectors.toMap(ExerciseAttempt::getId, a -> a));
            List<StudentAnswer> essayAnswers = studentAnswerRepository.findByExerciseAttemptIdIn(List.copyOf(attemptById.keySet())).stream()
                    .filter(a -> !a.isAutoGradable())
                    .toList();
            Map<Long, List<StudentAnswerGrading>> gradingsByAnswer = essayAnswers.isEmpty() ? Map.of()
                    : studentAnswerGradingRepository.findHistoryByStudentAnswerIds(essayAnswers.stream().map(StudentAnswer::getId).toList()).stream()
                        .collect(Collectors.groupingBy(g -> g.getStudentAnswer().getId()));
            Map<Long, Integer> displayOrderByQuestion = new HashMap<>();
            exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(assignment.getExercise().getId())
                    .forEach(eq -> displayOrderByQuestion.put(eq.getQuestion().getId(), eq.getDisplayOrder()));

            essayAnswers.stream()
                    .filter(a -> gradingsByAnswer.getOrDefault(a.getId(), List.of()).stream()
                            .anyMatch(g -> g.getGradingSource() == StudentAnswerGrading.GradingSource.AI))
                    .sorted(Comparator.<StudentAnswer, String>comparing(a -> attemptById.get(a.getExerciseAttempt().getId()).getStudent().getStudentCode())
                            .thenComparingInt(a -> attemptById.get(a.getExerciseAttempt().getId()).getAttemptNumber())
                            .thenComparingInt(a -> displayOrderByQuestion.getOrDefault(a.getQuestion().getId(), 0)))
                    .forEach(a -> rows.add(toExportRow(a, attemptById.get(a.getExerciseAttempt().getId()),
                            displayOrderByQuestion.getOrDefault(a.getQuestion().getId(), 0), gradingsByAnswer.get(a.getId()))));
        }

        List<String> notes = List.of(
                "Bài: " + assignment.getExercise().getTitle() + " (" + assignment.getExercise().getCode() + ")",
                "Mỗi dòng là 1 câu tự luận (ESSAY) từng được AI chấm. Điểm chính thức là bản chấm hiện hành; "
                        + "cột AI và giáo viên là bản chấm mới nhất của từng nguồn (trống = chưa có).");
        return ExcelExportHelper.buildWorkbook("Bài viết và nhận xét", headers, rows, notes);
    }

    private List<Object> toExportRow(StudentAnswer answer, ExerciseAttempt attempt, int displayOrder,
                                     List<StudentAnswerGrading> gradings) {
        Comparator<StudentAnswerGrading> byTime = Comparator.comparing(StudentAnswerGrading::getGradedAt,
                Comparator.nullsFirst(Comparator.naturalOrder()));
        StudentAnswerGrading official = gradings.stream().filter(StudentAnswerGrading::isLatest).findFirst().orElse(null);
        StudentAnswerGrading ai = gradings.stream()
                .filter(g -> g.getGradingSource() == StudentAnswerGrading.GradingSource.AI).max(byTime).orElse(null);
        StudentAnswerGrading human = gradings.stream()
                .filter(g -> g.getGradingSource() == StudentAnswerGrading.GradingSource.HUMAN).max(byTime).orElse(null);
        List<CriteriaScoreItem> criteria = ai == null ? null : ai.getCriteriaScores();
        return List.<Object>of(
                attempt.getStudent().getStudentCode(),
                attempt.getStudent().getUser().getFullName(),
                attempt.getAttemptNumber(),
                attempt.getSubmittedAt() == null ? "" : attempt.getSubmittedAt().toString(),
                displayOrder,
                nz(answer.getQuestion().getContent()),
                nz(answer.getAnswerText()),
                official == null || official.getScore() == null ? "" : official.getScore(),
                official == null || official.getMaxScore() == null ? "" : official.getMaxScore(),
                official == null ? "" : (official.getGradingSource() == StudentAnswerGrading.GradingSource.AI ? "AI" : "Giáo viên"),
                ai == null || ai.getScore() == null ? "" : ai.getScore(),
                ai == null ? "" : nz(ai.getFeedback()),
                criteria == null || criteria.isEmpty() ? ""
                        : criteria.stream().map(c -> c.criterion() + ": " + c.percent() + "%").collect(Collectors.joining("; ")),
                human == null || human.getScore() == null ? "" : human.getScore(),
                human == null ? "" : nz(human.getFeedback()),
                human == null ? "" : human.getGrader().getFullName(),
                human == null || human.getGradedAt() == null ? "" : human.getGradedAt().toString());
    }

    private static String nz(String value) {
        return value == null ? "" : value;
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
