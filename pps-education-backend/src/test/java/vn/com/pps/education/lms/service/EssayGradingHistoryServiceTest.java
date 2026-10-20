package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.com.pps.education.academic.domain.SchoolClass;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.lms.domain.Exercise;
import vn.com.pps.education.lms.domain.ExerciseAssignment;
import vn.com.pps.education.lms.domain.ExerciseAttempt;
import vn.com.pps.education.lms.domain.ExerciseQuestion;
import vn.com.pps.education.lms.domain.Question;
import vn.com.pps.education.lms.domain.StudentAnswer;
import vn.com.pps.education.lms.domain.StudentAnswerGrading;
import vn.com.pps.education.lms.dto.EssayGradingHistoryResponse;
import vn.com.pps.education.lms.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.lms.repository.ExerciseAttemptRepository;
import vn.com.pps.education.lms.repository.ExerciseQuestionRepository;
import vn.com.pps.education.lms.repository.StudentAnswerGradingRepository;
import vn.com.pps.education.lms.repository.StudentAnswerRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lịch sử chấm AI của câu ESSAY (Thống kê BTVN) — chỉ trả câu từng do AI chấm, kèm cờ bản chính thức. */
@ExtendWith(MockitoExtension.class)
class EssayGradingHistoryServiceTest {

    private static final long ASSIGNMENT_ID = 4L;
    private static final long STUDENT_ID = 3L;
    private static final long CLASS_ID = 10L;
    private static final long ACTOR_ID = 5L;

    @Mock
    private ExerciseAssignmentRepository exerciseAssignmentRepository;
    @Mock
    private ExerciseAttemptRepository exerciseAttemptRepository;
    @Mock
    private StudentAnswerRepository studentAnswerRepository;
    @Mock
    private StudentAnswerGradingRepository studentAnswerGradingRepository;
    @Mock
    private ExerciseQuestionRepository exerciseQuestionRepository;
    @Mock
    private AiSecondGradingService aiSecondGradingService;

    private EssayGradingHistoryService service;
    private ExerciseAssignment assignment;
    private ExerciseAttempt attempt;
    private Question question;

    @BeforeEach
    void setUp() {
        service = new EssayGradingHistoryService(exerciseAssignmentRepository, exerciseAttemptRepository,
                studentAnswerRepository, studentAnswerGradingRepository, exerciseQuestionRepository, aiSecondGradingService);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(CLASS_ID);
        Exercise exercise = new Exercise();
        exercise.setId(8L);
        assignment = new ExerciseAssignment();
        assignment.setSchoolClass(schoolClass);
        assignment.setExercise(exercise);

        attempt = new ExerciseAttempt();
        attempt.setId(20L);
        attempt.setAttemptNumber(1);
        attempt.setSubmittedAt(OffsetDateTime.now());

        question = new Question();
        question.setId(30L);
        question.setContent("Write about your day");
    }

    private StudentAnswer essayAnswer(long id) {
        StudentAnswer answer = new StudentAnswer();
        answer.setId(id);
        answer.setExerciseAttempt(attempt);
        answer.setQuestion(question);
        answer.setAnswerText("I goes to school");
        answer.setAutoGradable(false);
        return answer;
    }

    private StudentAnswerGrading grading(long id, StudentAnswer answer, StudentAnswerGrading.GradingSource source,
                                         String score, boolean latest) {
        User grader = new User();
        grader.setFullName(source == StudentAnswerGrading.GradingSource.AI ? "Giáo viên tạo bài" : "Cô Lan");
        StudentAnswerGrading g = new StudentAnswerGrading();
        g.setId(id);
        g.setStudentAnswer(answer);
        g.setGrader(grader);
        g.setScore(new BigDecimal(score));
        g.setMaxScore(BigDecimal.TEN);
        g.setGradingSource(source);
        g.setLatest(latest);
        g.setGradedAt(OffsetDateTime.now());
        return g;
    }

