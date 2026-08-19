package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateExamQuestionRequest;
import vn.com.pps.education.dto.CreateExamRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.ExamResponse;
import vn.com.pps.education.dto.ExerciseAssignmentQuestionStatsResponse;
import vn.com.pps.education.dto.ExerciseAssignmentStatsResponse;
import vn.com.pps.education.dto.ExerciseAssignmentStudentStatsResponse;
import vn.com.pps.education.dto.ExerciseAttemptResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.QuestionChoiceRequest;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.SaveAnswerRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
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

/** UC-66: Thống kê BTVN theo lớp — Main Flow, A1 (rỗng), A2 (không có quyền). */
@Transactional
class ExerciseReportServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ExerciseReportService exerciseReportService;

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
    private SiteManagerRepository siteManagerRepository;

    @Autowired
    private ExerciseAssignmentRepository exerciseAssignmentRepository;

    private User headAcademic;
    private User teacher;
    private User siteManagerUser;
    private User outsiderUser;
    private Site site;
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

        site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

        siteManagerUser = newUser("site.manager");
        assignRole(siteManagerUser, "SITE_MANAGER");
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(site);
        siteManager.setUser(siteManagerUser);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(siteManagerUser);
        siteManagerRepository.save(siteManager);

        outsiderUser = newUser("outsider.teacher");
        assignRole(outsiderUser, "TEACHER");

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

    @Test
    void listAssignmentStats_UC66_A1_returnsEmptyListWhenClassHasNoHomework() {
        List<ExerciseAssignmentStatsResponse> stats = exerciseReportService.listAssignmentStats(schoolClass.id(), teacher.getId());

        assertThat(stats).isEmpty();
    }

    @Test
    void listAssignmentStats_UC66_A2_rejectsActorNotAssignedToClass() {
        assertThatThrownBy(() -> exerciseReportService.listAssignmentStats(schoolClass.id(), outsiderUser.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    /** V107: quyền lms.exercise-report.manage cho phép quản trị viên xem thống kê BTVN của lớp bất kỳ. */
    @Test
    void listAssignmentStats_allowsAdminWithManagePermissionBypassingAssignedTeacherCheck() {
        User admin = newUser("report.admin");
        assignRole(admin, "SYS_ADMIN");
        deliverSelfPracticeExercise("BTVN 1");

        List<ExerciseAssignmentStatsResponse> stats = exerciseReportService.listAssignmentStats(schoolClass.id(), admin.getId());

        assertThat(stats).hasSize(1);
    }

    @Test
    void listAssignmentStats_UC66_MainFlow_allowsSiteManagerOfClassSite() {
        deliverSelfPracticeExercise("BTVN 1");

        List<ExerciseAssignmentStatsResponse> stats = exerciseReportService.listAssignmentStats(schoolClass.id(), siteManagerUser.getId());

        assertThat(stats).hasSize(1);
    }

    @Test
    void listAssignmentStats_UC66_MainFlow_studentWithZeroAttemptsStillCountedAsNotCompleted() {
        deliverSelfPracticeExercise("BTVN chưa làm");

        List<ExerciseAssignmentStatsResponse> stats = exerciseReportService.listAssignmentStats(schoolClass.id(), teacher.getId());

        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).totalStudents()).isEqualTo(1);
        assertThat(stats.get(0).completedCount()).isEqualTo(0);
        assertThat(stats.get(0).completionPercent()).isEqualByComparingTo("0");
    }

    @Test
    void listAssignmentStats_UC66_boSung_includesCompletedAssignmentAfterStudentPasses() {
        QuestionResponse mc = createMcQuestion();
        ExerciseAssignment assignment = deliverSelfPracticeExerciseWithQuestion("BTVN đạt", mc);
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(assignment.getExercise().getId(), assignment.getId(), studentUser.getId());
        answerCorrectly(attempt.id(), mc);
        exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        ExerciseAssignment refreshed = exerciseAssignmentRepository.findById(assignment.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(ExerciseAssignment.Status.COMPLETED);

        List<ExerciseAssignmentStatsResponse> stats = exerciseReportService.listAssignmentStats(schoolClass.id(), teacher.getId());

        assertThat(stats).extracting(ExerciseAssignmentStatsResponse::assignmentId).contains(assignment.getId());
        assertThat(stats.get(0).passedCount()).isEqualTo(1);
        assertThat(stats.get(0).passRatePercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void getStudentStats_UC66_MainFlow_studentWithNoAttemptShowsChuaLam() {
        ExerciseAssignment assignment = deliverSelfPracticeExercise("BTVN kết quả");

        ExerciseAssignmentStudentStatsResponse stats = exerciseReportService.getStudentStats(assignment.getId(), teacher.getId());

        assertThat(stats.students()).hasSize(1);
        assertThat(stats.students().get(0).status()).isEqualTo("CHUA_LAM");
        assertThat(stats.students().get(0).passed()).isNull();
    }

    @Test
    void getStudentStats_UC66_MainFlow_readsStoredPassedAndScoreWithoutRecomputing() {
        QuestionResponse mc1 = createMcQuestion();
        QuestionResponse mc2 = createMcQuestion();
        ExerciseAssignment assignment = deliverSelfPracticeExerciseWithQuestions("BTVN 2 câu", List.of(mc1, mc2));
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(assignment.getExercise().getId(), assignment.getId(), studentUser.getId());
        answerCorrectly(attempt.id(), mc1);
        exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        ExerciseAssignmentStudentStatsResponse stats = exerciseReportService.getStudentStats(assignment.getId(), teacher.getId());

        assertThat(stats.students()).hasSize(1);
        var row = stats.students().get(0);
        assertThat(row.status()).isEqualTo("DA_NOP");
        assertThat(row.totalScore()).isEqualByComparingTo("1.0");
        assertThat(row.percentage()).isEqualByComparingTo("50.00");
        assertThat(row.passed()).isFalse();
    }

    @Test
    void getQuestionStats_UC66_MainFlow_countsWrongAnswersExcludingUngraded() {
        QuestionResponse mc = createMcQuestion();
        QuestionResponse essay = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("ESSAY", "WRITING", "MEDIUM", "Viết đoạn văn.", null, null, null,
                        null, null, new BigDecimal("1.0"), null, null, null, null),
                teacher.getId());
        ExerciseAssignment assignment = deliverSelfPracticeExerciseWithQuestions("BTVN phân tích câu", List.of(mc, essay));
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(assignment.getExercise().getId(), assignment.getId(), studentUser.getId());
        Long wrongChoiceId = mc.choices().stream().filter(c -> !c.isCorrect()).findFirst().orElseThrow().id();
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(mc.id(), null, List.of(wrongChoiceId), null, null), studentUser.getId());
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(essay.id(), "Bài làm...", null, null, null), studentUser.getId());
        exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        ExerciseAssignmentQuestionStatsResponse stats = exerciseReportService.getQuestionStats(assignment.getId(), teacher.getId());

        assertThat(stats.questions()).hasSize(2);
        var mcRow = stats.questions().stream().filter(q -> q.questionId().equals(mc.id())).findFirst().orElseThrow();
        assertThat(mcRow.answeredCount()).isEqualTo(1);
        assertThat(mcRow.wrongCount()).isEqualTo(1);
        assertThat(mcRow.wrongStudents()).extracting(ExerciseAssignmentQuestionStatsResponse.WrongStudent::studentId)
                .containsExactly(student.getId());

        var essayRow = stats.questions().stream().filter(q -> q.questionId().equals(essay.id())).findFirst().orElseThrow();
        assertThat(essayRow.answeredCount()).isEqualTo(0);
        assertThat(essayRow.wrongCount()).isEqualTo(0);
    }

    @Test
    void exportStudentStatsExcel_UC66_MainFlow_producesNonEmptyXlsx() {
        ExerciseAssignment assignment = deliverSelfPracticeExercise("BTVN xuất Excel");

        byte[] content = exerciseReportService.exportStudentStatsExcel(assignment.getId(), teacher.getId());

        assertThat(content).isNotEmpty();
    }

    /** Test fixture: tạo dữ liệu StudentAnswer để test lịch sử trả lời câu hỏi ở frontend. */
    @Test
    void createFixtureWithStudentAnswerHistory_forManualFrontendTesting() {
        QuestionResponse mc1 = createMcQuestion();
        QuestionResponse mc2 = createMcQuestion();
        ExerciseAssignment assignment = deliverSelfPracticeExerciseWithQuestions("BTVN Test Lịch sử trả lời", List.of(mc1, mc2));
        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(assignment.getExercise().getId(), assignment.getId(), studentUser.getId());

        // Trả lời câu 1 đúng
        answerCorrectly(attempt.id(), mc1);

        // Trả lời câu 2 sai
        Long wrongChoiceId = mc2.choices().stream().filter(c -> !c.isCorrect()).findFirst().orElseThrow().id();
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(mc2.id(), null, List.of(wrongChoiceId), null, null), studentUser.getId());

        // Nộp bài → auto-grade → lưu vào exercise_attempts
        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        // Xác minh dữ liệu đã được tạo
        assertThat(submitted.status()).isIn("SUBMITTED", "AUTO_GRADED", "FULLY_GRADED");
        assertThat(submitted.totalScore()).isNotNull();

        // Verify qua API report: xem lịch sử trả lời ở /api/attempts/{id}/answers (frontend gọi)
        ExerciseAssignmentStudentStatsResponse stats = exerciseReportService.getStudentStats(assignment.getId(), teacher.getId());
        assertThat(stats.students()).hasSize(1);
        assertThat(stats.students().get(0).attemptId()).isNotNull();
        System.out.println("✅ Fixture created: attempt " + submitted.id() + " with 2 answers (1 correct, 1 wrong)");
    }

    private void answerCorrectly(Long attemptId, QuestionResponse question) {
        Long correctChoiceId = question.choices().stream().filter(c -> c.isCorrect()).findFirst().orElseThrow().id();
        exerciseAttemptService.saveAnswer(attemptId,
                new SaveAnswerRequest(question.id(), null, List.of(correctChoiceId), null, null), studentUser.getId());
    }

    private QuestionResponse createMcQuestion() {
        return examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("MULTIPLE_CHOICE", "GRAMMAR", "EASY",
                        "She ___ to school.", null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", false, 1), new QuestionChoiceRequest("B", "goes", true, 2)), null, null),
                teacher.getId());
    }

    private ExerciseAssignment deliverSelfPracticeExercise(String title) {
        return deliverSelfPracticeExerciseWithQuestion(title, createMcQuestion());
    }

    private ExerciseAssignment deliverSelfPracticeExerciseWithQuestion(String title, QuestionResponse mc) {
        return deliverSelfPracticeExerciseWithQuestions(title, List.of(mc));
    }

    private ExerciseAssignment deliverSelfPracticeExerciseWithQuestions(String title, List<QuestionResponse> questions) {
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), title, defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal(questions.size()), null, true, null, true), teacher.getId());
        int order = 1;
        for (QuestionResponse q : questions) {
            exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(q.id(), order++, new BigDecimal("1.0")), teacher.getId());
        }
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        return exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());
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
