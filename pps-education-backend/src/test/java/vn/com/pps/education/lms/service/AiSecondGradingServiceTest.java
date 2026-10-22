package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.com.pps.education.academic.domain.SchoolClass;
import vn.com.pps.education.academic.repository.ClassTeacherRepository;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.auth.repository.UserRepository;
import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.exception.AnswerNotManuallyGradableException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.lms.domain.ReflexQuestionProgress;
import vn.com.pps.education.lms.domain.ReflexQuestionProgressHistory;
import vn.com.pps.education.lms.domain.ReviewVideo;
import vn.com.pps.education.lms.domain.ReviewVideoAssignment;
import vn.com.pps.education.lms.domain.ReviewVideoQuestion;
import vn.com.pps.education.lms.domain.ReviewVideoSet;
import vn.com.pps.education.lms.dto.ReviewReflexStepRequest;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressHistoryRepository;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.permission.service.PermissionEvaluationService;
import vn.com.pps.education.student.domain.Student;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Chấm lần 2 bài AI chấm (Video phản xạ) — điểm GV thay điểm chính thức, điểm AI gốc được giữ. */
@ExtendWith(MockitoExtension.class)
class AiSecondGradingServiceTest {

    private static final long CLASS_ID = 10L;
    private static final long TEACHER_ID = 5L;
    private static final long PROGRESS_ID = 7L;

    @Mock
    private ClassTeacherRepository classTeacherRepository;
    @Mock
    private ReflexQuestionProgressRepository reflexQuestionProgressRepository;
    @Mock
    private ReflexQuestionProgressHistoryRepository reflexQuestionProgressHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PermissionEvaluationService permissionEvaluationService;

    private AiSecondGradingService service;
    private ReflexQuestionProgress progress;
    private User teacher;

    @BeforeEach
    void setUp() {
        service = new AiSecondGradingService(classTeacherRepository, reflexQuestionProgressRepository, reflexQuestionProgressHistoryRepository,
                userRepository, permissionEvaluationService);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(CLASS_ID);
        ReviewVideoAssignment assignment = new ReviewVideoAssignment();
        assignment.setSchoolClass(schoolClass);

        ReviewVideoSet set = new ReviewVideoSet();
        set.setTitle("Bộ 1");
        ReviewVideo video = new ReviewVideo();
        video.setTitle("Video 1");
        video.setReviewVideoSet(set);
        ReviewVideoQuestion question = new ReviewVideoQuestion();
        question.setPrompt("Describe your day");
        question.setReviewVideo(video);

        User studentUser = new User();
        studentUser.setFullName("Học sinh A");
        Student student = new Student();
        student.setId(3L);
        student.setUser(studentUser);

        progress = new ReflexQuestionProgress();
        progress.setId(PROGRESS_ID);
        progress.setReviewVideoAssignment(assignment);
        progress.setReviewVideoQuestion(question);
        progress.setStudent(student);
        progress.setAnswerText("I goes to school");
        progress.setWritingScore(BigDecimal.valueOf(40));
        progress.setWritingMaxScore(BigDecimal.valueOf(100));
        progress.setWritingFeedback("AI feedback");
        progress.setWritingGradedAt(OffsetDateTime.now());
        progress.setSpeakingScore(BigDecimal.valueOf(55));
        progress.setSpeakingGradedAt(OffsetDateTime.now());

        teacher = new User();
        teacher.setId(TEACHER_ID);
        teacher.setFullName("Cô Lan");
    }

