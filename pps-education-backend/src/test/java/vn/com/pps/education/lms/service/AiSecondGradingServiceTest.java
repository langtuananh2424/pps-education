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
import vn.com.pps.education.exception.AnswerNotManuallyGradableException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.lms.domain.ReflexQuestionProgress;
import vn.com.pps.education.lms.domain.ReviewVideo;
import vn.com.pps.education.lms.domain.ReviewVideoAssignment;
import vn.com.pps.education.lms.domain.ReviewVideoQuestion;
import vn.com.pps.education.lms.domain.ReviewVideoSet;
import vn.com.pps.education.lms.dto.AiGradedItemResponse;
import vn.com.pps.education.lms.dto.ReviewReflexStepRequest;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.lms.repository.StudentAnswerGradingRepository;
import vn.com.pps.education.permission.service.PermissionEvaluationService;
import vn.com.pps.education.student.domain.Student;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
    private StudentAnswerGradingRepository studentAnswerGradingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PermissionEvaluationService permissionEvaluationService;

    private AiSecondGradingService service;
    private ReflexQuestionProgress progress;
    private User teacher;

    @BeforeEach
    void setUp() {
        service = new AiSecondGradingService(classTeacherRepository, reflexQuestionProgressRepository,
                studentAnswerGradingRepository, userRepository, permissionEvaluationService);

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

        AiGradedItemResponse response = service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(BigDecimal.valueOf(85), "Bài đúng ngữ pháp"), TEACHER_ID);

        assertThat(progress.getWritingScore()).isEqualByComparingTo("85");
        assertThat(progress.getWritingFeedback()).isEqualTo("Bài đúng ngữ pháp");
        assertThat(progress.getWritingAiScore()).isEqualByComparingTo("40");
        assertThat(progress.getWritingAiFeedback()).isEqualTo("AI feedback");
        assertThat(progress.getWritingReviewedBy()).isSameAs(teacher);
        // Bước Nói không bị đụng tới
        assertThat(progress.getSpeakingScore()).isEqualByComparingTo("55");
        assertThat(progress.getSpeakingReviewedAt()).isNull();

        assertThat(response.itemType()).isEqualTo("REFLEX_WRITING");
        assertThat(response.reviewed()).isTrue();
        assertThat(response.aiScore()).isEqualByComparingTo("40");
        assertThat(response.reviewedScore()).isEqualByComparingTo("85");
        assertThat(response.reviewerName()).isEqualTo("Cô Lan");
    }

    @Test
    void reviewReflexStep_reviewingTwiceKeepsFirstAiScore() {
        givenAssignedTeacher();
        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.SPEAKING,
                new ReviewReflexStepRequest(BigDecimal.valueOf(70), null), TEACHER_ID);

        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.SPEAKING,
                new ReviewReflexStepRequest(BigDecimal.valueOf(90), null), TEACHER_ID);

        assertThat(progress.getSpeakingScore()).isEqualByComparingTo("90");
        assertThat(progress.getSpeakingAiScore()).isEqualByComparingTo("55");
    }

    @Test
    void reviewReflexStep_rejectsStepNotGradedByAi() {
        givenAssignedTeacher();
        progress.setSpeakingScore(null);
        progress.setSpeakingGradedAt(null);

        assertThatThrownBy(() -> service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.SPEAKING,
                new ReviewReflexStepRequest(BigDecimal.TEN, null), TEACHER_ID))
                .isInstanceOf(AnswerNotManuallyGradableException.class);
        verify(reflexQuestionProgressRepository, never()).save(any());
    }

    @Test
    void reviewReflexStep_rejectsTeacherNotAssignedToClass() {
        when(reflexQuestionProgressRepository.findById(PROGRESS_ID)).thenReturn(Optional.of(progress));
        when(classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(CLASS_ID, TEACHER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(BigDecimal.TEN, null), TEACHER_ID))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
        verify(reflexQuestionProgressRepository, never()).save(any());
    }

    @Test
    void clearReview_whenStudentResubmits_dropsReviewMarkers() {
        givenAssignedTeacher();
        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(BigDecimal.valueOf(85), null), TEACHER_ID);

        progress.clearWritingReview();

        assertThat(progress.getWritingReviewedAt()).isNull();
        assertThat(progress.getWritingAiScore()).isNull();
        assertThat(progress.getWritingReviewedBy()).isNull();
    }

    @Test
    void listClasses_admin_seesEveryClassWithAiDataWithoutTeachingIt() {
        SchoolClass reflexClass = new SchoolClass();
        reflexClass.setId(CLASS_ID);
        reflexClass.setClassCode("L1");
        reflexClass.setName("Lớp 1");
        SchoolClass essayClass = new SchoolClass();
        essayClass.setId(11L);
        essayClass.setClassCode("L2");
        essayClass.setName("Lớp 2");
        when(permissionEvaluationService.hasPermission(TEACHER_ID, AiSecondGradingService.PERM_ADMIN_SCOPE)).thenReturn(true);
        when(reflexQuestionProgressRepository.findClassesWithAiGraded()).thenReturn(java.util.List.of(reflexClass));
        when(studentAnswerGradingRepository.findClassesByGradingSource(any())).thenReturn(java.util.List.of(reflexClass, essayClass));
        when(reflexQuestionProgressRepository.findAiGradedByClassId(CLASS_ID)).thenReturn(java.util.List.of(progress));
        when(reflexQuestionProgressRepository.findAiGradedByClassId(11L)).thenReturn(java.util.List.of());
        when(studentAnswerGradingRepository.findByGradingSourceAndClassId(any(), any())).thenReturn(java.util.List.of());

        var classes = service.listClasses(TEACHER_ID);

        // Lớp 1 chỉ xuất hiện 1 lần dù có cả dữ liệu Video phản xạ lẫn bài tập; lớp 2 không còn mục nào nên bị ẩn.
        assertThat(classes).hasSize(1);
        assertThat(classes.get(0).classId()).isEqualTo(CLASS_ID);
        assertThat(classes.get(0).awaitingReviewCount()).isEqualTo(2);
        verify(classTeacherRepository, never()).findByTeacherIdAndAssignedToIsNull(any());
    }

    @Test
    void reviewReflexStep_admin_canReviewClassHeDoesNotTeach() {
        when(permissionEvaluationService.hasPermission(TEACHER_ID, AiSecondGradingService.PERM_ADMIN_SCOPE)).thenReturn(true);
        when(reflexQuestionProgressRepository.findById(PROGRESS_ID)).thenReturn(Optional.of(progress));
        when(userRepository.findById(TEACHER_ID)).thenReturn(Optional.of(teacher));
        when(reflexQuestionProgressRepository.save(any(ReflexQuestionProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reviewReflexStep(PROGRESS_ID, AiSecondGradingService.ReflexStep.WRITING,
                new ReviewReflexStepRequest(BigDecimal.valueOf(90), null), TEACHER_ID);

        assertThat(progress.getWritingScore()).isEqualByComparingTo("90");
        verify(classTeacherRepository, never()).existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(any(), any());
    }
}
