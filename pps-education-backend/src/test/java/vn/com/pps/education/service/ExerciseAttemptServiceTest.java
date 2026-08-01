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
import vn.com.pps.education.dto.CreateExamRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.ExamResponse;
import vn.com.pps.education.dto.ExerciseAttemptResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.QuestionBankResponse;
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
    private QuestionBankService questionBankService;

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
    private QuestionBankResponse bank;
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
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());

        bank = questionBankService.createBank(
                new CreateQuestionBankRequest(bankCode(), "Ngân hàng", activeCurriculum.id(), null, "A1"), teacher.getId());

        studentUser = newUser("student");
        student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-TEST-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        student = studentRepository.save(student);
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        defaultExam = examService.createExam(
                new CreateExamRequest(examCode(), "Đề mặc định", activeCurriculum.id()), teacher.getId());
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

        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());

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

        assertThatThrownBy(() -> exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId()))
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

        assertThatThrownBy(() -> exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId()))
                .isInstanceOf(ExerciseNotAvailableException.class);
    }

    @Test
    void submitAttempt_UC24_A3_allMultipleChoiceFullyGradesImmediately() {
        QuestionResponse mc1 = createMcQuestion();
        QuestionResponse mc2 = createMcQuestion();
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc1, mc2), null, false, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());

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
        QuestionResponse essay = questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "ESSAY", "WRITING", "MEDIUM", "Viết đoạn văn.", null, null, null,
                        null, null, new BigDecimal("2.0"), null, null),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc, essay), null, false, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());

        answerCorrectly(attempt.id(), mc);
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(essay.id(), "Bài làm của em...", null, null), studentUser.getId());
        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.status()).isEqualTo("AUTO_GRADED");
        assertThat(submitted.totalScore()).isNull();
        assertThat(submitted.autoGradeScore()).isEqualByComparingTo("1.0");
    }

    @Test
    void submitAttempt_UC24_A1_rejectsPastDeadlineWhenLateNotAllowed() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), OffsetDateTime.now().minusDays(1), false, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());
        answerCorrectly(attempt.id(), mc);

        assertThatThrownBy(() -> exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId()))
                .isInstanceOf(SubmissionPastDeadlineException.class);
    }

    @Test
    void submitAttempt_UC24_A1_allowsLateSubmissionWhenConfigured() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), OffsetDateTime.now().minusDays(1), true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());
        answerCorrectly(attempt.id(), mc);

        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.isLateSubmission()).isTrue();
    }

    @Test
    void startAttempt_UC24_A2_rejectsRetakeWhenNotAllowed() {
        ExerciseResponse exercise = createSelfPracticeExerciseWithOneMcQuestion(false, null);
        ExerciseAttemptResponse first = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());
        exerciseAttemptService.submitAttempt(first.id(), studentUser.getId());

        assertThatThrownBy(() -> exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId()))
                .isInstanceOf(RetakeNotAllowedException.class);
    }

    @Test
    void startAttempt_UC24_A2_allowsRetakeWithinMaxAttempts() {
        ExerciseResponse exercise = createSelfPracticeExerciseWithOneMcQuestion(true, 2);
        ExerciseAttemptResponse first = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());
        exerciseAttemptService.submitAttempt(first.id(), studentUser.getId());

        ExerciseAttemptResponse second = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());

        assertThat(second.attemptNumber()).isEqualTo(2);
        assertThatThrownBy(() -> exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId()))
                .isInstanceOf(RetakeNotAllowedException.class);
    }

    @Test
    void saveAnswer_rejectsWhenAttemptAlreadySubmitted() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = createSelfPracticeExerciseWithOneMcQuestion(true, null, mc);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());
        answerCorrectly(attempt.id(), mc);
        exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThatThrownBy(() -> exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(mc.id(), null, List.of(mc.choices().get(1).id()), null), studentUser.getId()))
                .isInstanceOf(AttemptNotEditableException.class);
    }

    @Test
    void submitAttempt_UC24_MainFlow_exposesCorrectAnswersButNoExplanationWhenAnsweredCorrectly() {
        QuestionResponse mc = questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "MULTIPLE_CHOICE", "GRAMMAR", "EASY",
                        "She ___ to school.", null, null, null, "Vì chủ ngữ số ít nên dùng 'goes'.", null,
                        new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", false, 1), new QuestionChoiceRequest("B", "goes", true, 2))),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());
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
        QuestionResponse mc = questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "MULTIPLE_CHOICE", "GRAMMAR", "EASY",
                        "She ___ to school.", null, null, null, "Vì chủ ngữ số ít nên dùng 'goes'.", null,
                        new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", false, 1), new QuestionChoiceRequest("B", "goes", true, 2))),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());
        Long wrongChoiceId = mc.choices().stream().filter(c -> !c.isCorrect()).findFirst().orElseThrow().id();
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(mc.id(), null, List.of(wrongChoiceId), null), studentUser.getId());
        exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);

        assertThat(answer.isCorrect()).isFalse();
        assertThat(answer.explanation()).isEqualTo("Vì chủ ngữ số ít nên dùng 'goes'.");
    }

    @Test
    void submitAttempt_UC24_MainFlow_essayExplanationAlwaysShownRegardlessOfCorrectness() {
        QuestionResponse essay = questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "ESSAY", "WRITING", "MEDIUM", "Viết đoạn văn.", null, null, null,
                        "Chú ý thì hiện tại đơn.", null, new BigDecimal("2.0"), null, null),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(essay), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(essay.id(), "Bài làm của em...", null, null), studentUser.getId());
        exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);

        // ESSAY chưa chấm tay (correct luôn null) — vẫn giữ hành vi cũ: luôn hiện explanation khi reveal.
        assertThat(answer.explanation()).isEqualTo("Chú ý thì hiện tại đơn.");
    }

    @Test
    void submitAttempt_UC27_MainFlow_fillInBlankAutoGradesCaseInsensitively() {
        QuestionResponse fillIn = questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "FILL_IN_BLANK", "GRAMMAR", "EASY",
                        "Thủ đô nước Pháp là ___.", null, null, null, null, "Paris",
                        new BigDecimal("1.0"), null, null),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(fillIn), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(fillIn.id(), " paris ", null, null), studentUser.getId());

        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.status()).isEqualTo("FULLY_GRADED");
        assertThat(submitted.totalScore()).isEqualByComparingTo("1.0");
        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);
        assertThat(answer.isCorrect()).isTrue();
        assertThat(answer.correctAnswerText()).isEqualTo("Paris");
    }

    @Test
    void submitAttempt_UC27_A_fillInBlankGradesZeroWhenAnswerDoesNotExactlyMatch() {
        QuestionResponse fillIn = questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "FILL_IN_BLANK", "GRAMMAR", "EASY",
                        "Thủ đô nước Pháp là ___.", null, null, null, null, "Paris",
                        new BigDecimal("1.0"), null, null),
                teacher.getId());
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(fillIn), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(fillIn.id(), "Pariss", null, null), studentUser.getId());

        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(submitted.status()).isEqualTo("FULLY_GRADED");
        assertThat(submitted.totalScore()).isEqualByComparingTo("0");
        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);
        assertThat(answer.isCorrect()).isFalse();
    }

    @Test
    void submitAttempt_UC24_A_hidesCorrectAnswersWhenShowCorrectAnswersFalse() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), null, false, true, false);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());
        answerCorrectly(attempt.id(), mc);
        exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        StudentAnswerResponse answer = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).get(0);

        assertThat(answer.correctChoiceIds()).isNull();
    }

    @Test
    void submitAttempt_UC24_hidesCorrectAnswersWhileAttemptStillInProgress() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = assignedExerciseWithQuestions(List.of(mc), null, false, true, true);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());
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
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());
        examService.assignToClass(defaultExam.id(), otherClass.id(), teacher.getId());
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

        assertThatThrownBy(() -> exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId()))
                .isInstanceOf(ExerciseNotAvailableException.class);
    }

    private QuestionResponse createMcQuestion() {
        return questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "MULTIPLE_CHOICE", "GRAMMAR", "EASY",
                        "She ___ to school.", null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", false, 1), new QuestionChoiceRequest("B", "goes", true, 2))),
                teacher.getId());
    }

    private void answerCorrectly(Long attemptId, QuestionResponse question) {
        Long correctChoiceId = question.choices().stream().filter(c -> c.isCorrect()).findFirst().orElseThrow().id();
        exerciseAttemptService.saveAnswer(attemptId,
                new SaveAnswerRequest(question.id(), null, List.of(correctChoiceId), null), studentUser.getId());
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

    private String bankCode() {
        return "QB-" + SEQ.incrementAndGet();
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
