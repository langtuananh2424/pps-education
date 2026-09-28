package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.com.pps.education.domain.AttendanceMark;
import vn.com.pps.education.domain.AttendanceSession;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CommentAiDraftResult;
import vn.com.pps.education.dto.ReviseCommentAiDraftRequest;
import vn.com.pps.education.exception.CommentAiDraftFailedException;
import vn.com.pps.education.exception.CommentAiDraftRejectedException;
import vn.com.pps.education.exception.StudentCommentNotEditableException;
import vn.com.pps.education.repository.AttendanceMarkRepository;
import vn.com.pps.education.repository.AttendanceSessionRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.StudentCommentRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-74: Trợ lý AI soạn nháp nhận xét hàng ngày từ audio — mỗi Alternate Flow 1 test (xem
 * docs/uc/phan-he-06-hoc-thuat.md). Test thuần Service logic: mock repository + NineRouterAiClient,
 * không chạm DB/mạng. A10 (Lưu nháp bỏ qua 1 số dòng) là hành vi sẵn có của saveDraftBatch (UC-21),
 * đã có test ở StudentCommentServiceTest — UC-74 không có đường ghi DB riêng.
 */
class CommentAiDraftServiceTest {

    private static final long SESSION_ID = 10L;
    private static final long ACTOR_ID = 99L;
    private static final LocalDate SESSION_DATE = LocalDate.of(2026, 9, 28);

    private final StudentCommentService studentCommentService = mock(StudentCommentService.class);
    private final ClassEnrollmentRepository classEnrollmentRepository = mock(ClassEnrollmentRepository.class);
    private final AttendanceSessionRepository attendanceSessionRepository = mock(AttendanceSessionRepository.class);
    private final AttendanceMarkRepository attendanceMarkRepository = mock(AttendanceMarkRepository.class);
    private final StudentCommentRepository studentCommentRepository = mock(StudentCommentRepository.class);
    private final NineRouterAiClient aiClient = mock(NineRouterAiClient.class);
    private final PromptTemplateLoader promptTemplateLoader = mock(PromptTemplateLoader.class);
    private final CommentAiDraftJobRegistry jobRegistry = mock(CommentAiDraftJobRegistry.class);

    private final CommentAiDraftService service = new CommentAiDraftService(studentCommentService,
            classEnrollmentRepository, attendanceSessionRepository, attendanceMarkRepository, studentCommentRepository,
            aiClient, promptTemplateLoader, new ObjectMapper(), jobRegistry,
            "comment-pps", 3, 120, 0.5, 10, 1024);

    private final ClassSession session = mock(ClassSession.class);

    private static final CommentAiDraftService.RosterStudent AN =
            new CommentAiDraftService.RosterStudent(1L, "Nguyễn Văn An", List.of());
    private static final CommentAiDraftService.RosterStudent BINH =
            new CommentAiDraftService.RosterStudent(2L, "Trần Thị Bình", List.of());
    private static final CommentAiDraftService.RosterStudent CHI =
            new CommentAiDraftService.RosterStudent(3L, "Lê Minh Chi", List.of());

