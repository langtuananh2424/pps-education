package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Exercise;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.ExerciseAttempt;
import vn.com.pps.education.domain.ExerciseAttemptHistory;
import vn.com.pps.education.domain.ExerciseQuestion;
import vn.com.pps.education.domain.Question;
import vn.com.pps.education.domain.QuestionChoice;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentAnswer;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AssignedExerciseResponse;
import vn.com.pps.education.dto.ExerciseAttemptResponse;
import vn.com.pps.education.dto.SaveAnswerRequest;
import vn.com.pps.education.dto.StudentAnswerResponse;
import vn.com.pps.education.exception.AttemptNotEditableException;
import vn.com.pps.education.exception.ExerciseNotAvailableException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RetakeNotAllowedException;
import vn.com.pps.education.exception.SubmissionPastDeadlineException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.repository.ExerciseAttemptHistoryRepository;
import vn.com.pps.education.repository.ExerciseAttemptRepository;
import vn.com.pps.education.repository.ExerciseQuestionRepository;
import vn.com.pps.education.repository.ExerciseRepository;
import vn.com.pps.education.repository.QuestionChoiceRepository;
import vn.com.pps.education.repository.StudentAnswerRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-24: Làm bài kiểm tra trực tuyến (FR-LMS-02, ASSIGNED, có deadline) +
 * UC-27: Làm bài tập/đề ôn tập (FR-LMS-06, SELF_PRACTICE, mở tự do). Xem
 * docs/uc/phan-he-07-lms-portal.md. Gộp 2 UC vào 1 Service vì cùng 1 cơ
 * chế làm bài/nộp bài/tự chấm trắc nghiệm — chỉ khác ở việc có
 * exercise_assignment (deadline/nộp muộn) hay không (xem
 * .claude/rules/solid.md — không tách khi cùng 1 nghiệp vụ lõi).
 *
 * Auto-gradable = MULTIPLE_CHOICE/MULTIPLE_ANSWER/TRUE_FALSE (so khớp
 * question_choices.is_correct) và FILL_IN_BLANK (so khớp CHÍNH XÁC,
 * case-insensitive + trim, với questions.correct_answer_text — V54, bổ
 * sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-27).
 * ESSAY/SPEAKING KHÔNG tự chấm được vì SDD không có cột đáp án tham
 * khảo dạng chấm được cho 2 loại này — luôn chờ Giáo viên chấm thủ công
 * (UC-41).
 *
 * GAP đã biết (không tự bịa hướng xử lý — xem .claude/rules/business-fidelity.md):
 * UC-24 Postcondition "kết quả cuối cùng được đồng bộ vào sổ điểm" nhưng
 * SDD không có cột nào liên kết exercise_attempts ↔ grade_entries/
 * grade_components — KHÔNG tự tạo liên kết. V93 (2026-08-06) đã thêm
 * {@code exercise_attempts.selected_for_grading} để Giáo viên đánh dấu 1
 * lượt làm CHÍNH THỨC khi có nhiều lượt (xem {@link #selectForGrading}) —
 * nhưng CHỈ dừng ở đánh dấu, CHƯA ghi tự động vào grade_entries vì còn
 * thiếu quy tắc ánh xạ Bài tập → đầu điểm (gradeComponent) nào, cần xác
 * nhận thêm với người dùng trước khi làm tiếp phần đó. UC-27 A1 "đề tự
 * chấm hoàn toàn không cần chờ GV" cũng không có cột cấu hình tương ứng
 * trong SDD — hành vi mặc định (FILL_IN_BLANK/ESSAY/SPEAKING luôn chờ GV)
 * áp dụng cho mọi trường hợp.
 */
@Service
public class ExerciseAttemptService {

    private final ExerciseAttemptRepository exerciseAttemptRepository;
    private final ExerciseAttemptHistoryRepository exerciseAttemptHistoryRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final ExerciseAssignmentRepository exerciseAssignmentRepository;
    private final QuestionChoiceRepository questionChoiceRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    private static final Set<Question.QuestionType> AUTO_GRADABLE_TYPES = Set.of(
            Question.QuestionType.MULTIPLE_CHOICE, Question.QuestionType.MULTIPLE_ANSWER, Question.QuestionType.TRUE_FALSE,
            Question.QuestionType.FILL_IN_BLANK, Question.QuestionType.WORD_BANK, Question.QuestionType.SENTENCE_BUILDING);

    public ExerciseAttemptService(ExerciseAttemptRepository exerciseAttemptRepository,
                                   ExerciseAttemptHistoryRepository exerciseAttemptHistoryRepository,
                                   StudentAnswerRepository studentAnswerRepository,
                                   ExerciseRepository exerciseRepository,
                                   ExerciseQuestionRepository exerciseQuestionRepository,
                                   ExerciseAssignmentRepository exerciseAssignmentRepository,
                                   QuestionChoiceRepository questionChoiceRepository,
                                   ClassEnrollmentRepository classEnrollmentRepository,
                                   StudentRepository studentRepository,
                                   UserRepository userRepository) {
        this.exerciseAttemptRepository = exerciseAttemptRepository;
        this.exerciseAttemptHistoryRepository = exerciseAttemptHistoryRepository;
        this.studentAnswerRepository = studentAnswerRepository;
        this.exerciseRepository = exerciseRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.exerciseAssignmentRepository = exerciseAssignmentRepository;
        this.questionChoiceRepository = questionChoiceRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
    }

    /** Main Flow bước 1, A2 (retake): mở đề, tạo 1 lượt làm bài mới. */
    @Transactional
    public ExerciseAttemptResponse startAttempt(Long exerciseId, Long actorUserId) {
        Student student = studentOrThrow(actorUserId);
        Exercise exercise = exerciseOrThrow(exerciseId);
        if (exercise.getStatus() != Exercise.Status.PUBLISHED) {
            throw new ExerciseNotAvailableException("Đề id=" + exerciseId + " chưa được publish.");
        }

        // Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
        // bỏ nhánh rẽ theo exerciseType — MỌI loại đề (kể cả SELF_PRACTICE/
        // MOCK_TEST/SKILL_PRACTICE) giờ đều cần ExerciseAssignment ACTIVE, không
        // còn "mở tự do sau khi Publish" (mirror ExerciseService#requireCanViewExercise).
        ExerciseAssignment assignment = findActiveAssignmentForStudent(exercise, student)
                .orElseThrow(() -> new ExerciseNotAvailableException(
                        "Đề id=" + exerciseId + " chưa được giao cho học sinh id=" + student.getId() + "."));
        if (assignment.getAvailableFrom().isAfter(OffsetDateTime.now())) {
            throw new ExerciseNotAvailableException("Đề id=" + exerciseId + " chưa tới thời gian mở làm bài.");
        }

        long previousAttempts = exerciseAttemptRepository.countByExerciseIdAndStudentId(exerciseId, student.getId());
        int attemptNumber = (int) previousAttempts + 1;
        if (previousAttempts > 0) {
            if (!exercise.isAllowRetake()) {
                throw new RetakeNotAllowedException("Đề id=" + exerciseId + " không cho phép làm lại.");
            }
            if (exercise.getMaxAttempts() != null && attemptNumber > exercise.getMaxAttempts()) {
                throw new RetakeNotAllowedException("Đề id=" + exerciseId + " đã hết lượt làm (tối đa " + exercise.getMaxAttempts() + ").");
            }
        }

        ExerciseAttempt attempt = new ExerciseAttempt();
        attempt.setExercise(exercise);
        attempt.setExerciseAssignment(assignment);
        attempt.setStudent(student);
        attempt.setAttemptNumber(attemptNumber);
        attempt = exerciseAttemptRepository.save(attempt);

        writeHistory(attempt, actorUserId, ExerciseAttemptHistory.Action.CREATED);
        return toResponse(attempt);
    }

    /** Main Flow bước 2: ghi/ghi đè câu trả lời khi đang làm bài (IN_PROGRESS). */
    @Transactional
    public StudentAnswerResponse saveAnswer(Long attemptId, SaveAnswerRequest request, Long actorUserId) {
        ExerciseAttempt attempt = attemptOwnedByActor(attemptId, actorUserId);
        if (attempt.getStatus() != ExerciseAttempt.Status.IN_PROGRESS) {
            throw new AttemptNotEditableException("Lượt làm bài id=" + attemptId + " không còn ở trạng thái IN_PROGRESS.");
        }
        if (!exerciseQuestionRepository.existsByExerciseIdAndQuestionId(attempt.getExercise().getId(), request.questionId())) {
            throw new ResourceNotFoundException("Câu hỏi id=" + request.questionId() + " không thuộc đề id=" + attempt.getExercise().getId());
        }

        StudentAnswer answer = studentAnswerRepository.findByExerciseAttemptIdAndQuestionId(attemptId, request.questionId())
                .orElseGet(StudentAnswer::new);
        answer.setExerciseAttempt(attempt);
        Question question = exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(attempt.getExercise().getId()).stream()
                .map(ExerciseQuestion::getQuestion)
                .filter(q -> q.getId().equals(request.questionId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy câu hỏi id=" + request.questionId()));
        answer.setQuestion(question);
        answer.setAutoGradable(AUTO_GRADABLE_TYPES.contains(question.getQuestionType()));
        answer.setAnswerText(request.answerText());
        answer.setSelectedChoiceIds(request.selectedChoiceIds());
        answer.setAudioAnswerUrl(request.audioAnswerUrl());
        answer.setStructuredAnswer(request.structuredAnswer());
        answer = studentAnswerRepository.save(answer);
        return toResponse(answer);
    }

    /** Main Flow bước 3-6, A1 (nộp muộn), A3 (toàn trắc nghiệm): nộp bài, tự chấm phần trắc nghiệm ngay. */
    @Transactional
    public ExerciseAttemptResponse submitAttempt(Long attemptId, Long actorUserId) {
        ExerciseAttempt attempt = attemptOwnedByActor(attemptId, actorUserId);
        if (attempt.getStatus() != ExerciseAttempt.Status.IN_PROGRESS) {
            throw new AttemptNotEditableException("Lượt làm bài id=" + attemptId + " không còn ở trạng thái IN_PROGRESS.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ExerciseAssignment assignment = attempt.getExerciseAssignment();
        if (assignment != null && assignment.getDueAt() != null && now.isAfter(assignment.getDueAt())) {
            if (!assignment.isLateSubmissionAllowed()) {
                throw new SubmissionPastDeadlineException("Lượt làm bài id=" + attemptId + " đã quá hạn nộp (" + assignment.getDueAt() + ").");
            }
            attempt.setLateSubmission(true);
        }

        attempt = gradeAndFinalize(attempt, now);
        writeHistory(attempt, actorUserId, ExerciseAttemptHistory.Action.UPDATED);
        return toResponse(attempt);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: hệ
     * thống ép dừng bài khi giám sát thoát ra vượt ngưỡng
     * (`integrity.notify_violation_count_threshold`, xem
     * AttemptIntegrityService#recordEvents) — chấm ngay phần trả lời đã
     * có (tái dùng {@link #gradeAndFinalize}, KHÔNG áp rào quá hạn nộp
     * như submitAttempt vì đây là dừng ép giữa chừng, không phải học sinh
     * tự nộp). Bỏ qua êm nếu attempt không còn IN_PROGRESS (đã nộp/dừng
     * trước đó trong cùng đợt gửi sự kiện — tránh chấm đè 2 lần).
     */
    @Transactional
    public ExerciseAttempt forceStopByIntegrityViolation(Long attemptId) {
        ExerciseAttempt attempt = exerciseAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt làm bài id=" + attemptId));
        if (attempt.getStatus() != ExerciseAttempt.Status.IN_PROGRESS) {
            return attempt;
        }
        attempt.setStoppedByIntegrityViolation(true);
        attempt = gradeAndFinalize(attempt, OffsetDateTime.now());
        writeHistory(attempt, attempt.getStudent().getUser().getId(), ExerciseAttemptHistory.Action.UPDATED);
        return attempt;
    }

    /** Tự chấm phần trắc nghiệm + chốt trạng thái — dùng chung cho submitAttempt (học sinh tự nộp) và forceStopByIntegrityViolation (hệ thống dừng ép). */
    private ExerciseAttempt gradeAndFinalize(ExerciseAttempt attempt, OffsetDateTime now) {
        List<ExerciseQuestion> exerciseQuestions = exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(attempt.getExercise().getId());
        Map<Long, BigDecimal> pointsByQuestionId = exerciseQuestions.stream()
                .collect(Collectors.toMap(eq -> eq.getQuestion().getId(), ExerciseQuestion::getPoints));
        boolean allAutoGradable = exerciseQuestions.stream()
                .allMatch(eq -> AUTO_GRADABLE_TYPES.contains(eq.getQuestion().getQuestionType()));

        List<StudentAnswer> answers = studentAnswerRepository.findByExerciseAttemptId(attempt.getId());
        BigDecimal autoGradeScore = BigDecimal.ZERO;
        for (StudentAnswer answer : answers) {
            if (!answer.isAutoGradable()) {
                continue;
            }
            boolean correct = isAnswerCorrect(answer);
            answer.setCorrect(correct);
            BigDecimal points = pointsByQuestionId.getOrDefault(answer.getQuestion().getId(), BigDecimal.ZERO);
            BigDecimal score = correct ? points : BigDecimal.ZERO;
            answer.setAutoScore(score);
            autoGradeScore = autoGradeScore.add(score);
            studentAnswerRepository.save(answer);
        }

        attempt.setAutoGradeScore(autoGradeScore);
        attempt.setSubmittedAt(now);
        if (allAutoGradable) {
            attempt.setTotalScore(autoGradeScore);
            attempt.setStatus(ExerciseAttempt.Status.FULLY_GRADED);
        } else {
            attempt.setStatus(ExerciseAttempt.Status.AUTO_GRADED);
        }
        attempt = exerciseAttemptRepository.save(attempt);
        return applyPassOutcome(attempt);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05: BTVN dưới
     * ngưỡng đạt ({@code exercises.pass_threshold_percent}, mặc định 80%,
     * cấu hình theo từng Bài) phải làm lại — áp dụng cho MỌI exerciseType học
     * sinh làm (không riêng ASSIGNED). Tính % + đánh dấu passed ngay khi lượt
     * làm bài về FULLY_GRADED (đã chấm xong toàn bộ, kể cả phần chấm tay —
     * gọi lại từ ManualGradingService#recomputeAttemptTotals). Nếu ĐẠT: đóng
     * bản giao ({@link ExerciseAssignment.Status#COMPLETED}) — học sinh không
     * cần làm lại nữa; nếu CHƯA ĐẠT: giữ bản giao ACTIVE để học sinh vẫn thấy
     * "cần làm lại" trong listMyAssignedExercises — chỉ giới hạn bởi
     * allowRetake/maxAttempts giáo viên đã cấu hình sẵn (không tự nới thêm
     * lượt để "ép" làm lại bằng mọi giá, xem RetakeNotAllowedException).
     */
    ExerciseAttempt applyPassOutcome(ExerciseAttempt attempt) {
        if (attempt.getStatus() != ExerciseAttempt.Status.FULLY_GRADED || attempt.getTotalScore() == null) {
            return attempt;
        }
        Exercise exercise = attempt.getExercise();
        BigDecimal percentage = percentageOf(attempt.getTotalScore(), exercise.getTotalPoints());
        boolean passed = percentage != null && percentage.compareTo(exercise.getPassThresholdPercent()) >= 0;
        attempt.setPassed(passed);
        attempt = exerciseAttemptRepository.save(attempt);

        ExerciseAssignment assignment = attempt.getExerciseAssignment();
        if (passed && assignment != null && assignment.getStatus() == ExerciseAssignment.Status.ACTIVE) {
            assignment.setStatus(ExerciseAssignment.Status.COMPLETED);
            exerciseAssignmentRepository.save(assignment);
        }
        return attempt;
    }

    /** package-private static: tái dùng ở ExerciseReportService (FR-ACA-07) để không lệch công thức làm tròn. */
    static BigDecimal percentageOf(BigDecimal score, BigDecimal totalPoints) {
        if (totalPoints == null || totalPoints.signum() <= 0) {
            return null;
        }
        return score.divide(totalPoints, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public ExerciseAttemptResponse getAttempt(Long attemptId, Long actorUserId) {
        return toResponse(attemptOwnedByActor(attemptId, actorUserId));
    }

    @Transactional(readOnly = true)
    public List<StudentAnswerResponse> listAnswers(Long attemptId, Long actorUserId) {
        attemptOwnedByActor(attemptId, actorUserId);
        return studentAnswerRepository.findByExerciseAttemptId(attemptId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ExerciseAttemptResponse> listMyAttempts(Long exerciseId, Long actorUserId) {
        Student student = studentOrThrow(actorUserId);
        return exerciseAttemptRepository.findByExerciseIdAndStudentIdOrderByAttemptNumberDesc(exerciseId, student.getId())
                .stream().map(this::toResponse).toList();
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: Giáo
     * viên xem toàn bộ lịch sử nhiều lượt làm bài của 1 học sinh (kèm cờ
     * {@code stoppedByIntegrityViolation}/{@code selectedForGrading}) khi
     * chấm, để tự chọn lượt phù hợp làm điểm chính thức qua
     * {@link #selectForGrading}. Rào quyền ở Controller (lms.grading.manage).
     */
    @Transactional(readOnly = true)
    public List<ExerciseAttemptResponse> listAttemptsForGrading(Long exerciseId, Long studentId) {
        return exerciseAttemptRepository.findByExerciseIdAndStudentIdOrderByAttemptNumberDesc(exerciseId, studentId)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: Giáo
     * viên đánh dấu 1 lượt làm bài (trong nhiều lượt của cùng 1 học sinh)
     * là kết quả CHÍNH THỨC — bỏ chọn mọi lượt khác của ĐÚNG (exercise,
     * student) đó (tối đa 1 lượt được chọn tại 1 thời điểm). Chỉ đánh
     * dấu, CHƯA tự ghi vào Sổ điểm (grade_entries) — SDD chưa có quy tắc
     * ánh xạ Bài tập → đầu điểm cụ thể, cần xác nhận thêm trước khi làm
     * (GAP đã biết, xem Javadoc lớp này). Rào quyền ở Controller.
     */
    @Transactional
    public List<ExerciseAttemptResponse> selectForGrading(Long attemptId, Long actorUserId) {
        ExerciseAttempt attempt = exerciseAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt làm bài id=" + attemptId));
        List<ExerciseAttempt> siblings = exerciseAttemptRepository.findByExerciseIdAndStudentIdOrderByAttemptNumberDesc(
                attempt.getExercise().getId(), attempt.getStudent().getId());
        for (ExerciseAttempt sibling : siblings) {
            boolean shouldBeSelected = sibling.getId().equals(attemptId);
            if (sibling.isSelectedForGrading() != shouldBeSelected) {
                sibling.setSelectedForGrading(shouldBeSelected);
                exerciseAttemptRepository.save(sibling);
            }
        }
        writeHistory(attempt, actorUserId, ExerciseAttemptHistory.Action.UPDATED);
        return siblings.stream().map(this::toResponse).toList();
    }

    /**
     * Bổ sung: Học sinh tự tra cứu đề đã được giao cho (các) lớp mình đang
     * hoặc đã TỪNG ghi danh (kể cả lớp cũ sau khi chuyển lớp — bổ sung
     * ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29; CHỈ xem, không
     * mở lại khả năng làm bài mới ở lớp cũ — startAttempt vẫn chặn theo
     * ACTIVE như cũ, xem findActiveAssignmentForStudent) — trước đây
     * không có API nào cho việc này, HS phải biết trước exerciseId mới
     * gọi được startAttempt/getExercise. classIdFilter tùy chọn (ngữ cảnh
     * "lớp đang xem" — UC-42). Dedupe theo classId vì 1 học sinh có thể
     * có nhiều dòng enrollment cho CÙNG 1 lớp theo thời gian (chuyển đi
     * rồi quay lại).
     */
    @Transactional(readOnly = true)
    public List<AssignedExerciseResponse> listMyAssignedExercises(Long actorUserId, Long classIdFilter) {
        Student student = studentOrThrow(actorUserId);
        List<ClassEnrollment> enrollments = classEnrollmentRepository.findByStudentId(student.getId()).stream()
                .filter(e -> classIdFilter == null || e.getSchoolClass().getId().equals(classIdFilter))
                .collect(Collectors.toMap(e -> e.getSchoolClass().getId(), e -> e, (a, b) -> a))
                .values().stream().toList();

        List<AssignedExerciseResponse> result = new java.util.ArrayList<>();
        for (ClassEnrollment enrollment : enrollments) {
            // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: lấy CẢ ACTIVE lẫn COMPLETED
            // (không chỉ ACTIVE) — trước đây bản giao ĐÃ ĐẠT (applyPassOutcome tự đóng COMPLETED) biến
            // mất hẳn khỏi danh sách BTVN của học sinh thay vì hiện dưới "Đã nộp & Đã chấm". CANCELLED
            // (GV hủy) vẫn loại — không hiện.
            List<ExerciseAssignment> assignments = exerciseAssignmentRepository.findBySchoolClassIdAndStatusIn(
                    enrollment.getSchoolClass().getId(), List.of(ExerciseAssignment.Status.ACTIVE, ExerciseAssignment.Status.COMPLETED));
            for (ExerciseAssignment assignment : assignments) {
                if (assignment.getTargetStudentIds() != null && !assignment.getTargetStudentIds().contains(student.getId())) {
                    continue;
                }
                result.add(toAssignedResponse(assignment, enrollment, student));
            }
        }
        return result;
    }

    // ===================== Helpers =====================

    private boolean isAnswerCorrect(StudentAnswer answer) {
        Question question = answer.getQuestion();
        if (question.getQuestionType() == Question.QuestionType.FILL_IN_BLANK) {
            String correct = question.getCorrectAnswerText();
            String given = answer.getAnswerText();
            return correct != null && given != null && correct.trim().equalsIgnoreCase(given.trim());
        }
        if (question.getQuestionType() == Question.QuestionType.WORD_BANK) {
            return structuredAnswerMatches(question, "blanks", answer.getStructuredAnswer());
        }
        if (question.getQuestionType() == Question.QuestionType.SENTENCE_BUILDING) {
            return structuredAnswerMatches(question, "chunks", answer.getStructuredAnswer());
        }
        List<QuestionChoice> choices = questionChoiceRepository.findByQuestionIdOrderByDisplayOrder(question.getId());
        Set<Long> correctChoiceIds = choices.stream().filter(QuestionChoice::isCorrect).map(QuestionChoice::getId).collect(Collectors.toSet());
        Set<Long> selected = answer.getSelectedChoiceIds() == null ? Set.of() : Set.copyOf(answer.getSelectedChoiceIds());
        return selected.equals(correctChoiceIds);
    }

    /**
     * WORD_BANK/SENTENCE_BUILDING (V85, bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-08-04): so khớp elementwise (case-insensitive + trim), ĐÚNG thứ tự — student phải chọn
     * đúng thứ tự (khớp key "blanks"/"chunks" trong Question.structuredContent).
     */
    private boolean structuredAnswerMatches(Question question, String key, List<String> given) {
        if (given == null || question.getStructuredContent() == null) {
            return false;
        }
        Object raw = question.getStructuredContent().get(key);
        if (!(raw instanceof List<?> correctList) || correctList.size() != given.size()) {
            return false;
        }
        for (int i = 0; i < correctList.size(); i++) {
            String correct = String.valueOf(correctList.get(i));
            String submitted = given.get(i);
            if (submitted == null || !correct.trim().equalsIgnoreCase(submitted.trim())) {
                return false;
            }
        }
        return true;
    }

    private java.util.Optional<ExerciseAssignment> findActiveAssignmentForStudent(Exercise exercise, Student student) {
        List<ClassEnrollment> activeEnrollments = classEnrollmentRepository.findByStudentId(student.getId()).stream()
                .filter(e -> e.getStatus() == ClassEnrollment.Status.ACTIVE)
                .toList();
        for (ClassEnrollment enrollment : activeEnrollments) {
            List<ExerciseAssignment> assignments = exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                    exercise.getId(), enrollment.getSchoolClass().getId(), ExerciseAssignment.Status.ACTIVE);
            for (ExerciseAssignment assignment : assignments) {
                if (assignment.getTargetStudentIds() == null || assignment.getTargetStudentIds().contains(student.getId())) {
                    return java.util.Optional.of(assignment);
                }
            }
        }
        return java.util.Optional.empty();
    }

    private ExerciseAttempt attemptOwnedByActor(Long attemptId, Long actorUserId) {
        Student student = studentOrThrow(actorUserId);
        ExerciseAttempt attempt = exerciseAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt làm bài id=" + attemptId));
        if (!attempt.getStudent().getId().equals(student.getId())) {
            throw new ResourceNotFoundException("Không tìm thấy lượt làm bài id=" + attemptId);
        }
        return attempt;
    }

    private Student studentOrThrow(Long actorUserId) {
        return studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản id=" + actorUserId + " không có hồ sơ học sinh."));
    }

    private Exercise exerciseOrThrow(Long id) {
        return exerciseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề id=" + id));
    }

    private void writeHistory(ExerciseAttempt attempt, Long actorUserId, ExerciseAttemptHistory.Action action) {
        User actor = userRepository.findById(actorUserId).orElseThrow();
        ExerciseAttemptHistory history = new ExerciseAttemptHistory();
        history.setExerciseAttempt(attempt);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("attemptNumber", attempt.getAttemptNumber());
        snapshot.put("status", attempt.getStatus().name());
        history.setDetails(snapshot);
        exerciseAttemptHistoryRepository.save(history);
    }

    private ExerciseAttemptResponse toResponse(ExerciseAttempt a) {
        BigDecimal percentage = a.getTotalScore() == null ? null
                : percentageOf(a.getTotalScore(), a.getExercise().getTotalPoints());
        return new ExerciseAttemptResponse(
                a.getId(), a.getExercise().getId(),
                a.getExerciseAssignment() == null ? null : a.getExerciseAssignment().getId(),
                a.getStudent().getId(), a.getAttemptNumber(), a.getStartedAt(), a.getSubmittedAt(),
                a.getAutoGradeScore(), a.getManualGradeScore(), a.getTotalScore(), a.getStatus().name(),
                a.isLateSubmission(), percentage, a.getPassed(), a.isStoppedByIntegrityViolation(), a.isSelectedForGrading());
    }

    private AssignedExerciseResponse toAssignedResponse(ExerciseAssignment assignment, ClassEnrollment enrollment, Student student) {
        Exercise exercise = assignment.getExercise();
        List<ExerciseAttempt> myAttempts = exerciseAttemptRepository
                .findByExerciseIdAndStudentIdOrderByAttemptNumberDesc(exercise.getId(), student.getId());
        ExerciseAttempt latest = myAttempts.isEmpty() ? null : myAttempts.get(0);
        BigDecimal latestPercentage = latest == null || latest.getTotalScore() == null ? null
                : percentageOf(latest.getTotalScore(), exercise.getTotalPoints());
        return new AssignedExerciseResponse(
                exercise.getId(), exercise.getCode(), exercise.getTitle(), exercise.getExerciseType().name(),
                assignment.getId(), enrollment.getSchoolClass().getId(), enrollment.getSchoolClass().getName(),
                assignment.getAvailableFrom(), assignment.getDueAt(), assignment.isLateSubmissionAllowed(),
                latest == null ? null : latest.getId(), latest == null ? null : latest.getStatus().name(),
                latest == null ? null : latest.getTotalScore(), latestPercentage,
                latest == null ? null : latest.getPassed());
    }

    private StudentAnswerResponse toResponse(StudentAnswer a) {
        ExerciseAttempt attempt = a.getExerciseAttempt();
        Exercise exercise = attempt.getExercise();
        boolean revealAnswer = attempt.getStatus() != ExerciseAttempt.Status.IN_PROGRESS
                && exercise.isShowCorrectAnswers();
        // UC-24/A4, UC-27/A2 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
        // 2026-08-05): nếu Bài có giới hạn số lần làm lại (max_attempts khác
        // NULL), đáp án chỉ hiện từ lượt làm CUỐI CÙNG (attemptNumber ==
        // maxAttempts) trở đi — các lượt trước chỉ thấy điểm, không thấy đáp án.
        // Chỉ áp dụng cho câu tự chấm được (a.isAutoGradable()) — câu tự luận/Nói
        // (ESSAY/SPEAKING) tạm thời chưa áp dụng, giữ nguyên hành vi cũ.
        if (revealAnswer && a.isAutoGradable() && exercise.getMaxAttempts() != null) {
            revealAnswer = attempt.getAttemptNumber() >= exercise.getMaxAttempts();
        }
        List<Long> correctChoiceIds = null;
        String correctAnswerText = null;
        String explanation = null;
        Map<String, Object> correctStructuredContent = null;
        if (revealAnswer) {
            correctChoiceIds = questionChoiceRepository.findByQuestionIdOrderByDisplayOrder(a.getQuestion().getId())
                    .stream().filter(QuestionChoice::isCorrect).map(QuestionChoice::getId).toList();
            correctAnswerText = a.getQuestion().getCorrectAnswerText();
            correctStructuredContent = a.getQuestion().getStructuredContent();
            // Câu tự chấm (MCQ/TRUE_FALSE/FILL_IN_BLANK) chỉ hiện giải thích khi trả lời SAI;
            // câu chấm tay (ESSAY/SPEAKING) không có cờ correct tin cậy (ManualGradingService
            // không set StudentAnswer.correct) nên giữ nguyên hành vi cũ — luôn hiện khi reveal.
            if (!a.isAutoGradable() || Boolean.FALSE.equals(a.getCorrect())) {
                explanation = a.getQuestion().getExplanation();
            }
        }
        return new StudentAnswerResponse(
                a.getId(), attempt.getId(), a.getQuestion().getId(), a.getAnswerText(),
                a.getSelectedChoiceIds(), a.getAudioAnswerUrl(), a.isAutoGradable(), a.getAutoScore(), a.getCorrect(),
                correctChoiceIds, correctAnswerText, explanation, a.getStructuredAnswer(), correctStructuredContent);
    }
}
