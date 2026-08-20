package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.AssignedExerciseResponse;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateExamQuestionRequest;
import vn.com.pps.education.dto.CreateExamRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.ExamResponse;
import vn.com.pps.education.dto.ExerciseAttemptResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.QuestionChoiceRequest;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.RecordTransferRequest;
import vn.com.pps.education.dto.SaveAnswerRequest;
import vn.com.pps.education.dto.StudentAnswerResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.AttemptNotEditableException;
import vn.com.pps.education.exception.ExerciseNotAvailableException;
import vn.com.pps.education.exception.RetakeNotAllowedException;
import vn.com.pps.education.exception.SubmissionPastDeadlineException;
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-24: Làm bài kiểm tra trực tuyến — Main Flow, A1 (nộp muộn), A2
 * (retake), A3 (toàn trắc nghiệm) + UC-27: Làm bài tập/đề ôn tập — Main
 * Flow (SELF_PRACTICE).
 */
@Transactional
class ExerciseAttemptServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ExerciseAttemptService exerciseAttemptService;

    @Autowired
    private ExamQuestionService examQuestionService;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private ExamService examService;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ExerciseAssignmentRepository exerciseAssignmentRepository;

    private User headAcademic;
    private User teacher;
    private CurriculumResponse activeCurriculum;
    private ClassResponse schoolClass;
    private User studentUser;
    private Student student;
    private ExamResponse defaultExam;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

        studentUser = newUser("student");
        student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-TEST-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        student = studentRepository.save(student);
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        defaultExam = examService.createExam(
                new CreateExamRequest(examCode(), "Đề mặc định", activeCurriculum.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());
    }

    /**
     * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * thay cho startAttempt_UC27_MainFlow_startsSelfPracticeWithoutAssignment
     * cũ — SELF_PRACTICE hết cơ chế "mở tự do không cần bản giao", giờ CŨNG
     * cần ExerciseAssignment ACTIVE như ASSIGNED (unified gate, xem
     * ExerciseAttemptService#startAttempt).
     */
    @Test
    void startAttempt_UC27_MainFlow_startsSelfPracticeWithAssignment() {
        ExerciseResponse exercise = createSelfPracticeExerciseWithOneMcQuestion(true, null);

        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());

        assertThat(attempt.status()).isEqualTo("IN_PROGRESS");
        assertThat(attempt.exerciseAssignmentId()).isNotNull();
        assertThat(attempt.attemptNumber()).isEqualTo(1);
    }

    /** Kho đề: SELF_PRACTICE chưa được giao (Đề chưa gán lớp/chưa deliverToClass) thì vẫn chặn — mirror startAttempt_rejectsWhenAssignedExerciseNotAssignedToStudentClass cho ASSIGNED. */
    @Test
    void startAttempt_UC27_A_rejectsSelfPracticeWithoutAssignment() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Ôn tập chưa giao", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("1"), null, true, null, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());
        // Chưa gọi examService.assignToClass/deliverToClass -> chưa có ExerciseAssignment nào.

        // assignmentId=null: Đề chưa publish -> startAttempt chặn ở bước kiểm tra exercise.getStatus()
        // trước khi kịp dùng assignmentId (xem ExerciseAttemptService#startAttempt).
        assertThatThrownBy(() -> exerciseAttemptService.startAttempt(exercise.id(), null, studentUser.getId()))
                .isInstanceOf(ExerciseNotAvailableException.class);
    }

    @Test
    void startAttempt_rejectsWhenAssignedExerciseNotAssignedToStudentClass() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal("10"), null, true, null, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());
        // chưa gọi deliverToClass -> chưa giao cho lớp nào

        // assignmentId=null: Đề chưa publish -> startAttempt chặn ở bước kiểm tra exercise.getStatus()
        // trước khi kịp dùng assignmentId (xem ExerciseAttemptService#startAttempt).
        assertThatThrownBy(() -> exerciseAttemptService.startAttempt(exercise.id(), null, studentUser.getId()))
                .isInstanceOf(ExerciseNotAvailableException.class);
    }

    @Test
    void submitAttempt_UC24_A3_allMultipleChoiceFullyGradesImmediately() {
        QuestionResponse mc1 = createMcQuestion();
        QuestionResponse mc2 = createMcQuestion();
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc1, mc2), null, false, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());

        answerCorrectly(attempt.id(), mc1);
        answerCorrectly(attempt.id(), mc2);
        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.status()).isEqualTo("FULLY_GRADED");
        assertThat(submitted.totalScore()).isEqualByComparingTo("2.0");
        assertThat(submitted.autoGradeScore()).isEqualByComparingTo("2.0");
    }

    @Test
    void submitAttempt_UC24_MainFlow_essayPendingLeavesTotalScoreNull() {
        QuestionResponse mc = createMcQuestion();
        QuestionResponse essay = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("ESSAY", "WRITING", "MEDIUM", "Viết đoạn văn.", null, null, null,
                        null, null, new BigDecimal("2.0"), null, null, null, null),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc, essay), null, false, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());

        answerCorrectly(attempt.id(), mc);
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(essay.id(), "Bài làm của em...", null, null, null), studentUser.getId());
        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.status()).isEqualTo("AUTO_GRADED");
        assertThat(submitted.totalScore()).isNull();
        assertThat(submitted.autoGradeScore()).isEqualByComparingTo("1.0");
    }

    @Test
    void submitAttempt_UC24_A1_rejectsPastDeadlineWhenLateNotAllowed() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), OffsetDateTime.now().minusDays(1), false, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        answerCorrectly(attempt.id(), mc);

        assertThatThrownBy(() -> exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId()))
                .isInstanceOf(SubmissionPastDeadlineException.class);
    }

    @Test
    void submitAttempt_UC24_A1_allowsLateSubmissionWhenConfigured() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), OffsetDateTime.now().minusDays(1), true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        answerCorrectly(attempt.id(), mc);

        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.isLateSubmission()).isTrue();
    }

    @Test
    void startAttempt_UC24_A2_rejectsRetakeWhenNotAllowed() {
        ExerciseResponse exercise = createSelfPracticeExerciseWithOneMcQuestion(false, null);
        ExerciseAttemptResponse first = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        exerciseAttemptService.submitAttempt(first.id(), studentUser.getId());

        assertThatThrownBy(() -> exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId()))
                .isInstanceOf(RetakeNotAllowedException.class);
    }

    @Test
    void startAttempt_UC24_A2_allowsRetakeWithinMaxAttempts() {
        ExerciseResponse exercise = createSelfPracticeExerciseWithOneMcQuestion(true, 2);
        ExerciseAttemptResponse first = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        exerciseAttemptService.submitAttempt(first.id(), studentUser.getId());

        ExerciseAttemptResponse second = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());

        assertThat(second.attemptNumber()).isEqualTo(2);
        assertThatThrownBy(() -> exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId()))
                .isInstanceOf(RetakeNotAllowedException.class);
    }

    @Test
    void saveAnswer_rejectsWhenAttemptAlreadySubmitted() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = createSelfPracticeExerciseWithOneMcQuestion(true, null, mc);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        answerCorrectly(attempt.id(), mc);
        exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThatThrownBy(() -> exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(mc.id(), null, List.of(mc.choices().get(1).id()), null, null), studentUser.getId()))
                .isInstanceOf(AttemptNotEditableException.class);
    }

    @Test
    void submitAttempt_UC24_MainFlow_exposesCorrectAnswersButNoExplanationWhenAnsweredCorrectly() {
        QuestionResponse mc = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("MULTIPLE_CHOICE", "GRAMMAR", "EASY",
                        "She ___ to school.", null, null, null, "Vì chủ ngữ số ít nên dùng 'goes'.", null,
                        new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", false, 1), new QuestionChoiceRequest("B", "goes", true, 2)), null, null),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        answerCorrectly(attempt.id(), mc);
        exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);

        Long correctChoiceId = mc.choices().stream().filter(c -> c.isCorrect()).findFirst().orElseThrow().id();
        assertThat(answer.correctChoiceIds()).containsExactly(correctChoiceId);
        // Câu tự chấm trả lời ĐÚNG — explanation KHÔNG hiện (chỉ hiện khi sai, xem ExerciseAttemptService.toResponse).
        assertThat(answer.explanation()).isNull();
    }

    @Test
    void submitAttempt_UC24_MainFlow_exposesExplanationWhenAutoGradedQuestionAnsweredIncorrectly() {
        QuestionResponse mc = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("MULTIPLE_CHOICE", "GRAMMAR", "EASY",
                        "She ___ to school.", null, null, null, "Vì chủ ngữ số ít nên dùng 'goes'.", null,
                        new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", false, 1), new QuestionChoiceRequest("B", "goes", true, 2)), null, null),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        Long wrongChoiceId = mc.choices().stream().filter(c -> !c.isCorrect()).findFirst().orElseThrow().id();
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(mc.id(), null, List.of(wrongChoiceId), null, null), studentUser.getId());
        exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);

        assertThat(answer.isCorrect()).isFalse();
        assertThat(answer.explanation()).isEqualTo("Vì chủ ngữ số ít nên dùng 'goes'.");
    }

    @Test
    void submitAttempt_UC24_MainFlow_essayExplanationAlwaysShownRegardlessOfCorrectness() {
        QuestionResponse essay = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("ESSAY", "WRITING", "MEDIUM", "Viết đoạn văn.", null, null, null,
                        "Chú ý thì hiện tại đơn.", null, new BigDecimal("2.0"), null, null, null, null),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(essay), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(essay.id(), "Bài làm của em...", null, null, null), studentUser.getId());
        exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);

        // ESSAY chưa chấm tay (correct luôn null) — vẫn giữ hành vi cũ: luôn hiện explanation khi reveal.
        assertThat(answer.explanation()).isEqualTo("Chú ý thì hiện tại đơn.");
    }

    @Test
    void submitAttempt_UC24_A4_hidesCorrectAnswersBeforeLastAllowedAttemptWhenMaxAttemptsConfigured() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = createSelfPracticeExerciseWithOneMcQuestion(true, 2, mc);

        ExerciseAttemptResponse firstAttempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        answerCorrectly(firstAttempt.id(), mc);
        exerciseAttemptService.submitAttempt(firstAttempt.id(), studentUser.getId());

        StudentAnswerResponse firstAnswer = exerciseAttemptService.listAnswers(firstAttempt.id(), studentUser.getId()).get(0);
        // Còn lượt làm lại (attemptNumber=1 < maxAttempts=2) — đáp án đúng/giải thích PHẢI ẩn dù đã nộp bài.
        assertThat(firstAnswer.correctChoiceIds()).isNull();
        assertThat(firstAnswer.correctAnswerText()).isNull();
    }

    @Test
    void submitAttempt_UC24_A4_showsCorrectAnswersOnLastAllowedAttemptWhenMaxAttemptsConfigured() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = createSelfPracticeExerciseWithOneMcQuestion(true, 2, mc);

        // Lượt 1 trả lời SAI (V89: bản giao chỉ đóng khi ĐẠT ngưỡng — cần giữ ACTIVE để còn lượt 2).
        ExerciseAttemptResponse firstAttempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        Long wrongChoiceId = mc.choices().stream().filter(c -> !c.isCorrect()).findFirst().orElseThrow().id();
        exerciseAttemptService.saveAnswer(firstAttempt.id(),
                new SaveAnswerRequest(mc.id(), null, List.of(wrongChoiceId), null, null), studentUser.getId());
        exerciseAttemptService.submitAttempt(firstAttempt.id(), studentUser.getId());

        ExerciseAttemptResponse secondAttempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        answerCorrectly(secondAttempt.id(), mc);
        exerciseAttemptService.submitAttempt(secondAttempt.id(), studentUser.getId());

        StudentAnswerResponse secondAnswer = exerciseAttemptService.listAnswers(secondAttempt.id(), studentUser.getId()).get(0);
        // Lượt làm cuối cùng (attemptNumber=2 == maxAttempts=2) — đáp án đúng PHẢI hiện.
        Long correctChoiceId = mc.choices().stream().filter(c -> c.isCorrect()).findFirst().orElseThrow().id();
        assertThat(secondAnswer.correctChoiceIds()).containsExactly(correctChoiceId);
    }

    @Test
    void submitAttempt_UC24_A4_essayAnswersIgnoreMaxAttemptsGate() {
        QuestionResponse essay = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("ESSAY", "WRITING", "MEDIUM", "Viết đoạn văn.", null, null, null,
                        "Chú ý thì hiện tại đơn.", null, new BigDecimal("2.0"), null, null, null, null),
                teacher.getId());
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Ôn tập", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("2"), null, true, 2, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(essay.id(), 1, new BigDecimal("2.0")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());

        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(essay.id(), "Bài làm của em...", null, null, null), studentUser.getId());
        exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);
        // Câu tự luận/Nói chưa áp dụng quy tắc ẩn theo max_attempts — vẫn hiện explanation dù còn lượt làm lại (attemptNumber=1 < maxAttempts=2).
        assertThat(answer.explanation()).isEqualTo("Chú ý thì hiện tại đơn.");
    }

    @Test
    void submitAttempt_UC27_MainFlow_fillInBlankAutoGradesCaseInsensitively() {
        QuestionResponse fillIn = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("FILL_IN_BLANK", "GRAMMAR", "EASY",
                        "Thủ đô nước Pháp là ___.", null, null, null, null, "Paris",
                        new BigDecimal("1.0"), null, null, null, null),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(fillIn), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(fillIn.id(), " paris ", null, null, null), studentUser.getId());

        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.status()).isEqualTo("FULLY_GRADED");
        assertThat(submitted.totalScore()).isEqualByComparingTo("1.0");
        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);
        assertThat(answer.isCorrect()).isTrue();
        assertThat(answer.correctAnswerText()).isEqualTo("Paris");
    }

    @Test
    void submitAttempt_UC27_A_fillInBlankGradesZeroWhenAnswerDoesNotExactlyMatch() {
        QuestionResponse fillIn = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("FILL_IN_BLANK", "GRAMMAR", "EASY",
                        "Thủ đô nước Pháp là ___.", null, null, null, null, "Paris",
                        new BigDecimal("1.0"), null, null, null, null),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(fillIn), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(fillIn.id(), "Pariss", null, null, null), studentUser.getId());

        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.status()).isEqualTo("FULLY_GRADED");
        assertThat(submitted.totalScore()).isEqualByComparingTo("0");
        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);
        assertThat(answer.isCorrect()).isFalse();
    }

    /** V78 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04): Điền từ - Hộp từ vựng. */
    @Test
    void submitAttempt_MainFlow_wordBankGradesCorrectOrderedAnswers() {
        QuestionResponse wordBank = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("WORD_BANK", "GRAMMAR", "EASY",
                        "She ___ ___ school every day.", null, null, null, null, null,
                        new BigDecimal("1.0"), null, null,
                        Map.of("blanks", List.of("goes", "to")), null),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(wordBank), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(wordBank.id(), null, null, null, List.of("goes", "to")), studentUser.getId());

        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.status()).isEqualTo("FULLY_GRADED");
        assertThat(submitted.totalScore()).isEqualByComparingTo("1.0");
        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);
        assertThat(answer.isCorrect()).isTrue();
        assertThat(answer.correctStructuredContent()).containsEntry("blanks", List.of("goes", "to"));
    }

    @Test
    void submitAttempt_A_wordBankGradesIncorrectWhenOrderWrong() {
        QuestionResponse wordBank = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("WORD_BANK", "GRAMMAR", "EASY",
                        "She ___ ___ school every day.", null, null, null, null, null,
                        new BigDecimal("1.0"), null, null,
                        Map.of("blanks", List.of("goes", "to")), null),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(wordBank), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(wordBank.id(), null, null, null, List.of("to", "goes")), studentUser.getId());

        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.status()).isEqualTo("FULLY_GRADED");
        assertThat(submitted.totalScore()).isEqualByComparingTo("0");
        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);
        assertThat(answer.isCorrect()).isFalse();
    }

    /** V78 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04): Sắp xếp câu. */
    @Test
    void submitAttempt_MainFlow_sentenceBuildingGradesCorrectOrder() {
        QuestionResponse sentence = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("SENTENCE_BUILDING", "GRAMMAR", "EASY",
                        "Sắp xếp thành câu hoàn chỉnh.", null, null, null, null, null,
                        new BigDecimal("1.0"), null, null,
                        Map.of("chunks", List.of("This", "is", "a test")), null),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(sentence), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(sentence.id(), null, null, null, List.of("This", "is", "a test")), studentUser.getId());

        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.status()).isEqualTo("FULLY_GRADED");
        assertThat(submitted.totalScore()).isEqualByComparingTo("1.0");
    }

    /**
     * V78 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04): "Nghe & nộp audio" (FOREIGN)
     * tái dùng questionType=SPEAKING + skill=LISTENING — vẫn PHẢI chờ chấm tay như Speaking oral gốc,
     * KHÔNG được lọt vào auto-grade dù skill khác nhau (xem AUTO_GRADABLE_TYPES).
     */
    @Test
    void submitAttempt_MainFlow_listeningAudioSubmissionStaysManuallyGraded() {
        QuestionResponse listening = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("SPEAKING", "LISTENING", "MEDIUM", "Nghe và ghi âm trả lời.",
                        "https://media.pps.edu.vn/lms/questions/audio/prompt.mp3", null, null, null, null,
                        new BigDecimal("2.0"), null, null, null, null),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(listening), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(listening.id(), null, null,
                        "https://media.pps.edu.vn/lms/exercise-answer-submissions/audio/answer.mp3", null),
                studentUser.getId());

        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.status()).isEqualTo("AUTO_GRADED");
        assertThat(submitted.totalScore()).isNull();
        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);
        assertThat(answer.isAutoGradable()).isFalse();
    }

    @Test
    void submitAttempt_UC24_A_hidesCorrectAnswersWhenShowCorrectAnswersFalse() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), null, false, true, false);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        answerCorrectly(attempt.id(), mc);
        exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);

        assertThat(answer.correctChoiceIds()).isNull();
    }

    @Test
    void submitAttempt_UC24_hidesCorrectAnswersWhileAttemptStillInProgress() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId());
        answerCorrectly(attempt.id(), mc);

        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);

        assertThat(answer.correctChoiceIds()).isNull();
    }

    @Test
    void listMyAssignedExercises_MainFlow_returnsAssignedExercisesForEnrolledClasses() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), null, false, true);

        List<AssignedExerciseResponse> assigned = exerciseAttemptService.listMyAssignedExercises(studentUser.getId(), null);

        assertThat(assigned).extracting(AssignedExerciseResponse::exerciseId).contains(exercise.id());
        assertThat(assigned).filteredOn(a -> a.exerciseId().equals(exercise.id()))
                .first().satisfies(a -> assertThat(a.classId()).isEqualTo(schoolClass.id()));
    }

    @Test
    void listMyAssignedExercises_doesNotReturnAssignmentsForClassStudentNotEnrolledIn() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra lớp khác", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal("10"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());
        Site otherSite = newSite();
        ClassResponse otherClass = classService.create(new CreateClassRequest(classCode(), "9B1", otherSite.getId(),
                activeCurriculum.id(), "OPEN", 20, null, LocalDate.now(), null, null), headAcademic.getId());
        classService.assignTeacher(otherClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());
        examService.assignToClass(defaultExam.id(), otherClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        exerciseService.deliverToClass(exercise.id(), otherClass.id(), null, teacher.getId());

        List<AssignedExerciseResponse> assigned = exerciseAttemptService.listMyAssignedExercises(studentUser.getId(), null);

        assertThat(assigned).extracting(AssignedExerciseResponse::exerciseId).doesNotContain(exercise.id());
    }

    @Test
    void listMyAssignedExercises_A_filtersByClassIdWhenProvided() {
        QuestionResponse mc = createMcQuestion();
        assignedExerciseWithQuestions(List.of(mc), null, false, true);

        List<AssignedExerciseResponse> filteredOut = exerciseAttemptService.listMyAssignedExercises(studentUser.getId(), 999_999L);

        assertThat(filteredOut).isEmpty();
    }

    /** Bổ sung (đã xác nhận với người dùng 2026-07-29): bài đã giao ở lớp cũ vẫn XEM được sau khi chuyển lớp. */
    @Test
    void listMyAssignedExercises_boSung_stillVisibleAfterTransferToAnotherClass() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), null, false, true);
        ClassResponse otherClass = classService.create(new CreateClassRequest(classCode(), "9B1", newSite().getId(),
                activeCurriculum.id(), "OPEN", 20, null, LocalDate.now(), null, null), headAcademic.getId());
        studentService.recordTransfer(student.getId(),
                new RecordTransferRequest("CLASS_CHANGE", schoolClass.id(), otherClass.id(), null, LocalDate.now(), "Chuyển lớp test"),
                headAcademic.getId());

        List<AssignedExerciseResponse> assigned = exerciseAttemptService.listMyAssignedExercises(studentUser.getId(), null);

        assertThat(assigned).extracting(AssignedExerciseResponse::exerciseId).contains(exercise.id());
    }

    /**
     * Bổ sung (đã xác nhận với người dùng 2026-07-29): CHỈ mở lại xem, KHÔNG
     * mở lại làm bài mới cho lớp cũ — startAttempt vẫn chặn như trước.
     */
    @Test
    void startAttempt_boSung_stillBlockedForOldClassExerciseAfterTransfer() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), null, false, true);
        ClassResponse otherClass = classService.create(new CreateClassRequest(classCode(), "9B2", newSite().getId(),
                activeCurriculum.id(), "OPEN", 20, null, LocalDate.now(), null, null), headAcademic.getId());
        studentService.recordTransfer(student.getId(),
                new RecordTransferRequest("CLASS_CHANGE", schoolClass.id(), otherClass.id(), null, LocalDate.now(), "Chuyển lớp test"),
                headAcademic.getId());

        // assignmentId: bản giao GỐC (schoolClass cũ) vẫn ACTIVE — resolveActiveAssignmentForStudent
        // chặn vì học sinh không còn enrollment ACTIVE ở lớp đó (đã chuyển sang otherClass).
        assertThatThrownBy(() -> exerciseAttemptService.startAttempt(exercise.id(), activeAssignmentId(exercise.id()), studentUser.getId()))
                .isInstanceOf(ExerciseNotAvailableException.class);
    }

    /**
     * V89 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05):
     * BTVN dưới ngưỡng đạt (mặc định 70% từ V100) phải làm lại — bản giao vẫn ACTIVE
     * (chưa COMPLETED) nên startAttempt còn cho làm lại lượt tiếp theo.
     */
    @Test
    void submitAttempt_boSung_belowThresholdKeepsAssignmentActiveForRetake() {
        QuestionResponse mc1 = createMcQuestion();
        QuestionResponse mc2 = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "BTVN", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("2"), null, true, null, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc1.id(), 1, new BigDecimal("1.0")), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc2.id(), 2, new BigDecimal("1.0")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        ExerciseAssignment assignment = exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());

        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), assignment.getId(), studentUser.getId());
        answerCorrectly(attempt.id(), mc1); // chỉ đúng 1/2 -> 50%, dưới ngưỡng mặc định 70%
        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.percentage()).isEqualByComparingTo("50.00");
        assertThat(submitted.passed()).isFalse();
        ExerciseAssignment refreshed = exerciseAssignmentRepository.findById(assignment.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(ExerciseAssignment.Status.ACTIVE);

        ExerciseAttemptResponse retry = exerciseAttemptService.startAttempt(exercise.id(), assignment.getId(), studentUser.getId());
        assertThat(retry.attemptNumber()).isEqualTo(2);
    }

    /**
     * V89 (điều chỉnh 2026-08-19): đạt ngưỡng nhưng ĐỀ CÒN LƯỢT làm lại
     * (allowRetake=true, maxAttempts=null = không giới hạn) -> bản giao vẫn
     * ACTIVE, học sinh tự nguyện làm lại được để thử điểm cao hơn (VD đạt
     * 80% muốn thử lại lấy 100%) — không còn tự đóng bản giao ngay khi đạt.
     */
    @Test
    void submitAttempt_boSung_atOrAboveThresholdKeepsAssignmentActiveWhenRetakeAvailable() {
        QuestionResponse mc1 = createMcQuestion();
        QuestionResponse mc2 = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "BTVN", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("2"), null, true, null, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc1.id(), 1, new BigDecimal("1.0")), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc2.id(), 2, new BigDecimal("1.0")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        ExerciseAssignment assignment = exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());

        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), assignment.getId(), studentUser.getId());
        answerCorrectly(attempt.id(), mc1);
        answerCorrectly(attempt.id(), mc2);
        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.percentage()).isEqualByComparingTo("100.00");
        assertThat(submitted.passed()).isTrue();
        ExerciseAssignment refreshed = exerciseAssignmentRepository.findById(assignment.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(ExerciseAssignment.Status.ACTIVE);

        ExerciseAttemptResponse retry = exerciseAttemptService.startAttempt(exercise.id(), assignment.getId(), studentUser.getId());
        assertThat(retry.attemptNumber()).isEqualTo(2);
    }

    /**
     * V89 (điều chỉnh 2026-08-19): đạt ngưỡng và ĐÃ HẾT lượt làm lại
     * (maxAttempts=1) -> đóng bản giao (COMPLETED) như cũ, vì không còn lượt
     * nào để làm lại nữa (khác trường hợp còn lượt ở test phía trên).
     */
    @Test
    void submitAttempt_boSung_atOrAboveThresholdCompletesAssignmentWhenNoAttemptsLeft() {
        QuestionResponse mc1 = createMcQuestion();
        QuestionResponse mc2 = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "BTVN", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("2"), null, true, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc1.id(), 1, new BigDecimal("1.0")), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc2.id(), 2, new BigDecimal("1.0")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        ExerciseAssignment assignment = exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());

        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), assignment.getId(), studentUser.getId());
        answerCorrectly(attempt.id(), mc1);
        answerCorrectly(attempt.id(), mc2);
        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.passed()).isTrue();
        ExerciseAssignment refreshed = exerciseAssignmentRepository.findById(assignment.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(ExerciseAssignment.Status.COMPLETED);
    }

    /** V89: ngưỡng đạt cấu hình được theo từng Bài — không cố định 80% khi Giáo viên chọn khác. */
    @Test
    void submitAttempt_boSung_usesCustomPassThresholdWhenConfigured() {
        QuestionResponse mc1 = createMcQuestion();
        QuestionResponse mc2 = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "BTVN ngưỡng thấp", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("2"), null, true, null, true, new BigDecimal("40")), teacher.getId());
        assertThat(exercise.passThresholdPercent()).isEqualByComparingTo("40");
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc1.id(), 1, new BigDecimal("1.0")), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc2.id(), 2, new BigDecimal("1.0")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        ExerciseAssignment assignment = exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());

        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), assignment.getId(), studentUser.getId());
        answerCorrectly(attempt.id(), mc1); // 50% -> dưới 70% mặc định nhưng TRÊN ngưỡng 40% đã cấu hình
        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.passed()).isTrue();
        // Đề còn lượt làm lại (allowRetake=true, maxAttempts=null) -> bản giao vẫn ACTIVE, xem
        // submitAttempt_boSung_atOrAboveThresholdKeepsAssignmentActiveWhenRetakeAvailable ở trên.
        ExerciseAssignment refreshed = exerciseAssignmentRepository.findById(assignment.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(ExerciseAssignment.Status.ACTIVE);
    }

    /** V89/V100: không truyền passThresholdPercent khi tạo Bài -> mặc định 70%. */
    @Test
    void createExercise_boSung_defaultsPassThresholdTo70PercentWhenNotSpecified() {
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "BTVN", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("1"), null, true, null, true), teacher.getId());

        assertThat(exercise.passThresholdPercent()).isEqualByComparingTo("70.00");
    }

    private QuestionResponse createMcQuestion() {
        return examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("MULTIPLE_CHOICE", "GRAMMAR", "EASY",
                        "She ___ to school.", null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", false, 1), new QuestionChoiceRequest("B", "goes", true, 2)), null, null),
                teacher.getId());
    }

    private void answerCorrectly(Long attemptId, QuestionResponse question) {
        Long correctChoiceId = question.choices().stream().filter(c -> c.isCorrect()).findFirst().orElseThrow().id();
        exerciseAttemptService.saveAnswer(attemptId,
                new SaveAnswerRequest(question.id(), null, List.of(correctChoiceId), null, null), studentUser.getId());
    }

    /**
     * V128: startAttempt giờ nhận thẳng assignmentId thay vì tự đoán bản giao ACTIVE — tra bản giao
     * ACTIVE (duy nhất, vì mỗi Đề trong file test này chỉ được deliverToClass 1 lần cho schoolClass)
     * để tái dùng ở các test không cần giữ tham chiếu ExerciseAssignment tường minh.
     */
    private Long activeAssignmentId(Long exerciseId) {
        return exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                exerciseId, schoolClass.id(), ExerciseAssignment.Status.ACTIVE).get(0).getId();
    }

    /**
     * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * SELF_PRACTICE giờ CŨNG cần Đề đã gán lớp + ExerciseAssignment ACTIVE
     * như ASSIGNED (unified gate) — helper này giờ LUÔN gán Đề + deliverToClass,
     * mirror hệt assignedExerciseWithQuestions(publish=true) bên dưới.
     */
    private ExerciseResponse createSelfPracticeExerciseWithOneMcQuestion(boolean allowRetake, Integer maxAttempts) {
        return createSelfPracticeExerciseWithOneMcQuestion(allowRetake, maxAttempts, createMcQuestion());
    }

    private ExerciseResponse createSelfPracticeExerciseWithOneMcQuestion(boolean allowRetake, Integer maxAttempts, QuestionResponse mc) {
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Ôn tập", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("1"), null, allowRetake, maxAttempts, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());
        return exercise;
    }

    private ExerciseResponse assignedExerciseWithQuestions(List<QuestionResponse> questions, OffsetDateTime dueAt,
                                                             boolean lateAllowed, boolean publish) {
        return assignedExerciseWithQuestions(questions, dueAt, lateAllowed, publish, true);
    }

    private ExerciseResponse assignedExerciseWithQuestions(List<QuestionResponse> questions, OffsetDateTime dueAt,
                                                             boolean lateAllowed, boolean publish, boolean showCorrectAnswers) {
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal(questions.size()), null, false, 1, showCorrectAnswers), teacher.getId());
        int order = 1;
        for (QuestionResponse q : questions) {
            exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(q.id(), order++, new BigDecimal("1.0")), teacher.getId());
        }
        if (publish) {
            examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
            commitCurrentTransactionAndStartNew();
            ExerciseAssignment assignment = exerciseService.deliverToClass(exercise.id(), schoolClass.id(), dueAt, teacher.getId());
            if (lateAllowed) {
                assignment.setLateSubmissionAllowed(true);
                exerciseAssignmentRepository.save(assignment);
            }
        }
        return exercise;
    }

    private String curriculumCode() {
        return "CUR-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-" + SEQ.incrementAndGet();
    }

    private String exerciseCode() {
        return "EX-" + SEQ.incrementAndGet();
    }

    private String examCode() {
        return "KD-" + SEQ.incrementAndGet();
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private Site newSite() {
        Site s = new Site();
        s.setCode("SITE-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
