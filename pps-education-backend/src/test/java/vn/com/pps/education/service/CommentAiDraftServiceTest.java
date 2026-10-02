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
import static org.mockito.ArgumentMatchers.anyDouble;
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
    private final StudentAttitudeAlertTrackingService attitudeAlertTrackingService = mock(StudentAttitudeAlertTrackingService.class);
    private final ClassEnrollmentRepository classEnrollmentRepository = mock(ClassEnrollmentRepository.class);
    private final AttendanceSessionRepository attendanceSessionRepository = mock(AttendanceSessionRepository.class);
    private final AttendanceMarkRepository attendanceMarkRepository = mock(AttendanceMarkRepository.class);
    private final StudentCommentRepository studentCommentRepository = mock(StudentCommentRepository.class);
    private final NineRouterAiClient aiClient = mock(NineRouterAiClient.class);
    private final PromptTemplateLoader promptTemplateLoader = mock(PromptTemplateLoader.class);
    private final AiJobRegistry jobRegistry = mock(AiJobRegistry.class);

    private final HomeworkInsightService homeworkInsightService = mock(HomeworkInsightService.class);
    private final StudentSignalService studentSignalService = mock(StudentSignalService.class);

    private final CommentAiDraftService service = new CommentAiDraftService(studentCommentService, attitudeAlertTrackingService,
            classEnrollmentRepository, attendanceSessionRepository, attendanceMarkRepository, studentCommentRepository,
            homeworkInsightService, studentSignalService, aiClient, new CommentAiJsonCaller(aiClient, promptTemplateLoader, new ObjectMapper()), jobRegistry,
            "comment-pps", 3, 120, 0.5, 10, 1024, 20, 0.3, 0.7);

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
        var stub = when(aiClient.chatWithFinishReason(eq(promptFile), anyString(), anyString(), anyDouble()));
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
        when(aiClient.transcribe(any(byte[].class), eq("audio/webm"), eq(null), anyString(), eq("vi")))
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
                  {"studentId": 2, "content": "Bình giữ được sự tập trung suốt buổi, rất đáng khen."},
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
        // Mức Trung bình luôn kèm lời nhắc hệ quả cảnh báo phụ huynh; các dòng khác không có cảnh báo.
        assertThat(an.warnings()).extracting(CommentAiDraftResult.Warning::type).containsExactly("ATTITUDE_ALERT");
        assertThat(result.rows().subList(1, 3)).allSatisfy(r -> assertThat(r.warnings()).isEmpty());
        assertThat(result.assistantMessage()).contains("3 học sinh").contains("Nguyễn Văn An");
        // Không có dòng trùng -> không gọi viết lại.
        verify(aiClient, times(1)).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT), anyString(), anyString(), anyDouble());
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
        verify(aiClient, times(2)).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT), anyString(), anyString(), anyDouble());
        verify(aiClient, never()).transcribe(any(), any(), any(), any(), any());
    }

    @Test
    void generateDraft_UC74_Step6_writesWithConfiguredTemperatureButExtractsAtZero() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": \"GOOD\", \"classPoints\": [\"cả lớp tập trung tốt\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT,
                "{\"comments\": [{\"studentId\": 1, \"content\": \"An tập trung nghe giảng suốt buổi.\"}]}");

        service.generateDraft(context(AN), null, null, "cả lớp tập trung tốt");

        // Bổ sung 2026-10-01: chỉ bước viết câu dùng nhiệt độ cấu hình (0.7) để câu chữ đa dạng; tách ý giữ 0.
        verify(aiClient).chatWithFinishReason(eq(CommentAiDraftService.EXTRACT_PROMPT), anyString(), anyString(), eq(0.0));
        verify(aiClient).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT), anyString(), anyString(), eq(0.7));
    }

    // ---- Alternate Flows ----

    @Test
    void loadContext_UC74_A1_foreignTeacherSessionIsAllowed() {
        when(session.getTeacherType()).thenReturn(ClassSession.TeacherType.FOREIGN);
        Student an = student(1L, "Nguyễn Văn An");
        List<ClassEnrollment> enrollments = List.of(enrollment(an));
        when(classEnrollmentRepository.findBySchoolClassIdAndStatus(5L, ClassEnrollment.Status.ACTIVE)).thenReturn(enrollments);

        CommentAiDraftService.DraftContext context = service.loadContext(SESSION_ID, ACTOR_ID);

        assertThat(context.roster()).extracting(CommentAiDraftService.RosterStudent::id).containsExactly(1L);
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
        assertThatThrownBy(() -> service.startDraft(SESSION_ID, null, "  ", null, ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class);
        verify(jobRegistry, never()).submit(any(), any(), any());
    }

    @Test
    void startDraft_UC74_A3_rejectsOversizedOrNonAudioFile() {
        MockMultipartFile big = new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[2048]);
        MockMultipartFile notAudio = new MockMultipartFile("audio", "a.pdf", "application/pdf", new byte[10]);

        assertThatThrownBy(() -> service.startDraft(SESSION_ID, big, null, null, ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class).hasMessageContaining("quá lớn");
        assertThatThrownBy(() -> service.startDraft(SESSION_ID, notAudio, null, null, ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class).hasMessageContaining("không phải audio");
        verify(jobRegistry, never()).submit(any(), any(), any());
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
        when(aiClient.transcribe(any(byte[].class), any(), any(), any(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.generateDraft(context(AN), new byte[]{1}, "audio/webm", null))
                .isInstanceOf(CommentAiDraftFailedException.class)
                .hasMessageContaining("Không chuyển được audio");
    }

    @Test
    void generateDraft_UC74_A5_failsWhenAiReturnsUnreadableOrTruncatedResult() {
        when(aiClient.chatWithFinishReason(eq(CommentAiDraftService.EXTRACT_PROMPT), anyString(), anyString(), anyDouble()))
                .thenReturn(new NineRouterAiClient.ChatResult("{\"classPoints\": [\"tốt\"", "length", null));

        assertThatThrownBy(() -> service.generateDraft(context(AN), null, null, "cả lớp tốt"))
                .isInstanceOf(CommentAiDraftFailedException.class);
    }

    @Test
    void generateDraft_UC74_A5_retriesFailedWriteBatchOnceBeforeWarning() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": \"GOOD\", \"classPoints\": [\"cả lớp tập trung tốt\"], \"individuals\": [], \"unmatched\": []}");
        when(aiClient.chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT), anyString(), anyString(), anyDouble()))
                .thenReturn(null)
                .thenReturn(new NineRouterAiClient.ChatResult("{\"comments\": ["
                        + "{\"studentId\": 1, \"content\": \"An chú tâm nghe giảng suốt buổi học.\"},"
                        + "{\"studentId\": 2, \"content\": \"Bình hăng hái phát biểu, xây dựng bài sôi nổi.\"}]}", "stop", null));

        CommentAiDraftResult result = service.generateDraft(context(AN, BINH), null, null, "cả lớp tập trung tốt");

        assertThat(result.rows()).extracting(CommentAiDraftResult.Row::content)
                .containsExactly("An chú tâm nghe giảng suốt buổi học.", "Bình hăng hái phát biểu, xây dựng bài sôi nổi.");
        assertThat(result.rows()).allSatisfy(r -> assertThat(r.warnings()).isEmpty());
        // Lô đầu lỗi -> thử lại đúng 1 lần (lô nửa kích thước vẫn chứa cả 2 học sinh).
        verify(aiClient, times(2)).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT), anyString(), anyString(), anyDouble());
    }

    @Test
    void generateDraft_UC74_A5_warnsNotWrittenWhenRetryAlsoFails() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": \"GOOD\", \"classPoints\": [\"cả lớp tập trung tốt\"], \"individuals\": [], \"unmatched\": []}");
        when(aiClient.chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT), anyString(), anyString(), anyDouble()))
                .thenReturn(new NineRouterAiClient.ChatResult(
                        "{\"comments\": [{\"studentId\": 1, \"content\": \"An chú tâm nghe giảng suốt buổi học.\"}]}", "stop", null))
                .thenReturn(null);

        CommentAiDraftResult result = service.generateDraft(context(AN, BINH), null, null, "cả lớp tập trung tốt");

        assertThat(result.rows().get(1).warnings()).extracting(CommentAiDraftResult.Warning::type).containsExactly("NOT_WRITTEN");
        // 1 lượt đầu + 1 lần thử lại cho Bình, không thử lần thứ 3.
        verify(aiClient, times(2)).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT), anyString(), anyString(), anyDouble());
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
    void generateDraft_UC74_Step7_rewritesAdjacentRowWithSameOpeningPattern() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": null, \"classPoints\": [\"tập trung tốt\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT, """
                {"comments": [{"studentId": 1, "content": "An tập trung nghe giảng suốt buổi. Mong con giữ vững."},
                              {"studentId": 2, "content": "Bình tập trung nghe giảng và làm bài rất cẩn thận, hẹn gặp con."}]}""",
                "{\"comments\": [{\"studentId\": 2, \"content\": \"Điểm đáng khen của Bình hôm nay là sự cẩn thận.\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(AN, BINH), null, null, "ghi chú");

        verify(aiClient, times(2)).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT), anyString(), anyString(), anyDouble());
        assertThat(result.rows().get(1).content()).startsWith("Điểm đáng khen");
        assertThat(result.rows()).allSatisfy(r -> assertThat(r.warnings()).extracting(CommentAiDraftResult.Warning::type)
                .doesNotContain("REPEATED_PATTERN"));
    }

    @Test
    void generateDraft_UC74_A8_warnsWhenOpeningPatternStillRepeatedAfterRewrite() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": null, \"classPoints\": [\"tập trung tốt\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT, """
                {"comments": [{"studentId": 1, "content": "An tập trung nghe giảng suốt buổi. Mong con giữ vững."},
                              {"studentId": 2, "content": "Bình tập trung nghe giảng và làm bài rất cẩn thận, hẹn gặp con."}]}""",
                "{\"comments\": [{\"studentId\": 2, \"content\": \"Bình tập trung nghe giảng, phát biểu sôi nổi hơn hẳn.\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(AN, BINH), null, null, "ghi chú");

        assertThat(result.rows().get(1).warnings()).extracting(CommentAiDraftResult.Warning::type).containsExactly("REPEATED_PATTERN");
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
        verify(aiClient, times(2)).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT), anyString(), anyString(), anyDouble());
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
                " ", "", null, List.of(), List.of(), null);

        assertThatThrownBy(() -> service.startRevise(SESSION_ID, request, ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class);
        verify(jobRegistry, never()).submit(any(), any(), any());
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
                List.of(new ReviseCommentAiDraftRequest.ChatTurn("teacher", "soạn giúp")), null);

        CommentAiDraftResult result = service.revise(context(AN, BINH), request);

        assertThat(result.assistantMessage()).isEqualTo("Đã hạ Thái độ của Bình xuống Trung bình.");
        assertThat(result.rows()).extracting(CommentAiDraftResult.Row::studentId).containsExactly(1L, 2L);
        assertThat(result.rows().get(0).attitude()).isEqualTo("GOOD");
        assertThat(result.rows().get(0).content()).isEqualTo("An tập trung tốt.");
        assertThat(result.rows().get(1).attitude()).isEqualTo("AVERAGE");
        assertThat(result.rows().get(1).content()).isEqualTo("Bình hôm nay còn lơ là, cần cố gắng hơn.");
    }

    @Test
    void revise_UC74_Step7_rephrasesRevisedRowThatDuplicatesClassmateOnce() {
        stubAi(CommentAiDraftService.REVISE_PROMPT, """
                {"assistantMessage": "Đã viết lại nhận xét của Bình.",
                 "changes": [{"studentId": 2, "content": "Hôm nay con tập trung rất tốt trong giờ học và hăng hái phát biểu."}]}""",
                """
                {"assistantMessage": "Đã diễn đạt lại.",
                 "changes": [{"studentId": 2, "content": "Bình chú tâm suốt buổi, giơ tay phát biểu rất mạnh dạn."},
                             {"studentId": 1, "content": "không được đổi dòng của An"}]}""");
        ReviseCommentAiDraftRequest request = new ReviseCommentAiDraftRequest(ReviseCommentAiDraftRequest.Mode.INSTRUCTION,
                "viết lại nhận xét của Bình", "transcript", null,
                List.of(new ReviseCommentAiDraftRequest.CurrentRow(1L, "GOOD", "Hôm nay con tập trung rất tốt trong giờ học và hăng hái phát biểu."),
                        new ReviseCommentAiDraftRequest.CurrentRow(2L, "GOOD", "Bình tập trung tốt.")),
                List.of(), null);

        CommentAiDraftResult result = service.revise(context(AN, BINH), request);

        // Dòng vừa sửa trùng nguyên văn bạn An -> nhờ AI diễn đạt lại đúng 1 lần, chỉ nhận thay đổi của dòng đó.
        assertThat(result.rows().get(1).content()).isEqualTo("Bình chú tâm suốt buổi, giơ tay phát biểu rất mạnh dạn.");
        assertThat(result.rows().get(0).content()).isEqualTo("Hôm nay con tập trung rất tốt trong giờ học và hăng hái phát biểu.");
        assertThat(result.rows()).allSatisfy(r -> assertThat(r.warnings()).isEmpty());
        verify(aiClient, times(2)).chatWithFinishReason(eq(CommentAiDraftService.REVISE_PROMPT), anyString(), anyString(), anyDouble());
        verify(aiClient).chatWithFinishReason(eq(CommentAiDraftService.REVISE_PROMPT),
                org.mockito.ArgumentMatchers.contains("Diễn đạt lại câu chữ nhận xét của: Trần Thị Bình (studentId 2)"), anyString(), eq(0.7));
    }

    @Test
    void revise_UC74_Step9_doesNotRephraseWhenRevisedRowIsDistinct() {
        stubAi(CommentAiDraftService.REVISE_PROMPT, """
                {"assistantMessage": "Đã sửa.", "changes": [{"studentId": 2, "content": "Bình hôm nay còn lơ là, cần cố gắng hơn."}]}""");
        ReviseCommentAiDraftRequest request = new ReviseCommentAiDraftRequest(ReviseCommentAiDraftRequest.Mode.INSTRUCTION,
                "Bình lơ là", "transcript", null,
                List.of(new ReviseCommentAiDraftRequest.CurrentRow(1L, "GOOD", "An tập trung tốt."),
                        new ReviseCommentAiDraftRequest.CurrentRow(2L, "GOOD", "Bình tập trung tốt.")),
                List.of(), null);

        service.revise(context(AN, BINH), request);

        verify(aiClient, times(1)).chatWithFinishReason(eq(CommentAiDraftService.REVISE_PROMPT), anyString(), anyString(), anyDouble());
        verify(aiClient).chatWithFinishReason(eq(CommentAiDraftService.REVISE_PROMPT), anyString(), anyString(), eq(0.0));
    }

    @Test
    void revise_UC74_rewriteAllKeepsTeacherAdjustedAttitudeAndAvoidsOldTexts() {
        stubAi(CommentAiDraftService.WRITE_PROMPT, """
                {"comments": [{"studentId": 1, "content": "An giữ được sự chú ý trong giờ, rất đáng khen."}]}""");
        CommentAiDraftResult.Extraction extraction = new CommentAiDraftResult.Extraction("GOOD", List.of("tập trung tốt"), List.of(), "cô");
        ReviseCommentAiDraftRequest request = new ReviseCommentAiDraftRequest(ReviseCommentAiDraftRequest.Mode.REWRITE_ALL,
                null, "transcript", extraction,
                List.of(new ReviseCommentAiDraftRequest.CurrentRow(1L, "FAIR", "An tập trung tốt.")), List.of(), null);

        CommentAiDraftResult result = service.revise(context(AN), request);

        assertThat(result.rows().get(0).attitude()).isEqualTo("FAIR");
        assertThat(result.rows().get(0).content()).isEqualTo("An giữ được sự chú ý trong giờ, rất đáng khen.");
        verify(aiClient).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT),
                org.mockito.ArgumentMatchers.contains("An tập trung tốt."), anyString(), anyDouble());
        verify(aiClient).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT),
                org.mockito.ArgumentMatchers.contains("\"teacherPronoun\":\"cô\""), anyString(), anyDouble());
    }

    // ---- Đại từ giáo viên tự xưng + rubric ----

    @Test
    void generateDraft_UC74_teacherPronounFromAiIsPassedToWritingStep() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"teacherPronoun\": \"Thầy\", \"classAttitude\": \"GOOD\", \"classPoints\": [\"tích cực\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT, "{\"comments\": [{\"studentId\": 1, \"content\": \"Thầy rất vui vì An tích cực.\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(AN), null, null, "Thầy tuyên dương cả lớp tích cực");

        assertThat(result.extraction().teacherPronoun()).isEqualTo("thầy");
        verify(aiClient).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT),
                org.mockito.ArgumentMatchers.contains("\"teacherPronoun\":\"thầy\""), anyString(), anyDouble());
    }

    @Test
    void generateDraft_UC74_pronounDetectedFromTeacherTextWhenAiOmitsIt() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": null, \"classPoints\": [\"tích cực\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT, "{\"comments\": [{\"studentId\": 1, \"content\": \"An học tích cực.\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(AN), null, null, "Hôm nay cô thấy cả lớp rất tích cực");

        assertThat(result.extraction().teacherPronoun()).isEqualTo("cô");
    }

    @Test
    void generateDraft_UC74_studentsSharingOneSentenceAreSplitWithSharedWithFiltered() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT, """
                {"classAttitude": null, "classPoints": [],
                 "individuals": [
                   {"studentId": 1, "attitude": "AVERAGE", "points": ["nói chuyện riêng"], "evidence": "An / Bình: nói chuyện riêng", "sharedWith": [2, 1, 999]},
                   {"studentId": 2, "attitude": "AVERAGE", "points": ["nói chuyện riêng"], "evidence": "An / Bình: nói chuyện riêng", "sharedWith": [1]}],
                 "unmatched": []}""");
        stubAi(CommentAiDraftService.WRITE_PROMPT, """
                {"comments": [{"studentId": 1, "content": "An cần tập trung hơn trong giờ học."},
                              {"studentId": 2, "content": "Buổi này Bình còn làm việc riêng, mong con chú ý hơn."}]}""");

        CommentAiDraftResult result = service.generateDraft(context(AN, BINH, CHI), null, null, "An / Bình: nói chuyện riêng");

        assertThat(result.rows()).extracting(CommentAiDraftResult.Row::studentId).containsExactly(1L, 2L);
        assertThat(result.extraction().individuals()).extracting(CommentAiDraftResult.IndividualPoints::sharedWith)
                .containsExactly(List.of(2L), List.of(1L));
        verify(aiClient).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT),
                org.mockito.ArgumentMatchers.contains("\"sharedWithStudentIds\":[2]"), anyString(), anyDouble());
    }

    @Test
    void generateDraft_UC74_warnsWhenPronounWrittenButTeacherPronounUnknown() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": null, \"classPoints\": [\"tích cực\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT, "{\"comments\": [{\"studentId\": 1, \"content\": \"Thầy/cô ghi nhận An học tích cực.\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(AN), null, null, "Cả lớp học tích cực");

        assertThat(result.extraction().teacherPronoun()).isNull();
        assertThat(result.rows().get(0).warnings()).extracting(CommentAiDraftResult.Warning::type).contains("PRONOUN_MISMATCH");
    }

    @Test
    void pronounMismatchWarning_UC74_flagsOnlyWrongOrUnknownPronoun() {
        assertThat(CommentAiDraftService.pronounMismatchWarning("Cô mong con cố gắng hơn.", "thầy")).isNotNull();
        assertThat(CommentAiDraftService.pronounMismatchWarning("Thầy rất vui vì con.", "cô")).isNotNull();
        assertThat(CommentAiDraftService.pronounMismatchWarning("Cô mong con cố gắng hơn.", null)).isNotNull();
        assertThat(CommentAiDraftService.pronounMismatchWarning("Cô mong con cố gắng hơn.", "cô")).isNull();
        assertThat(CommentAiDraftService.pronounMismatchWarning("Mong con có thêm tự tin, con cố lên.", null)).isNull();
    }

    @Test
    void detectPronoun_UC74_ambiguousOrMissingReturnsNull() {
        assertThat(CommentAiDraftService.detectPronoun("Cô giáo chủ nhiệm nhờ thầy nhắc cả lớp")).isNull();
        assertThat(CommentAiDraftService.detectPronoun("Cả lớp hôm nay học tốt")).isNull();
        assertThat(CommentAiDraftService.detectPronoun("Côn trùng")).isNull();
    }

    @Test
    void generateDraft_UC74_rubricIsInjectedIntoPrompts() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": null, \"classPoints\": [\"tích cực\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT, "{\"comments\": [{\"studentId\": 1, \"content\": \"An học tích cực.\"}]}");

        service.generateDraft(context(AN), null, null, "ghi chú");

        verify(promptTemplateLoader, org.mockito.Mockito.atLeastOnce()).load(eq(CommentAiDraftService.RUBRIC_FILE), anyMap());
        verify(promptTemplateLoader, org.mockito.Mockito.atLeastOnce()).load(eq(CommentAiDraftService.WRITE_PROMPT),
                eq(java.util.Map.of("RUBRIC", CommentAiDraftService.RUBRIC_FILE)));
    }

    @Test
    void normalizeAttitude_UC74_acceptsOnlyFiveAttitudeValues() {
        assertThat(CommentAiDraftService.normalizeAttitude("good")).isEqualTo("GOOD");
        assertThat(CommentAiDraftService.normalizeAttitude("POOR")).isNull();
    }

    @Test
    void generateDraft_UC74_lessonTitleNotSentToAiAndWarnedIfWritten() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": null, \"classPoints\": [\"tích cực\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT,
                "{\"comments\": [{\"studentId\": 1, \"content\": \"Trong bài Unit 4 - Past simple, An rất tích cực.\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(AN), null, null, "ghi chú");

        verify(aiClient, never()).chatWithFinishReason(anyString(), org.mockito.ArgumentMatchers.contains("lessonContent"), anyString(), anyDouble());
        assertThat(result.rows().get(0).warnings()).extracting(CommentAiDraftResult.Warning::type).contains("LESSON_TITLE");
    }

    // ---- Nhắc chuỗi cảnh báo Thái độ (4.1) ----

    @Test
    void generateDraft_UC74_warnsWhenAttitudeWouldReachEscalationStreak() {
        CommentAiDraftService.RosterStudent anWithStreak = new CommentAiDraftService.RosterStudent(1L, "Nguyễn Văn An", List.of(), 2);
        stubAi(CommentAiDraftService.EXTRACT_PROMPT, """
                {"classAttitude": null, "classPoints": ["cả lớp ổn"],
                 "individuals": [{"studentId": 1, "attitude": "WEAK", "points": ["không làm bài"]}], "unmatched": []}""");
        stubAi(CommentAiDraftService.WRITE_PROMPT, "{\"comments\": [{\"studentId\": 1, \"content\": \"An cần tập trung hơn.\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(anWithStreak), null, null, "ghi chú");

        // AI không tự đổi mức Thái độ — chỉ nhắc giáo viên.
        assertThat(result.rows().get(0).attitude()).isEqualTo("WEAK");
        assertThat(result.rows().get(0).warnings()).extracting(CommentAiDraftResult.Warning::message)
                .anySatisfy(m -> assertThat(m).contains("2 buổi").contains("mốc cảnh báo 3 buổi"));
    }

    @Test
    void attitudeAlertWarning_UC74_onlyForWeakOrAverage() {
        assertThat(CommentAiDraftService.attitudeAlertWarning("GOOD", 5)).isNull();
        assertThat(CommentAiDraftService.attitudeAlertWarning(null, 0)).isNull();
        assertThat(CommentAiDraftService.attitudeAlertWarning("AVERAGE", 0).message()).contains("gửi cảnh báo thái độ cho phụ huynh");
    }

    // ---- Điểm BTVN buổi trước (bổ sung 2026-09-29) ----

    @Test
    void generateDraft_UC74_homeworkNoteSentToAiInWordsWithoutNumbers() {
        CommentAiDraftService.RosterStudent an = new CommentAiDraftService.RosterStudent(1L, "Nguyễn Văn An", List.of(), 0,
                "BTVN buổi trước theo kỹ năng: nghe — cần cố gắng.");
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": null, \"classPoints\": [\"tích cực\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT, "{\"comments\": [{\"studentId\": 1, \"content\": \"An học tích cực.\"}]}");

        service.generateDraft(context(an), null, null, "ghi chú");

        verify(aiClient).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT),
                org.mockito.ArgumentMatchers.contains("nghe — cần cố gắng"), anyString(), anyDouble());
    }

    @Test
    void loadContext_UC74_buildsHomeworkNoteFromTableScoresAndAutoProgress() {
        vn.com.pps.education.domain.Student an = student(1L, "Nguyễn Văn An");
        vn.com.pps.education.domain.Student binh = student(2L, "Trần Thị Bình");
        List<vn.com.pps.education.domain.ClassEnrollment> enrollments = List.of(enrollment(an), enrollment(binh));
        when(classEnrollmentRepository.findBySchoolClassIdAndStatus(5L, vn.com.pps.education.domain.ClassEnrollment.Status.ACTIVE)).thenReturn(enrollments);
        when(studentCommentService.previewAutoProgress(SESSION_ID, ACTOR_ID)).thenReturn(List.of(
                new vn.com.pps.education.dto.AutoProgressPreviewResponse(1L, "Chưa làm bài", null, null, null),
                new vn.com.pps.education.dto.AutoProgressPreviewResponse(2L, "65%", null, null, null)));

        CommentAiDraftService.DraftContext context = service.loadContext(SESSION_ID, ACTOR_ID,
                List.of(new vn.com.pps.education.dto.HomeworkScoreInput(1L, "90%", null, null, null)));

        CommentAiDraftService.RosterStudent anRow = context.rosterById().get(1L);
        CommentAiDraftService.RosterStudent binhRow = context.rosterById().get(2L);
        // Buổi GV Việt Nam: kênh chính = ngữ pháp; 2 nguồn cùng kỹ năng cho 2 mức khác nhau thì ghi rõ nguồn.
        assertThat(anRow.homeworkNote()).isEqualTo(
                "BTVN buổi trước theo kỹ năng: ngữ pháp — làm tốt (bài trên giấy), chưa hoàn thành (bài online).");
        // 65% = "làm được" — không nổi bật nên không nhắc.
        assertThat(binhRow.homeworkNote()).isNull();
    }

    @Test
    void generateDraft_UC74_homeworkDetailsSentToAiWhenPresent() {
        CommentAiDraftService.RosterStudent thuy = new CommentAiDraftService.RosterStudent(1L, "Nguyễn Thanh Thủy", List.of(), 0,
                null, List.of("kỹ năng nghe đi xuống qua các buổi gần đây"));
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": null, \"classPoints\": [\"tích cực\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT, "{\"comments\": [{\"studentId\": 1, \"content\": \"Thủy học tích cực.\"}]}");

        service.generateDraft(context(thuy), null, null, "ghi chú");

        verify(aiClient).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT),
                org.mockito.ArgumentMatchers.contains("\"homeworkDetails\":[\"kỹ năng nghe đi xuống qua các buổi gần đây\"]"), anyString(), anyDouble());
    }

    @Test
    void generateDraft_UC74_signalsSentToAiAndStudentInfoMentionIsFlaggedForVerification() {
        StudentSignalService.Signals signals = new StudentSignalService.Signals(
                List.of("hôm nay đến lớp muộn (nhắc nhẹ đến lớp đúng giờ)"),
                List.of("học sinh nhỏ tuổi: câu chữ đơn giản, ấm áp, khích lệ nhiều hơn"),
                List.of("mới vào lớp gần đây (theo dữ liệu hệ thống)"),
                new StudentSignalService.OtherTeacherComment(SESSION_DATE.minusDays(1), "Thủy mạnh dạn nói tiếng Anh."));
        CommentAiDraftService.RosterStudent thuy = new CommentAiDraftService.RosterStudent(1L, "Nguyễn Thanh Thủy", List.of(), 0,
                null, List.of(), signals);
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": null, \"classPoints\": [\"tích cực\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT,
                "{\"comments\": [{\"studentId\": 1, \"content\": \"Thủy mới vào lớp nhưng học rất tích cực. Con chú ý đến lớp đúng giờ nhé.\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(thuy), null, null, "ghi chú");

        verify(aiClient).chatWithFinishReason(eq(CommentAiDraftService.WRITE_PROMPT),
                org.mockito.ArgumentMatchers.argThat(payload -> payload.contains("\"attendance\":[\"hôm nay đến lớp muộn")
                        && payload.contains("\"toneHints\":[\"học sinh nhỏ tuổi")
                        && payload.contains("\"studentInfo\":[\"mới vào lớp gần đây")
                        && payload.contains("\"otherTeacherComment\":\"Thủy mạnh dạn nói tiếng Anh.\"")), anyString(), anyDouble());
        assertThat(result.rows().get(0).warnings()).extracting(CommentAiDraftResult.Warning::type).contains("STUDENT_INFO_CHECK");
    }

    @Test
    void generateDraft_UC74_commentWithoutStudentInfoIsNotFlagged() {
        stubAi(CommentAiDraftService.EXTRACT_PROMPT,
                "{\"classAttitude\": null, \"classPoints\": [\"tích cực\"], \"individuals\": [], \"unmatched\": []}");
        stubAi(CommentAiDraftService.WRITE_PROMPT, "{\"comments\": [{\"studentId\": 1, \"content\": \"An học rất tích cực.\"}]}");

        CommentAiDraftResult result = service.generateDraft(context(AN), null, null, "ghi chú");

        assertThat(result.rows().get(0).warnings()).extracting(CommentAiDraftResult.Warning::type).doesNotContain("STUDENT_INFO_CHECK");
    }

    @Test
    void loadContext_UC74_otherTeacherCommentJoinsPreviousCommentsForDuplicateCheck() {
        vn.com.pps.education.domain.Student an = student(1L, "Nguyễn Văn An");
        List<vn.com.pps.education.domain.ClassEnrollment> enrollments = List.of(enrollment(an));
        when(classEnrollmentRepository.findBySchoolClassIdAndStatus(5L, vn.com.pps.education.domain.ClassEnrollment.Status.ACTIVE)).thenReturn(enrollments);
        StudentSignalService.Signals signals = new StudentSignalService.Signals(List.of(), List.of(), List.of(),
                new StudentSignalService.OtherTeacherComment(SESSION_DATE, "An phản xạ nhanh với câu hỏi."));
        when(studentSignalService.describe(org.mockito.ArgumentMatchers.eq(session), org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.Map.of(1L, signals));

        CommentAiDraftService.DraftContext context = service.loadContext(SESSION_ID, ACTOR_ID, null);

        assertThat(context.rosterById().get(1L).previousComments())
                .containsExactly(new CommentAiDraftService.PreviousComment(SESSION_DATE, "An phản xạ nhanh với câu hỏi."));
        assertThat(context.rosterById().get(1L).signals()).isEqualTo(signals);
    }

    @Test
    void loadContext_UC74_passesTableScoresToHomeworkInsight() {
        vn.com.pps.education.domain.Student an = student(1L, "Nguyễn Văn An");
        List<vn.com.pps.education.domain.ClassEnrollment> enrollments = List.of(enrollment(an));
        when(classEnrollmentRepository.findBySchoolClassIdAndStatus(5L, vn.com.pps.education.domain.ClassEnrollment.Status.ACTIVE)).thenReturn(enrollments);
        vn.com.pps.education.dto.HomeworkScoreInput input = new vn.com.pps.education.dto.HomeworkScoreInput(1L, "40%", null, null, null);
        when(homeworkInsightService.describe(session, List.of(1L), java.util.Map.of(1L, input)))
                .thenReturn(java.util.Map.of(1L, List.of("chăm làm lại bài để cải thiện điểm")));

        CommentAiDraftService.DraftContext context = service.loadContext(SESSION_ID, ACTOR_ID, List.of(input));

        assertThat(context.rosterById().get(1L).homeworkDetails()).containsExactly("chăm làm lại bài để cải thiện điểm");
    }

    @Test
    void loadContext_UC74_foreignSessionHomeworkNamedListeningAndReadingWritingBySkill() {
        when(session.getTeacherType()).thenReturn(ClassSession.TeacherType.FOREIGN);
        vn.com.pps.education.domain.Student thuy = student(1L, "Nguyễn Thanh Thủy");
        List<vn.com.pps.education.domain.ClassEnrollment> enrollments = List.of(enrollment(thuy));
        when(classEnrollmentRepository.findBySchoolClassIdAndStatus(5L, vn.com.pps.education.domain.ClassEnrollment.Status.ACTIVE))
                .thenReturn(enrollments);
        when(studentCommentService.previewAutoProgress(SESSION_ID, ACTOR_ID)).thenReturn(List.of(
                new vn.com.pps.education.dto.AutoProgressPreviewResponse(1L, "40%", "90%", null, null)));

        CommentAiDraftService.DraftContext context = service.loadContext(SESSION_ID, ACTOR_ID, null);

        assertThat(context.rosterById().get(1L).homeworkNote())
                .isEqualTo("BTVN buổi trước theo kỹ năng: nghe — cần cố gắng; phản xạ nói — làm tốt.");
    }
}
