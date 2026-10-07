package vn.com.pps.education.lms.service;

import vn.com.pps.education.media.service.MediaStorageService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.com.pps.education.common.AiTokenUsage;
import vn.com.pps.education.common.ReflexV2Task;
import vn.com.pps.education.lms.domain.AiGradingTokenUsage;
import vn.com.pps.education.academic.domain.ClassEnrollment;
import vn.com.pps.education.academic.domain.Curriculum;
import vn.com.pps.education.lms.domain.ReflexQuestionProgress;
import vn.com.pps.education.lms.domain.ReflexQuestionProgressHistory;
import vn.com.pps.education.lms.domain.ReviewVideo;
import vn.com.pps.education.lms.domain.ReviewVideoAssignment;
import vn.com.pps.education.lms.domain.ReviewVideoQuestion;
import vn.com.pps.education.lms.domain.ReviewVideoSet;
import vn.com.pps.education.academic.domain.SchoolClass;
import vn.com.pps.education.student.domain.Student;
import vn.com.pps.education.exception.ReflexAudioRejectedException;
import vn.com.pps.education.exception.RetakeNotAllowedException;
import vn.com.pps.education.lms.dto.ReflexQuestionProgressResponse;
import vn.com.pps.education.academic.repository.ClassEnrollmentRepository;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressHistoryRepository;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.lms.repository.ReviewVideoAssignmentRepository;
import vn.com.pps.education.lms.repository.ReviewVideoQuestionRepository;
import vn.com.pps.education.student.repository.StudentRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * UC-23b V2 — Bước 2 (nộp ghi âm). Test thuần Service logic, mock repository/AI (xem .claude/rules/testing.md).
 */
class ReflexSequentialGradingServiceTest {

    private static final Long QUESTION_ID = 10L;
    private static final Long ASSIGNMENT_ID = 20L;
    private static final Long STUDENT_ID = 30L;
    private static final Long ACTOR_USER_ID = 40L;
    private static final Long CLASS_ID = 50L;
    private static final Long PROGRESS_ID = 99L;
    private static final String AUDIO_URL = "https://r2.example/reflex/audio.webm";

