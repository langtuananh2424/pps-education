package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.academic.dto.AssignTeacherRequest;
import vn.com.pps.education.academic.dto.CreateClassRequest;
import vn.com.pps.education.academic.dto.CreateCurriculumRequest;
import vn.com.pps.education.academic.dto.CurriculumResponse;
import vn.com.pps.education.academic.dto.EnrollStudentRequest;
import vn.com.pps.education.academic.dto.UpdateCurriculumRequest;
import vn.com.pps.education.academic.service.ClassService;
import vn.com.pps.education.academic.service.CurriculumService;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.auth.repository.UserRepository;
import vn.com.pps.education.exception.AttemptNotEditableException;
import vn.com.pps.education.exception.ListeningHintNotUnlockedException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RetakeNotAllowedException;
import vn.com.pps.education.facility.domain.Site;
import vn.com.pps.education.facility.repository.SiteRepository;
import vn.com.pps.education.lms.domain.ExerciseAssignment;
import vn.com.pps.education.lms.domain.Question;
import vn.com.pps.education.lms.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.lms.dto.ClassResponse;
import vn.com.pps.education.lms.dto.CreateExamQuestionRequest;
import vn.com.pps.education.lms.dto.CreateExamRequest;
import vn.com.pps.education.lms.dto.CreateExerciseRequest;
import vn.com.pps.education.lms.dto.ExamResponse;
import vn.com.pps.education.lms.dto.ExerciseAttemptResponse;
import vn.com.pps.education.lms.dto.ExerciseResponse;
import vn.com.pps.education.lms.dto.ExerciseSectionRequest;
import vn.com.pps.education.lms.dto.QuestionChoiceRequest;
import vn.com.pps.education.lms.dto.QuestionResponse;
import vn.com.pps.education.lms.dto.SaveAnswerRequest;
import vn.com.pps.education.lms.dto.SectionResultResponse;
import vn.com.pps.education.lms.dto.SectionResultsResponse;
import vn.com.pps.education.lms.dto.StudentAnswerResponse;
import vn.com.pps.education.lms.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.lms.repository.ListeningHintEventRepository;
import vn.com.pps.education.lms.repository.QuestionRepository;
import vn.com.pps.education.permission.domain.Role;
import vn.com.pps.education.permission.domain.UserRole;
import vn.com.pps.education.permission.repository.RoleRepository;
import vn.com.pps.education.permission.repository.UserRoleRepository;
import vn.com.pps.education.student.domain.Student;
import vn.com.pps.education.student.repository.StudentRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V226 — phương pháp "Nghe ép" (UC-40 soạn bài tập con, UC-24/UC-27 làm bài): Main Flow + A1-A5 trong
 * mục "Bổ sung V226" của docs/uc/phan-he-07-lms-portal.md. Bài dựng sẵn: Part 1 (4 câu, mức đạt 75%) và
 * Part 2 (3 câu, mức đạt 50%), mỗi câu 1 điểm; tapescript mặc định mở ở lượt chưa đạt 3, 5, 7...
 */