    @BeforeEach
    void setUp() {
        // Prompt hệ thống = chính tên file, để stub AI theo từng bước (tách ý / viết / sửa).
        when(promptTemplateLoader.load(anyString(), anyMap())).thenAnswer(inv -> inv.getArgument(0));
        SchoolClass schoolClass = mock(SchoolClass.class);
        when(schoolClass.getId()).thenReturn(5L);
        when(schoolClass.getName()).thenReturn("KT-7A4");
        when(session.getId()).thenReturn(SESSION_ID);
        when(session.getSchoolClass()).thenReturn(schoolClass);
        when(session.getSessionDate()).thenReturn(SESSION_DATE);
        when(session.getTeacherType()).thenReturn(ClassSession.TeacherType.VIETNAMESE);
        when(studentCommentService.requireCanWriteDailyCommentFor(SESSION_ID, ACTOR_ID)).thenReturn(session);
        when(attendanceSessionRepository.findByClassSessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(studentCommentRepository.findByClassSessionId(SESSION_ID)).thenReturn(List.of());
        when(studentCommentRepository.findRecentByStudentIds(any(), any(), any(), any())).thenReturn(List.of());
    }

    private CommentAiDraftService.DraftContext context(CommentAiDraftService.RosterStudent... students) {
        return new CommentAiDraftService.DraftContext(SESSION_ID, "KT-7A4", SESSION_DATE, "Unit 4 - Past simple",
                List.of(students), List.of());
    }

    private void stubAi(String promptFile, String... responses) {
        var stub = when(aiClient.chatWithFinishReason(eq(promptFile), anyString(), anyString()));
        for (String response : responses) {
            stub = stub.thenReturn(new NineRouterAiClient.ChatResult(response, "stop", null));
        }
    }

    private Student student(long id, String name) {
        Student student = mock(Student.class);
        User user = mock(User.class);
        when(student.getId()).thenReturn(id);
        when(student.getUser()).thenReturn(user);
        when(user.getFullName()).thenReturn(name);
        return student;
    }

    private ClassEnrollment enrollment(Student student) {
        ClassEnrollment enrollment = mock(ClassEnrollment.class);
        when(enrollment.getStudent()).thenReturn(student);
        return enrollment;
    }

    // ---- Main Flow ----

    @Test
    void loadContext_UC74_MainFlow_skipsAbsentAndSubmittedStudentsAndKeepsRecentComments() {
        Student an = student(1L, "Nguyễn Văn An");
        Student binh = student(2L, "Trần Thị Bình");
        Student chi = student(3L, "Lê Minh Chi");
        List<ClassEnrollment> enrollments = List.of(enrollment(an), enrollment(binh), enrollment(chi));
        when(classEnrollmentRepository.findBySchoolClassIdAndStatus(5L, ClassEnrollment.Status.ACTIVE)).thenReturn(enrollments);
        AttendanceSession attendanceSession = mock(AttendanceSession.class);
        when(attendanceSession.getId()).thenReturn(7L);
        when(attendanceSessionRepository.findByClassSessionId(SESSION_ID)).thenReturn(Optional.of(attendanceSession));
        AttendanceMark absent = mock(AttendanceMark.class);
        when(absent.getStudent()).thenReturn(binh);
        when(absent.getStatus()).thenReturn(AttendanceMark.Status.ABSENT);
        when(attendanceMarkRepository.findByAttendanceSessionId(7L)).thenReturn(List.of(absent));
        StudentComment submitted = mock(StudentComment.class);
        when(submitted.getStudent()).thenReturn(chi);
        when(submitted.getStatus()).thenReturn(StudentComment.Status.PENDING);
        when(studentCommentRepository.findByClassSessionId(SESSION_ID)).thenReturn(List.of(submitted));
        StudentComment old1 = mock(StudentComment.class);
        when(old1.getStudent()).thenReturn(an);
        when(old1.getContent()).thenReturn("An hôm nay rất chăm chỉ.");
        when(old1.getCommentDate()).thenReturn(SESSION_DATE.minusDays(2));
        StudentComment blank = mock(StudentComment.class);
        when(blank.getStudent()).thenReturn(an);
        when(blank.getContent()).thenReturn("  ");
        when(studentCommentRepository.findRecentByStudentIds(eq(List.of(1L)), eq(StudentComment.Status.REJECTED),
                eq(SESSION_DATE.minusDays(120)), eq(SESSION_DATE))).thenReturn(List.of(old1, blank));

        CommentAiDraftService.DraftContext context = service.loadContext(SESSION_ID, ACTOR_ID);

        assertThat(context.roster()).extracting(CommentAiDraftService.RosterStudent::id).containsExactly(1L);
        assertThat(context.roster().get(0).previousComments())
                .containsExactly(new CommentAiDraftService.PreviousComment(SESSION_DATE.minusDays(2), "An hôm nay rất chăm chỉ."));
        assertThat(context.skipped()).extracting(CommentAiDraftResult.SkippedStudent::reason)
                .containsExactlyInAnyOrder("Vắng", "Đã gửi duyệt");
    }

    @Test
    void generateDraft_UC74_MainFlow_classCommentForMostAndIndividualForMentioned() {
        when(aiClient.transcribe(any(byte[].class), eq("audio/webm"), eq(null), anyString()))
                .thenReturn("Hôm nay cả lớp tập trung tốt. Riêng bạn An còn nói chuyện riêng.");
        stubAi(CommentAiDraftService.EXTRACT_PROMPT, """
                ```json
                {"classAttitude": "GOOD", "classPoints": ["cả lớp tập trung tốt"],
                 "individuals": [{"studentId": 1, "attitude": "AVERAGE", "points": ["còn nói chuyện riêng"], "evidence": "Riêng bạn An còn nói chuyện riêng"}],
                 "unmatched": []}
                ```""");
        stubAi(CommentAiDraftService.WRITE_PROMPT, """
                {"comments": [
                  {"studentId": 1, "content": "An cần chú ý hơn, trong giờ con vẫn còn nói chuyện riêng."},
                  {"studentId": 2, "content": "Bình giữ được sự tập trung suốt buổi, thầy cô rất vui."},
                  {"studentId": 3, "content": "Chi theo sát bài giảng và rất chú tâm vào hoạt động lớp."}
                ]}""");

        CommentAiDraftResult result = service.generateDraft(context(AN, BINH, CHI), new byte[]{1, 2}, "audio/webm", null);

        assertThat(result.transcript()).contains("cả lớp tập trung tốt");
        assertThat(result.rows()).extracting(CommentAiDraftResult.Row::studentId).containsExactly(1L, 2L, 3L);
        CommentAiDraftResult.Row an = result.rows().get(0);
        assertThat(an.source()).isEqualTo(CommentAiDraftService.SOURCE_INDIVIDUAL);
        assertThat(an.attitude()).isEqualTo("AVERAGE");
        assertThat(result.rows().get(1).source()).isEqualTo(CommentAiDraftService.SOURCE_CLASS);
        assertThat(result.rows().get(1).attitude()).isEqualTo("GOOD");
        assertThat(result.rows()).allSatisfy(r -> assertThat(r.warnings()).isEmpty());
        assertThat(result.assistantMessage()).contains("3 học sinh").contains("Nguyễn Văn An");
        // Không có dòng trùng -> không gọi viết lại.
        verify(aiClient, times(1)).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT), anyString(), anyString());
    }

    @Test
    void generateDraft_UC74_Step7_rewritesSimilarRowsOnce() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": \"GOOD\", \"classPoints\": [\"cả lớp tập trung tốt\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT,
                "{\"comments\": [{\"studentId\": 1, \"content\": \"Hôm nay con tập trung rất tốt trong giờ học.\"},"
                        + "{\"studentId\": 2, \"content\": \"Hôm nay con tập trung rất tốt trong giờ học.\"}]}",
                "{\"comments\": [{\"studentId\": 2, \"content\": \"Bình chú tâm nghe giảng suốt buổi, rất đáng khen.\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(AN, BINH), null, null, "cả lớp tập trung tốt");

        assertThat(result.rows().get(1).content()).isEqualTo("Bình chú tâm nghe giảng suốt buổi, rất đáng khen.");
        assertThat(result.rows()).allSatisfy(r -> assertThat(r.warnings()).isEmpty());
        verify(aiClient, times(2)).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT), anyString(), anyString());
        verify(aiClient, never()).transcribe(any(), any(), any(), any());
    }

    // ---- Alternate Flows ----

    @Test
    void loadContext_UC74_A1_rejectsForeignTeacherSession() {
        when(session.getTeacherType()).thenReturn(ClassSession.TeacherType.FOREIGN);

        assertThatThrownBy(() -> service.loadContext(SESSION_ID, ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class)
                .hasMessageContaining("giáo viên nước ngoài");
    }

    @Test
    void loadContext_UC74_A2_rejectsOutsideEditWindowLikeSaveDraft() {
        when(studentCommentService.requireCanWriteDailyCommentFor(SESSION_ID, ACTOR_ID))
                .thenThrow(new StudentCommentNotEditableException("hết hạn"));

        assertThatThrownBy(() -> service.loadContext(SESSION_ID, ACTOR_ID))
                .isInstanceOf(StudentCommentNotEditableException.class);
    }

    @Test
    void startDraft_UC74_A3_rejectsWhenNoAudioAndNoNote() {
        assertThatThrownBy(() -> service.startDraft(SESSION_ID, null, "  ", ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class);
        verify(jobRegistry, never()).submit(any(), any());
    }

    @Test
    void startDraft_UC74_A3_rejectsOversizedOrNonAudioFile() {
        MockMultipartFile big = new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[2048]);
        MockMultipartFile notAudio = new MockMultipartFile("audio", "a.pdf", "application/pdf", new byte[10]);

        assertThatThrownBy(() -> service.startDraft(SESSION_ID, big, null, ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class).hasMessageContaining("quá lớn");
        assertThatThrownBy(() -> service.startDraft(SESSION_ID, notAudio, null, ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class).hasMessageContaining("không phải audio");
        verify(jobRegistry, never()).submit(any(), any());
    }

    @Test
    void loadContext_UC74_A4_rejectsWhenNoStudentLeftToDraft() {
        Student an = student(1L, "Nguyễn Văn An");
        List<ClassEnrollment> enrollments = List.of(enrollment(an));
        when(classEnrollmentRepository.findBySchoolClassIdAndStatus(5L, ClassEnrollment.Status.ACTIVE)).thenReturn(enrollments);
        StudentComment approved = mock(StudentComment.class);
        when(approved.getStudent()).thenReturn(an);
        when(approved.getStatus()).thenReturn(StudentComment.Status.APPROVED);
        when(studentCommentRepository.findByClassSessionId(SESSION_ID)).thenReturn(List.of(approved));

        assertThatThrownBy(() -> service.loadContext(SESSION_ID, ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class)
                .hasMessageContaining("Không còn học sinh");
    }

    @Test
    void generateDraft_UC74_A5_failsWhenTranscriptionFails() {
        when(aiClient.transcribe(any(byte[].class), any(), any(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.generateDraft(context(AN), new byte[]{1}, "audio/webm", null))
                .isInstanceOf(CommentAiDraftFailedException.class)
                .hasMessageContaining("Không chuyển được audio");
    }

    @Test
    void generateDraft_UC74_A5_failsWhenAiReturnsUnreadableOrTruncatedResult() {
        when(aiClient.chatWithFinishReason(eq(CommentAiDraftService.EXTRACT_PROMPT), anyString(), anyString()))
                .thenReturn(new NineRouterAiClient.ChatResult("{\"classPoints\": [\"tốt\"", "length", null));

        assertThatThrownBy(() -> service.generateDraft(context(AN), null, null, "cả lớp tốt"))
                .isInstanceOf(CommentAiDraftFailedException.class);
    }

    @Test
    void generateDraft_UC74_A6_studentOutsideRosterGoesToUnmatchedInsteadOfGuessing() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT, """
                {"classAttitude": null, "classPoints": ["cả lớp làm bài đầy đủ"],
                 "individuals": [{"studentId": 777, "attitude": "WEAK", "points": ["hay đi muộn"], "evidence": "bạn Minh hay đi muộn"}],
                 "unmatched": [{"quote": "bạn Hùng hôm nay ngoan", "candidateStudentIds": [1, 888]}]}""");
        stubAi(CommentAiDraftService.WRITE_PROMPT,
                "{\"comments\": [{\"studentId\": 1, \"content\": \"An làm bài đầy đủ, thầy cô ghi nhận sự cố gắng.\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(AN), null, null, "ghi chú");

        assertThat(result.rows()).extracting(CommentAiDraftResult.Row::studentId).containsExactly(1L);
        assertThat(result.unmatchedMentions()).extracting(CommentAiDraftResult.UnmatchedMention::quote)
                .containsExactly("bạn Minh hay đi muộn", "bạn Hùng hôm nay ngoan");
        assertThat(result.unmatchedMentions().get(1).candidateStudentIds()).containsExactly(1L);
    }

    @Test
    void generateDraft_UC74_A7_attitudeLeftBlankWhenTeacherDidNotMentionIt() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT, """
                {"classAttitude": "GOOD", "classPoints": ["cả lớp tập trung"],
                 "individuals": [{"studentId": 1, "attitude": null, "points": ["phát âm cần luyện thêm"]}],
                 "unmatched": []}""");
        stubAi(CommentAiDraftService.WRITE_PROMPT, """
                {"comments": [{"studentId": 1, "content": "An cần luyện phát âm thêm ở nhà."},
                              {"studentId": 2, "content": "Bình rất tập trung trong suốt tiết học."}]}""");

        CommentAiDraftResult result = service.generateDraft(context(AN, BINH), null, null, "ghi chú");

        assertThat(result.rows().get(0).attitude()).isNull();
        assertThat(result.rows().get(1).attitude()).isEqualTo("GOOD");
    }

    @Test
    void generateDraft_UC74_A7_invalidAttitudeFromAiIsDropped() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": \"VERY_GOOD\", \"classPoints\": [\"cả lớp ổn\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT, "{\"comments\": [{\"studentId\": 1, \"content\": \"An học ổn định.\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(AN), null, null, "ghi chú");

        assertThat(result.rows().get(0).attitude()).isNull();
    }

    @Test
    void generateDraft_UC74_A8_warnsWhenStillSimilarAfterRewrite() {
        String same = "Hôm nay con tập trung rất tốt trong giờ học.";
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": null, \"classPoints\": [\"tập trung tốt\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT,
                "{\"comments\": [{\"studentId\": 1, \"content\": \"" + same + "\"}, {\"studentId\": 2, \"content\": \"" + same + "\"}]}",
                "{\"comments\": [{\"studentId\": 2, \"content\": \"" + same + "\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(AN, BINH), null, null, "ghi chú");

        assertThat(result.rows().get(1).warnings()).extracting(CommentAiDraftResult.Warning::type)
                .containsExactly("SIMILAR_IN_SESSION");
        assertThat(result.rows().get(1).warnings().get(0).message()).contains("Nguyễn Văn An").contains("100%");
    }

    @Test
    void generateDraft_UC74_A8_warnsWhenSimilarToPreviousSessionOfSameStudent() {
        String old = "An hôm nay rất chăm chỉ và hăng hái phát biểu xây dựng bài.";
        CommentAiDraftService.RosterStudent anWithHistory = new CommentAiDraftService.RosterStudent(1L, "Nguyễn Văn An",
                List.of(new CommentAiDraftService.PreviousComment(SESSION_DATE.minusDays(7), old)));
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": null, \"classPoints\": [\"chăm chỉ\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT,
                "{\"comments\": [{\"studentId\": 1, \"content\": \"" + old + "\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(anWithHistory), null, null, "ghi chú");

        assertThat(result.rows().get(0).warnings()).extracting(CommentAiDraftResult.Warning::type)
                .containsExactly("SIMILAR_TO_PREVIOUS");
        assertThat(result.rows().get(0).warnings().get(0).message()).contains(SESSION_DATE.minusDays(7).toString());
        // Lượt viết lại có kèm câu cũ để tránh.
        verify(aiClient, times(2)).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT), anyString(), anyString());
    }

    @Test
    void generateDraft_UC74_A9_warnsWhenCommentContainsDigits() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": null, \"classPoints\": [\"làm bài tốt\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT,
                "{\"comments\": [{\"studentId\": 1, \"content\": \"An làm đúng 9 trên 10 câu.\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(AN), null, null, "ghi chú");

        assertThat(result.rows().get(0).warnings()).extracting(CommentAiDraftResult.Warning::type)
                .containsExactly("CONTAINS_DIGITS");
    }

    // ---- Bước 9: trò chuyện sửa bản nháp ----

    @Test
    void startRevise_UC74_A3_rejectsBlankInstruction() {
        ReviseCommentAiDraftRequest request = new ReviseCommentAiDraftRequest(ReviseCommentAiDraftRequest.Mode.INSTRUCTION,
                " ", "", null, List.of(), List.of());

        assertThatThrownBy(() -> service.startRevise(SESSION_ID, request, ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class);
        verify(jobRegistry, never()).submit(any(), any());
    }

    @Test
    void revise_UC74_MainFlow_instructionChangesOnlyTargetedRows() {
        stubAi(CommentAiDraftService.REVISE_PROMPT, """
                {"assistantMessage": "Đã hạ Thái độ của Bình xuống Trung bình.",
                 "changes": [{"studentId": 2, "attitude": "AVERAGE", "content": "Bình hôm nay còn lơ là, cần cố gắng hơn."},
                             {"studentId": 1, "attitude": "SUPER"},
                             {"studentId": 555, "content": "không thuộc lớp"}]}""");
        ReviseCommentAiDraftRequest request = new ReviseCommentAiDraftRequest(ReviseCommentAiDraftRequest.Mode.INSTRUCTION,
                "Bình hôm nay cho Trung bình", "transcript", null,
                List.of(new ReviseCommentAiDraftRequest.CurrentRow(1L, "GOOD", "An tập trung tốt."),
                        new ReviseCommentAiDraftRequest.CurrentRow(2L, "GOOD", "Bình tập trung tốt.")),
                List.of(new ReviseCommentAiDraftRequest.ChatTurn("teacher", "soạn giúp")));

        CommentAiDraftResult result = service.revise(context(AN, BINH), request);

        assertThat(result.assistantMessage()).isEqualTo("Đã hạ Thái độ của Bình xuống Trung bình.");
        assertThat(result.rows()).extracting(CommentAiDraftResult.Row::studentId).containsExactly(1L, 2L);
        assertThat(result.rows().get(0).attitude()).isEqualTo("GOOD");
        assertThat(result.rows().get(0).content()).isEqualTo("An tập trung tốt.");
        assertThat(result.rows().get(1).attitude()).isEqualTo("AVERAGE");
        assertThat(result.rows().get(1).content()).isEqualTo("Bình hôm nay còn lơ là, cần cố gắng hơn.");
    }

    @Test
    void revise_UC74_rewriteAllKeepsTeacherAdjustedAttitudeAndAvoidsOldTexts() {
        stubAi(CommentAiDraftService.WRITE_PROMPT, """
                {"comments": [{"studentId": 1, "content": "An giữ được sự chú ý trong giờ, rất đáng khen."}]}""");
        CommentAiDraftResult.Extraction extraction = new CommentAiDraftResult.Extraction("GOOD", List.of("tập trung tốt"), List.of());
        ReviseCommentAiDraftRequest request = new ReviseCommentAiDraftRequest(ReviseCommentAiDraftRequest.Mode.REWRITE_ALL,
                null, "transcript", extraction,
                List.of(new ReviseCommentAiDraftRequest.CurrentRow(1L, "FAIR", "An tập trung tốt.")), List.of());

        CommentAiDraftResult result = service.revise(context(AN), request);

        assertThat(result.rows().get(0).attitude()).isEqualTo("FAIR");
        assertThat(result.rows().get(0).content()).isEqualTo("An giữ được sự chú ý trong giờ, rất đáng khen.");
        verify(aiClient).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT),
                org.mockito.ArgumentMatchers.contains("An tập trung tốt."), anyString());
    }

    @Test
    void normalizeAttitude_UC74_acceptsOnlyFiveAttitudeValues() {
        assertThat(CommentAiDraftService.normalizeAttitude("good")).isEqualTo("GOOD");
        assertThat(CommentAiDraftService.normalizeAttitude("POOR")).isNull();
    }
}