    private final ReviewVideoQuestionRepository questionRepository = mock(ReviewVideoQuestionRepository.class);
    private final ReviewVideoAssignmentRepository assignmentRepository = mock(ReviewVideoAssignmentRepository.class);
    private final ReflexQuestionProgressRepository progressRepository = mock(ReflexQuestionProgressRepository.class);
    private final ReflexQuestionProgressHistoryRepository historyRepository = mock(ReflexQuestionProgressHistoryRepository.class);
    private final ClassEnrollmentRepository enrollmentRepository = mock(ClassEnrollmentRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final MediaStorageService mediaStorageService = mock(MediaStorageService.class);
    private final ReflexV2AiGradingService reflexV2GradingService = mock(ReflexV2AiGradingService.class);
    private final AiGradingTokenUsageRecorder tokenUsageRecorder = mock(AiGradingTokenUsageRecorder.class);
    private final ReflexWritingGrammarAiGradingService writingGradingService = mock(ReflexWritingGrammarAiGradingService.class);
    private final ReflexSpeakingContentAiGradingService speakingGradingService = mock(ReflexSpeakingContentAiGradingService.class);

    private final ReflexSequentialGradingService service = new ReflexSequentialGradingService(
            questionRepository, assignmentRepository, progressRepository, historyRepository, enrollmentRepository,
            studentRepository, mediaStorageService, writingGradingService,
            speakingGradingService, reflexV2GradingService, tokenUsageRecorder);

    private final AiTokenUsage transcriptionUsage =
            new AiTokenUsage("gemini-3.6-flash-medium", true, 5200, 0, 310, 900, 6100);

    /**
     * Alternate Flow "bản ghi bị từ chối" (nói khác bài viết / không đọc được): lượt phiên âm mù đã gọi AI
     * thật — phải ghi đúng 1 dòng chi phí TRANSCRIPTION (kèm học sinh/bài/câu hỏi) rồi vẫn ném 422 như cũ,
     * không lưu điểm/feedback (progressRepository.save không được gọi).
     *
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07 — từ nay lượt bị từ chối VẪN phải
     * tính vào giới hạn {@value ReflexSequentialGradingService#MAX_STEP_ATTEMPTS} lần (trước đó KHÔNG
     * tính — đã đổi vì phát hiện qua test thật: học sinh gửi bản ghi im lặng liên tục không bao giờ chạm
     * giới hạn). Việc này đi qua {@code incrementSpeakingAttemptCountInNewTransaction} (REQUIRES_NEW) —
     * xem Javadoc {@link vn.com.pps.education.lms.repository.ReflexQuestionProgressRepository}.
     */
    @Test
    void submitSpokenAnswer_UC23b_A_audioRejected_recordsTranscriptionUsageOnceAndStillRejectsButCountsAttempt() {
        Fixture f = v2SpeakingFixture();
        // Mô phỏng đúng thứ tự thật: Lượt A phiên âm trả về (đẩy chi phí ra sink) rồi mới bị từ chối.
        when(reflexV2GradingService.gradeSpeaking(any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            ReflexV2AiGradingService.SpeakingUsageSink sink = inv.getArgument(5);
            sink.record(AiGradingTokenUsage.Step.TRANSCRIPTION, transcriptionUsage);
            throw new ReflexAudioRejectedException(ReflexV2AiGradingService.MSG_SPOKE_DIFFERENT);
        });

        assertThatThrownBy(() -> service.submitSpokenAnswer(QUESTION_ID, ASSIGNMENT_ID, AUDIO_URL, true, ACTOR_USER_ID))
                .isInstanceOf(ReflexAudioRejectedException.class)
                .hasMessage(ReflexV2AiGradingService.MSG_SPOKE_DIFFERENT);

        verify(tokenUsageRecorder, times(1)).record(eq(AiGradingTokenUsage.Step.TRANSCRIPTION), eq("chatWithAudioJson"),
                isNull(), eq(transcriptionUsage), eq(f.student()), eq(f.assignment()), eq(f.question()));
        verifyNoMoreInteractions(tokenUsageRecorder);
        verify(progressRepository, never()).save(any());
        verify(progressRepository, times(1)).incrementSpeakingAttemptCountInNewTransaction(PROGRESS_ID);
    }

    /**
     * Alternate Flow — 3 lượt LIÊN TIẾP đều bị AI từ chối (im lặng/không đọc được) vẫn phải tính đủ vào
     * giới hạn {@value ReflexSequentialGradingService#MAX_STEP_ATTEMPTS} lần, không để học sinh né giới
     * hạn bằng cách gửi bản ghi rác — mỗi lượt đều phải gọi {@code incrementSpeakingAttemptCountInNewTransaction}
     * (REQUIRES_NEW), kể cả khi bị từ chối ngay sau đó.
     *
     * Lưu ý giới hạn của test thuần mock này: repository mock không mô phỏng ROLLBACK thật của DB (field
     * Java trên {@code progress} không tự bị "hoàn tác" như 1 giao dịch thật bị huỷ) — persist thật qua
     * REQUIRES_NEW không kiểm chứng được ở đây (cần Testcontainers, xem .claude/rules/testing.md). Set
     * tường minh {@code speakingAttemptCount=3} trước lần gọi thứ 4 để khẳng định RÕ RÀNG tiền đề đang
     * kiểm: "progress nạp lại từ DB với count=3 (dù 3 lượt trước đều bị từ chối) → cổng chặn phải kích
     * hoạt", tách biệt khỏi chi tiết triển khai REQUIRES_NEW.
     */
    @Test
    void submitSpokenAnswer_UC23b_A_audioRejectedThreeTimes_thenBlocksFurtherResubmission() {
        Fixture f = v2SpeakingFixture();
        when(reflexV2GradingService.gradeSpeaking(any(), any(), any(), any(), any(), any()))
                .thenThrow(new ReflexAudioRejectedException(ReflexV2AiGradingService.MSG_SPOKE_DIFFERENT));

        for (int i = 1; i <= 3; i++) {
            assertThatThrownBy(() -> service.submitSpokenAnswer(QUESTION_ID, ASSIGNMENT_ID, AUDIO_URL, true, ACTOR_USER_ID))
                    .isInstanceOf(ReflexAudioRejectedException.class);
        }
        verify(progressRepository, times(3)).incrementSpeakingAttemptCountInNewTransaction(PROGRESS_ID);
        verify(progressRepository, never()).save(any());

        f.progress().setSpeakingAttemptCount(3);

        assertThatThrownBy(() -> service.submitSpokenAnswer(QUESTION_ID, ASSIGNMENT_ID, AUDIO_URL, true, ACTOR_USER_ID))
                .isInstanceOf(RetakeNotAllowedException.class);
    }

    /**
     * Bước nói định tuyến theo rubricVersion ĐÃ LƯU của dòng: câu chấm viết bằng v2 trước khi lên v3 vẫn chấm nói
     * bằng v2 (thư mục rubrics-v2/, cấu hình v2); câu mới chấm bằng v3.
     */
    @Test
    void submitSpokenAnswer_UC23b_routesByStoredRubricVersion() {
        for (String version : List.of(ReflexV2Task.RUBRIC_V2, ReflexV2Task.RUBRIC_V3)) {
            clearInvocations(reflexV2GradingService);
            speakingFixture(version);
            doThrow(new ReflexAudioRejectedException(ReflexV2AiGradingService.MSG_SPOKE_DIFFERENT))
                    .when(reflexV2GradingService).gradeSpeaking(any(), any(), any(), any(), any(), any());

            assertThatThrownBy(() -> service.submitSpokenAnswer(QUESTION_ID, ASSIGNMENT_ID, AUDIO_URL, true, ACTOR_USER_ID))
                    .isInstanceOf(ReflexAudioRejectedException.class);

            ArgumentCaptor<ReflexV2Task> task = ArgumentCaptor.forClass(ReflexV2Task.class);
            verify(reflexV2GradingService).gradeSpeaking(task.capture(), any(), any(), any(), any(), any());
            assertThat(task.getValue().rubricVersion()).isEqualTo(version);
            assertThat(task.getValue().rubricDir()).isEqualTo("rubrics-" + version + "/");
        }
    }

    /**
     * V198 — lần ghi âm mà lượt chấm đánh dấu cần giáo viên soát Ngữ pháp (speaking_audit) phải để lại đúng cờ +
     * các đoạn bị tô đỏ trong dòng lịch sử, để trang thống kê chỉ ra cho giáo viên.
     */
    @Test
    void submitSpokenAnswer_UC23b_grammarReviewFlag_isCopiedIntoTheHistoryRow() {
        speakingFixture(ReflexV2Task.RUBRIC_V3);
        when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> audit = new HashMap<>();
        audit.put(ReflexV2AiGradingService.AUDIT_GRAMMAR_REVIEW_REQUIRED, true);
        audit.put(ReflexV2AiGradingService.AUDIT_GRAMMAR_REVIEW_QUOTES, List.of("often play", "really fun"));
        when(reflexV2GradingService.gradeSpeaking(any(), any(), any(), any(), any(), any())).thenReturn(
                new ReflexV2AiGradingService.SpeakingResult("marked", List.of(), 60, 65, "Em nói rõ ý.", "", List.of(), audit));

        service.submitSpokenAnswer(QUESTION_ID, ASSIGNMENT_ID, AUDIO_URL, true, ACTOR_USER_ID);

        ArgumentCaptor<ReflexQuestionProgressHistory> history = ArgumentCaptor.forClass(ReflexQuestionProgressHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().isGrammarReviewRequired()).isTrue();
        assertThat(history.getValue().getGrammarReviewQuotes()).containsExactly("often play", "really fun");
        // V199 — chế độ thu âm thực tế của lần ghi được lưu lại để so sánh hai chế độ
        assertThat(history.getValue().getRecordingFilter()).isTrue();
        assertThat(audit).containsEntry("recordingFilter", true);
    }

    /**
     * UC-23b V2 — giới hạn {@value ReflexSequentialGradingService#MAX_STEP_ATTEMPTS} lần nộp mỗi bước
     * (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07). Main Flow: đạt trước khi hết lượt
     * vẫn mở khoá ngay như cũ.
     */
    @Test
    void submitWrittenAnswer_UC23b_MainFlow_passingBeforeCapUnlocksSpeakingImmediately() {
        writingFixture();
        when(writingGradingService.grade(any(), any(), any())).thenReturn(
                new ReflexWritingGrammarAiGradingService.GradeResult(80, "marked", null, null, null));

        ReflexQuestionProgressResponse response = service.submitWrittenAnswer(QUESTION_ID, ASSIGNMENT_ID, "answer", ACTOR_USER_ID);

        assertThat(response.writingAttemptCount()).isEqualTo(1);
        assertThat(response.writingPassed()).isTrue();
        assertThat(response.writingExhausted()).isFalse();
        assertThat(response.writingUnlocked()).isTrue();
    }

    /**
     * Alternate Flow — hết {@value ReflexSequentialGradingService#MAX_STEP_ATTEMPTS} lần nộp bước VIẾT mà
     * vẫn chưa đạt ngưỡng %: tự mở khoá ghi âm (writingUnlocked=true) dùng điểm của lần nộp cuối, KHÔNG
     * coi là đạt thật (writingPassed vẫn false).
     */
    @Test
    void submitWrittenAnswer_UC23b_A_writingExhausted_unlocksSpeakingWithoutPassing() {
        writingFixture();
        when(writingGradingService.grade(any(), any(), any())).thenReturn(
                new ReflexWritingGrammarAiGradingService.GradeResult(40, "marked", null, null, null));

        service.submitWrittenAnswer(QUESTION_ID, ASSIGNMENT_ID, "answer 1", ACTOR_USER_ID);
        service.submitWrittenAnswer(QUESTION_ID, ASSIGNMENT_ID, "answer 2", ACTOR_USER_ID);
        ReflexQuestionProgressResponse third = service.submitWrittenAnswer(QUESTION_ID, ASSIGNMENT_ID, "answer 3", ACTOR_USER_ID);

        assertThat(third.writingAttemptCount()).isEqualTo(3);
        assertThat(third.writingPassed()).isFalse();
        assertThat(third.writingExhausted()).isTrue();
        assertThat(third.writingUnlocked()).isTrue();
        assertThat(third.writingScorePercent()).isEqualTo(40);
    }

    /** Alternate Flow — đã hết lượt bước VIẾT (xem test trên): nộp lần thứ 4 phải bị từ chối. */
    @Test
    void submitWrittenAnswer_UC23b_A_writingAlreadyExhausted_rejectsFurtherResubmission() {
        writingFixture();
        when(writingGradingService.grade(any(), any(), any())).thenReturn(
                new ReflexWritingGrammarAiGradingService.GradeResult(40, "marked", null, null, null));
        service.submitWrittenAnswer(QUESTION_ID, ASSIGNMENT_ID, "answer 1", ACTOR_USER_ID);
        service.submitWrittenAnswer(QUESTION_ID, ASSIGNMENT_ID, "answer 2", ACTOR_USER_ID);
        service.submitWrittenAnswer(QUESTION_ID, ASSIGNMENT_ID, "answer 3", ACTOR_USER_ID);

        assertThatThrownBy(() -> service.submitWrittenAnswer(QUESTION_ID, ASSIGNMENT_ID, "answer 4", ACTOR_USER_ID))
                .isInstanceOf(RetakeNotAllowedException.class);
    }

    /**
     * Alternate Flow — hết {@value ReflexSequentialGradingService#MAX_STEP_ATTEMPTS} lần nộp bước NÓI mà
     * vẫn chưa đạt ngưỡng %: câu hỏi coi là KHÔNG đạt (questionPassed=false) nhưng VẪN mở khoá câu tiếp
     * theo (questionFinalized=true) — không kẹt học sinh lại mãi ở 1 câu.
     */
    @Test
    void submitSpokenAnswer_UC23b_A_speakingExhausted_finalizesQuestionAsNotPassed() {
        legacySpeakingFixture();
        when(speakingGradingService.grade(any(), any(), any(), any())).thenReturn(
                new ReflexSpeakingContentAiGradingService.GradeResult("transcript", List.of(), 50, "feedback", null));

        service.submitSpokenAnswer(QUESTION_ID, ASSIGNMENT_ID, AUDIO_URL, true, ACTOR_USER_ID);
        service.submitSpokenAnswer(QUESTION_ID, ASSIGNMENT_ID, AUDIO_URL, true, ACTOR_USER_ID);
        ReflexQuestionProgressResponse third = service.submitSpokenAnswer(QUESTION_ID, ASSIGNMENT_ID, AUDIO_URL, true, ACTOR_USER_ID);

        assertThat(third.speakingAttemptCount()).isEqualTo(3);
        assertThat(third.speakingPassed()).isFalse();
        assertThat(third.speakingExhausted()).isTrue();
        assertThat(third.questionPassed()).isFalse();
        assertThat(third.questionFinalized()).isTrue();
    }

    /** Alternate Flow — đã hết lượt bước NÓI (xem test trên): nộp lần thứ 4 phải bị từ chối. */
    @Test
    void submitSpokenAnswer_UC23b_A_speakingAlreadyExhausted_rejectsFurtherResubmission() {
        legacySpeakingFixture();
        when(speakingGradingService.grade(any(), any(), any(), any())).thenReturn(
                new ReflexSpeakingContentAiGradingService.GradeResult("transcript", List.of(), 50, "feedback", null));
        service.submitSpokenAnswer(QUESTION_ID, ASSIGNMENT_ID, AUDIO_URL, true, ACTOR_USER_ID);
        service.submitSpokenAnswer(QUESTION_ID, ASSIGNMENT_ID, AUDIO_URL, true, ACTOR_USER_ID);
        service.submitSpokenAnswer(QUESTION_ID, ASSIGNMENT_ID, AUDIO_URL, true, ACTOR_USER_ID);

        assertThatThrownBy(() -> service.submitSpokenAnswer(QUESTION_ID, ASSIGNMENT_ID, AUDIO_URL, true, ACTOR_USER_ID))
                .isInstanceOf(RetakeNotAllowedException.class);
    }

    private record Fixture(ReviewVideoQuestion question, ReviewVideoAssignment assignment, Student student,
                            ReflexQuestionProgress progress) {
    }

    /** Câu hỏi REFLEX mới toanh, chưa nộp bước viết lần nào — curriculum không quan trọng (reflexV2Enabled mặc định tắt ở test, luôn đi luồng cũ). */
    private Fixture writingFixture() {
        Curriculum curriculum = mock(Curriculum.class);

        ReviewVideoSet set = mock(ReviewVideoSet.class);
        when(set.getId()).thenReturn(1L);
        when(set.getStatus()).thenReturn(ReviewVideoSet.Status.PUBLISHED);
        when(set.getVideoType()).thenReturn(ReviewVideoSet.VideoType.REFLEX);
        when(set.getCurriculum()).thenReturn(curriculum);

        ReviewVideo video = mock(ReviewVideo.class);
        when(video.getReviewVideoSet()).thenReturn(set);
        when(video.getCompletionThresholdPercent()).thenReturn(70);

        ReviewVideoQuestion question = mock(ReviewVideoQuestion.class);
        when(question.getId()).thenReturn(QUESTION_ID);
        when(question.getReviewVideo()).thenReturn(video);
        when(question.getPrompt()).thenReturn("What is your favourite sport?");
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));

