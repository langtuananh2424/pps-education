package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ExerciseAssignment;
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
import vn.com.pps.education.dto.CreateExamQuestionRequest;
import vn.com.pps.education.dto.CreateExamRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.ExamResponse;
import vn.com.pps.education.dto.ExerciseAssignmentResponse;
import vn.com.pps.education.dto.ExerciseQuestionChoiceResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.QuestionBankResponse;
import vn.com.pps.education.dto.QuestionChoiceRequest;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateExerciseQuestionPointsRequest;
import vn.com.pps.education.dto.UpdateExerciseRequest;
import vn.com.pps.education.dto.UpdateQuestionBankStatusRequest;
import vn.com.pps.education.dto.UpdateQuestionRequest;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.QuestionLockedException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.NotificationRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentAnswerRepository;
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

/** UC-40: Soạn & giao đề kiểm tra — Main Flow (bước 1-4), A1 (đề có câu tự luận/Nói). */
@Transactional
class ExerciseAuthoringTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private QuestionBankService questionBankService;

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
    private StudentAnswerRepository studentAnswerRepository;

    @Autowired
    private vn.com.pps.education.repository.ExerciseRepository exerciseRepository;

    @Autowired
    private vn.com.pps.education.repository.QuestionRepository questionRepository;

    @Autowired
    private vn.com.pps.education.repository.ExerciseAttemptRepository exerciseAttemptRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private User headAcademic;
    private User teacher;
    private CurriculumResponse activeCurriculum;
    private ClassResponse schoolClass;
    private QuestionBankResponse bank;
    private ExamResponse defaultExam;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

        bank = questionBankService.createBank(
                new CreateQuestionBankRequest(bankCode(), "Ngân hàng Ngữ pháp", activeCurriculum.id(), null, "A1"),
                teacher.getId());

        defaultExam = examService.createExam(
                new CreateExamRequest(examCode(), "Đề mặc định", activeCurriculum.id(), "VIETNAMESE", "HOMEWORK", null), teacher.getId());
    }

    @Test
    void createQuestion_UC40_MainFlow_savesMultipleChoiceWithChoices() {
        QuestionResponse question = createMcQuestion();

        assertThat(question.questionType()).isEqualTo("MULTIPLE_CHOICE");
        assertThat(question.choices()).hasSize(2);
        assertThat(question.choices()).anySatisfy(c -> assertThat(c.isCorrect()).isTrue());
    }

    /** V78 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04): Điền từ - Hộp từ vựng. */
    @Test
    void createQuestion_A_rejectsWordBankWithoutStructuredContent() {
        assertThatThrownBy(() -> questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "WORD_BANK", "GRAMMAR", "EASY",
                        "She ___ to school.", null, null, null, null, null,
                        new BigDecimal("1.0"), null, null, null, null),
                teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** V78 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04): Sắp xếp câu. */
    @Test
    void createQuestion_A_rejectsSentenceBuildingWithoutStructuredContent() {
        assertThatThrownBy(() -> questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "SENTENCE_BUILDING", "GRAMMAR", "EASY",
                        "Sắp xếp thành câu.", null, null, null, null, null,
                        new BigDecimal("1.0"), null, null, null, null),
                teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateQuestion_rejectsWhenAlreadyHasStudentAnswers() {
        QuestionResponse question = createLegacyMcQuestion();
        markQuestionAsAnswered(question.id());

        assertThatThrownBy(() -> questionBankService.updateQuestion(question.id(),
                new UpdateQuestionRequest("Nội dung mới", null, null, null, null, null, null, null, null, null, null), teacher.getId()))
                .isInstanceOf(QuestionLockedException.class);
    }

    @Test
    void updateQuestion_allowsStatusChangeEvenWhenLocked() {
        QuestionResponse question = createLegacyMcQuestion();
        markQuestionAsAnswered(question.id());

        QuestionResponse archived = questionBankService.updateQuestion(question.id(),
                new UpdateQuestionRequest(question.content(), question.audioUrl(), question.imageUrl(),
                        question.referencePassage(), question.explanation(), question.correctAnswerText(), null, question.defaultPoints(),
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
        QuestionResponse essay = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("ESSAY", "WRITING", "MEDIUM", "Viết đoạn văn 50 từ.",
                        null, null, null, null, null, new BigDecimal("2.0"), null, null, null, null),
                teacher.getId());

        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Đề ôn tập Ngữ pháp 1", defaultExam.id(), null,
                        "SELF_PRACTICE", new BigDecimal("10"), 30, true, null, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(essay.id(), 2, new BigDecimal("2.0")), teacher.getId());

        ExerciseResponse withQuestions = exerciseService.getExercise(exercise.id(), teacher.getId());
        assertThat(withQuestions.hasEssayOrSpeaking()).isTrue();
        assertThat(exerciseService.listQuestions(exercise.id(), teacher.getId())).hasSize(2);
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04 — sửa lại thông tin 1 Bài đã soạn. */
    @Test
    void updateExercise_MainFlow_savesNewTitleAndPoints() {
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Đề cũ", defaultExam.id(), null,
                        "SELF_PRACTICE", new BigDecimal("10"), 30, false, null, true),
                teacher.getId());

        ExerciseResponse updated = exerciseService.updateExercise(exercise.id(),
                new UpdateExerciseRequest("Đề mới", null, new BigDecimal("20"), true, 3, false),
                teacher.getId());

        assertThat(updated.title()).isEqualTo("Đề mới");
        assertThat(updated.totalPoints()).isEqualByComparingTo("20");
        assertThat(updated.allowRetake()).isTrue();
        assertThat(updated.maxAttempts()).isEqualTo(3);
        assertThat(updated.showCorrectAnswers()).isFalse();
    }

    /**
     * V80 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — "Xóa Bài" = lưu trữ
     * (status=ARCHIVED), ẩn khỏi listByExam (Kho đề) nhưng vẫn xem được qua getExercise (không xóa cứng).
     */
    @Test
    void deleteExercise_MainFlow_archivesAndHidesFromExamListing() {
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Unit 1", defaultExam.id(), null,
                        "SELF_PRACTICE", new BigDecimal("10"), null, false, null, true),
                teacher.getId());

        exerciseService.deleteExercise(exercise.id(), teacher.getId());

        assertThat(exerciseService.getExercise(exercise.id(), teacher.getId()).status()).isEqualTo("ARCHIVED");
        assertThat(exerciseService.listByExam(defaultExam.id(), teacher.getId()))
                .extracting(ExerciseResponse::id).doesNotContain(exercise.id());
    }

    /**
     * V78 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — BẢO MẬT: structuredContent
     * của WORD_BANK lưu ĐÚNG thứ tự đáp án đúng. listQuestions (dùng bởi Portal TakeExerciseModal) phải
     * KHÔNG BAO GIỜ trả nguyên thứ tự gốc cho học sinh (lộ đáp án) — chỉ trả tập hợp từ đã xáo trộn.
     * Gọi lặp lại nhiều lần để hạ xác suất false-negative (thứ tự xáo trộn trùng ngẫu nhiên thứ tự gốc).
     */
    @Test
    void listQuestions_A_neverExposesWordBankAnswersInOriginalOrder() {
        List<String> correctOrder = List.of("went", "to", "school", "yesterday");
        QuestionResponse wordBank = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("WORD_BANK", "GRAMMAR", "EASY",
                        "She ___ ___ ___ ___.", null, null, null, null, null,
                        new BigDecimal("1.0"), null, null,
                        Map.of("blanks", correctOrder), null),
                teacher.getId());
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Đề Word Bank", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("1"), null, true, null, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(wordBank.id(), 1, new BigDecimal("1.0")), teacher.getId());

        boolean everDifferedFromOriginalOrder = false;
        for (int i = 0; i < 30; i++) {
            var questions = exerciseService.listQuestions(exercise.id(), teacher.getId());
            @SuppressWarnings("unchecked")
            List<String> exposedOrder = (List<String>) questions.get(0).structuredContent().get("blanks");
            assertThat(exposedOrder).containsExactlyInAnyOrderElementsOf(correctOrder);
            if (!exposedOrder.equals(correctOrder)) {
                everDifferedFromOriginalOrder = true;
            }
        }
        assertThat(everDifferedFromOriginalOrder).as("structuredContent phải được xáo trộn, không trả nguyên thứ tự đáp án đúng").isTrue();
    }

    @Test
    void addQuestion_rejectsDuplicateQuestionInSameExercise() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Đề X", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("1"), null, true, null, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());

        assertThatThrownBy(() -> exerciseService.addQuestion(exercise.id(),
                new AddExerciseQuestionRequest(mc.id(), 2, new BigDecimal("1.0")), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-18 — tổng điểm câu hỏi trong 1 Bài
     * không được vượt quá exercises.total_points đã setup lúc tạo Bài.
     */
    @Test
    void addQuestion_boSung_rejectsWhenTotalPointsExceeded() {
        QuestionResponse mc1 = createMcQuestion();
        QuestionResponse mc2 = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Đề giới hạn điểm", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("5"), null, true, null, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc1.id(), 1, new BigDecimal("4")), teacher.getId());

        assertThatThrownBy(() -> exerciseService.addQuestion(exercise.id(),
                new AddExerciseQuestionRequest(mc2.id(), 2, new BigDecimal("2")), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tổng điểm");
        assertThat(exerciseService.listQuestions(exercise.id(), teacher.getId())).hasSize(1);
    }

    /** Ranh giới: tổng điểm khớp CHÍNH XÁC total_points vẫn cho phép (chỉ chặn khi VƯỢT quá). */
    @Test
    void addQuestion_boSung_allowsReachingExactlyTotalPoints() {
        QuestionResponse mc1 = createMcQuestion();
        QuestionResponse mc2 = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Đề vừa khít điểm", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("5"), null, true, null, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc1.id(), 1, new BigDecimal("3")), teacher.getId());

        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc2.id(), 2, new BigDecimal("2")), teacher.getId());

        assertThat(exerciseService.listQuestions(exercise.id(), teacher.getId())).hasSize(2);
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-18 — sửa lại điểm 1 câu đã gắn vào Bài. */
    @Test
    void updateQuestionPoints_boSung_MainFlow_updatesPointsWithinTotal() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Đề sửa điểm", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("10"), null, true, null, true),
                teacher.getId());
        var eq = exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("5")), teacher.getId());

        var updated = exerciseService.updateQuestionPoints(exercise.id(), eq.id(),
                new UpdateExerciseQuestionPointsRequest(new BigDecimal("8")), teacher.getId());

        assertThat(updated.points()).isEqualByComparingTo("8");
        assertThat(exerciseService.listQuestions(exercise.id(), teacher.getId()).get(0).points()).isEqualByComparingTo("8");
    }

    /** A: sửa điểm 1 câu khiến tổng vượt total_points (loại điểm CŨ của chính câu đó trước khi cộng điểm mới) -> chặn. */
    @Test
    void updateQuestionPoints_boSung_A_rejectsWhenExceedsTotalPoints() {
        QuestionResponse mc1 = createMcQuestion();
        QuestionResponse mc2 = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Đề sửa điểm vượt", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("5"), null, true, null, true),
                teacher.getId());
        var eq1 = exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc1.id(), 1, new BigDecimal("3")), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc2.id(), 2, new BigDecimal("2")), teacher.getId());

        assertThatThrownBy(() -> exerciseService.updateQuestionPoints(exercise.id(), eq1.id(),
                new UpdateExerciseQuestionPointsRequest(new BigDecimal("4")), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tổng điểm");
        assertThat(exerciseService.listQuestions(exercise.id(), teacher.getId()))
                .filteredOn(q -> q.id().equals(eq1.id()))
                .singleElement()
                .satisfies(q -> assertThat(q.points()).isEqualByComparingTo("3"));
    }

    /** A: mirror removeQuestion_boSung_A_rejectsWhenExerciseAlreadyPublished — sửa điểm chỉ làm được khi Bài còn DRAFT. */
    @Test
    void updateQuestionPoints_boSung_A_rejectsWhenExerciseAlreadyPublished() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal("10"), 15, false, 1, true),
                teacher.getId());
        var eq = exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());

        assertThatThrownBy(() -> exerciseService.updateQuestionPoints(exercise.id(), eq.id(),
                new UpdateExerciseQuestionPointsRequest(new BigDecimal("5")), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — gỡ câu hỏi khỏi Bài còn DRAFT. */
    @Test
    void removeQuestion_boSung_MainFlow_removesQuestionFromDraftExercise() {
        QuestionResponse mc1 = createMcQuestion();
        QuestionResponse mc2 = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Đề gỡ câu hỏi", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("2"), null, true, null, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc1.id(), 1, new BigDecimal("1.0")), teacher.getId());
        var eq2 = exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc2.id(), 2, new BigDecimal("1.0")), teacher.getId());

        exerciseService.removeQuestion(exercise.id(), eq2.id(), teacher.getId());

        assertThat(exerciseService.listQuestions(exercise.id(), teacher.getId()))
                .extracting(q -> q.questionId()).containsExactly(mc1.id());
    }

    /** A: đề đã Publish thì không gỡ câu hỏi được nữa, theo đúng quyết định đã chốt. */
    @Test
    void removeQuestion_boSung_A_rejectsWhenExerciseAlreadyPublished() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal("10"), 15, false, 1, true),
                teacher.getId());
        var eq = exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());

        assertThatThrownBy(() -> exerciseService.removeQuestion(exercise.id(), eq.id(), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(exerciseService.listQuestions(exercise.id(), teacher.getId())).hasSize(1);
    }

    @Test
    void deliverToClass_UC40_MainFlow_assignsToClassAndPublishes() {
        Student student = enrollStudent();
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra 15 phút", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal("10"), 15, false, 1, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());

        commitCurrentTransactionAndStartNew();
        ExerciseAssignment assignment = exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());

        assertThat(assignment.getSchoolClass().getId()).isEqualTo(schoolClass.id());
        assertThat(exerciseService.getExercise(exercise.id(), teacher.getId()).status()).isEqualTo("PUBLISHED");
    }

    /**
     * V70 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31) —
     * fix bug thật: "Gửi nhận xét" hàng loạt cho N học sinh CÙNG buổi,
     * CÙNG chọn 1 Bài → StudentCommentService gọi deliverToClass N lần
     * với CÙNG (exerciseId, classId, dueAt) → trước đây tạo N
     * ExerciseAssignment trùng lặp, mỗi bản ghi lại thông báo lại cho
     * TOÀN BỘ học sinh lớp → 1 học sinh nhận N thông báo giống hệt nhau.
     *
     * V71 (REQUIRES_NEW): test này gọi deliverToClass 3 lần liên tiếp để test
     * anti-duplicate logic. Không dùng @Transactional vì cần auto-commit giữa
     * các lần gọi để anti-duplicate findByExerciseIdAndSchoolClassIdAndStatus
     * thấy được assignment từ lần trước.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deliverToClass_V70_boSung_reusesExistingAssignmentForSameSessionInsteadOfDuplicating() {
        Student student = enrollStudent();
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra 15 phút", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal("10"), 15, false, 1, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        OffsetDateTime dueAt = OffsetDateTime.now().plusDays(2);

        // Mô phỏng N=3 request riêng biệt (3 học sinh khác nhau CÙNG chọn Bài này CÙNG buổi).
        ExerciseAssignment first = exerciseService.deliverToClass(exercise.id(), schoolClass.id(), dueAt, teacher.getId());
        ExerciseAssignment second = exerciseService.deliverToClass(exercise.id(), schoolClass.id(), dueAt, teacher.getId());
        ExerciseAssignment third = exerciseService.deliverToClass(exercise.id(), schoolClass.id(), dueAt, teacher.getId());

        assertThat(second.getId()).as("tái dùng đúng bản ghi cũ, không tạo mới").isEqualTo(first.getId());
        assertThat(third.getId()).isEqualTo(first.getId());
        assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(student.getUser().getId(), PageRequest.of(0, 10)))
                .as("chỉ nhận đúng 1 thông báo dù deliverToClass bị gọi 3 lần cho cùng buổi")
                .hasSize(1);
    }

    /**
     * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * thay cho deliverToClass_rejectsWhenExerciseIsNotAssignedType cũ (hành
     * vi "chỉ ASSIGNED mới giao được" đã bị bỏ — MỌI loại đề đều giao được).
     * Rào mới: Đề của Bài phải đã gán cho lớp — kể cả SELF_PRACTICE.
     */
    @Test
    void deliverToClass_rejectsWhenExamNotAssignedToClass() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Bài tự luyện", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("10"), null, true, null, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());
        // Chưa gọi examService.assignToClass -> Đề chưa được gán cho lớp.

        assertThatThrownBy(() -> exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deliverToClass_rejectsWhenActorNotAssignedTeacherForClass() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal("10"), null, false, 1, true),
                teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());

        assertThatThrownBy(() -> exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void listAssignmentsForClass_boSung_teacherSeesExercisesAssignedToTheirClass() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal("10"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());

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
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal("10"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());
        // chưa gọi deliverToClass cho lớp của student -> chưa được giao

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
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal("10"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("10")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());

        ExerciseResponse viewed = exerciseService.getExercise(exercise.id(), student.getUser().getId());

        assertThat(viewed.id()).isEqualTo(exercise.id());
        assertThat(exerciseService.listQuestions(exercise.id(), student.getUser().getId())).hasSize(1);
    }

    /**
     * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * thay cho getExercise_boSung_allowsAnyStudentForPublishedSelfPractice cũ
     * — SELF_PRACTICE hết cơ chế "mở tự do sau khi Publish", giờ CŨNG cần
     * Đề đã gán lớp + ExerciseAssignment ACTIVE như mọi loại khác (unified
     * gate, xem ExerciseService#requireCanViewExercise).
     */
    @Test
    void getExercise_boSung_rejectsSelfPracticeWithoutActiveAssignment() {
        Student student = enrollStudent();
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Ôn tập tự do", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("1"), null, true, null, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());
        exerciseService.publishExercise(exercise.id(), teacher.getId());
        // Publish rồi nhưng chưa gán Đề cho lớp + chưa deliverToClass -> vẫn chặn.

        assertThatThrownBy(() -> exerciseService.getExercise(exercise.id(), student.getUser().getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getExercise_boSung_allowsSelfPracticeWithActiveAssignment() {
        Student student = enrollStudent();
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Ôn tập tự do", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("1"), null, true, null, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());

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
                new CreateExerciseRequest(exerciseCode(), "Ôn tập trắc nghiệm", defaultExam.id(), null,
                        "SELF_PRACTICE", new BigDecimal("1"), null, true, null, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());

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
        QuestionResponse essay = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("ESSAY", "WRITING", "MEDIUM", "Viết đoạn văn 50 từ.",
                        null, null, null, null, null, new BigDecimal("2.0"), null, null, null, null), teacher.getId());
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Đề tự luận", defaultExam.id(), null, "SELF_PRACTICE",
                        new BigDecimal("2"), null, true, null, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(essay.id(), 1, new BigDecimal("2.0")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
        exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());

        var questions = exerciseService.listQuestions(exercise.id(), student.getUser().getId());

        assertThat(questions).hasSize(1);
        assertThat(questions.get(0).questionType()).isEqualTo("ESSAY");
        assertThat(questions.get(0).choices()).isEmpty();
    }

    /** Câu hỏi soạn trực tiếp qua Đề (V75, Kho đề) — dùng cho mọi test cần addQuestion vào 1 Exercise. */
    private QuestionResponse createMcQuestion() {
        return examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("MULTIPLE_CHOICE", "GRAMMAR", "EASY",
                        "She ___ to school every day.", null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(
                                new QuestionChoiceRequest("A", "go", null, false, 1),
                                new QuestionChoiceRequest("B", "goes", null, true, 2)), null, null),
                teacher.getId());
    }

    /** Câu hỏi trong Ngân hàng câu hỏi legacy (generic, tách khỏi Đề) — chỉ dùng cho test chạm thẳng QuestionBankService#updateQuestion (yêu cầu bank legacy). */
    private QuestionResponse createLegacyMcQuestion() {
        return questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "MULTIPLE_CHOICE", "GRAMMAR", "EASY",
                        "She ___ to school every day.", null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(
                                new QuestionChoiceRequest("A", "go", null, false, 1),
                                new QuestionChoiceRequest("B", "goes", null, true, 2)), null, null),
                teacher.getId());
    }

    /** Giả lập đã có học sinh trả lời — không đi qua UC-24/27 (chưa build), ghi thẳng vào bảng nền tảng đã tạo ở V19. */
    private void markQuestionAsAnswered(Long questionId) {
        vn.com.pps.education.domain.Exercise exercise = exerciseRepository.findById(
                exerciseService.createExercise(
                        new CreateExerciseRequest(exerciseCode(), "Đề dùng để test khóa câu hỏi", defaultExam.id(), null,
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
