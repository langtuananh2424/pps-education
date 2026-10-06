package vn.com.pps.education.academic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import vn.com.pps.education.academic.domain.AcademicTerm;
import vn.com.pps.education.academic.domain.ClassEnrollment;
import vn.com.pps.education.academic.domain.GradeComponentSetup;
import vn.com.pps.education.academic.domain.GradeEntry;
import vn.com.pps.education.academic.domain.GradeEvaluationComponent;
import vn.com.pps.education.academic.domain.GradeEvaluationResult;
import vn.com.pps.education.academic.dto.ReviseTermCommentAiDraftRequest;
import vn.com.pps.education.academic.dto.TermCommentAiDraftResult;
import vn.com.pps.education.academic.repository.ClassEnrollmentRepository;
import vn.com.pps.education.academic.repository.GradeComponentSetupRepository;
import vn.com.pps.education.academic.repository.GradeEntryRepository;
import vn.com.pps.education.academic.repository.GradeEvaluationComponentRepository;
import vn.com.pps.education.academic.repository.GradeEvaluationResultRepository;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.exception.CommentAiDraftFailedException;
import vn.com.pps.education.exception.CommentAiDraftRejectedException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.lms.service.AiJobRegistry;
import vn.com.pps.education.lms.service.CommentAiJsonCaller;
import vn.com.pps.education.lms.service.NineRouterAiClient;
import vn.com.pps.education.lms.service.PromptTemplateLoader;
import vn.com.pps.education.permission.service.PermissionEvaluationService;
import vn.com.pps.education.student.domain.Student;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-76: Trợ lý AI soạn nháp Nhận xét Giữa kỳ/Cuối kỳ — mỗi Alternate Flow 1 test (xem docs/uc/phan-he-06-hoc-thuat.md).
 * Test thuần Service logic: mock repository + NineRouterAiClient, không chạm DB/mạng. Bước 8 (Áp dụng) và A8 ghi DB qua
 * {@link GradeService#applyAiDraftComments} — test tích hợp ở GradeServiceTest.
 */
class TermCommentAiDraftServiceTest {

    private static final long CLASS_ID = 5L;
    private static final long SETUP_ID = 20L;
    private static final long MID_SETUP_ID = 19L;
    private static final long TERM_ID = 3L;
    private static final long ACTOR_ID = 99L;

    private final GradeService gradeService = mock(GradeService.class);
    private final GradeComponentSetupRepository setupRepository = mock(GradeComponentSetupRepository.class);
    private final GradeEvaluationComponentRepository componentRepository = mock(GradeEvaluationComponentRepository.class);
    private final GradeEntryRepository entryRepository = mock(GradeEntryRepository.class);
    private final GradeEvaluationResultRepository resultRepository = mock(GradeEvaluationResultRepository.class);
    private final ClassEnrollmentRepository enrollmentRepository = mock(ClassEnrollmentRepository.class);
    private final PermissionEvaluationService permissionEvaluationService = mock(PermissionEvaluationService.class);
    private final NineRouterAiClient aiClient = mock(NineRouterAiClient.class);
    private final PromptTemplateLoader promptTemplateLoader = mock(PromptTemplateLoader.class);
    private final AiJobRegistry jobRegistry = mock(AiJobRegistry.class);

    private final TermCommentAiDraftService service = new TermCommentAiDraftService(gradeService, setupRepository,
            componentRepository, entryRepository, resultRepository, enrollmentRepository, permissionEvaluationService,
            aiClient, new CommentAiJsonCaller(aiClient, promptTemplateLoader, new ObjectMapper()), jobRegistry,
            "comment-pps", 0.5, 10, 0.7, 1024, 85, 50, 10, 10, 0.5);

    private final AcademicTerm term = new AcademicTerm();
    private final List<ClassEnrollment> enrollments = new ArrayList<>();
    private long nextId = 1000;

    @BeforeEach
    void setUp() {
        // Prompt hệ thống = chính tên file, để stub AI theo prompt.
        when(promptTemplateLoader.load(anyString(), anyMap())).thenAnswer(inv -> inv.getArgument(0));
        term.setId(TERM_ID);
        when(enrollmentRepository.findBySchoolClassIdAndStatus(CLASS_ID, ClassEnrollment.Status.ACTIVE)).thenReturn(enrollments);
        when(resultRepository.findByStudentIdWithContext(anyLong())).thenReturn(List.of());
        when(resultRepository.findBySchoolClassIdAndAcademicTermIdAndEvaluationTypeOrderByStudentId(anyLong(), anyLong(), any()))
                .thenReturn(List.of());
        when(setupRepository.findBySchoolClassIdAndAcademicTermIdAndEvaluationType(anyLong(), anyLong(), any()))
                .thenReturn(Optional.empty());
    }

    // ---- Dựng dữ liệu ----

    private GradeComponentSetup setup(long id, GradeComponentSetup.EvaluationType type) {
        GradeComponentSetup setup = new GradeComponentSetup();
        setup.setId(id);
        setup.setAcademicTerm(term);
        setup.setEvaluationType(type);
        setup.setScaleType(GradeComponentSetup.ScaleType.POINT_10);
        return setup;
    }

    private GradeEvaluationComponent component(GradeComponentSetup setup, GradeEvaluationComponent.ComponentCode code, String name) {
        GradeEvaluationComponent component = new GradeEvaluationComponent();
        component.setId(nextId++);
        component.setGradeComponentSetup(setup);
        component.setCode(code);
        component.setName(name);
        component.setMaxScore(BigDecimal.TEN);
        return component;
    }

    private Student student(long id, String name) {
        Student student = mock(Student.class);
        User user = mock(User.class);
        when(student.getId()).thenReturn(id);
        when(student.getUser()).thenReturn(user);
        when(user.getFullName()).thenReturn(name);
        ClassEnrollment enrollment = new ClassEnrollment();
        enrollment.setStudent(student);
        enrollments.add(enrollment);
        return student;
    }

    private GradeEntry entry(Student student, GradeEvaluationComponent component, String score, boolean absent) {
        GradeEntry entry = new GradeEntry();
        entry.setStudent(student);
        entry.setGradeComponent(component);
        entry.setScore(new BigDecimal(score));
        entry.setAbsenceFlag(absent);
        return entry;
    }

    private GradeEvaluationResult result(long id, Student student, GradeEvaluationResult.Status status, String comment) {
        GradeEvaluationResult result = new GradeEvaluationResult();
        result.setId(id);
        result.setStudent(student);
        result.setStatus(status);
        result.setComment(comment);
        return result;
    }

    private void givenSetup(GradeComponentSetup setup, List<GradeEvaluationComponent> components, List<GradeEntry> entries,
                            List<GradeEvaluationResult> results) {
        when(componentRepository.findByGradeComponentSetupIdOrderByDisplayOrder(setup.getId())).thenReturn(components);
        for (GradeEvaluationComponent component : components) {
            when(entryRepository.findBySchoolClassIdAndGradeComponentIdOrderByStudentId(CLASS_ID, component.getId()))
                    .thenReturn(entries.stream().filter(e -> e.getGradeComponent() == component).toList());
        }
        when(resultRepository.findBySchoolClassIdAndAcademicTermIdAndEvaluationTypeOrderByStudentId(CLASS_ID, TERM_ID,
                setup.getEvaluationType())).thenReturn(results);
    }

    private void stubAi(String... responses) {
        var stub = when(aiClient.chatWithFinishReason(eq(TermCommentAiDraftService.WRITE_PROMPT), anyString(), anyString(), anyDouble()));
        for (String response : responses) {
            stub = stub.thenReturn(response == null ? null : new NineRouterAiClient.ChatResult(response, "stop", null));
        }
    }

    private static TermScoreInsight.Insight insight(String skillName, String assessment) {
        return new TermScoreInsight.Insight(List.of(new TermScoreInsight.SkillInsight(skillName, List.of(assessment))),
                null, List.of(), List.of());
    }

    private static TermCommentAiDraftService.Target target(long id, String name, String existingComment) {
        return new TermCommentAiDraftService.Target(id, name, insight("Đọc", TermScoreInsight.STRONG), existingComment, List.of());
    }

    private static TermCommentAiDraftService.DraftContext context(TermCommentAiDraftService.Target... targets) {
        return new TermCommentAiDraftService.DraftContext(GradeComponentSetup.EvaluationType.MID_TERM, List.of(targets), List.of());
    }

    // ---- Main Flow ----

    @Test
    void loadContext_UC76_MainFlow_skipsLockedAndNoScoreStudentsAndSortsByCallName() {
        GradeComponentSetup mid = setup(SETUP_ID, GradeComponentSetup.EvaluationType.MID_TERM);
        when(gradeService.getSetupOfClassOrThrow(CLASS_ID, SETUP_ID)).thenReturn(mid);
        GradeEvaluationComponent reading = component(mid, GradeEvaluationComponent.ComponentCode.READING, "Đọc");
        Student binh = student(2L, "Trần Thị Bình");
        Student an = student(1L, "Nguyễn Văn An");
        Student chi = student(3L, "Lê Minh Chi");
        Student dung = student(4L, "Phạm Dũng");
        givenSetup(mid, List.of(reading),
                List.of(entry(an, reading, "9", false), entry(binh, reading, "4", false), entry(chi, reading, "8", false)),
                List.of(result(50L, chi, GradeEvaluationResult.Status.SUBMITTED, "Đã gửi"),
                        result(51L, binh, GradeEvaluationResult.Status.REJECTED, "Nhận xét bị trả lại")));

        TermCommentAiDraftService.DraftContext context = service.loadContext(CLASS_ID, SETUP_ID, null, ACTOR_ID);

        verify(gradeService).requireCanEnterGrades(CLASS_ID, ACTOR_ID);
        assertThat(context.targets()).extracting(TermCommentAiDraftService.Target::studentId).containsExactly(1L, 2L);
        assertThat(context.targets().get(0).insight().skills().get(0).assessment()).containsExactly(TermScoreInsight.STRONG);
        assertThat(context.targets().get(1).existingComment()).isEqualTo("Nhận xét bị trả lại");
        assertThat(context.skipped()).extracting(TermCommentAiDraftResult.SkippedStudent::reason)
                .containsExactly(TermCommentAiDraftService.SKIP_LOCKED, TermCommentAiDraftService.SKIP_NO_SCORE);
    }

    @Test
    void loadContext_UC76_MainFlow_onlyRequestedStudentsWhenRewritingSelectedRows() {
        GradeComponentSetup mid = setup(SETUP_ID, GradeComponentSetup.EvaluationType.MID_TERM);
        when(gradeService.getSetupOfClassOrThrow(CLASS_ID, SETUP_ID)).thenReturn(mid);
        GradeEvaluationComponent reading = component(mid, GradeEvaluationComponent.ComponentCode.READING, "Đọc");
        Student an = student(1L, "Nguyễn Văn An");
        Student binh = student(2L, "Trần Thị Bình");
        givenSetup(mid, List.of(reading), List.of(entry(an, reading, "9", false), entry(binh, reading, "4", false)), List.of());

        TermCommentAiDraftService.DraftContext context = service.loadContext(CLASS_ID, SETUP_ID, List.of(2L), ACTOR_ID);

        assertThat(context.targets()).extracting(TermCommentAiDraftService.Target::studentId).containsExactly(2L);
    }

    @Test
    void loadContext_UC76_MainFlow_endTermComparesWithMidTermOfSameTerm() {
        GradeComponentSetup end = setup(SETUP_ID, GradeComponentSetup.EvaluationType.END_TERM);
        GradeComponentSetup mid = setup(MID_SETUP_ID, GradeComponentSetup.EvaluationType.MID_TERM);
        when(gradeService.getSetupOfClassOrThrow(CLASS_ID, SETUP_ID)).thenReturn(end);
        when(setupRepository.findBySchoolClassIdAndAcademicTermIdAndEvaluationType(CLASS_ID, TERM_ID,
                GradeComponentSetup.EvaluationType.MID_TERM)).thenReturn(Optional.of(mid));
        GradeEvaluationComponent endSpeaking = component(end, GradeEvaluationComponent.ComponentCode.SPEAKING, "Nói");
        GradeEvaluationComponent midSpeaking = component(mid, GradeEvaluationComponent.ComponentCode.SPEAKING, "Speaking");
        Student an = student(1L, "Nguyễn Văn An");
        givenSetup(end, List.of(endSpeaking), List.of(entry(an, endSpeaking, "8", false)), List.of());
        givenSetup(mid, List.of(midSpeaking), List.of(entry(an, midSpeaking, "6", false)), List.of());

        TermCommentAiDraftService.DraftContext context = service.loadContext(CLASS_ID, SETUP_ID, null, ACTOR_ID);

        assertThat(context.targets().get(0).insight().trend()).containsExactly("Nói tiến bộ rõ so với Giữa kỳ");
    }

    @Test
    void generateDraft_UC76_MainFlow_returnsPreviewRowsWithoutWritingDb() {
        stubAi("{\"comments\":[{\"studentId\":1,\"content\":\"Kỳ này An đọc hiểu rất vững vàng, nắm ý chính nhanh.\"},"
                + "{\"studentId\":2,\"content\":\"Điểm sáng của Bình là phần đọc, con tìm thông tin chi tiết chính xác.\"}]}");

        TermCommentAiDraftResult result = service.generateDraft(context(target(1L, "Nguyễn Văn An", null),
                target(2L, "Trần Thị Bình", null)));

        assertThat(result.evaluationType()).isEqualTo("MID_TERM");
        assertThat(result.rows()).extracting(TermCommentAiDraftResult.Row::studentId).containsExactly(1L, 2L);
        assertThat(result.rows()).allSatisfy(row -> {
            assertThat(row.content()).isNotBlank();
            assertThat(row.warnings()).isEmpty();
            assertThat(row.scoreSummary()).isEqualTo("Đọc: tốt");
        });
        verify(gradeService, never()).applyAiDraftComments(anyLong(), anyLong(), any(), anyLong());
    }

    // ---- Alternate Flow ----

    @Test
    void loadContext_UC76_A1_rejectsActorNotAllowedToEnterGrades() {
        doThrow(new NotAssignedTeacherForClassException("error.notAssignedTeacherForClass.gradeManagement", new Object[]{}, "x"))
                .when(gradeService).requireCanEnterGrades(CLASS_ID, ACTOR_ID);

        assertThatThrownBy(() -> service.startDraft(CLASS_ID, SETUP_ID, null, null, null, ACTOR_ID))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
        verify(jobRegistry, never()).submit(any(), any(), any());
    }

    @Test
    void loadContext_UC76_A2_rejectsSetupOfAnotherClass() {
        when(gradeService.getSetupOfClassOrThrow(CLASS_ID, SETUP_ID))
                .thenThrow(new ResourceNotFoundException("error.grade.componentSetupNotFound", new Object[]{SETUP_ID}, "x"));

        assertThatThrownBy(() -> service.startDraft(CLASS_ID, SETUP_ID, null, null, null, ACTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(jobRegistry, never()).submit(any(), any(), any());
    }

    @Test
    void loadContext_UC76_A3_rejectsWhenNoStudentNeedsComment() {
        GradeComponentSetup mid = setup(SETUP_ID, GradeComponentSetup.EvaluationType.MID_TERM);
        when(gradeService.getSetupOfClassOrThrow(CLASS_ID, SETUP_ID)).thenReturn(mid);
        GradeEvaluationComponent reading = component(mid, GradeEvaluationComponent.ComponentCode.READING, "Đọc");
        Student an = student(1L, "Nguyễn Văn An");
        student(2L, "Trần Thị Bình");
        givenSetup(mid, List.of(reading), List.of(entry(an, reading, "9", false)),
                List.of(result(50L, an, GradeEvaluationResult.Status.OFFICIAL, "Đã duyệt")));

        assertThatThrownBy(() -> service.startDraft(CLASS_ID, SETUP_ID, null, null, null, ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class);
        verify(jobRegistry, never()).submit(any(), any(), any());
    }

    @Test
    void loadContext_UC76_A3_overrideActorStillDraftsSubmittedRows() {
        GradeComponentSetup mid = setup(SETUP_ID, GradeComponentSetup.EvaluationType.MID_TERM);
        when(gradeService.getSetupOfClassOrThrow(CLASS_ID, SETUP_ID)).thenReturn(mid);
        when(permissionEvaluationService.hasPermission(ACTOR_ID, "academic.grade.edit.override")).thenReturn(true);
        GradeEvaluationComponent reading = component(mid, GradeEvaluationComponent.ComponentCode.READING, "Đọc");
        Student an = student(1L, "Nguyễn Văn An");
        givenSetup(mid, List.of(reading), List.of(entry(an, reading, "9", false)),
                List.of(result(50L, an, GradeEvaluationResult.Status.SUBMITTED, "Đã gửi")));

        TermCommentAiDraftService.DraftContext context = service.loadContext(CLASS_ID, SETUP_ID, null, ACTOR_ID);

        assertThat(context.targets()).extracting(TermCommentAiDraftService.Target::studentId).containsExactly(1L);
    }

    @Test
    void generateDraft_UC76_A4_failsJobWhenAiNeverAnswers() {
        stubAi((String) null);

        assertThatThrownBy(() -> service.generateDraft(context(target(1L, "Nguyễn Văn An", null))))
                .isInstanceOf(CommentAiDraftFailedException.class);
    }

    @Test
    void generateDraft_UC76_A4_retriesMissingStudentOnceThenMarksNotWritten() {
        stubAi("{\"comments\":[{\"studentId\":1,\"content\":\"Kỳ này An đọc hiểu rất vững vàng.\"}]}",
                "{\"comments\":[]}");

        TermCommentAiDraftResult result = service.generateDraft(context(target(1L, "Nguyễn Văn An", null),
                target(2L, "Trần Thị Bình", null)));

        TermCommentAiDraftResult.Row binh = result.rows().get(1);
        assertThat(binh.content()).isNull();
        assertThat(binh.warnings()).extracting(TermCommentAiDraftResult.Warning::type).containsExactly("NOT_WRITTEN");
        verify(aiClient, times(2)).chatWithFinishReason(eq(TermCommentAiDraftService.WRITE_PROMPT),
                anyString(), anyString(), anyDouble());
    }

    @Test
    void generateDraft_UC76_A5_rewritesOnceAndWarnsWhenStillSimilar() {
        String same = "Kỳ này con đọc hiểu rất vững vàng và nắm ý chính nhanh.";
        stubAi("{\"comments\":[{\"studentId\":1,\"content\":\"" + same + "\"},{\"studentId\":2,\"content\":\"" + same + "\"}]}",
                "{\"comments\":[{\"studentId\":2,\"content\":\"" + same + "\"}]}");

        TermCommentAiDraftResult result = service.generateDraft(context(target(1L, "Nguyễn Văn An", null),
                target(2L, "Trần Thị Bình", null)));

        assertThat(result.rows().get(0).warnings()).isEmpty();
        assertThat(result.rows().get(1).warnings()).extracting(TermCommentAiDraftResult.Warning::type)
                .containsExactly("SIMILAR_IN_CLASS");
        verify(aiClient, times(2)).chatWithFinishReason(eq(TermCommentAiDraftService.WRITE_PROMPT),
                anyString(), anyString(), anyDouble());
    }

    @Test
    void generateDraft_UC76_A6_warnsWhenCommentContainsDigits() {
        stubAi("{\"comments\":[{\"studentId\":1,\"content\":\"Kỳ này An đạt 9 điểm đọc.\"}]}");

        TermCommentAiDraftResult result = service.generateDraft(context(target(1L, "Nguyễn Văn An", null)));

        assertThat(result.rows().get(0).warnings()).extracting(TermCommentAiDraftResult.Warning::type)
                .containsExactly("HAS_DIGITS");
        assertThat(result.rows().get(0).content()).isEqualTo("Kỳ này An đạt 9 điểm đọc.");
    }

    @Test
    void generateDraft_UC76_A7_keepsExistingCommentForTeacherToCompare() {
        stubAi("{\"comments\":[{\"studentId\":1,\"content\":\"Kỳ này An đọc hiểu rất vững vàng.\"}]}");

        TermCommentAiDraftResult result = service.generateDraft(context(target(1L, "Nguyễn Văn An", "Nhận xét GV tự viết")));

        assertThat(result.rows().get(0).existingComment()).isEqualTo("Nhận xét GV tự viết");
        assertThat(result.rows().get(0).content()).isEqualTo("Kỳ này An đọc hiểu rất vững vàng.");
    }

    @Test
    void generateDraft_UC76_step5_sendsNoScoreNumbersToAi() {
        stubAi("{\"comments\":[{\"studentId\":1,\"content\":\"Kỳ này An đọc hiểu rất vững vàng.\"}]}");

        service.generateDraft(context(target(1L, "Nguyễn Văn An", null)));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(aiClient).chatWithFinishReason(eq(TermCommentAiDraftService.WRITE_PROMPT), payload.capture(), anyString(), anyDouble());
        assertThat(payload.getValue()).contains("\"assessment\":[\"tốt\"]").doesNotContain("score");
    }

    // ---- Bổ sung 2026-10-06: sidebar trò chuyện (lời giáo viên, sửa theo yêu cầu, viết lại) ----

    private void stubRevise(String response) {
        when(aiClient.chatWithFinishReason(eq(TermCommentAiDraftService.REVISE_PROMPT), anyString(), anyString(), anyDouble()))
                .thenReturn(response == null ? null : new NineRouterAiClient.ChatResult(response, "stop", null));
    }

    private static ReviseTermCommentAiDraftRequest instructionRequest(Long... ids) {
        List<ReviseTermCommentAiDraftRequest.CurrentRow> rows = new ArrayList<>();
        for (Long id : ids) {
            rows.add(new ReviseTermCommentAiDraftRequest.CurrentRow(id, "Nhận xét cũ của học sinh số " + id + " trong kỳ."));
        }
        return new ReviseTermCommentAiDraftRequest(ReviseTermCommentAiDraftRequest.Mode.INSTRUCTION, rows, List.of());
    }

    @Test
    void generateDraft_UC76_step5_sendsTeacherTranscriptAndNoteToAi() {
        when(aiClient.transcribe(any(), anyString(), any(), anyString(), eq("vi"))).thenReturn("An dạo này rất chăm phát biểu");
        stubAi("{\"comments\":[{\"studentId\":1,\"content\":\"Kỳ này An đọc hiểu vững và rất chăm phát biểu.\"}]}");

        TermCommentAiDraftResult result = service.generateDraft(context(target(1L, "Nguyễn Văn An", null)),
                new TermCommentAiDraftService.AudioInput(new byte[]{1}, "audio/webm"), "viết ấm áp");

        assertThat(result.transcript()).isEqualTo("An dạo này rất chăm phát biểu");
        assertThat(result.assistantMessage()).contains("lời giáo viên");
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(aiClient).chatWithFinishReason(eq(TermCommentAiDraftService.WRITE_PROMPT), payload.capture(), anyString(), anyDouble());
        assertThat(payload.getValue()).contains("\"teacherInstruction\":\"An dạo này rất chăm phát biểu\\nviết ấm áp\"");
    }

    @Test
    void startDraft_UC76_A9_rejectsNonAudioFile() {
        MockMultipartFile file = new MockMultipartFile("audio", "a.txt", "text/plain", new byte[]{1, 2});

        assertThatThrownBy(() -> service.startDraft(CLASS_ID, SETUP_ID, file, null, null, ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class);
        verify(jobRegistry, never()).submit(any(), any(), any());
    }

    @Test
    void startDraft_UC76_A9_rejectsAudioOverSizeLimit() {
        MockMultipartFile file = new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[2048]);

        assertThatThrownBy(() -> service.startDraft(CLASS_ID, SETUP_ID, file, null, null, ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class);
        verify(jobRegistry, never()).submit(any(), any(), any());
    }

    @Test
    void startRevise_UC76_step7b_rejectsInstructionModeWithoutAudioOrText() {
        assertThatThrownBy(() -> service.startRevise(CLASS_ID, SETUP_ID, null, "  ", instructionRequest(1L), ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class);
        verify(jobRegistry, never()).submit(any(), any(), any());
    }

    @Test
    void revise_UC76_step7b_changesOnlyRowsTheTeacherMentioned() {
        stubRevise("{\"assistantMessage\":\"Đã viết ngắn lại nhận xét của An.\",\"changes\":[{\"studentId\":1,\"content\":\"An đọc rất vững.\"},"
                + "{\"studentId\":99,\"content\":\"Không thuộc bản nháp.\"}]}");

        TermCommentAiDraftResult result = service.revise(context(target(1L, "Nguyễn Văn An", null), target(2L, "Trần Thị Bình", null)),
                instructionRequest(1L, 2L), null, "An viết ngắn lại");

        assertThat(result.assistantMessage()).isEqualTo("Đã viết ngắn lại nhận xét của An.");
        assertThat(result.rows()).extracting(TermCommentAiDraftResult.Row::content)
                .containsExactly("An đọc rất vững.", "Nhận xét cũ của học sinh số 2 trong kỳ.");
        verify(aiClient, never()).chatWithFinishReason(eq(TermCommentAiDraftService.WRITE_PROMPT), anyString(), anyString(), anyDouble());
    }

    @Test
    void revise_UC76_A4_failsWhenAiCannotHandleInstruction() {
        stubRevise(null);

        assertThatThrownBy(() -> service.revise(context(target(1L, "Nguyễn Văn An", null)), instructionRequest(1L), null, "ngắn hơn"))
                .isInstanceOf(CommentAiDraftFailedException.class);
    }

    @Test
    void revise_UC76_step7b_rewriteAllAvoidsCurrentTexts() {
        stubAi("{\"comments\":[{\"studentId\":1,\"content\":\"Điểm sáng của An là phần đọc hiểu.\"}]}");

        TermCommentAiDraftResult result = service.revise(context(target(1L, "Nguyễn Văn An", null)),
                new ReviseTermCommentAiDraftRequest(ReviseTermCommentAiDraftRequest.Mode.REWRITE_ALL,
                        List.of(new ReviseTermCommentAiDraftRequest.CurrentRow(1L, "Câu cũ cần tránh lặp lại.")), List.of()),
                null, null);

        assertThat(result.rows().get(0).content()).isEqualTo("Điểm sáng của An là phần đọc hiểu.");
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(aiClient).chatWithFinishReason(eq(TermCommentAiDraftService.WRITE_PROMPT), payload.capture(), anyString(), anyDouble());
        assertThat(payload.getValue()).contains("Câu cũ cần tránh lặp lại.");
    }
}