        Student student = mock(Student.class);
        when(student.getId()).thenReturn(STUDENT_ID);
        when(studentRepository.findByUserId(ACTOR_USER_ID)).thenReturn(Optional.of(student));

        SchoolClass schoolClass = mock(SchoolClass.class);
        when(schoolClass.getId()).thenReturn(CLASS_ID);
        ReviewVideoAssignment assignment = mock(ReviewVideoAssignment.class);
        when(assignment.getId()).thenReturn(ASSIGNMENT_ID);
        when(assignment.getReviewVideoSet()).thenReturn(set);
        when(assignment.getStatus()).thenReturn(ReviewVideoAssignment.Status.ACTIVE);
        when(assignment.getSchoolClass()).thenReturn(schoolClass);
        when(assignment.getTargetStudentIds()).thenReturn(null);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(enrollmentRepository.findBySchoolClassIdAndStudentIdAndStatus(CLASS_ID, STUDENT_ID, ClassEnrollment.Status.ACTIVE))
                .thenReturn(Optional.of(mock(ClassEnrollment.class)));

        ReflexQuestionProgress progress = new ReflexQuestionProgress();
        progress.setId(PROGRESS_ID);
        progress.setReviewVideoQuestion(question);
        progress.setStudent(student);
        progress.setReviewVideoAssignment(assignment);
        when(progressRepository.findByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentId(QUESTION_ID, STUDENT_ID, ASSIGNMENT_ID))
                .thenReturn(Optional.of(progress));
        when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        return new Fixture(question, assignment, student, progress);
    }

    /**
     * Câu hỏi REFLEX đã đạt Bước 1 (viết) từ trước, CHƯA bắt đầu Bước 2 (nói) — curriculum=null để đi
     * đúng luồng chấm nói CŨ ({@link ReflexSpeakingContentAiGradingService}), tránh phải dựng
     * {@link ReflexV2Task} đầy đủ (không phải mục tiêu của các test giới hạn lượt nộp này).
     */
    private Fixture legacySpeakingFixture() {
        ReviewVideoSet set = mock(ReviewVideoSet.class);
        when(set.getId()).thenReturn(1L);
        when(set.getStatus()).thenReturn(ReviewVideoSet.Status.PUBLISHED);
        when(set.getVideoType()).thenReturn(ReviewVideoSet.VideoType.REFLEX);
        when(set.getCurriculum()).thenReturn(null);

        ReviewVideo video = mock(ReviewVideo.class);
        when(video.getReviewVideoSet()).thenReturn(set);
        when(video.getCompletionThresholdPercent()).thenReturn(70);

        ReviewVideoQuestion question = mock(ReviewVideoQuestion.class);
        when(question.getId()).thenReturn(QUESTION_ID);
        when(question.getReviewVideo()).thenReturn(video);
        when(question.getMaxRecordingSeconds()).thenReturn(20);
        when(question.getPrompt()).thenReturn("What is your favourite sport?");
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));

        Student student = mock(Student.class);
        when(student.getId()).thenReturn(STUDENT_ID);
        when(studentRepository.findByUserId(ACTOR_USER_ID)).thenReturn(Optional.of(student));

        SchoolClass schoolClass = mock(SchoolClass.class);
        when(schoolClass.getId()).thenReturn(CLASS_ID);
        ReviewVideoAssignment assignment = mock(ReviewVideoAssignment.class);
        when(assignment.getId()).thenReturn(ASSIGNMENT_ID);
        when(assignment.getReviewVideoSet()).thenReturn(set);
        when(assignment.getStatus()).thenReturn(ReviewVideoAssignment.Status.ACTIVE);
        when(assignment.getSchoolClass()).thenReturn(schoolClass);
        when(assignment.getTargetStudentIds()).thenReturn(null);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(enrollmentRepository.findBySchoolClassIdAndStudentIdAndStatus(CLASS_ID, STUDENT_ID, ClassEnrollment.Status.ACTIVE))
                .thenReturn(Optional.of(mock(ClassEnrollment.class)));

        ReflexQuestionProgress progress = new ReflexQuestionProgress();
        progress.setId(PROGRESS_ID);
        progress.setReviewVideoQuestion(question);
        progress.setStudent(student);
        progress.setReviewVideoAssignment(assignment);
        progress.setWritingScore(BigDecimal.valueOf(80));
        progress.setAnswerText("My favourite sport is football.");
        when(progressRepository.findByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentId(QUESTION_ID, STUDENT_ID, ASSIGNMENT_ID))
                .thenReturn(Optional.of(progress));
        when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(mediaStorageService.downloadWithContentType(AUDIO_URL))
                .thenReturn(new MediaStorageService.DownloadedFile(new byte[]{1, 2, 3}, "audio/webm"));

        return new Fixture(question, assignment, student, progress);
    }

    private Fixture v2SpeakingFixture() {
        return speakingFixture(ReflexV2Task.RUBRIC_V2);
    }

    /** Câu hỏi REFLEX Khối 6, dòng tiến trình đã đạt Bước 1 bằng bộ rubric {@code rubricVersion}. */
    private Fixture speakingFixture(String rubricVersion) {
        Curriculum curriculum = mock(Curriculum.class);
        when(curriculum.getGradeLevel()).thenReturn(Curriculum.GradeLevel.GRADE_6);

        ReviewVideoSet set = mock(ReviewVideoSet.class);
        when(set.getId()).thenReturn(1L);
        when(set.getStatus()).thenReturn(ReviewVideoSet.Status.PUBLISHED);
        when(set.getVideoType()).thenReturn(ReviewVideoSet.VideoType.REFLEX);
        when(set.getCurriculum()).thenReturn(curriculum);

        ReviewVideo video = mock(ReviewVideo.class);
        when(video.getReviewVideoSet()).thenReturn(set);
        when(video.getCompletionThresholdPercent()).thenReturn(70);

        ReviewVideoQuestion question = mock(ReviewVideoQuestion.class);
        when(question.getId()).thenReturn(QUESTION_ID);
        when(question.getReviewVideo()).thenReturn(video);
        when(question.getMaxRecordingSeconds()).thenReturn(20);
        when(question.getPrompt()).thenReturn("What is your favourite sport?");
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));

        Student student = mock(Student.class);
        when(student.getId()).thenReturn(STUDENT_ID);
        when(studentRepository.findByUserId(ACTOR_USER_ID)).thenReturn(Optional.of(student));

        SchoolClass schoolClass = mock(SchoolClass.class);
        when(schoolClass.getId()).thenReturn(CLASS_ID);
        ReviewVideoAssignment assignment = mock(ReviewVideoAssignment.class);
        when(assignment.getId()).thenReturn(ASSIGNMENT_ID);
        when(assignment.getReviewVideoSet()).thenReturn(set);
        when(assignment.getStatus()).thenReturn(ReviewVideoAssignment.Status.ACTIVE);
        when(assignment.getSchoolClass()).thenReturn(schoolClass);
        when(assignment.getTargetStudentIds()).thenReturn(null); // giao cả lớp (mock mặc định trả tập rỗng)
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(enrollmentRepository.findBySchoolClassIdAndStudentIdAndStatus(CLASS_ID, STUDENT_ID, ClassEnrollment.Status.ACTIVE))
                .thenReturn(Optional.of(mock(ClassEnrollment.class)));

        ReflexQuestionProgress progress = new ReflexQuestionProgress();
        progress.setId(PROGRESS_ID);
        progress.setReviewVideoQuestion(question);
        progress.setStudent(student);
        progress.setReviewVideoAssignment(assignment);
        progress.setRubricVersion(rubricVersion);
        progress.setWritingScore(BigDecimal.valueOf(80));
        progress.setWritingLockedGrammarPercent(BigDecimal.valueOf(80));
        progress.setWritingRedErrorCount(0);
        progress.setAnswerText("My favourite sport is football because I play it with my friends every weekend.");
        when(progressRepository.findByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentId(QUESTION_ID, STUDENT_ID, ASSIGNMENT_ID))
                .thenReturn(Optional.of(progress));

        when(mediaStorageService.downloadWithContentType(AUDIO_URL))
                .thenReturn(new MediaStorageService.DownloadedFile(new byte[]{1, 2, 3}, "audio/webm"));
        return new Fixture(question, assignment, student, progress);
    }
}
