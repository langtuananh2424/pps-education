package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import vn.com.pps.education.common.AiTokenUsage;
import vn.com.pps.education.domain.AiGradingTokenUsage;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.ReflexQuestionProgress;
import vn.com.pps.education.domain.ReviewVideo;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.ReviewVideoQuestion;
import vn.com.pps.education.domain.ReviewVideoSet;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.exception.ReflexAudioRejectedException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ReflexQuestionProgressHistoryRepository;
import vn.com.pps.education.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.repository.ReviewVideoAssignmentRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionRepository;
import vn.com.pps.education.repository.StudentRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    private final ReflexSequentialGradingService service = new ReflexSequentialGradingService(
            questionRepository, assignmentRepository, progressRepository, historyRepository, enrollmentRepository,
            studentRepository, mediaStorageService, mock(ReflexWritingGrammarAiGradingService.class),
            mock(ReflexSpeakingContentAiGradingService.class), reflexV2GradingService, tokenUsageRecorder);

    private final AiTokenUsage transcriptionUsage =
            new AiTokenUsage("gemini-3.6-flash-medium", true, 5200, 0, 310, 900, 6100);

    /**
     * Alternate Flow "bản ghi bị từ chối" (nói khác bài viết / không đọc được): lượt phiên âm mù đã gọi AI
     * thật — phải ghi đúng 1 dòng chi phí TRANSCRIPTION (kèm học sinh/bài/câu hỏi) rồi vẫn ném 422 như cũ,
     * không lưu tiến trình.
     */
    @Test
    void submitSpokenAnswer_UC23b_audioRejected_recordsTranscriptionUsageOnceAndStillRejects() {
        Fixture f = v2SpeakingFixture();
        // Mô phỏng đúng thứ tự thật: Lượt A phiên âm trả về (đẩy chi phí ra sink) rồi mới bị từ chối.
        when(reflexV2GradingService.gradeSpeaking(any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            ReflexV2AiGradingService.SpeakingUsageSink sink = inv.getArgument(5);
            sink.record(AiGradingTokenUsage.Step.TRANSCRIPTION, transcriptionUsage);
            throw new ReflexAudioRejectedException(ReflexV2AiGradingService.MSG_SPOKE_DIFFERENT);
        });

        assertThatThrownBy(() -> service.submitSpokenAnswer(QUESTION_ID, ASSIGNMENT_ID, AUDIO_URL, ACTOR_USER_ID))
                .isInstanceOf(ReflexAudioRejectedException.class)
                .hasMessage(ReflexV2AiGradingService.MSG_SPOKE_DIFFERENT);

        verify(tokenUsageRecorder, times(1)).record(eq(AiGradingTokenUsage.Step.TRANSCRIPTION), eq("chatWithAudioJson"),
                isNull(), eq(transcriptionUsage), eq(f.student()), eq(f.assignment()), eq(f.question()));
        verifyNoMoreInteractions(tokenUsageRecorder);
        verify(progressRepository, never()).save(any());
    }

    private record Fixture(ReviewVideoQuestion question, ReviewVideoAssignment assignment, Student student) {
    }

    /** Câu hỏi REFLEX Khối 6, dòng tiến trình đã đạt Bước 1 bằng rubric v2 (có điểm Ngữ pháp khoá). */
    private Fixture v2SpeakingFixture() {
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
        progress.setReviewVideoQuestion(question);
        progress.setStudent(student);
        progress.setReviewVideoAssignment(assignment);
        progress.setRubricVersion("v2");
        progress.setWritingScore(BigDecimal.valueOf(80));
        progress.setWritingLockedGrammarPercent(BigDecimal.valueOf(80));
        progress.setWritingRedErrorCount(0);
        progress.setAnswerText("My favourite sport is football because I play it with my friends every weekend.");
        when(progressRepository.findByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentId(QUESTION_ID, STUDENT_ID, ASSIGNMENT_ID))
                .thenReturn(Optional.of(progress));

        when(mediaStorageService.downloadWithContentType(AUDIO_URL))
                .thenReturn(new MediaStorageService.DownloadedFile(new byte[]{1, 2, 3}, "audio/webm"));
        return new Fixture(question, assignment, student);
    }
}
