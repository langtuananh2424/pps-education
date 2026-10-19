package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.lms.domain.ReviewVideo;
import vn.com.pps.education.lms.domain.ReviewVideoAssignment;
import vn.com.pps.education.lms.domain.ReviewVideoConnectionAnswer;
import vn.com.pps.education.lms.domain.ReviewVideoConnectionQuestion;
import vn.com.pps.education.lms.domain.ReviewVideoProgress;
import vn.com.pps.education.lms.domain.ReviewVideoSet;
import vn.com.pps.education.lms.repository.ExerciseAttemptRepository;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.lms.repository.ReviewVideoConnectionAnswerRepository;
import vn.com.pps.education.lms.repository.ReviewVideoConnectionQuestionRepository;
import vn.com.pps.education.lms.repository.ReviewVideoProgressRepository;
import vn.com.pps.education.lms.repository.ReviewVideoQuestionRepository;
import vn.com.pps.education.lms.repository.ReviewVideoRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 2026-10-16: CONNECTION "hoàn thành" không còn ngầm hiểu là "đạt" (lượt làm sai vẫn tính hoàn thành), nên
 * HomeworkProgressService#videoPassed (nhãn Đạt/Chưa đạt Cổng phụ huynh) phải tự xét điểm trắc nghiệm gộp.
 */
class HomeworkProgressServiceConnectionPassedTest {

    private static final Long STUDENT_ID = 10L;
    private static final Long ASSIGNMENT_ID = 100L;

    private final ReviewVideoRepository reviewVideoRepository = mock(ReviewVideoRepository.class);
    private final ReviewVideoProgressRepository progressRepository = mock(ReviewVideoProgressRepository.class);
    private final ReviewVideoConnectionQuestionRepository questionRepository = mock(ReviewVideoConnectionQuestionRepository.class);
    private final ReviewVideoConnectionAnswerRepository answerRepository = mock(ReviewVideoConnectionAnswerRepository.class);
    private final HomeworkProgressService service = new HomeworkProgressService(
            mock(ExerciseAttemptRepository.class), reviewVideoRepository, progressRepository,
            mock(ReviewVideoQuestionRepository.class), mock(ReflexQuestionProgressRepository.class),
            questionRepository, answerRepository);

    private ReviewVideoAssignment assignment;
    private ReviewVideoConnectionQuestion q1;
    private ReviewVideoConnectionQuestion q2;

    @BeforeEach
    void setUp() {
        ReviewVideoSet set = new ReviewVideoSet();
        set.setId(1L);
        set.setVideoType(ReviewVideoSet.VideoType.CONNECTION);
        assignment = new ReviewVideoAssignment();
        assignment.setId(ASSIGNMENT_ID);
        assignment.setReviewVideoSet(set);

        ReviewVideo video = new ReviewVideo();
        video.setId(2L);
        video.setCompletionThresholdPercent(75);
        q1 = question(21L);
        q2 = question(22L);
        when(reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(1L)).thenReturn(List.of(video));
        when(questionRepository.findByReviewVideoIdOrderByDisplayOrder(2L)).thenReturn(List.of(q1, q2));
    }

    @Test
    void videoPassed_completedButAllWrong_isNotPassed() {
        completed(true);
        answers(answer(q1, false), answer(q2, false));

        assertThat(service.videoPassed(assignment, STUDENT_ID, 70)).isFalse();
    }

    @Test
    void videoPassed_completedAndScoreAtThreshold_isPassed() {
        completed(true);
        // 1/2 = 50% < 75 → chưa đạt; 2/2 = 100% → đạt.
        answers(answer(q1, true), answer(q2, false));
        assertThat(service.videoPassed(assignment, STUDENT_ID, 70)).isFalse();

        answers(answer(q1, true), answer(q2, true));
        assertThat(service.videoPassed(assignment, STUDENT_ID, 70)).isTrue();
    }

    @Test
    void videoPassed_notCompleted_isNotPassedEvenWithPerfectScore() {
        completed(false);
        answers(answer(q1, true), answer(q2, true));

        assertThat(service.videoPassed(assignment, STUDENT_ID, 70)).isFalse();
    }

    private void completed(boolean completed) {
        ReviewVideoProgress progress = new ReviewVideoProgress();
        progress.setCompleted(completed);
        when(progressRepository.findByReviewVideoIdAndStudentIdAndReviewVideoAssignmentId(2L, STUDENT_ID, ASSIGNMENT_ID))
                .thenReturn(Optional.of(progress));
    }

    private void answers(ReviewVideoConnectionAnswer... answers) {
        when(answerRepository.findByReviewVideoConnectionQuestion_ReviewVideoIdAndStudentIdAndWatchSession_ArchivedAtIsNull(2L, STUDENT_ID))
                .thenReturn(List.of(answers));
    }

    private static ReviewVideoConnectionQuestion question(Long id) {
        ReviewVideoConnectionQuestion q = new ReviewVideoConnectionQuestion();
        q.setId(id);
        return q;
    }

    private static ReviewVideoConnectionAnswer answer(ReviewVideoConnectionQuestion question, boolean correct) {
        ReviewVideoConnectionAnswer a = new ReviewVideoConnectionAnswer();
        a.setReviewVideoConnectionQuestion(question);
        a.setCorrect(correct);
        a.setAnsweredAt(OffsetDateTime.now());
        return a;
    }
}