    private void givenAssignedTeacher() {
        when(classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(CLASS_ID, TEACHER_ID)).thenReturn(true);
        when(reflexQuestionProgressRepository.findById(PROGRESS_ID)).thenReturn(Optional.of(progress));
        when(userRepository.findById(TEACHER_ID)).thenReturn(Optional.of(teacher));
        lenient().when(reflexQuestionProgressRepository.save(any(ReflexQuestionProgress.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void reviewReflexStep_writing_replacesScoreAndKeepsAiOriginal() {
        givenAssignedTeacher();

        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(BigDecimal.valueOf(85), "Bài đúng ngữ pháp", null), TEACHER_ID);

        assertThat(progress.getWritingScore()).isEqualByComparingTo("85");
        assertThat(progress.getWritingFeedback()).isEqualTo("Bài đúng ngữ pháp");
        assertThat(progress.getWritingAiScore()).isEqualByComparingTo("40");
        assertThat(progress.getWritingAiFeedback()).isEqualTo("AI feedback");
        assertThat(progress.getWritingReviewedBy()).isSameAs(teacher);
        // Bước Nói không bị đụng tới
        assertThat(progress.getSpeakingScore()).isEqualByComparingTo("55");
        assertThat(progress.getSpeakingReviewedAt()).isNull();
    }

    @Test
    void reviewReflexStep_reviewingTwiceKeepsFirstAiScore() {
        givenAssignedTeacher();
        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.SPEAKING,
                new ReviewReflexStepRequest(BigDecimal.valueOf(70), null, null), TEACHER_ID);

        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.SPEAKING,
                new ReviewReflexStepRequest(BigDecimal.valueOf(90), null, null), TEACHER_ID);

        assertThat(progress.getSpeakingScore()).isEqualByComparingTo("90");
        assertThat(progress.getSpeakingAiScore()).isEqualByComparingTo("55");
    }

    @Test
    void reviewReflexStep_rejectsStepNotGradedByAi() {
        givenAssignedTeacher();
        progress.setSpeakingScore(null);
        progress.setSpeakingGradedAt(null);

        assertThatThrownBy(() -> service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.SPEAKING,
                new ReviewReflexStepRequest(BigDecimal.TEN, null, null), TEACHER_ID))
                .isInstanceOf(AnswerNotManuallyGradableException.class);
        verify(reflexQuestionProgressRepository, never()).save(any());
    }

    @Test
    void reviewReflexStep_rejectsTeacherNotAssignedToClass() {
        when(reflexQuestionProgressRepository.findById(PROGRESS_ID)).thenReturn(Optional.of(progress));
        when(classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(CLASS_ID, TEACHER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(BigDecimal.TEN, null, null), TEACHER_ID))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
        verify(reflexQuestionProgressRepository, never()).save(any());
    }

    @Test
    void clearReview_whenStudentResubmits_dropsReviewMarkers() {
        givenAssignedTeacher();
        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(BigDecimal.valueOf(85), null, null), TEACHER_ID);

        progress.clearWritingReview();

