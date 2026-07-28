package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.dto.AssignExerciseRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.ExerciseAssignmentResponse;
import vn.com.pps.education.dto.ExerciseQuestionChoiceResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.QuestionBankResponse;
import vn.com.pps.education.dto.QuestionChoiceRequest;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateQuestionBankStatusRequest;
import vn.com.pps.education.dto.UpdateQuestionRequest;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.QuestionLockedException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentAnswerRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC-40: Soạn & giao đề kiểm tra — Main Flow (bước 1-4), A1 (đề có câu tự luận/Nói). */
@Transactional
class ExerciseAuthoringTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private QuestionBankService questionBankService;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

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
    private StudentAnswerRepository studentAnswerRepository;

    @Autowired
    private vn.com.pps.education.repository.ExerciseRepository exerciseRepository;

    @Autowired
    private vn.com.pps.education.repository.QuestionRepository questionRepository;

    @Autowired
    private vn.com.pps.education.repository.ExerciseAttemptRepository exerciseAttemptRepository;

    private User headAcademic;
    private User teacher;
    private CurriculumResponse activeCurriculum;
    private ClassResponse schoolClass;
    private QuestionBankResponse bank;

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
                        LocalDate.now(), null, null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());

        bank = questionBankService.createBank(
                new CreateQuestionBankRequest(bankCode(), "Ngân hàng Ngữ pháp", activeCurriculum.id(), null, "A1"),
                teacher.getId());
    }

    @Test
    void createQuestion_UC40_MainFlow_savesMultipleChoiceWithChoices() {
        QuestionResponse question = createMcQuestion();

        assertThat(question.questionType()).isEqualTo("MULTIPLE_CHOICE");
        assertThat(question.choices()).hasSize(2);
        assertThat(question.choices()).anySatisfy(c -> assertThat(c.isCorrect()).isTrue());
    }

    @Test
    void updateQuestion_rejectsWhenAlreadyHasStudentAnswers() {
        QuestionResponse question = createMcQuestion();
        markQuestionAsAnswered(question.id());

        assertThatThrownBy(() -> questionBankService.updateQuestion(question.id(),
                new UpdateQuestionRequest("Nội dung mới", null, null, null, null, null, null, null, null, null), teacher.getId()))
                .isInstanceOf(QuestionLockedException.class);
    }

    @Test
    void updateQuestion_allowsStatusChangeEvenWhenLocked() {
        QuestionResponse question = createMcQuestion();
        markQuestionAsAnswered(question.id());

        QuestionResponse archived = questionBankService.updateQuestion(question.id(),
                new UpdateQuestionRequest(question.content(), question.audioUrl(), question.imageUrl(),
                        question.referencePassage(), question.explanation(), question.correctAnswerText(), question.defaultPoints(),
                        question.tags(), null, "ARCHIVED"),
                teacher.getId());

        assertThat(archived.status()).isEqualTo("ARCHIVED");
    }

    @Test
    void updateBankStatus_boSung_deactivatesAndReactivatesBank() {
        assertThat(bank.isActive()).isTrue();

        QuestionBankResponse deactivated = questionBankService.updateBankStatus(bank.id(),
                new UpdateQuestionBankStatusRequest(false), teacher.getId());
        assertThat(deactivated.isActive()).isFalse();

        QuestionBankResponse reactivated = questionBankService.updateBankStatus(bank.id(),
                new UpdateQuestionBankStatusRequest(true), teacher.getId());
        assertThat(reactivated.isActive()).isTrue();
    }

    @Test
    void createExercise_UC40_MainFlow_assemblesQuestionsAndFlagsEssayA1() {
        QuestionResponse mc = createMcQuestion();
        QuestionResponse essay = questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "ESSAY", "WRITING", "MEDIUM", "Viết đoạn văn 50 từ.",
                        null, null, null, null, null, new BigDecimal("2.0"), null, null),
                teacher.getId());

        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Đề ôn tập Ngữ pháp 1", activeCurriculum.id(), null,
                        "SELF_PRACTICE", new BigDecimal("10"), 30, true, null, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(essay.id(), 2, new BigDecimal("2.0")), teacher.getId());

        ExerciseResponse withQuestions = exerciseService.getExercise(exercise.id(), teacher.getId());
        assertThat(withQuestions.hasEssayOrSpeaking()).isTrue();
        assertThat(exerciseService.listQuestions(exercise.id(), teacher.getId())).hasSize(2);
    }

    @Test
    void addQuestion_rejectsDuplicateQuestionInSameExercise() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Đề X", activeCurriculum.id(), null, "SELF_PRACTICE",
                        new BigDecimal("1"), null, true, null, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());

        assertThatThrownBy(() -> exerciseService.addQuestion(exercise.id(),
                new AddExerciseQuestionRequest(mc.id(), 2, new BigDecimal("1.0")), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignExercise_UC40_MainFlow_assignsToClassAndPublishes() {
        Student student = enrollStudent();
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra 15 phút", activeCurriculum.id(), null, "ASSIGNED",
                        new BigDecimal("10"), 15, false, 1, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());

        ExerciseAssignmentResponse assignment = exerciseService.assignExercise(exercise.id(),
                new AssignExerciseRequest(schoolClass.id(), null, null, false, null, null), teacher.getId());

        assertThat(assignment.classId()).isEqualTo(schoolClass.id());
        assertThat(exerciseService.getExercise(exercise.id(), teacher.getId()).status()).isEqualTo("PUBLISHED");
    }

    @Test
    void assignExercise_rejectsWhenExerciseIsNotAssignedType() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Bài tự luyện", activeCurriculum.id(), null, "SELF_PRACTICE",
                        new BigDecimal("10"), null, true, null, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());

        assertThatThrownBy(() -> exerciseService.assignExercise(exercise.id(),
                new AssignExerciseRequest(schoolClass.id(), null, null, false, null, null), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignExercise_rejectsWhenActorNotAssignedTeacherForClass() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", activeCurriculum.id(), null, "ASSIGNED",
                        new BigDecimal("10"), null, false, 1, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());

        assertThatThrownBy(() -> exerciseService.assignExercise(exercise.id(),
                new AssignExerciseRequest(schoolClass.id(), null, null, false, null, null), outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void listAssignmentsForClass_boSung_teacherSeesExercisesAssignedToTheirClass() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", activeCurriculum.id(), null, "ASSIGNED",
                        new BigDecimal("10"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());
        exerciseService.assignExercise(exercise.id(),
                new AssignExerciseRequest(schoolClass.id(), null, null, false, null, null), teacher.getId());

        List<ExerciseAssignmentResponse> assignments = exerciseService.listAssignmentsForClass(schoolClass.id(), teacher.getId());

        assertThat(assignments).extracting(ExerciseAssignmentResponse::exerciseId).contains(exercise.id());
    }

    @Test
    void listAssignmentsForClass_rejectsWhenActorNotAssignedTeacherForClass() {
        User outsider = newUser("outsider.list.teacher");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> exerciseService.listAssignmentsForClass(schoolClass.id(), outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void getExercise_boSung_rejectsStudentWithoutActiveAssignmentForAssignedExercise() {
        Student student = enrollStudent();
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", activeCurriculum.id(), null, "ASSIGNED",
                        new BigDecimal("10"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());
        // chưa gọi assignExercise cho lớp của student -> chưa được giao

        assertThatThrownBy(() -> exerciseService.getExercise(exercise.id(), student.getUser().getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> exerciseService.listQuestions(exercise.id(), student.getUser().getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getExercise_boSung_allowsStudentWithActiveAssignment() {
        Student student = enrollStudent();
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", activeCurriculum.id(), null, "ASSIGNED",
                        new BigDecimal("10"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());
        exerciseService.assignExercise(exercise.id(),
                new AssignExerciseRequest(schoolClass.id(), null, null, false, null, null), teacher.getId());

        ExerciseResponse viewed = exerciseService.getExercise(exercise.id(), student.getUser().getId());

        assertThat(viewed.id()).isEqualTo(exercise.id());
        assertThat(exerciseService.listQuestions(exercise.id(), student.getUser().getId())).hasSize(1);
    }

    @Test
    void getExercise_boSung_allowsAnyStudentForPublishedSelfPractice() {
        Student student = enrollStudent();
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Ôn tập tự do", activeCurriculum.id(), null, "SELF_PRACTICE",
                        new BigDecimal("1"), null, true, null, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());
        exerciseService.publishExercise(exercise.id(), teacher.getId());

        ExerciseResponse viewed = exerciseService.getExercise(exercise.id(), student.getUser().getId());

        assertThat(viewed.id()).isEqualTo(exercise.id());
    }

    /**
     * UC-24/UC-27 Main Flow bước 2 (HS trả lời câu trắc nghiệm): danh sách
     * câu hỏi trả cho HS phải kèm phương án để chọn — nhưng TUYỆT ĐỐI không
     * lộ đáp án đúng trước khi nộp (ExerciseQuestionChoiceResponse không có
     * field is_correct).
     */
    @Test
    void listQuestions_UC24_MainFlow_studentGetsChoicesWithoutRevealingCorrectAnswer() {
        Student student = enrollStudent();
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Ôn tập trắc nghiệm", activeCurriculum.id(), null,
                        "SELF_PRACTICE", new BigDecimal("1"), null, true, null, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());
        exerciseService.publishExercise(exercise.id(), teacher.getId());

        var questions = exerciseService.listQuestions(exercise.id(), student.getUser().getId());

        assertThat(questions).hasSize(1);
        var choices = questions.get(0).choices();
        assertThat(choices).extracting(ExerciseQuestionChoiceResponse::choiceLabel).containsExactly("A", "B");
        assertThat(choices).extracting(ExerciseQuestionChoiceResponse::content).containsExactly("go", "goes");
        assertThat(choices).allSatisfy(c -> assertThat(c.id()).isNotNull());
        // ExerciseQuestionChoiceResponse là record KHÔNG có field is_correct -> không thể lộ đáp án đúng cho HS.
    }

    /** Câu ESSAY/SPEAKING/FILL_IN_BLANK không có phương án chọn sẵn -> choices rỗng (không null). */
    @Test
    void listQuestions_UC24_MainFlow_nonChoiceQuestionHasEmptyChoices() {
        Student student = enrollStudent();
        QuestionResponse essay = questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "ESSAY", "WRITING", "MEDIUM", "Viết đoạn văn 50 từ.",
                        null, null, null, null, null, new BigDecimal("2.0"), null, null), teacher.getId());
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Đề tự luận", activeCurriculum.id(), null, "SELF_PRACTICE",
                        new BigDecimal("2"), null, true, null, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(essay.id(), 1, new BigDecimal("2.0")), teacher.getId());
        exerciseService.publishExercise(exercise.id(), teacher.getId());

        var questions = exerciseService.listQuestions(exercise.id(), student.getUser().getId());

        assertThat(questions).hasSize(1);
        assertThat(questions.get(0).questionType()).isEqualTo("ESSAY");
        assertThat(questions.get(0).choices()).isEmpty();
    }

    private QuestionResponse createMcQuestion() {
        return questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "MULTIPLE_CHOICE", "GRAMMAR", "EASY",
                        "She ___ to school every day.", null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(
                                new QuestionChoiceRequest("A", "go", false, 1),
                                new QuestionChoiceRequest("B", "goes", true, 2))),
                teacher.getId());
    }

    /** Giả lập đã có học sinh trả lời — không đi qua UC-24/27 (chưa build), ghi thẳng vào bảng nền tảng đã tạo ở V19. */
    private void markQuestionAsAnswered(Long questionId) {
        vn.com.pps.education.domain.Exercise exercise = exerciseRepository.findById(
                exerciseService.createExercise(
                        new CreateExerciseRequest(exerciseCode(), "Đề dùng để test khóa câu hỏi", activeCurriculum.id(), null,
                                "SELF_PRACTICE", new BigDecimal("1"), null, true, null, true),
                        teacher.getId()).id())
                .orElseThrow();

        vn.com.pps.education.domain.ExerciseAttempt attempt = new vn.com.pps.education.domain.ExerciseAttempt();
        attempt.setExercise(exercise);
        attempt.setStudent(enrollStudent());
        attempt = exerciseAttemptRepository.save(attempt);

        vn.com.pps.education.domain.StudentAnswer answer = new vn.com.pps.education.domain.StudentAnswer();
        answer.setExerciseAttempt(attempt);
        answer.setQuestion(questionRepository.findById(questionId).orElseThrow());
        answer.setAutoGradable(true);
        studentAnswerRepository.save(answer);
    }

    private Student enrollStudent() {
        User studentUser = newUser("student");
        Student student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-TEST-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        student = studentRepository.save(student);
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        return student;
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
