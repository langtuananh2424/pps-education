package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.com.pps.education.dto.CommentAiInstructionResult;
import vn.com.pps.education.dto.CommentAiReviewRequest;
import vn.com.pps.education.dto.CommentAiReviewResult;
import vn.com.pps.education.dto.CommentAiSuggestionResult;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
import vn.com.pps.education.exception.CommentAiDraftFailedException;
import vn.com.pps.education.exception.CommentAiDraftRejectedException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.StudentCommentRepository;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-75: Trợ lý AI soát nhận xét chờ duyệt — mỗi Alternate Flow 1 test (xem docs/uc/phan-he-06-hoc-thuat.md).
 * Test thuần Service logic: mock repository + NineRouterAiClient, không chạm DB/mạng.
 */
class CommentAiReviewServiceTest {

    private static final long ACTOR_ID = 7L;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 29);

    private final StudentCommentService studentCommentService = mock(StudentCommentService.class);
    private final NineRouterAiClient aiClient = mock(NineRouterAiClient.class);
    private final PromptTemplateLoader promptTemplateLoader = mock(PromptTemplateLoader.class);
    private final AiJobRegistry jobRegistry = mock(AiJobRegistry.class);

    private final CommentAiReviewService service = new CommentAiReviewService(studentCommentService,
            mock(ClassEnrollmentRepository.class), mock(StudentCommentRepository.class),
            new CommentAiJsonCaller(aiClient, promptTemplateLoader, new ObjectMapper()), jobRegistry, aiClient,
            "comment-pps", 3, 120, 0.5, 1024);

    @BeforeEach
    void setUp() {
        when(promptTemplateLoader.load(anyString(), anyMap())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CommentAiReviewService.ReviewItem item(long id, String name, String attitude, String content,
                                                   List<CommentAiDraftService.PreviousComment> previous) {
        return new CommentAiReviewService.ReviewItem(id, 100L, name, attitude, content, DATE, "Unit 4",
                List.of("Nguyễn Văn An", "Trần Thị Bình", "Lê Minh Chi").stream().filter(n -> !n.equals(name)).toList(), previous);
    }

    private void stubAi(String prompt, String response) {
        when(aiClient.chatWithFinishReason(eq(prompt), anyString(), anyString()))
                .thenReturn(new NineRouterAiClient.ChatResult(response, "stop", null));
    }

    private List<String> types(CommentAiReviewResult result, long commentId) {
        return result.reviews().stream().filter(r -> r.commentId() == commentId).findFirst().orElseThrow()
                .issues().stream().map(CommentAiReviewResult.Issue::type).toList();
    }

    // ---- Main Flow ----

    @Test
    void review_UC75_MainFlow_combinesRuleAndAiIssuesAndLeavesCleanRowsEmpty() {
        stubAi(CommentAiReviewService.REVIEW_PROMPT, """
                {"reviews": [{"commentId": 2, "issues": [{"type": "attitude_mismatch", "message": "Thái độ Tốt nhưng nội dung chê mất tập trung."}]}]}""");
        List<CommentAiReviewService.ReviewItem> items = List.of(
                item(1, "Nguyễn Văn An", "GOOD", "An tập trung nghe giảng và hăng hái phát biểu.", List.of()),
                item(2, "Trần Thị Bình", "GOOD", "Bình còn mất tập trung, hay quay ngang.", List.of()),
                item(3, "Lê Minh Chi", "FAIR", "Chi làm đúng 8 câu, cần chủ động hơn.", List.of()));

        CommentAiReviewResult result = service.review(items);

        assertThat(result.checkedCount()).isEqualTo(3);
        assertThat(result.flaggedCount()).isEqualTo(2);
        assertThat(result.aiCheckComplete()).isTrue();
        assertThat(types(result, 1)).isEmpty();
        assertThat(types(result, 2)).containsExactly("ATTITUDE_MISMATCH");
        assertThat(types(result, 3)).containsExactly("CONTAINS_DIGITS");
        assertThat(result.message()).contains("2 dòng có cảnh báo");
    }

    @Test
    void ruleIssues_UC75_flagsOtherStudentFullNameAndSimilarity() {
        String anText = "Hôm nay An tập trung rất tốt trong giờ học, con hăng hái phát biểu.";
        CommentAiReviewService.ReviewItem an = item(1, "Nguyễn Văn An", "GOOD", anText,
                List.of(new CommentAiDraftService.PreviousComment(DATE.minusDays(7), anText)));
        CommentAiReviewService.ReviewItem binh = item(2, "Trần Thị Bình", "GOOD",
                "Hôm nay Bình tập trung rất tốt trong giờ học, con hăng hái phát biểu.", List.of());
        CommentAiReviewService.ReviewItem chi = item(3, "Lê Minh Chi", "FAIR",
                "Chi còn nói chuyện riêng với bạn Nguyễn Văn An trong giờ.", List.of());
        List<CommentAiReviewService.ReviewItem> all = List.of(an, binh, chi);

        assertThat(service.ruleIssues(an, all)).extracting(CommentAiReviewResult.Issue::type)
                .containsExactly("SIMILAR_IN_SESSION", "SIMILAR_TO_PREVIOUS");
        assertThat(service.ruleIssues(binh, all)).extracting(CommentAiReviewResult.Issue::type).containsExactly("SIMILAR_IN_SESSION");
        List<CommentAiReviewResult.Issue> chiIssues = service.ruleIssues(chi, all);
        assertThat(chiIssues).extracting(CommentAiReviewResult.Issue::type).containsExactly("OTHER_STUDENT_NAME");
        assertThat(chiIssues).allSatisfy(i -> assertThat(i.source()).isEqualTo(CommentAiReviewService.SOURCE_RULE));
    }

    @Test
    void ruleIssues_UC75_flagsEmptyAndTooLong() {
        CommentAiReviewService.ReviewItem empty = item(1, "Nguyễn Văn An", null, "  ", List.of());
        CommentAiReviewService.ReviewItem longOne = item(2, "Trần Thị Bình", null, "a ".repeat(300), List.of());

        assertThat(service.ruleIssues(empty, List.of(empty))).extracting(CommentAiReviewResult.Issue::type).containsExactly("EMPTY");
        assertThat(service.ruleIssues(longOne, List.of(longOne))).extracting(CommentAiReviewResult.Issue::type).contains("TOO_LONG");
    }

    @Test
    void review_UC75_aiOtherStudentNotDuplicatedWhenRuleAlreadyFoundIt() {
        stubAi(CommentAiReviewService.REVIEW_PROMPT, """
                {"reviews": [{"commentId": 2, "issues": [{"type": "OTHER_STUDENT", "message": "Nhắc bạn An."}, {"type": "WEIRD", "message": "Khác."}]}]}""");
        CommentAiReviewService.ReviewItem binh = item(2, "Trần Thị Bình", "GOOD", "Bình ngồi cạnh Nguyễn Văn An nói chuyện.", List.of());

        CommentAiReviewResult result = service.review(List.of(binh));

        assertThat(types(result, 2)).containsExactly("OTHER_STUDENT_NAME", "OTHER");
    }

    // ---- Alternate Flows ----

    @Test
    void startReview_UC75_A1_rejectsWithoutApprovePermissionOrSite() {
        when(studentCommentService.requirePendingCommentsForAiReview(List.of(1L), ACTOR_ID))
                .thenThrow(new NotSiteManagerForSiteException("Bạn không được gán phụ trách điểm trường này."));

        assertThatThrownBy(() -> service.startReview(new CommentAiReviewRequest(List.of(1L)), ACTOR_ID))
                .isInstanceOf(NotSiteManagerForSiteException.class);
        verify(jobRegistry, never()).submit(any(), any());
    }

    @Test
    void startReview_UC75_A2_rejectsCommentNoLongerPending() {
        when(studentCommentService.requirePendingCommentsForAiReview(List.of(1L), ACTOR_ID))
                .thenThrow(new ApprovalAlreadyDecidedException("error.approvalAlreadyDecided.comment", new Object[]{"APPROVED"}, "đã duyệt"));

        assertThatThrownBy(() -> service.startReview(new CommentAiReviewRequest(List.of(1L)), ACTOR_ID))
                .isInstanceOf(ApprovalAlreadyDecidedException.class);
        verify(jobRegistry, never()).submit(any(), any());
    }

    @Test
    void review_UC75_A4_aiFailureKeepsRuleResultsAndMarksIncomplete() {
        when(aiClient.chatWithFinishReason(eq(CommentAiReviewService.REVIEW_PROMPT), anyString(), anyString())).thenReturn(null);
        CommentAiReviewService.ReviewItem chi = item(3, "Lê Minh Chi", "FAIR", "Chi làm đúng 8 câu.", List.of());

        CommentAiReviewResult result = service.review(List.of(chi));

        assertThat(result.aiCheckComplete()).isFalse();
        assertThat(types(result, 3)).containsExactly("CONTAINS_DIGITS");
        assertThat(result.message()).contains("chỉ có kết quả kiểm tra tự động");
    }

    // ---- Đề xuất bản sửa ----

    @Test
    void suggest_UC75_MainFlow_returnsSuggestionWithoutSavingAndKeepsPronoun() {
        stubAi(CommentAiReviewService.SUGGEST_PROMPT,
                "{\"content\": \"Cô thấy Bình còn chưa tập trung, mong con chú ý hơn.\", \"explanation\": \"Bỏ tên bạn khác.\"}");
        CommentAiReviewService.ReviewItem binh = item(2, "Trần Thị Bình", "AVERAGE",
                "Cô thấy Bình nói chuyện với Nguyễn Văn An suốt giờ.", List.of());

        CommentAiSuggestionResult result = service.suggest(binh, List.of("Nhắc họ tên bạn khác"));

        assertThat(result.suggestedContent()).isEqualTo("Cô thấy Bình còn chưa tập trung, mong con chú ý hơn.");
        assertThat(result.originalContent()).isEqualTo("Cô thấy Bình nói chuyện với Nguyễn Văn An suốt giờ.");
        assertThat(result.explanation()).isEqualTo("Bỏ tên bạn khác.");
        assertThat(result.warnings()).isEmpty();
        verify(aiClient).chatWithFinishReason(eq(CommentAiReviewService.SUGGEST_PROMPT), contains("\"teacherPronoun\":\"cô\""), anyString());
        verify(studentCommentService, never()).updatePendingCommentContent(any(), any(), any());
    }

    @Test
    void suggest_UC75_A4_failsWhenAiReturnsNothing() {
        when(aiClient.chatWithFinishReason(eq(CommentAiReviewService.SUGGEST_PROMPT), anyString(), anyString())).thenReturn(null);
        CommentAiReviewService.ReviewItem binh = item(2, "Trần Thị Bình", null, "Bình học ổn.", List.of());

        assertThatThrownBy(() -> service.suggest(binh, List.of("x"))).isInstanceOf(CommentAiDraftFailedException.class);
    }

    @Test
    void suggest_UC75_warnsWhenSuggestionHasDigitsOrIsUnchanged() {
        stubAi(CommentAiReviewService.SUGGEST_PROMPT, "{\"content\": \"Bình làm đúng 9 câu.\", \"explanation\": \"\"}");
        CommentAiReviewService.ReviewItem binh = item(2, "Trần Thị Bình", null, "Bình làm đúng 9 câu.", List.of());

        CommentAiSuggestionResult result = service.suggest(binh, List.of("x"));

        assertThat(result.warnings()).hasSize(2);
    }

    // ---- Yêu cầu sửa bằng audio/chữ (bước 9) ----

    @Test
    void instruct_UC75_MainFlow_audioInstructionProposesChangesOnlyForTargetedComments() {
        when(aiClient.transcribe(any(byte[].class), eq("audio/webm"), eq(null), contains("Trần Thị Bình"), eq("vi")))
                .thenReturn("bỏ cụm nói chuyện với An trong nhận xét của Bình");
        stubAi(CommentAiReviewService.INSTRUCTION_PROMPT, """
                {"assistantMessage": "Đã bỏ tên bạn An khỏi nhận xét của Bình.",
                 "changes": [{"commentId": 2, "content": "Bình còn nói chuyện riêng trong giờ."},
                             {"commentId": 1, "content": "An tập trung nghe giảng."},
                             {"commentId": 999, "content": "không thuộc lô"}]}""");
        List<CommentAiReviewService.ReviewItem> items = List.of(
                item(1, "Nguyễn Văn An", "GOOD", "An tập trung nghe giảng.", List.of()),
                item(2, "Trần Thị Bình", "AVERAGE", "Bình nói chuyện với Nguyễn Văn An suốt giờ.", List.of()));

        CommentAiInstructionResult result = service.instruct(items, new byte[]{1}, "audio/webm", null);

        assertThat(result.transcript()).contains("nhận xét của Bình");
        assertThat(result.assistantMessage()).isEqualTo("Đã bỏ tên bạn An khỏi nhận xét của Bình.");
        // Bản sửa trùng nguyên văn (An) và commentId ngoài lô bị bỏ; không ghi DB.
        assertThat(result.changes()).extracting(CommentAiInstructionResult.Change::commentId).containsExactly(2L);
        assertThat(result.changes().get(0).originalContent()).isEqualTo("Bình nói chuyện với Nguyễn Văn An suốt giờ.");
        verify(studentCommentService, never()).updatePendingCommentContent(any(), any(), any());
    }

    @Test
    void startInstruction_UC75_A7_rejectsMissingOrInvalidInput() {
        MockMultipartFile big = new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[2048]);
        MockMultipartFile notAudio = new MockMultipartFile("audio", "a.pdf", "application/pdf", new byte[10]);

        assertThatThrownBy(() -> service.startInstruction(List.of(1L), null, " ", ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class);
        assertThatThrownBy(() -> service.startInstruction(List.of(), null, "sửa giúp", ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class);
        assertThatThrownBy(() -> service.startInstruction(List.of(1L), big, null, ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class).hasMessageContaining("quá lớn");
        assertThatThrownBy(() -> service.startInstruction(List.of(1L), notAudio, null, ACTOR_ID))
                .isInstanceOf(CommentAiDraftRejectedException.class).hasMessageContaining("không phải audio");
        verify(jobRegistry, never()).submit(any(), any());
    }

    @Test
    void instruct_UC75_A4_failsWhenTranscriptionOrAiFails() {
        CommentAiReviewService.ReviewItem binh = item(2, "Trần Thị Bình", null, "Bình học ổn.", List.of());
        when(aiClient.transcribe(any(byte[].class), any(), any(), any(), any())).thenReturn(null);
        when(aiClient.chatWithFinishReason(eq(CommentAiReviewService.INSTRUCTION_PROMPT), anyString(), anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.instruct(List.of(binh), new byte[]{1}, "audio/webm", null))
                .isInstanceOf(CommentAiDraftFailedException.class).hasMessageContaining("Không chuyển được audio");
        assertThatThrownBy(() -> service.instruct(List.of(binh), null, null, "viết ngắn lại"))
                .isInstanceOf(CommentAiDraftFailedException.class);
    }

    @Test
    void ruleIssues_UC75_flagsLessonTitleInComment() {
        CommentAiReviewService.ReviewItem dat = new CommentAiReviewService.ReviewItem(4L, 100L, "Trần Tiến Đạt", "AVERAGE",
                "Trong buổi học Unit 1: Hello Friend hôm nay, Đạt cần chú ý hơn.", DATE, "Unit 1: Hello Friend", List.of(), List.of());

        assertThat(service.ruleIssues(dat, List.of(dat))).extracting(CommentAiReviewResult.Issue::type).contains("LESSON_TITLE");
    }

    @Test
    void review_UC75_lessonTitleIsNotSentToAi() {
        stubAi(CommentAiReviewService.REVIEW_PROMPT, "{\"reviews\": []}");
        CommentAiReviewService.ReviewItem an = item(1, "Nguyễn Văn An", "GOOD", "An tập trung nghe giảng.", List.of());

        service.review(List.of(an));

        verify(aiClient, never()).chatWithFinishReason(anyString(), contains("lessonContent"), anyString());
    }
}