@Transactional
class ForcedListeningServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired private ExerciseAttemptService exerciseAttemptService;
    @Autowired private ForcedListeningService forcedListeningService;
    @Autowired private ForcedListeningImportService forcedListeningImportService;
    @Autowired private ListeningHintService listeningHintService;
    @Autowired private ExamQuestionService examQuestionService;
    @Autowired private ExerciseService exerciseService;
    @Autowired private ExamService examService;
    @Autowired private ClassService classService;
    @Autowired private CurriculumService curriculumService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ExerciseAssignmentRepository exerciseAssignmentRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private ListeningHintEventRepository listeningHintEventRepository;

    private User teacher;
    private User studentUser;
    private ClassResponse schoolClass;
    private ExamResponse exam;
    private ExerciseResponse exercise;
    private Long sectionA;
    private Long sectionB;
    private List<QuestionResponse> qa;
    private List<QuestionResponse> qb;
    private Long assignmentId;

    @BeforeEach
    void setUp() {
        User head = newUser("head.academic");
        assignRole(head, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest("CUR-" + SEQ.incrementAndGet(), "Chuẩn", "MAIN", null, null, null, null, null), head.getId());
        CurriculumResponse active = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), head.getId());
        Site site = new Site();
        site.setCode("SITE-" + SEQ.incrementAndGet());
        site.setName("Test Site");
        site.setSiteType(Site.SiteType.OWNED);
        site = siteRepository.save(site);
        schoolClass = classService.create(new CreateClassRequest("CLS-" + SEQ.incrementAndGet(), "8A2", site.getId(), active.id(),
                "OPEN", 20, null, LocalDate.now(), null, null), head.getId());
        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), head.getId());
        studentUser = newUser("student");
        Student student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-TEST-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        student = studentRepository.save(student);
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), head.getId());
        exam = examService.createExam(
                new CreateExamRequest("KD-" + SEQ.incrementAndGet(), "Đề nghe ép", active.id(), "VIETNAMESE", "HOMEWORK", null), teacher.getId());

        exercise = forcedExercise(new BigDecimal("7"));
        sectionA = forcedListeningService.createSection(exercise.id(), new ExerciseSectionRequest("Part 1", null, new BigDecimal("75"))).id();
        sectionB = forcedListeningService.createSection(exercise.id(), new ExerciseSectionRequest("Part 2", null, new BigDecimal("50"))).id();
        qa = newQuestions(4);
        qb = newQuestions(3);
        int order = 1;
        for (QuestionResponse q : qa) {
            exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(q.id(), order++, BigDecimal.ONE, sectionA), teacher.getId());
        }
        for (QuestionResponse q : qb) {
            exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(q.id(), order++, BigDecimal.ONE, sectionB), teacher.getId());
        }
        examService.assignToClass(exam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());
        assignmentId = exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                exercise.id(), schoolClass.id(), ExerciseAssignment.Status.ACTIVE).get(0).getId();
    }

    // ===================== Main Flow =====================

    @Test
    void submitAttempt_UC24_V226_MainFlow_gradesEachSectionSeparatelyAndHidesVerdicts() {
        ExerciseAttemptResponse attempt = start();
        answerAll(attempt.id(), qa.subList(0, 2), true);
        answerAll(attempt.id(), qa.subList(2, 4), false);
        answerAll(attempt.id(), qb, true);

        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        SectionResultResponse a = section(results(attempt.id()), sectionA);
        SectionResultResponse b = section(results(attempt.id()), sectionB);
        assertThat(a.passed()).isFalse();
        assertThat(a.latestScorePercent()).isEqualByComparingTo("50.00");
        assertThat(a.bestScorePercent()).isNull();
        assertThat(a.failedAttemptsCount()).isEqualTo(1);
        assertThat(a.attemptsUntilNextTapescript()).isEqualTo(2);
        assertThat(a.unlockedQuestionIds()).isEmpty();
        assertThat(b.passed()).isTrue();
        assertThat(b.bestScorePercent()).isEqualByComparingTo("100.00");
        assertThat(submitted.passed()).isFalse();
        List<StudentAnswerResponse> answers = exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId());
        assertThat(answers).allSatisfy(x -> {
            assertThat(x.isCorrect()).isNull();
            assertThat(x.correctChoiceIds()).isNull();
        });
    }

    // ===================== A1: bài chưa đạt làm lại, bài đã đạt mang sang và khoá =====================

    @Test
    void startAttempt_UC24_V226_A1_carriesPassedSectionLockedAndReopensFailedOneUnlimited() {
        ExerciseAttemptResponse first = start();
        answerAll(first.id(), qa.subList(0, 2), true);
        answerAll(first.id(), qa.subList(2, 4), false);
        answerAll(first.id(), qb, true);
        exerciseAttemptService.submitAttempt(first.id(), studentUser.getId());

        // Bài cho allowRetake=false, maxAttempts=1 — Nghe ép bỏ qua cả hai.
        ExerciseAttemptResponse second = start();

        assertThat(second.attemptNumber()).isEqualTo(2);
        assertThatThrownBy(() -> answer(second.id(), qb.get(0), false)).isInstanceOf(AttemptNotEditableException.class);
        List<StudentAnswerResponse> answers = exerciseAttemptService.listAnswers(second.id(), studentUser.getId());
        for (QuestionResponse q : qa) {
            assertThat(answers.stream().filter(x -> x.questionId().equals(q.id())).findFirst().orElseThrow().selectedChoiceIds())
                    .as("bài chưa đạt phải mở sạch, kể cả câu đã đúng ở lượt trước").isNullOrEmpty();
        }
        answerAll(second.id(), qa, true);
        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(second.id(), studentUser.getId());

        assertThat(submitted.passed()).isTrue();
        assertThat(submitted.status()).isEqualTo("FULLY_GRADED");
        assertThat(section(results(second.id()), sectionA).bestScorePercent()).isEqualByComparingTo("100.00");
    }

    // ===================== A2: làm lại tự nguyện bài đã đạt =====================

    @Test
    void startAttempt_UC24_V226_A2_voluntaryRetakeBelowThresholdIgnoredAndHigherRaisesBest() {
        ExerciseAttemptResponse first = start();
        answerAll(first.id(), qa, true);
        answerAll(first.id(), qb.subList(0, 2), true);
        answer(first.id(), qb.get(2), false);
        exerciseAttemptService.submitAttempt(first.id(), studentUser.getId());
        assertThat(section(results(first.id()), sectionB).bestScorePercent()).isEqualByComparingTo("66.67");

        ExerciseAttemptResponse lower = start(sectionB);
        assertThatThrownBy(() -> answer(lower.id(), qa.get(0), true)).isInstanceOf(AttemptNotEditableException.class);
        answerAll(lower.id(), qb, false);
        ExerciseAttemptResponse lowerSubmitted = exerciseAttemptService.submitAttempt(lower.id(), studentUser.getId());

        SectionResultResponse afterLower = section(results(lower.id()), sectionB);
        assertThat(afterLower.passed()).isTrue();
        assertThat(afterLower.bestScorePercent()).isEqualByComparingTo("66.67");
        assertThat(afterLower.latestScorePercent()).isEqualByComparingTo("0.00");
        assertThat(afterLower.failedAttemptsCount()).isZero();
        assertThat(lowerSubmitted.passed()).isTrue();

        ExerciseAttemptResponse higher = start(sectionB);
        answerAll(higher.id(), qb, true);
        exerciseAttemptService.submitAttempt(higher.id(), studentUser.getId());

        assertThat(section(results(higher.id()), sectionB).bestScorePercent()).isEqualByComparingTo("100.00");
    }

    // ===================== A3: mở dần tapescript =====================

    @Test
    void submitAttempt_UC24_V226_A3_blankSubmissionDoesNotCountAsFailedAttempt() {
        ExerciseAttemptResponse blank = start();
        exerciseAttemptService.submitAttempt(blank.id(), studentUser.getId());

        SectionResultsResponse results = results(blank.id());

        assertThat(section(results, sectionA).failedAttemptsCount()).isZero();
        assertThat(section(results, sectionB).failedAttemptsCount()).isZero();
        assertThat(section(results, sectionA).latestScorePercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void submitAttempt_UC24_V226_A3_unlocksFirstWrongAtAttempt3AndSecondAtAttempt5SkippingBlank() {
        failSectionA(2);
        Long afterTwo = failSectionA(0);
        assertThat(section(results(afterTwo), sectionA).unlockedQuestionIds()).isEmpty();

        Long attemptThree = failSectionA(1);
        SectionResultResponse three = section(results(attemptThree), sectionA);
        assertThat(three.failedAttemptsCount()).isEqualTo(3);
        assertThat(three.unlockedQuestionIds()).containsExactly(qa.get(0).id());

        failSectionA(1);
        Long attemptFive = failSectionA(1);
        SectionResultResponse five = section(results(attemptFive), sectionA);
        assertThat(five.failedAttemptsCount()).isEqualTo(5);
        // qa[1] bỏ trống ở mọi lượt nên không bao giờ được mở — câu sai thứ hai là qa[2].
        assertThat(five.unlockedQuestionIds()).containsExactly(qa.get(0).id(), qa.get(2).id());
    }

    @Test
    void viewTapescript_UC24_V226_A3_rejectsQuestionNotUnlocked() {
        Long attempt = failSectionA(3);

        assertThatThrownBy(() -> forcedListeningService.viewTapescript(attempt, qa.get(2).id(), studentUser.getId()))
                .isInstanceOf(ListeningHintNotUnlockedException.class);
        assertThatThrownBy(() -> forcedListeningService.viewTapescript(attempt, qb.get(0).id(), studentUser.getId()))
                .isInstanceOf(ListeningHintNotUnlockedException.class);
    }

    // ===================== A4: thống kê lượt xem =====================

    @Test
    void viewTapescript_UC24_V226_A4_logsOneEventPerViewAfterSubmit() {
        Long attempt = failSectionA(3);
        Question unlocked = questionRepository.findById(qa.get(0).id()).orElseThrow();

        String text1 = forcedListeningService.viewTapescript(attempt, qa.get(0).id(), studentUser.getId()).tapescript();
        forcedListeningService.viewTapescript(attempt, qa.get(0).id(), studentUser.getId());

        assertThat(text1).isEqualTo(unlocked.getTapescript()).startsWith("Tape ");
        assertThat(listeningHintEventRepository.countByQuestionId(qa.get(0).id())).isEqualTo(2);
    }

    @Test
    void recordPlay_UC24_V226_A4_oldHintEndpointsRejectForcedListening() {
        ExerciseAttemptResponse attempt = start();

        assertThatThrownBy(() -> listeningHintService.recordPlay(attempt.id(), qa.get(0).id(), studentUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===================== A5: hoàn thành và xem đáp án =====================

    @Test
    void revealAnswersAndClose_UC24_V226_A5_revealsOnlyWhenAllPassedAndThenLocksRetake() {
        ExerciseAttemptResponse attempt = start();
        answerAll(attempt.id(), qa.subList(0, 3), true);
        answer(attempt.id(), qa.get(3), false);
        answerAll(attempt.id(), qb, true);
        ExerciseAttemptResponse submitted = exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        SectionResultsResponse before = results(attempt.id());
        assertThat(submitted.passed()).isTrue();
        assertThat(before.allPassed()).isTrue();
        assertThat(before.overallPercent()).isEqualByComparingTo("87.50");
        assertThat(before.canRevealAnswers()).isTrue();
        assertThat(before.answersRevealed()).isFalse();

        exerciseAttemptService.revealAnswersAndClose(attempt.id(), studentUser.getId());

        SectionResultsResponse after = results(attempt.id());
        assertThat(after.answersRevealed()).isTrue();
        assertThat(after.canRevealAnswers()).isFalse();
        assertThat(exerciseAttemptService.listAnswers(attempt.id(), studentUser.getId()))
                .allSatisfy(x -> assertThat(x.isCorrect()).isNotNull());
        assertThatThrownBy(this::start).isInstanceOf(RetakeNotAllowedException.class);
    }

    @Test
    void revealAnswersAndClose_UC24_V226_A5_rejectsWhileASectionIsNotPassed() {
        ExerciseAttemptResponse attempt = start();
        answerAll(attempt.id(), qa, false);
        answerAll(attempt.id(), qb, true);
        exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());

        assertThat(results(attempt.id()).canRevealAnswers()).isFalse();
        assertThatThrownBy(() -> exerciseAttemptService.revealAnswersAndClose(attempt.id(), studentUser.getId()))
                .isInstanceOf(AttemptNotEditableException.class);
    }

    // ===================== UC-40: soạn bài tập con =====================

    @Test
    void createExercise_UC40_V226_rejectsForcedListeningOutsideListeningCategory() {
        assertThatThrownBy(() -> exerciseService.createExercise(
                new CreateExerciseRequest("EX-" + SEQ.incrementAndGet(), "Sai nhóm", exam.id(), null, "ASSIGNED",
                        BigDecimal.ONE, null, false, 1, true, null, "READING", true, null, null), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addQuestion_UC40_V226_rejectsMissingSectionAndPublishNeedsSections() {
        ExerciseResponse draft = forcedExercise(BigDecimal.ONE);
        QuestionResponse q = newQuestions(1).get(0);

        assertThatThrownBy(() -> exerciseService.addQuestion(draft.id(), new AddExerciseQuestionRequest(q.id(), 1, BigDecimal.ONE), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> exerciseService.publishExercise(draft.id(), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSection_UC40_V226_rejectsMoreThanTwelveSections() {
        ExerciseResponse draft = forcedExercise(BigDecimal.ONE);
        for (int i = 1; i <= 12; i++) {
            forcedListeningService.createSection(draft.id(), new ExerciseSectionRequest("Part " + i, null, null));
        }

        assertThatThrownBy(() -> forcedListeningService.createSection(draft.id(), new ExerciseSectionRequest("Part 13", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===================== Import Excel riêng cho Nghe ép =====================

    private static final String[] IMPORT_HEADER = {"Bài tập con", "Mức đạt %", "Loại câu hỏi", "Nội dung", "Đáp án A", "Đáp án B", "Đáp án C",
            "Đáp án D", "Đáp án đúng", "URL Audio", "Tapescript", "Điểm"};

    @Test
    void importExcel_UC40_V226_createsSectionsQuestionsTapescriptAndSharedAudioGroup() throws Exception {
        ExerciseResponse draft = forcedExercise(new BigDecimal("4"));
        org.springframework.mock.web.MockMultipartFile file = xlsx(
                new String[]{"Part 1", "60", "TRAC_NGHIEM", "Q1", "a", "b", "c", "d", "B", "https://x/a.mp3", "Lời thoại 1", "1"},
                new String[]{"Part 1", "", "NHIEU_DAP_AN", "Q2", "a", "b", "c", "d", "B|D", "https://x/a.mp3", "", "2"},
                new String[]{"Part 2", "", "DIEN_TU", "Q3", "", "", "", "", "go/goes", "https://x/b.mp3", "", "1"});

        var result = forcedListeningImportService.importExcel(draft.id(), file, teacher.getId());

        assertThat(result.errors()).isEmpty();
        assertThat(result.createdSections()).isEqualTo(2);
        assertThat(result.createdQuestions()).isEqualTo(3);
        var sections = forcedListeningService.listSections(draft.id());
        assertThat(sections).extracting(s -> s.title()).containsExactly("Part 1", "Part 2");
        assertThat(sections.get(0).passThresholdPercent()).isEqualByComparingTo("60");
        assertThat(sections.get(0).questionCount()).isEqualTo(2);
        var questions = exerciseService.listQuestions(draft.id(), teacher.getId());
        assertThat(questions).extracting(q -> q.questionType()).containsExactly("MULTIPLE_CHOICE", "MULTIPLE_ANSWER", "FILL_IN_BLANK");
        assertThat(questions.get(0).groupKey()).isNotNull().isEqualTo(questions.get(1).groupKey());
        assertThat(questions.get(2).groupKey()).isNull();
        Question first = questionRepository.findById(questions.get(0).questionId()).orElseThrow();
        assertThat(first.getTapescript()).isEqualTo("Lời thoại 1");
    }

    @Test
    void importExcel_UC40_V226_rejectsAllRowsWhenAnyRowInvalid() throws Exception {
        ExerciseResponse draft = forcedExercise(new BigDecimal("4"));
        org.springframework.mock.web.MockMultipartFile file = xlsx(
                new String[]{"Part 1", "", "TRAC_NGHIEM", "Q1", "a", "b", "", "", "B", "", "", "1"},
                new String[]{"Part 1", "", "TRAC_NGHIEM", "Q2", "a", "b", "", "", "C", "", "", "1"});

        var result = forcedListeningImportService.importExcel(draft.id(), file, teacher.getId());

        assertThat(result.errors()).hasSize(1).first().asString().contains("Dòng 3");
        assertThat(forcedListeningService.listSections(draft.id())).isEmpty();
        assertThat(exerciseService.listQuestions(draft.id(), teacher.getId())).isEmpty();
    }

    @Test
    void importExcel_UC40_V226_reusesExistingSectionByTitle() throws Exception {
        ExerciseResponse draft = forcedExercise(new BigDecimal("4"));
        forcedListeningService.createSection(draft.id(), new ExerciseSectionRequest("Part 1", null, new BigDecimal("80")));

        var result = forcedListeningImportService.importExcel(draft.id(), xlsx(
                new String[]{"part 1", "", "TRAC_NGHIEM", "Q1", "a", "b", "", "", "A", "", "", "1"}), teacher.getId());

        assertThat(result.createdSections()).isZero();
        assertThat(forcedListeningService.listSections(draft.id())).singleElement().satisfies(s -> {
            assertThat(s.passThresholdPercent()).isEqualByComparingTo("80");
            assertThat(s.questionCount()).isEqualTo(1);
        });
    }

    private static org.springframework.mock.web.MockMultipartFile xlsx(String[]... dataRows) throws java.io.IOException {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Nghe ép");
            java.util.List<String[]> all = new java.util.ArrayList<>();
            all.add(IMPORT_HEADER);
            all.addAll(java.util.Arrays.asList(dataRows));
            for (int r = 0; r < all.size(); r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r);
                for (int c = 0; c < all.get(r).length; c++) {
                    row.createCell(c).setCellValue(all.get(r)[c]);
                }
            }
            wb.write(out);
            return new org.springframework.mock.web.MockMultipartFile("file", "nghe-ep.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    // ===================== Helpers =====================

    private ExerciseAttemptResponse start(Long... reopenSectionIds) {
        return exerciseAttemptService.startAttempt(exercise.id(), assignmentId,
                reopenSectionIds.length == 0 ? null : List.of(reopenSectionIds), studentUser.getId());
    }

    private Long lastAttemptId;

    /**
     * Chạy {@code times} lượt chưa đạt cho Part 1: trả lời sai qa[0], qa[2], qa[3], bỏ trống qa[1]. Lượt
     * đầu tiên của bài test cũng làm đúng Part 2 để nó đạt và được mang sang (không phải trả lời lại).
     * {@code times = 0} chỉ trả về id lượt gần nhất để tiện đọc kết quả. Trả về id lượt cuối.
     */
    private Long failSectionA(int times) {
        for (int i = 0; i < times; i++) {
            boolean firstEver = lastAttemptId == null;
            ExerciseAttemptResponse attempt = start();
            answer(attempt.id(), qa.get(0), false);
            answer(attempt.id(), qa.get(2), false);
            answer(attempt.id(), qa.get(3), false);
            if (firstEver) {
                answerAll(attempt.id(), qb, true);
            }
            exerciseAttemptService.submitAttempt(attempt.id(), studentUser.getId());
            lastAttemptId = attempt.id();
        }
        return lastAttemptId;
    }

    private SectionResultsResponse results(Long attemptId) {
        return forcedListeningService.getSectionResults(attemptId, studentUser.getId());
    }

    private static SectionResultResponse section(SectionResultsResponse results, Long sectionId) {
        return results.sections().stream().filter(s -> s.sectionId().equals(sectionId)).findFirst().orElseThrow();
    }

    private void answerAll(Long attemptId, List<QuestionResponse> questions, boolean correct) {
        for (QuestionResponse q : questions) {
            answer(attemptId, q, correct);
        }
    }

    private void answer(Long attemptId, QuestionResponse question, boolean correct) {
        Long choiceId = question.choices().stream().filter(c -> c.isCorrect() == correct).findFirst().orElseThrow().id();
        exerciseAttemptService.saveAnswer(attemptId,
                new SaveAnswerRequest(question.id(), null, List.of(choiceId), null, null), studentUser.getId());
    }

    private ExerciseResponse forcedExercise(BigDecimal totalPoints) {
        return exerciseService.createExercise(
                new CreateExerciseRequest("EX-" + SEQ.incrementAndGet(), "Nghe ép", exam.id(), null, "ASSIGNED",
                        totalPoints, null, false, 1, true, null, "LISTENING", true, null, null), teacher.getId());
    }

    private List<QuestionResponse> newQuestions(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> {
            QuestionResponse q = examQuestionService.createQuestion(exam.id(),
                    new CreateExamQuestionRequest("MULTIPLE_CHOICE", "LISTENING", "EASY", "Question " + i, null, null, null, null, null,
                            BigDecimal.ONE, null,
                            List.of(new QuestionChoiceRequest("A", "wrong", null, false, 1), new QuestionChoiceRequest("B", "right", null, true, 2)),
                            null, null),
                    teacher.getId());
            Question entity = questionRepository.findById(q.id()).orElseThrow();
            entity.setTapescript("Tape " + q.id());
            questionRepository.save(entity);
            return q;
        }).toList();
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
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