    private void givenAttemptAndQuestionOrder() {
        when(exerciseAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(exerciseAttemptRepository.findByExerciseAssignmentIdAndStudentIdOrderByAttemptNumberDesc(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(List.of(attempt));
    }

    @Test
    void getStudentEssayHistory_aiThenTeacher_returnsBothVersionsWithOfficialFlagOnTeacherVersion() {
        givenAttemptAndQuestionOrder();
        StudentAnswer answer = essayAnswer(100L);
        when(studentAnswerRepository.findByExerciseAttemptIdIn(any())).thenReturn(List.of(answer));
        // Mới nhất trước: bản giáo viên (hiện hành) rồi bản AI cũ (is_final=false).
        when(studentAnswerGradingRepository.findHistoryByStudentAnswerIds(any())).thenReturn(List.of(
                grading(2L, answer, StudentAnswerGrading.GradingSource.HUMAN, "8", true),
                grading(1L, answer, StudentAnswerGrading.GradingSource.AI, "5", false)));
        ExerciseQuestion eq = new ExerciseQuestion();
        eq.setQuestion(question);
        eq.setDisplayOrder(2);
        when(exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(8L)).thenReturn(List.of(eq));

        List<EssayGradingHistoryResponse> result = service.getStudentEssayHistory(ASSIGNMENT_ID, STUDENT_ID, ACTOR_ID);

        assertThat(result).hasSize(1);
        EssayGradingHistoryResponse item = result.get(0);
        assertThat(item.studentAnswerId()).isEqualTo(100L);
        assertThat(item.questionDisplayOrder()).isEqualTo(2);
        assertThat(item.versions()).hasSize(2);
        assertThat(item.versions().get(0).official()).isTrue();
        assertThat(item.versions().get(0).gradingSource()).isEqualTo("HUMAN");
        assertThat(item.versions().get(1).official()).isFalse();
        assertThat(item.versions().get(1).score()).isEqualByComparingTo("5");
        verify(aiSecondGradingService).requireAssignedTeacher(CLASS_ID, ACTOR_ID);
    }

    @Test
    void getStudentEssayHistory_humanOnlyAnswer_isExcluded() {
        givenAttemptAndQuestionOrder();
        StudentAnswer answer = essayAnswer(100L);
        when(studentAnswerRepository.findByExerciseAttemptIdIn(any())).thenReturn(List.of(answer));
        when(studentAnswerGradingRepository.findHistoryByStudentAnswerIds(any())).thenReturn(List.of(
                grading(1L, answer, StudentAnswerGrading.GradingSource.HUMAN, "6", true)));
        when(exerciseQuestionRepository.findByExerciseIdOrderByDisplayOrder(8L)).thenReturn(List.of());

        assertThat(service.getStudentEssayHistory(ASSIGNMENT_ID, STUDENT_ID, ACTOR_ID)).isEmpty();
    }

    @Test
    void getStudentEssayHistory_autoGradableAnswer_isIgnored() {
        givenAttemptAndQuestionOrder();
        StudentAnswer mcq = essayAnswer(100L);
        mcq.setAutoGradable(true);
        when(studentAnswerRepository.findByExerciseAttemptIdIn(any())).thenReturn(List.of(mcq));

        assertThat(service.getStudentEssayHistory(ASSIGNMENT_ID, STUDENT_ID, ACTOR_ID)).isEmpty();
    }

    @Test
    void getStudentEssayHistory_noAttempt_returnsEmpty() {
        when(exerciseAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(exerciseAttemptRepository.findByExerciseAssignmentIdAndStudentIdOrderByAttemptNumberDesc(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(List.of());

        assertThat(service.getStudentEssayHistory(ASSIGNMENT_ID, STUDENT_ID, ACTOR_ID)).isEmpty();
    }

    @Test
    void getStudentEssayHistory_teacherNotInClass_isRejected() {
        when(exerciseAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        doThrow(new NotAssignedTeacherForClassException("error.notAssignedTeacherForClass.default", new Object[]{}, "no"))
                .when(aiSecondGradingService).requireAssignedTeacher(CLASS_ID, ACTOR_ID);

        assertThatThrownBy(() -> service.getStudentEssayHistory(ASSIGNMENT_ID, STUDENT_ID, ACTOR_ID))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void getStudentEssayHistory_unknownAssignment_throwsNotFound() {
        when(exerciseAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStudentEssayHistory(ASSIGNMENT_ID, STUDENT_ID, ACTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