        assertThat(progress.getWritingReviewedAt()).isNull();
        assertThat(progress.getWritingAiScore()).isNull();
        assertThat(progress.getWritingReviewedBy()).isNull();
    }

    @Test
    void reviewReflexStep_admin_canReviewClassHeDoesNotTeach() {
        when(permissionEvaluationService.hasPermission(TEACHER_ID, AiSecondGradingService.PERM_ADMIN_SCOPE)).thenReturn(true);
        when(reflexQuestionProgressRepository.findById(PROGRESS_ID)).thenReturn(Optional.of(progress));
        when(userRepository.findById(TEACHER_ID)).thenReturn(Optional.of(teacher));
        when(reflexQuestionProgressRepository.save(any(ReflexQuestionProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(BigDecimal.valueOf(90), null, null), TEACHER_ID);

        assertThat(progress.getWritingScore()).isEqualByComparingTo("90");
        verify(classTeacherRepository, never()).existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(any(), any());
    }

    private ReflexQuestionProgressHistory writingAttempt(int number, String score, String feedback, String answer) {
        ReflexQuestionProgressHistory h = new ReflexQuestionProgressHistory();
        h.setAttemptType(ReflexQuestionProgressHistory.AttemptType.WRITING);
        h.setAttemptNumber(number);
        h.setScore(new BigDecimal(score));
        h.setMaxScore(BigDecimal.valueOf(100));
        h.setFeedback(feedback);
        h.setAnswerText(answer);
        h.setMarkedAnswer("marked " + answer);
        return h;
    }

    private void givenWritingAttempts(ReflexQuestionProgressHistory... attempts) {
        when(reflexQuestionProgressHistoryRepository.findByReflexQuestionProgressIdAndAttemptTypeOrderByAttemptNumberAsc(
                PROGRESS_ID, ReflexQuestionProgressHistory.AttemptType.WRITING)).thenReturn(List.of(attempts));
    }

    @Test
    void selectReflexAttempt_olderBestAttempt_becomesOfficialAndKeepsAiOriginal() {
        givenAssignedTeacher();
        ReflexQuestionProgressHistory first = writingAttempt(1, "80", "AI lần 1 tốt", "I go to school");
        ReflexQuestionProgressHistory second = writingAttempt(2, "40", "AI lần 2", "I goes to school");
        givenWritingAttempts(first, second);

        service.selectReflexAttempt(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING, 1, TEACHER_ID);

        assertThat(first.isTeacherSelected()).isTrue();
        assertThat(first.getTeacherScore()).isEqualByComparingTo("80");
        assertThat(second.isTeacherSelected()).isFalse();
        // Điểm chính thức của tiến trình = lần được chọn, kèm nội dung bài làm tương ứng; AI gốc giữ để đối chiếu.
        assertThat(progress.getWritingScore()).isEqualByComparingTo("80");
        assertThat(progress.getAnswerText()).isEqualTo("I go to school");
        assertThat(progress.getWritingFeedback()).isEqualTo("AI lần 1 tốt");
        assertThat(progress.getWritingMarkedAnswer()).isEqualTo("marked I go to school");
        assertThat(progress.getWritingAiScore()).isEqualByComparingTo("80");
        assertThat(progress.getWritingReviewedBy()).isSameAs(teacher);
    }

    @Test
    void selectReflexAttempt_switchingSelection_unsetsPreviousSelectedAttempt() {
        givenAssignedTeacher();
        ReflexQuestionProgressHistory first = writingAttempt(1, "80", "a", "x");
        ReflexQuestionProgressHistory second = writingAttempt(2, "40", "b", "y");
        first.setTeacherSelected(true);
        givenWritingAttempts(first, second);

        service.selectReflexAttempt(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING, 2, TEACHER_ID);

        assertThat(first.isTeacherSelected()).isFalse();
        assertThat(second.isTeacherSelected()).isTrue();
        // Lần mới nhất, giáo viên chưa chỉnh gì → không đánh dấu "GV đã chỉnh".
        assertThat(progress.getWritingScore()).isEqualByComparingTo("40");
        assertThat(progress.getWritingReviewedAt()).isNull();
        verify(reflexQuestionProgressHistoryRepository).saveAndFlush(first);
    }

    @Test
    void selectReflexAttempt_reusesTeacherScoreAlreadySetOnThatAttempt() {
        givenAssignedTeacher();
        ReflexQuestionProgressHistory first = writingAttempt(1, "50", "AI", "x");
        first.setTeacherScore(BigDecimal.valueOf(75));
        first.setTeacherFeedback("GV chốt");
        givenWritingAttempts(first, writingAttempt(2, "40", "b", "y"));

        service.selectReflexAttempt(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING, 1, TEACHER_ID);

        assertThat(progress.getWritingScore()).isEqualByComparingTo("75");
        assertThat(progress.getWritingFeedback()).isEqualTo("GV chốt");
        assertThat(progress.getWritingAiScore()).isEqualByComparingTo("50");
    }

    @Test
    void selectReflexAttempt_unknownAttempt_throwsNotFound() {
        givenAssignedTeacher();
        givenWritingAttempts(writingAttempt(1, "80", "a", "x"));

        assertThatThrownBy(() -> service.selectReflexAttempt(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING, 5, TEACHER_ID))
                .isInstanceOf(vn.com.pps.education.exception.ResourceNotFoundException.class);
    }

    @Test
    void selectReflexAttempt_rejectsTeacherNotAssignedToClass() {
        when(reflexQuestionProgressRepository.findById(PROGRESS_ID)).thenReturn(Optional.of(progress));
        when(classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(CLASS_ID, TEACHER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.selectReflexAttempt(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING, 1, TEACHER_ID))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
        verify(reflexQuestionProgressRepository, never()).save(any());
    }

    @Test
    void reviewReflexStep_persistsTeacherScoreOnSelectedAttemptNotLatest() {
        givenAssignedTeacher();
        ReflexQuestionProgressHistory first = writingAttempt(1, "80", "a", "x");
        ReflexQuestionProgressHistory second = writingAttempt(2, "40", "b", "y");
        first.setTeacherSelected(true);
        givenWritingAttempts(first, second);

        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(BigDecimal.valueOf(90), "Cô chỉnh", null), TEACHER_ID);

        assertThat(first.getTeacherScore()).isEqualByComparingTo("90");
        assertThat(first.getTeacherFeedback()).isEqualTo("Cô chỉnh");
        assertThat(first.getScore()).isEqualByComparingTo("80"); // AI gốc không đổi
        assertThat(second.getTeacherScore()).isNull();
    }

    @Test
    void reviewReflexStep_withoutSelection_persistsTeacherScoreOnLatestAttempt() {
        givenAssignedTeacher();
        ReflexQuestionProgressHistory first = writingAttempt(1, "80", "a", "x");
        ReflexQuestionProgressHistory second = writingAttempt(2, "40", "b", "y");
        givenWritingAttempts(first, second);

        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(BigDecimal.valueOf(60), null, null), TEACHER_ID);

        assertThat(second.getTeacherScore()).isEqualByComparingTo("60");
        assertThat(first.getTeacherScore()).isNull();
    }

    private static List<ReviewReflexStepRequest.BandScore> bands(Object... nameAndPercent) {
        java.util.ArrayList<ReviewReflexStepRequest.BandScore> out = new java.util.ArrayList<>();
        for (int i = 0; i < nameAndPercent.length; i += 2) {
            out.add(new ReviewReflexStepRequest.BandScore((String) nameAndPercent[i], (Integer) nameAndPercent[i + 1]));
        }
        return out;
    }

    private ReflexQuestionProgressHistory writingAttemptWithBands(String score) {
        ReflexQuestionProgressHistory h = writingAttempt(1, score, "AI", "x");
        h.setCriteriaScores(List.of(new CriteriaScoreItem("Grammar and Vocabulary", 40), new CriteriaScoreItem("Discourse Management", 60)));
        return h;
    }

    @Test
    void reviewReflexStep_bands_v2RecomputesTotalFloorTo5AndStoresTeacherBands() {
        givenAssignedTeacher();
        progress.setRubricVersion("v2");
        ReflexQuestionProgressHistory attempt = writingAttemptWithBands("50");
        givenWritingAttempts(attempt);

        // (70 + 85) / 2 = 77,5 → làm tròn XUỐNG bội 5 = 75; điểm tổng client gửi kèm (10) bị bỏ qua.
        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(BigDecimal.TEN, "GV sửa band",
                        bands("Discourse Management", 85, "Grammar and Vocabulary", 70)), TEACHER_ID);

        assertThat(progress.getWritingScore()).isEqualByComparingTo("75");
        // Thứ tự + nhãn giữ theo bản AI gốc, bất kể thứ tự client gửi.
        assertThat(progress.getWritingCriteriaScores()).extracting(CriteriaScoreItem::criterion, CriteriaScoreItem::percent)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Grammar and Vocabulary", 70),
                        org.assertj.core.groups.Tuple.tuple("Discourse Management", 85));
        assertThat(attempt.getTeacherScore()).isEqualByComparingTo("75");
        assertThat(attempt.getTeacherCriteriaScores()).hasSize(2);
        assertThat(attempt.getCriteriaScores()).extracting(CriteriaScoreItem::percent).containsExactly(40, 60); // AI gốc không đổi
    }

    @Test
    void reviewReflexStep_bands_v2SpeakingExcludesReferenceOnlyPronunciation() {
        givenAssignedTeacher();
        progress.setRubricVersion("v2");
        ReflexQuestionProgressHistory attempt = new ReflexQuestionProgressHistory();
        attempt.setAttemptType(ReflexQuestionProgressHistory.AttemptType.SPEAKING);
        attempt.setAttemptNumber(1);
        attempt.setScore(BigDecimal.valueOf(55));
        attempt.setCriteriaScores(List.of(new CriteriaScoreItem("Grammar and Vocabulary", 50),
                new CriteriaScoreItem("Pronunciation (tham khảo)", 90)));
        when(reflexQuestionProgressHistoryRepository.findByReflexQuestionProgressIdAndAttemptTypeOrderByAttemptNumberAsc(
                PROGRESS_ID, ReflexQuestionProgressHistory.AttemptType.SPEAKING)).thenReturn(List.of(attempt));

        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.SPEAKING,
                new ReviewReflexStepRequest(null, null,
                        bands("Grammar and Vocabulary", 80, "Pronunciation (tham khảo)", 10)), TEACHER_ID);

        assertThat(progress.getSpeakingScore()).isEqualByComparingTo("80"); // Phát âm tham khảo không vào điểm mở khoá
        assertThat(progress.getSpeakingCriteriaScores()).extracting(CriteriaScoreItem::percent).containsExactly(80, 10);
    }

    @Test
    void reviewReflexStep_bands_v1UsesRoundedAverage() {
        givenAssignedTeacher();
        givenWritingAttempts(writingAttemptWithBands("50"));

        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(null, null,
                        bands("Grammar and Vocabulary", 71, "Discourse Management", 70)), TEACHER_ID);

        assertThat(progress.getWritingScore()).isEqualByComparingTo("71"); // 70,5 → 71
    }

    @Test
    void reviewReflexStep_bands_rejectsMismatchedOrMissingBands() {
        givenAssignedTeacher();
        givenWritingAttempts(writingAttemptWithBands("50"));

        assertThatThrownBy(() -> service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(null, null, bands("Grammar and Vocabulary", 70)), TEACHER_ID))
                .isInstanceOf(AnswerNotManuallyGradableException.class);
        assertThatThrownBy(() -> service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(null, null, bands("Grammar and Vocabulary", 70, "Other", 60)), TEACHER_ID))
                .isInstanceOf(AnswerNotManuallyGradableException.class);
        verify(reflexQuestionProgressRepository, never()).save(any());
    }

    @Test
    void reviewReflexStep_bands_rejectsAttemptWithoutBands() {
        givenAssignedTeacher();
        givenWritingAttempts(writingAttempt(1, "50", "AI", "x"));

        assertThatThrownBy(() -> service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(null, null, bands("Grammar and Vocabulary", 70)), TEACHER_ID))
                .isInstanceOf(AnswerNotManuallyGradableException.class);
    }

    @Test
    void reviewReflexStep_requiresTotalOrBands() {
        givenAssignedTeacher();

        assertThatThrownBy(() -> service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(null, null, null), TEACHER_ID))
                .isInstanceOf(AnswerNotManuallyGradableException.class);
    }

    @Test
    void reviewReflexStep_totalOnlyAfterBandEdit_dropsTeacherBands() {
        givenAssignedTeacher();
        ReflexQuestionProgressHistory attempt = writingAttemptWithBands("50");
        attempt.setTeacherCriteriaScores(List.of(new CriteriaScoreItem("Grammar and Vocabulary", 70), new CriteriaScoreItem("Discourse Management", 85)));
        givenWritingAttempts(attempt);

        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(BigDecimal.valueOf(90), null, null), TEACHER_ID);

        assertThat(attempt.getTeacherCriteriaScores()).isNull();
        assertThat(progress.getWritingCriteriaScores()).extracting(CriteriaScoreItem::percent).containsExactly(40, 60);
    }

    @Test
    void selectReflexAttempt_mirrorsTeacherBandsOfChosenAttempt() {
        givenAssignedTeacher();
        ReflexQuestionProgressHistory first = writingAttemptWithBands("50");
        first.setTeacherScore(BigDecimal.valueOf(75));
        first.setTeacherCriteriaScores(List.of(new CriteriaScoreItem("Grammar and Vocabulary", 70), new CriteriaScoreItem("Discourse Management", 85)));
        givenWritingAttempts(first, writingAttempt(2, "40", "b", "y"));

        service.selectReflexAttempt(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING, 1, TEACHER_ID);

        assertThat(progress.getWritingCriteriaScores()).extracting(CriteriaScoreItem::percent).containsExactly(70, 85);
        assertThat(progress.getWritingReviewedAt()).isNotNull();
    }
}
