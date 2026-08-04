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
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateExamRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.ExerciseAttemptResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.GradeAnswerRequest;
import vn.com.pps.education.dto.PendingGradingResponse;
import vn.com.pps.education.dto.QuestionBankResponse;
import vn.com.pps.education.dto.QuestionChoiceRequest;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.SaveAnswerRequest;
import vn.com.pps.education.dto.StudentAnswerGradingResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.AnswerNotManuallyGradableException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
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

/** UC-41: Chấm bài thủ công — Main Flow (bước 1-5), A1 (nhiều bài chờ chấm). */
@Transactional
class ManualGradingServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ManualGradingService manualGradingService;

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
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private StudentRepository studentRepository;

    private User headAcademic;
    private User teacher;
    private User studentUser;
    private CurriculumResponse activeCurriculum;
    private ClassResponse schoolClass;
    private QuestionResponse mcQuestion;
    private QuestionResponse essayQuestion;
    private ExerciseAttemptResponse attempt;

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

        QuestionBankResponse bank = questionBankService.createBank(
                new CreateQuestionBankRequest(bankCode(), "Ngân hàng", activeCurriculum.id(), null, "A1"), teacher.getId());

        mcQuestion = questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "MULTIPLE_CHOICE", "GRAMMAR", "EASY", "She ___ to school.",
                        null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", false, 1), new QuestionChoiceRequest("B", "goes", true, 2)), null, null),
                teacher.getId());
        essayQuestion = questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "ESSAY", "WRITING", "MEDIUM", "Viết đoạn văn 50 từ.",
                        null, null, null, null, null, new BigDecimal("2.0"), null, null, null, null),
                teacher.getId());

        studentUser = newUser("student");
        Student student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-TEST-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        student = studentRepository.save(student);
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        var exam = examService.createExam(
                new CreateExamRequest(examCode(), "Đề mặc định", activeCurriculum.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", exam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("3"), null, false, null, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mcQuestion.id(), 1, new BigDecimal("1.0")), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(essayQuestion.id(), 2, new BigDecimal("2.0")), teacher.getId());
        // Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
        // SELF_PRACTICE giờ cũng cần Đề đã gán lớp + ExerciseAssignment ACTIVE (unified gate).
        examService.assignToClass(exam.id(), schoolClass.id(), teacher.getId());
        exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());

        attempt = exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());
        Long correctChoiceId = mcQuestion.choices().stream().filter(c -> c.isCorrect()).findFirst().orElseThrow().id();
        exerciseAttemptService.saveAnswer(attempt.id(), new SaveAnswerRequest(mcQuestion.id(), null, List.of(correctChoiceId), null, null), studentUser.getId());
        exerciseAttemptService.saveAnswer(attempt.id(), new SaveAnswerRequest(essayQuestion.id(), "Bài làm của em.", null, null, null), studentUser.getId());
        attempt = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());
    }

    @Test
    void listPendingGrading_UC41_MainFlow_showsSubmittedEssayAnswer() {
        List<PendingGradingResponse> pending = manualGradingService.listPendingGrading(teacher.getId());

        assertThat(pending).extracting(PendingGradingResponse::questionId).contains(essayQuestion.id());
        assertThat(pending).extracting(PendingGradingResponse::studentId).contains(attempt.studentId());
    }

    @Test
    void gradeAnswer_UC41_MainFlow_gradesEssayAndComputesFinalTotalScore() {
        Long essayAnswerId = manualGradingService.listPendingGrading(teacher.getId()).stream()
                .filter(p -> p.questionId().equals(essayQuestion.id()))
                .findFirst().orElseThrow().studentAnswerId();

        StudentAnswerGradingResponse grading = manualGradingService.gradeAnswer(essayAnswerId,
                new GradeAnswerRequest(new BigDecimal("1.5"), new BigDecimal("2.0"), "Khá tốt, cần thêm ví dụ."), teacher.getId());

        assertThat(grading.score()).isEqualByComparingTo("1.5");
        ExerciseAttemptResponse updated = exerciseAttemptService.getAttempt(attempt.id(), studentUser.getId());
        assertThat(updated.status()).isEqualTo("FULLY_GRADED");
        assertThat(updated.manualGradeScore()).isEqualByComparingTo("1.5");
        assertThat(updated.totalScore()).isEqualByComparingTo("2.5");
        assertThat(manualGradingService.listPendingGrading(teacher.getId()))
                .extracting(PendingGradingResponse::studentAnswerId).doesNotContain(essayAnswerId);
    }

    @Test
    void gradeAnswer_UC41_A1_regradingCreatesNewRecordInsteadOfUpdating() {
        Long essayAnswerId = manualGradingService.listPendingGrading(teacher.getId()).stream()
                .filter(p -> p.questionId().equals(essayQuestion.id()))
                .findFirst().orElseThrow().studentAnswerId();
        manualGradingService.gradeAnswer(essayAnswerId,
                new GradeAnswerRequest(new BigDecimal("1.0"), new BigDecimal("2.0"), "Chấm lần 1"), teacher.getId());

        StudentAnswerGradingResponse regraded = manualGradingService.gradeAnswer(essayAnswerId,
                new GradeAnswerRequest(new BigDecimal("1.8"), new BigDecimal("2.0"), "Chấm lại, khá hơn"), teacher.getId());

        assertThat(regraded.score()).isEqualByComparingTo("1.8");
        ExerciseAttemptResponse updated = exerciseAttemptService.getAttempt(attempt.id(), studentUser.getId());
        assertThat(updated.manualGradeScore()).isEqualByComparingTo("1.8");
    }

    @Test
    void gradeAnswer_rejectsWhenAnswerIsAutoGradable() {
        Long mcAnswerId = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()).stream()
                .filter(a -> a.questionId().equals(mcQuestion.id()))
                .findFirst().orElseThrow().id();

        assertThatThrownBy(() -> manualGradingService.gradeAnswer(mcAnswerId,
                new GradeAnswerRequest(new BigDecimal("1.0"), new BigDecimal("1.0"), null), teacher.getId()))
                .isInstanceOf(AnswerNotManuallyGradableException.class);
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
