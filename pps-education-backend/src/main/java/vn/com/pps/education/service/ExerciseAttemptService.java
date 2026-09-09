package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.Exercise;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.ExerciseAttempt;
import vn.com.pps.education.domain.ExerciseAttemptHistory;
import vn.com.pps.education.domain.ExerciseQuestion;
import vn.com.pps.education.domain.Question;
import vn.com.pps.education.domain.QuestionChoice;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentAnswer;
import vn.com.pps.education.domain.StudentAnswerGrading;
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
import vn.com.pps.education.repository.StudentAnswerGradingRepository;
import vn.com.pps.education.repository.StudentAnswerRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
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
 * question_choices.is_correct) và FILL_IN_BLANK (so khớp case-insensitive +
 * trim, với questions.correct_answer_text — V54, bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng 2026-07-27; V166 nới lỏng thêm 2026-09-05 — bỏ
 * dấu câu Ở CUỐI chuỗi trước khi so khớp, xem {@link #stripTrailingPunctuation}).
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
    private final StudentAnswerGradingRepository studentAnswerGradingRepository;
    private final WritingAiGradingService writingAiGradingService;

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
                                   UserRepository userRepository,
                                   StudentAnswerGradingRepository studentAnswerGradingRepository,
                                   WritingAiGradingService writingAiGradingService) {
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
        this.studentAnswerGradingRepository = studentAnswerGradingRepository;
        this.writingAiGradingService = writingAiGradingService;
    }

    /**
     * Main Flow bước 1, A2 (retake): mở đề, tạo 1 lượt làm bài mới.
     *
     * V128 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — nhận thẳng
     * {@code assignmentId} thay vì tự đoán "bản giao ACTIVE" qua {@code exerciseId} — từ khi 1 Bài có
     * thể có NHIỀU bản giao ACTIVE song song cho cùng 1 lớp (giao độc lập từ nhiều buổi Nhận xét khác
     * nhau, xem {@link ExerciseService#deliverToClass}), suy đoán 1 bản duy nhất không còn đáng tin —
     * FE (mỗi thẻ BTVN) đã biết sẵn đúng assignmentId của thẻ đang bấm.
     */
    @Transactional
    public ExerciseAttemptResponse startAttempt(Long exerciseId, Long assignmentId, Long actorUserId) {
        Student student = studentOrThrow(actorUserId);
        Exercise exercise = exerciseOrThrow(exerciseId);
        if (exercise.getStatus() != Exercise.Status.PUBLISHED) {
            throw new ExerciseNotAvailableException("error.exerciseNotAvailable.notPublished", new Object[]{}, "Đề này chưa được publish.");
        }

        // Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
        // bỏ nhánh rẽ theo exerciseType — MỌI loại đề (kể cả SELF_PRACTICE/
        // MOCK_TEST/SKILL_PRACTICE) giờ đều cần ExerciseAssignment ACTIVE, không
        // còn "mở tự do sau khi Publish" (mirror ExerciseService#requireCanViewExercise).
        ExerciseAssignment assignment = resolveActiveAssignmentForStudent(assignmentId, exercise, student);
        if (assignment.getAvailableFrom().isAfter(OffsetDateTime.now())) {
            throw new ExerciseNotAvailableException("error.exerciseNotAvailable.notYetOpen", new Object[]{}, "Đề này chưa tới thời gian mở làm bài.");
        }

        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — fix bug thật: đếm lượt đã làm
        // CHỈ trong phạm vi bản giao hiện tại (không tính lượt đã làm ở bản giao TRƯỚC đó) — mirror
        // ReviewVideoService#submitQuestionAudio (V69) "giao lại = 1 lượt MỚI", maxAttempts áp dụng
        // lại từ đầu mỗi lần giao. Trước đây đếm theo exerciseId+studentId toàn cục nên 1 học sinh đã
        // hết lượt (maxAttempts) ở bản giao cũ sẽ bị chặn làm luôn cả bản giao MỚI vừa được giao lại.
        long previousAttempts = exerciseAttemptRepository.countByExerciseAssignmentIdAndStudentId(assignment.getId(), student.getId());
        int attemptNumber = (int) previousAttempts + 1;
        if (previousAttempts > 0) {
            if (!exercise.isAllowRetake()) {
                throw new RetakeNotAllowedException("error.retakeNotAllowed.notAllowed", new Object[]{}, "Đề này không cho phép làm lại.");
            }
            if (exercise.getMaxAttempts() != null && attemptNumber > exercise.getMaxAttempts()) {
                throw new RetakeNotAllowedException("error.retakeNotAllowed.maxAttemptsReached", new Object[]{exercise.getMaxAttempts()}, "Đề này đã hết lượt làm (tối đa " + exercise.getMaxAttempts() + ").");
            }
            // V148 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — hết hạn nộp + bản
            // giao không cho nộp muộn (isLateSubmissionAllowed=false) thì KHÔNG cho mở lượt LÀM LẠI mới
            // nữa (lượt đầu tiên không bị chặn ở đây — có thể đã mở từ trước hạn). Trước đây chỉ chặn ở
            // submitAttempt (lúc NỘP), nên học sinh vẫn mở được 1 lượt mới sau hạn rồi bỏ dở mãi mãi
            // IN_PROGRESS (không có điểm) — làm lượt "mới nhất" của HomeworkProgressService luôn hiện
            // "Đang chờ chấm" dù trước đó đã có lượt được chấm hợp lệ. Đọc dueAt/isLateSubmissionAllowed
            // TRỰC TIẾP từ assignment mỗi lần gọi (không cache) — quản trị/giáo viên gia hạn dueAt hoặc
            // bật lại "Cho phép nộp muộn" thì học sinh mở lại "Làm lại" được ngay, không cần thao tác gì
            // thêm phía học sinh.
            if (assignment.getDueAt() != null && OffsetDateTime.now().isAfter(assignment.getDueAt())
                    && !assignment.isLateSubmissionAllowed()) {
                throw new RetakeNotAllowedException("error.retakeNotAllowed.pastDeadline", new Object[]{assignment.getDueAt()},
                        "Đề này đã quá hạn nộp (" + assignment.getDueAt() + "), không thể làm lại.");
            }
            // Sửa 2026-09-04 — mirror guard mới ở canStartNewAttempt/revealAnswersAndClose: chặn ở BE
            // (không chỉ ẩn nút FE) học sinh đã TỰ NGUYỆN đóng sớm lượt gần nhất để xem đáp án.
            ExerciseAttempt lastAttempt = exerciseAttemptRepository
                    .findByExerciseAssignmentIdAndStudentIdOrderByAttemptNumberDesc(assignment.getId(), student.getId())
                    .stream().findFirst().orElse(null);
            if (lastAttempt != null && lastAttempt.isAnswersRevealedEarly()) {
                throw new RetakeNotAllowedException("error.retakeNotAllowed.answersRevealedEarly", new Object[]{},
                        "Đã xem đáp án sớm cho lượt làm trước — không thể làm lại.");
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
            throw new AttemptNotEditableException("error.attemptNotEditable.default", new Object[]{}, "Lượt làm bài này không còn ở trạng thái đang làm (IN_PROGRESS).");
        }
        if (isTimeLimitExceeded(attempt)) {
            autoFinalizeExpiredAttempt(attempt);
            throw new AttemptNotEditableException("error.attemptNotEditable.timeLimitExceeded", new Object[]{attempt.getExercise().getTimeLimitMinutes()},
                    "Đã hết thời gian làm bài (" + attempt.getExercise().getTimeLimitMinutes() + " phút), hệ thống đã tự động nộp bài.");
        }
        // V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — UC-24/UC-27 A1: lượt
        // làm CÒN IN_PROGRESS (chưa nộp) nhưng đã quá hạn nộp và bản giao không cho nộp muộn phải bị
        // khoá HẲN, không riêng gì submitAttempt (đã chặn từ trước) — trước đây học sinh vẫn ghi/sửa
        // được câu trả lời sau hạn dù không bao giờ nộp nổi. Mirror ĐÚNG rào đã có ở submitAttempt.
        ExerciseAssignment assignment = attempt.getExerciseAssignment();
        if (assignment != null && assignment.getDueAt() != null && OffsetDateTime.now().isAfter(assignment.getDueAt())
                && !assignment.isLateSubmissionAllowed()) {
            throw new SubmissionPastDeadlineException("error.submissionPastDeadline.exerciseAttempt", new Object[]{assignment.getDueAt()},
                    "Lượt làm bài này đã quá hạn nộp (" + assignment.getDueAt() + ").");
        }
        if (!exerciseQuestionRepository.existsByExerciseIdAndQuestionId(attempt.getExercise().getId(), request.questionId())) {
            throw new ResourceNotFoundException("error.exerciseAttempt.answerQuestionMismatch",
                    new Object[]{request.questionId(), attempt.getExercise().getId()},
                    "Câu hỏi id=" + request.questionId() + " không thuộc đề id=" + attempt.getExercise().getId());
        }

        StudentAnswer answer = studentAnswerRepository.findByExerciseAttemptIdAndQuestionId(attemptId, request.questionId())
                .orElseGet(StudentAnswer::new);
        answer.setExerciseAttempt(attempt);
        Question question = exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(attempt.getExercise().getId()).stream()
                .map(ExerciseQuestion::getQuestion)
                .filter(q -> q.getId().equals(request.questionId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseAttempt.questionNotFound",
                        new Object[]{request.questionId()}, "Không tìm thấy câu hỏi id=" + request.questionId()));
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
            throw new AttemptNotEditableException("error.attemptNotEditable.default", new Object[]{}, "Lượt làm bài này không còn ở trạng thái đang làm (IN_PROGRESS).");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ExerciseAssignment assignment = attempt.getExerciseAssignment();
        if (assignment != null && assignment.getDueAt() != null && now.isAfter(assignment.getDueAt())) {
            if (!assignment.isLateSubmissionAllowed()) {
                throw new SubmissionPastDeadlineException("error.submissionPastDeadline.exerciseAttempt", new Object[]{assignment.getDueAt()}, "Lượt làm bài này đã quá hạn nộp (" + assignment.getDueAt() + ").");
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
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseAttempt.notFoundById",
                        new Object[]{attemptId}, "Không tìm thấy lượt làm bài id=" + attemptId));
        if (attempt.getStatus() != ExerciseAttempt.Status.IN_PROGRESS) {
            return attempt;
        }
        attempt.setStoppedByIntegrityViolation(true);
        attempt = gradeAndFinalize(attempt, OffsetDateTime.now());
        writeHistory(attempt, attempt.getStudent().getUser().getId(), ExerciseAttemptHistory.Action.UPDATED);
        return attempt;
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 —
     * enforcement Exercise.timeLimitMinutes (trường có sẵn từ trước,
     * trước đây chưa từng được thực thi ở đâu). NULL = không giới hạn.
     */
    private boolean isTimeLimitExceeded(ExerciseAttempt attempt) {
        Integer timeLimitMinutes = attempt.getExercise().getTimeLimitMinutes();
        return timeLimitMinutes != null
                && OffsetDateTime.now().isAfter(attempt.getStartedAt().plusMinutes(timeLimitMinutes));
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22: tự
     * động chốt + chấm 1 lượt làm hết giờ như nộp bình thường (tái dùng
     * {@link #gradeAndFinalize}, KHÔNG dùng trạng thái EXPIRED có sẵn
     * trong SDD — xem docs/uc/phan-he-07-lms-portal.md). Dùng chung cho
     * saveAnswer (chặn ngay khi học sinh còn thao tác) và
     * ExerciseAttemptTimeoutSchedulerService (quét lượt bị bỏ dở).
     */
    ExerciseAttempt autoFinalizeExpiredAttempt(ExerciseAttempt attempt) {
        attempt = gradeAndFinalize(attempt, OffsetDateTime.now());
        writeHistory(attempt, attempt.getStudent().getUser().getId(), ExerciseAttemptHistory.Action.UPDATED);
        return attempt;
    }

    /** Tự chấm phần trắc nghiệm + chốt trạng thái — dùng chung cho submitAttempt (học sinh tự nộp) và forceStopByIntegrityViolation (hệ thống dừng ép). */
    private ExerciseAttempt gradeAndFinalize(ExerciseAttempt attempt, OffsetDateTime now) {
        List<ExerciseQuestion> exerciseQuestions = exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(attempt.getExercise().getId());
        Map<Long, BigDecimal> pointsByQuestionId = exerciseQuestions.stream()
                .collect(Collectors.toMap(eq -> eq.getQuestion().getId(), ExerciseQuestion::getPoints));

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

        // V138 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22): ESSAY thuộc Bài
        // Exercise.skillCategory=WRITING được AI chấm NGAY lúc nộp bài (thay UC-41 chờ GV thủ công CHỈ
        // cho riêng nhóm Bài này) — ghi StudentAnswerGrading trực tiếp tại đây (KHÔNG gọi
        // ManualGradingService#gradeAnswer để tránh phụ thuộc vòng: ManualGradingService đã phụ thuộc
        // NGƯỢC lại ExerciseAttemptService qua applyPassOutcome). GV vẫn chấm tay đè lên được sau qua
        // ManualGradingService#gradeAnswer bình thường nếu thấy AI chấm sai — KHÔNG có rào chặn
        // re-grade, và câu đã có điểm AI tự động biến mất khỏi "chờ chấm" (listPendingGrading chỉ liệt
        // kê câu CHƯA có điểm) — đúng tinh thần "ẩn nhưng vẫn giữ được" đã xác nhận với người dùng.
        BigDecimal aiGradeScore = BigDecimal.ZERO;
        Set<Long> aiGradedAnswerIds = new HashSet<>();
        if (attempt.getExercise().getSkillCategory() == Exercise.SkillCategory.WRITING) {
            for (StudentAnswer answer : answers) {
                if (answer.isAutoGradable() || answer.getQuestion().getQuestionType() != Question.QuestionType.ESSAY) {
                    continue;
                }
                BigDecimal points = pointsByQuestionId.getOrDefault(answer.getQuestion().getId(), BigDecimal.ZERO);
                BigDecimal score = gradeEssayWithAi(answer, points, now, attempt.getExercise().getExam().getCurriculum());
                if (score != null) {
                    aiGradeScore = aiGradeScore.add(score);
                    aiGradedAnswerIds.add(answer.getId());
                }
            }
        }

        boolean allGraded = answers.stream()
                .allMatch(a -> a.isAutoGradable() || aiGradedAnswerIds.contains(a.getId()));

        attempt.setAutoGradeScore(autoGradeScore);
        attempt.setManualGradeScore(aiGradeScore);
        attempt.setSubmittedAt(now);
        if (allGraded) {
            attempt.setTotalScore(autoGradeScore.add(aiGradeScore));
            attempt.setStatus(ExerciseAttempt.Status.FULLY_GRADED);
        } else {
            attempt.setStatus(ExerciseAttempt.Status.AUTO_GRADED);
        }
        attempt = exerciseAttemptRepository.save(attempt);
        return applyPassOutcome(attempt);
    }

    /**
     * V138 — chấm 1 câu ESSAY bằng AI (WritingAiGradingService, rubric.md) và ghi StudentAnswerGrading
     * (gradingSource=AI, grader=exercise.createdBy — KHÔNG thêm user hệ thống ảo, xem Javadoc
     * StudentAnswerGrading.GradingSource). Trả null nếu AI chưa cấu hình/gọi lỗi — answer giữ nguyên
     * chưa có điểm, tự rơi vào hàng chờ chấm tay UC-41 như hành vi mặc định cũ.
     */
    private BigDecimal gradeEssayWithAi(StudentAnswer answer, BigDecimal maxPoints, OffsetDateTime now, Curriculum curriculum) {
        WritingAiGradingService.GradeResult result = writingAiGradingService.grade(answer.getAnswerText(), curriculum);
        if (result == null) {
            return null;
        }
        BigDecimal score = maxPoints
                .multiply(BigDecimal.valueOf(result.scorePercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        studentAnswerGradingRepository.findByStudentAnswerIdAndLatestIsTrue(answer.getId()).ifPresent(previous -> {
            previous.setLatest(false);
            studentAnswerGradingRepository.saveAndFlush(previous);
        });

        StudentAnswerGrading grading = new StudentAnswerGrading();
        grading.setStudentAnswer(answer);
        grading.setGrader(answer.getExerciseAttempt().getExercise().getCreatedBy());
        grading.setGradingSource(StudentAnswerGrading.GradingSource.AI);
        grading.setScore(score);
        grading.setMaxScore(maxPoints);
        grading.setFeedback(result.feedback());
        grading.setGradedAt(now);
        grading.setLatest(true);
        studentAnswerGradingRepository.save(grading);
        return score;
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05 (điều
     * chỉnh lại 2026-08-19): BTVN dưới ngưỡng đạt
     * ({@code exercises.pass_threshold_percent}, mặc định 70% từ V100, cấu
     * hình theo từng Bài) phải làm lại — áp dụng cho MỌI exerciseType học
     * sinh làm (không riêng ASSIGNED). Tính % + đánh dấu passed ngay khi lượt
     * làm bài về FULLY_GRADED (đã chấm xong toàn bộ, kể cả phần chấm tay —
     * gọi lại từ ManualGradingService#recomputeAttemptTotals). Nếu ĐẠT
     * NHƯNG vẫn còn lượt làm lại (allowRetake=true và chưa hết maxAttempts):
     * giữ bản giao ACTIVE — học sinh có thể tự nguyện làm lại để thử điểm
     * cao hơn (KHÔNG bắt buộc, chỉ là còn quyền truy cập). Nếu ĐẠT và đã hết
     * lượt (allowRetake=false hoặc đã dùng hết maxAttempts): đóng bản giao
     * ({@link ExerciseAssignment.Status#COMPLETED}). Nếu CHƯA ĐẠT: luôn giữ
     * bản giao ACTIVE để học sinh vẫn thấy "cần làm lại" trong
     * listMyAssignedExercises — chỉ giới hạn bởi allowRetake/maxAttempts
     * giáo viên đã cấu hình sẵn (không tự nới thêm lượt để "ép" làm lại bằng
     * mọi giá, xem RetakeNotAllowedException).
     * Trước 2026-08-19, ĐẠT luôn đóng bản giao ngay cả khi còn lượt, khiến
     * học sinh đạt 80% (trên ngưỡng) không thể tự làm lại để thử đạt 100%.
     *
     * Sửa 2026-09-04 (bug thật, xem báo cáo 422 "Đề này chưa được giao cho học sinh" trên staging) —
     * BỎ đoạn tự đóng {@code assignment.setStatus(COMPLETED)} khi 1 học sinh đạt + hết lượt: 1
     * {@code ExerciseAssignment} là bản giao CHUNG CHO CẢ LỚP ({@code targetStudentIds} luôn null,
     * không có cá nhân hoá thật nào tạo ra nó), nên đóng bản giao ở đây vô tình chặn LUÔN mọi học
     * sinh khác trong lớp (kể cả chưa từng mở bài) — resolveActiveAssignmentForStudent chỉ chấp
     * nhận status=ACTIVE. "Học sinh này đã hết lượt làm lại" đã được gate ĐÚNG phạm vi cá nhân ở
     * startAttempt (đếm attempt theo assignmentId+studentId) và canStartNewAttempt
     * (toAssignedResponse) rồi — không cần đụng tới assignment dùng chung để chặn thêm.
     */
    ExerciseAttempt applyPassOutcome(ExerciseAttempt attempt) {
        if (attempt.getStatus() != ExerciseAttempt.Status.FULLY_GRADED || attempt.getTotalScore() == null) {
            return attempt;
        }
        Exercise exercise = attempt.getExercise();
        BigDecimal percentage = percentageOf(attempt.getTotalScore(), exercise.getTotalPoints());
        boolean passed = percentage != null && percentage.compareTo(exercise.getPassThresholdPercent()) >= 0;
        attempt.setPassed(passed);
        return exerciseAttemptRepository.save(attempt);
    }

    /**
     * V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — UC-24/A4, UC-27/A2: học
     * sinh ĐÃ ĐẠT ngưỡng nhưng vẫn còn lượt làm lại (xem applyPassOutcome — bản giao vẫn ACTIVE để
     * TỰ NGUYỆN thử lại) có thể chủ động dừng lại NGAY, đổi lại được xem đáp án đúng của lượt vừa đạt
     * (bình thường phải làm hết maxAttempts mới được xem — xem toResponse(StudentAnswer)). Khoá LUÔN
     * quyền làm lại của CHÍNH học sinh này (không hoàn tác được) — FE phải xác nhận trước khi gọi
     * (xem TakeExerciseModal/BatchTakeExerciseModal).
     *
     * Sửa 2026-09-04 (bug thật, cùng nguyên nhân với fix ở applyPassOutcome) — trước đây đóng LUÔN
     * {@code assignment.setStatus(COMPLETED)}, nhưng 1 {@code ExerciseAssignment} là bản giao CHUNG
     * CHO CẢ LỚP nên vô tình chặn hết mọi học sinh khác. Giờ chỉ đánh dấu {@code answersRevealedEarly}
     * trên CHÍNH attempt này — canStartNewAttempt (toAssignedResponse) đã đọc đúng cờ này để ẩn nút
     * "Làm lại" của riêng học sinh đó, không đụng gì tới assignment dùng chung.
     */
    @Transactional
    public ExerciseAttemptResponse revealAnswersAndClose(Long attemptId, Long actorUserId) {
        ExerciseAttempt attempt = attemptOwnedByActor(attemptId, actorUserId);
        if (attempt.getStatus() != ExerciseAttempt.Status.FULLY_GRADED || !Boolean.TRUE.equals(attempt.getPassed())) {
            throw new AttemptNotEditableException("error.attemptNotEditable.notPassedYet", new Object[]{},
                    "Chỉ áp dụng cho lượt làm đã ĐẠT và đã chấm xong toàn bộ.");
        }
        if (attempt.isAnswersRevealedEarly()) {
            throw new AttemptNotEditableException("error.attemptNotEditable.alreadyRevealedEarly", new Object[]{},
                    "Lượt làm bài này đã được đóng sớm để xem đáp án từ trước.");
        }
        attempt.setAnswersRevealedEarly(true);
        attempt = exerciseAttemptRepository.save(attempt);
        writeHistory(attempt, actorUserId, ExerciseAttemptHistory.Action.UPDATED);
        return toResponse(attempt);
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

    /**
     * V169 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-05) — fix bug thật: học sinh
     * KHÔNG hề đụng vào 1 câu hỏi (không gõ/không chọn dropdown lần nào, kể cả để trắng) thì
     * saveAnswer() chưa từng được gọi cho câu đó → KHÔNG có dòng student_answers nào — trước đây hàm
     * này chỉ trả về đúng những dòng ĐÃ tồn tại, nên câu chưa từng động tới hoàn toàn biến mất khỏi
     * response, kể cả khi lượt đã hết + đã hiện đáp án (Exercise.showCorrectAnswers=true) — học sinh
     * hết lượt làm lại vẫn không thấy đáp án đúng cho ĐÚNG những câu đó (trắc nghiệm không dính vì
     * saveAnswer chạy ngay khi bấm chọn, dù chọn sai). Bù thêm 1 dòng StudentAnswer "rỗng" (KHÔNG lưu
     * DB — chỉ dựng tạm trong bộ nhớ để đi qua toResponse() lấy đúng đáp án đúng/giải thích nếu đủ điều
     * kiện lộ) cho mọi câu hỏi của Bài chưa có dòng nào, y hệt học sinh đã "trả lời" rỗng. Cố tình
     * KHÔNG ghi xuống student_answers thật — tránh đổi ý nghĩa "đã tồn tại dòng" đang được
     * ExerciseReportService#getQuestionStats dùng để phân biệt "đã thử làm" khỏi "chưa từng động tới".
     */
    @Transactional(readOnly = true)
    public List<StudentAnswerResponse> listAnswers(Long attemptId, Long actorUserId) {
        ExerciseAttempt attempt = attemptOwnedByActor(attemptId, actorUserId);
        List<StudentAnswer> answers = new ArrayList<>(studentAnswerRepository.findByExerciseAttemptId(attemptId));
        Set<Long> answeredQuestionIds = answers.stream().map(a -> a.getQuestion().getId()).collect(Collectors.toSet());
        for (ExerciseQuestion eq : exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(attempt.getExercise().getId())) {
            if (answeredQuestionIds.add(eq.getQuestion().getId())) {
                StudentAnswer blank = new StudentAnswer();
                blank.setExerciseAttempt(attempt);
                blank.setQuestion(eq.getQuestion());
                blank.setAutoGradable(AUTO_GRADABLE_TYPES.contains(eq.getQuestion().getQuestionType()));
                answers.add(blank);
            }
        }
        return answers.stream().map(this::toResponse).toList();
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
     * viên xem lịch sử trả lời câu hỏi của 1 lượt làm bài BẤT KỲ khi chấm
     * (không giới hạn ở lượt của chính mình như {@link #listAnswers}) —
     * mirror {@link #listAttemptsForGrading}, không check sở hữu, rào
     * quyền ở Controller (lms.grading.manage).
     */
    @Transactional(readOnly = true)
    public List<StudentAnswerResponse> listAnswersForGrading(Long attemptId) {
        if (!exerciseAttemptRepository.existsById(attemptId)) {
            throw new ResourceNotFoundException("error.exerciseAttempt.notFoundById",
                    new Object[]{attemptId}, "Không tìm thấy lượt làm bài id=" + attemptId);
        }
        return studentAnswerRepository.findByExerciseAttemptId(attemptId).stream().map(this::toResponse).toList();
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
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseAttempt.notFoundById",
                        new Object[]{attemptId}, "Không tìm thấy lượt làm bài id=" + attemptId));
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
     * ACTIVE như cũ, xem resolveActiveAssignmentForStudent) — trước đây
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
            // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: lấy CẢ ACTIVE/COMPLETED/
            // CANCELLED — trước đây bản giao ĐÃ ĐẠT (applyPassOutcome tự đóng COMPLETED) biến mất hẳn
            // khỏi danh sách BTVN của học sinh thay vì hiện dưới "Đã nộp & Đã chấm" (đã sửa, không loại
            // COMPLETED nữa). Từ khi thêm cơ chế "giao lại = 1 lượt MỚI, hủy bản giao ACTIVE cũ" ở
            // deliverToClass (mirror ReviewVideoService V69), bản giao CŨ bị CANCELLED cũng KHÔNG được
            // loại nữa — đã xác nhận lại với người dùng: bài đã làm hay chưa làm, học sinh vẫn phải xem
            // được, không được biến mất khỏi tầm nhìn chỉ vì có 1 bản giao MỚI hơn thay thế nó.
            List<ExerciseAssignment> assignments = exerciseAssignmentRepository.findBySchoolClassIdAndStatusIn(
                    enrollment.getSchoolClass().getId(),
                    List.of(ExerciseAssignment.Status.ACTIVE, ExerciseAssignment.Status.COMPLETED, ExerciseAssignment.Status.CANCELLED));
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
            return correct != null && given != null
                    && stripTrailingPunctuation(correct).equalsIgnoreCase(stripTrailingPunctuation(given));
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
     * V166 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-05) — bỏ dấu câu Ở CUỐI chuỗi
     * (sau khi trim khoảng trắng 2 đầu) trước khi so khớp FILL_IN_BLANK, VD đáp án soạn "goes to
     * school." và học sinh gõ "goes to school" (thiếu dấu chấm cuối câu) vẫn tính ĐÚNG. Dấu câu Ở GIỮA
     * câu (dấu phẩy liệt kê, nháy đơn trong "don't"...) KHÔNG bị đụng tới — chỉ dấu câu liên tiếp ở
     * đúng cuối chuỗi mới bị bỏ qua.
     */
    private static String stripTrailingPunctuation(String text) {
        return text.trim().replaceAll("\\p{Punct}+$", "").trim();
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

    /**
     * V128 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — thay
     * {@code findActiveAssignmentForStudent} (đoán "1 bản giao ACTIVE duy nhất" theo exerciseId, VỠ
     * ngay khi có ≥2 bản active song song từ 2 buổi Nhận xét khác nhau): tra thẳng theo
     * {@code assignmentId} FE đã biết sẵn, chỉ còn xác thực đúng đề + còn ACTIVE + học sinh nằm trong
     * phạm vi được giao (targetStudentIds null = cả lớp) + học sinh còn ghi danh ACTIVE lớp đó.
     */
    private ExerciseAssignment resolveActiveAssignmentForStudent(Long assignmentId, Exercise exercise, Student student) {
        ExerciseAssignment assignment = exerciseAssignmentRepository.findById(assignmentId)
                .filter(a -> a.getExercise().getId().equals(exercise.getId()))
                .filter(a -> a.getStatus() == ExerciseAssignment.Status.ACTIVE)
                .filter(a -> a.getTargetStudentIds() == null || a.getTargetStudentIds().contains(student.getId()))
                .filter(a -> classEnrollmentRepository
                        .findBySchoolClassIdAndStudentIdAndStatus(a.getSchoolClass().getId(), student.getId(), ClassEnrollment.Status.ACTIVE)
                        .isPresent())
                .orElseThrow(() -> new ExerciseNotAvailableException("error.exerciseNotAvailable.notAssigned", new Object[]{}, "Đề này chưa được giao cho học sinh."));
        return assignment;
    }

    /** Package-private (không private) — tái dùng ở ListeningHintService (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06). */
    ExerciseAttempt attemptOwnedByActor(Long attemptId, Long actorUserId) {
        Student student = studentOrThrow(actorUserId);
        ExerciseAttempt attempt = exerciseAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseAttempt.notFoundById",
                        new Object[]{attemptId}, "Không tìm thấy lượt làm bài id=" + attemptId));
        if (!attempt.getStudent().getId().equals(student.getId())) {
            throw new ResourceNotFoundException("error.exerciseAttempt.notFoundById",
                    new Object[]{attemptId}, "Không tìm thấy lượt làm bài id=" + attemptId);
        }
        return attempt;
    }

    private Student studentOrThrow(Long actorUserId) {
        return studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseAttempt.studentProfileNotFound",
                        new Object[]{actorUserId}, "Tài khoản id=" + actorUserId + " không có hồ sơ học sinh."));
    }

    private Exercise exerciseOrThrow(Long id) {
        return exerciseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseAttempt.exerciseNotFound",
                        new Object[]{id}, "Không tìm thấy đề id=" + id));
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
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — fix bug thật: trước đây lấy
        // "lượt làm gần nhất của Bài này" theo exerciseId+studentId TOÀN CỤC, nên khi 1 Bài được giao
        // LẠI (bản giao mới), thẻ BTVN mới vẫn hiển thị nhầm điểm/trạng thái "Đã có điểm" của lượt làm
        // ở bản giao CŨ — học sinh tưởng không có gì mới. Lấy đúng phạm vi bản giao ĐANG hiển thị
        // (assignment.getId()), mirror ReviewVideoService cách scope theo reviewVideoAssignmentId.
        List<ExerciseAttempt> myAttempts = exerciseAttemptRepository
                .findByExerciseAssignmentIdAndStudentIdOrderByAttemptNumberDesc(assignment.getId(), student.getId());
        ExerciseAttempt latest = myAttempts.isEmpty() ? null : myAttempts.get(0);
        BigDecimal latestPercentage = latest == null || latest.getTotalScore() == null ? null
                : percentageOf(latest.getTotalScore(), exercise.getTotalPoints());
        // Xem Javadoc AssignedExerciseResponse#canStartNewAttempt — mirror ĐÚNG điều kiện startAttempt()
        // kiểm tra (assignment ACTIVE + đã mở + còn lượt theo allowRetake/maxAttempts), KHÔNG đòi hỏi
        // lượt gần nhất phải FULLY_GRADED. V148 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
        // 2026-08-23) — thêm điều kiện hết hạn nộp (chỉ áp dụng khi ĐÃ có lượt trước đó, mirror đúng
        // nhánh previousAttempts>0 ở startAttempt) để nút "Làm lại" ở FE (portal, TakeExerciseModal) ẩn
        // đúng lúc thay vì hiện ra rồi bấm mới báo lỗi 422.
        boolean retakeBlockedByDeadline = !myAttempts.isEmpty() && assignment.getDueAt() != null
                && OffsetDateTime.now().isAfter(assignment.getDueAt()) && !assignment.isLateSubmissionAllowed();
        // Sửa 2026-09-04 — mirror đúng guard mới ở revealAnswersAndClose: học sinh đã TỰ NGUYỆN đóng
        // sớm lượt vừa đạt để xem đáp án thì khoá "Làm lại" của RIÊNG học sinh đó (trước đây khoá qua
        // assignment.setStatus(COMPLETED) dùng chung cả lớp — đã bỏ vì gây bug 422 cho học sinh khác).
        boolean revealedEarly = latest != null && latest.isAnswersRevealedEarly();
        boolean canStartNewAttempt = assignment.getStatus() == ExerciseAssignment.Status.ACTIVE
                && !assignment.getAvailableFrom().isAfter(OffsetDateTime.now())
                && (latest == null || latest.getStatus() != ExerciseAttempt.Status.IN_PROGRESS)
                && (myAttempts.isEmpty() || exercise.isAllowRetake())
                && (exercise.getMaxAttempts() == null || myAttempts.size() < exercise.getMaxAttempts())
                && !retakeBlockedByDeadline
                && !revealedEarly;
        return new AssignedExerciseResponse(
                exercise.getId(), exercise.getCode(), exercise.getTitle(), exercise.getExerciseType().name(),
                assignment.getId(), enrollment.getSchoolClass().getId(), enrollment.getSchoolClass().getName(),
                assignment.getAvailableFrom(), assignment.getDueAt(), assignment.isLateSubmissionAllowed(),
                latest == null ? null : latest.getId(), latest == null ? null : latest.getStatus().name(),
                latest == null ? null : latest.getTotalScore(), latestPercentage,
                latest == null ? null : latest.getPassed(),
                exercise.getExam().getTeacherType().name(),
                assignment.getSourceClassSession() == null ? null : assignment.getSourceClassSession().getSessionDate(),
                canStartNewAttempt,
                assignment.getHomeworkBatch() == null ? null : assignment.getHomeworkBatch().getId(),
                exercise.getTotalPoints(),
                exercise.getExam().getId(), exercise.getExam().getTitle(),
                exercise.getSkillCategory() == null ? null : exercise.getSkillCategory().name(),
                exercise.getExam().getSubTopic() == null ? null : exercise.getExam().getSubTopic().getUnit().getTitle(),
                exercise.getExam().getSubTopic() == null ? null : exercise.getExam().getSubTopic().getTitle());
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
        // V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — HOẶC học sinh đã ĐẠT
        // và tự nguyện đóng lượt sớm (xem revealAnswersAndClose) — không cần đợi tới lượt cuối cùng nữa.
        if (revealAnswer && a.isAutoGradable() && exercise.getMaxAttempts() != null) {
            revealAnswer = attempt.getAttemptNumber() >= exercise.getMaxAttempts() || attempt.isAnswersRevealedEarly();
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
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — điểm/nhận xét câu tự luận/nói
        // đã chấm (tay hoặc AI), hiện cho học sinh ngay khi có (KHÔNG phụ thuộc showCorrectAnswers —
        // đó là cấu hình riêng cho việc lộ ĐÁP ÁN ĐÚNG, khác với việc học sinh xem lại nhận xét bài
        // của chính mình). Chỉ tra cứu cho câu KHÔNG tự chấm được, tránh query thừa cho MCQ/FILL_IN_BLANK.
        BigDecimal gradingScore = null;
        BigDecimal gradingMaxScore = null;
        String gradingFeedback = null;
        String gradingSource = null;
        if (!a.isAutoGradable() && attempt.getStatus() != ExerciseAttempt.Status.IN_PROGRESS) {
            StudentAnswerGrading grading = studentAnswerGradingRepository.findByStudentAnswerIdAndLatestIsTrue(a.getId()).orElse(null);
            if (grading != null) {
                gradingScore = grading.getScore();
                gradingMaxScore = grading.getMaxScore();
                gradingFeedback = grading.getFeedback();
                gradingSource = grading.getGradingSource().name();
            }
        }
        return new StudentAnswerResponse(
                a.getId(), attempt.getId(), a.getQuestion().getId(), a.getAnswerText(),
                a.getSelectedChoiceIds(), a.getAudioAnswerUrl(), a.isAutoGradable(), a.getAutoScore(), a.getCorrect(),
                correctChoiceIds, correctAnswerText, explanation, a.getStructuredAnswer(), correctStructuredContent,
                gradingScore, gradingMaxScore, gradingFeedback, gradingSource);
    }
}
