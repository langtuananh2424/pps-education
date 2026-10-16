package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.lms.domain.ReflexQuestionProgress;
import vn.com.pps.education.lms.domain.ReviewVideo;
import vn.com.pps.education.lms.domain.ReviewVideoAssignment;
import vn.com.pps.education.lms.domain.ReviewVideoQuestion;
import vn.com.pps.education.lms.domain.ReviewVideoSet;
import vn.com.pps.education.lms.repository.ExerciseAttemptRepository;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.lms.repository.ReviewVideoProgressRepository;
import vn.com.pps.education.lms.repository.ReviewVideoQuestionRepository;
import vn.com.pps.education.lms.repository.ReviewVideoRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HomeworkProgressService#videoCompleted (2026-10-15): thông báo Phụ huynh/Học sinh của Clip phản xạ chỉ
 * xét đã NỘP đủ mọi câu hay chưa, không xét đạt/chưa đạt — mirror cột "Hoàn thành" ở Thống kê BTVN.
 */
class HomeworkProgressServiceVideoCompletedTest {

    private static final Long STUDENT_ID = 10L;
    private static final Long ASSIGNMENT_ID = 100L;

    private final ReviewVideoRepository reviewVideoRepository = mock(ReviewVideoRepository.class);
    private final ReviewVideoQuestionRepository reviewVideoQuestionRepository = mock(ReviewVideoQuestionRepository.class);
    private final ReflexQuestionProgressRepository reflexQuestionProgressRepository = mock(ReflexQuestionProgressRepository.class);
    private final HomeworkProgressService service = new HomeworkProgressService(
            mock(ExerciseAttemptRepository.class), reviewVideoRepository, mock(ReviewVideoProgressRepository.class),
            reviewVideoQuestionRepository, reflexQuestionProgressRepository,
            mock(vn.com.pps.education.lms.repository.ReviewVideoConnectionQuestionRepository.class),
            mock(vn.com.pps.education.lms.repository.ReviewVideoConnectionAnswerRepository.class));

    private ReviewVideoAssignment assignment;
    private ReviewVideoQuestion q1;
    private ReviewVideoQuestion q2;

    @BeforeEach
    void setUp() {
        ReviewVideoSet set = new ReviewVideoSet();
        set.setId(1L);
        set.setVideoType(ReviewVideoSet.VideoType.REFLEX);
        assignment = new ReviewVideoAssignment();
        assignment.setId(ASSIGNMENT_ID);
        assignment.setReviewVideoSet(set);

        ReviewVideo video = new ReviewVideo();
        video.setId(2L);
        video.setCompletionThresholdPercent(70);
        q1 = question(21L, video);
        q2 = question(22L, video);
        when(reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(1L)).thenReturn(List.of(video));
        when(reviewVideoQuestionRepository.findByReviewVideoIdOrderByDisplayOrder(2L)).thenReturn(List.of(q1, q2));
    }

    @Test
    void videoCompleted_UC23b_allQuestionsSubmittedButFailed_isCompleted() {
        when(reflexQuestionProgressRepository.findByReviewVideoAssignmentIdAndStudentId(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(List.of(progress(q1, 10), progress(q2, 20)));

        assertThat(service.videoCompleted(assignment, STUDENT_ID)).isTrue();
    }

    @Test
    void videoCompleted_UC23b_someQuestionsNotSubmittedEvenIfPassed_isNotCompleted() {
        when(reflexQuestionProgressRepository.findByReviewVideoAssignmentIdAndStudentId(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(List.of(progress(q1, 100)));

        assertThat(service.videoCompleted(assignment, STUDENT_ID)).isFalse();
    }

    @Test
    void videoCompleted_UC23b_nothingSubmitted_isNotCompleted() {
        when(reflexQuestionProgressRepository.findByReviewVideoAssignmentIdAndStudentId(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(List.of());

        assertThat(service.videoCompleted(assignment, STUDENT_ID)).isFalse();
    }

    private static ReviewVideoQuestion question(Long id, ReviewVideo video) {
        ReviewVideoQuestion q = new ReviewVideoQuestion();
        q.setId(id);
        q.setReviewVideo(video);
        return q;
    }

    private static ReflexQuestionProgress progress(ReviewVideoQuestion q, int score) {
        ReflexQuestionProgress p = new ReflexQuestionProgress();
        p.setReviewVideoQuestion(q);
        p.setWritingScore(BigDecimal.valueOf(score));
        p.setSpeakingScore(BigDecimal.valueOf(score));
        return p;
    }
}
