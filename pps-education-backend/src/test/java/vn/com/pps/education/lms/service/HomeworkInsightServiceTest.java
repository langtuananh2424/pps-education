package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.Test;
import vn.com.pps.education.academic.domain.ClassSession;
import vn.com.pps.education.lms.domain.Exercise;
import vn.com.pps.education.lms.domain.ExerciseAssignment;
import vn.com.pps.education.lms.domain.ExerciseAttempt;
import vn.com.pps.education.lms.domain.HomeworkSkillBatch;
import vn.com.pps.education.lms.domain.Question;
import vn.com.pps.education.academic.domain.SchoolClass;
import vn.com.pps.education.student.domain.Student;
import vn.com.pps.education.lms.domain.StudentAnswer;
import vn.com.pps.education.student.domain.StudentComment;
import vn.com.pps.education.lms.dto.HomeworkScoreInput;
import vn.com.pps.education.academic.repository.ClassSessionRepository;
import vn.com.pps.education.lms.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.lms.repository.ExerciseAttemptRepository;
import vn.com.pps.education.lms.repository.StudentAnswerGradingRepository;
import vn.com.pps.education.lms.repository.StudentAnswerRepository;
import vn.com.pps.education.student.repository.StudentCommentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC-74 (bổ sung 2026-09-30) — gom dữ liệu BTVN nhiều buổi theo lô cho trợ lý nhận xét. */
class HomeworkInsightServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 30);

    private final ClassSessionRepository classSessionRepository = mock(ClassSessionRepository.class);
    private final StudentCommentRepository studentCommentRepository = mock(StudentCommentRepository.class);
    private final ExerciseAssignmentRepository exerciseAssignmentRepository = mock(ExerciseAssignmentRepository.class);
    private final ExerciseAttemptRepository exerciseAttemptRepository = mock(ExerciseAttemptRepository.class);
    private final StudentAnswerRepository studentAnswerRepository = mock(StudentAnswerRepository.class);
    private final StudentAnswerGradingRepository studentAnswerGradingRepository = mock(StudentAnswerGradingRepository.class);

    private final HomeworkInsightService service = new HomeworkInsightService(classSessionRepository, studentCommentRepository,
            exerciseAssignmentRepository, exerciseAttemptRepository, studentAnswerRepository, studentAnswerGradingRepository);

    private final Student student = mock(Student.class);

    private ClassSession session(long id) {
        ClassSession session = mock(ClassSession.class);
        SchoolClass schoolClass = mock(SchoolClass.class);
        when(schoolClass.getId()).thenReturn(5L);
        when(session.getId()).thenReturn(id);
        when(session.getSchoolClass()).thenReturn(schoolClass);
        when(session.getSessionDate()).thenReturn(DATE);
        when(session.getTeacherType()).thenReturn(ClassSession.TeacherType.VIETNAMESE);
        return session;
    }

    private StudentComment comment(ClassSession session, HomeworkSkillBatch grammarBatch) {
        StudentComment comment = mock(StudentComment.class);
        when(comment.getStudent()).thenReturn(student);
        when(comment.getClassSession()).thenReturn(session);
        when(comment.getHomeworkNextGrammarBatch()).thenReturn(grammarBatch);
        return comment;
    }

    private HomeworkSkillBatch batch(long id, Exercise exercise) {
        HomeworkSkillBatch batch = mock(HomeworkSkillBatch.class);
        when(batch.getId()).thenReturn(id);
        ExerciseAssignment assignment = mock(ExerciseAssignment.class);
        when(assignment.getExercise()).thenReturn(exercise);
        when(exerciseAssignmentRepository.findByHomeworkBatchId(id)).thenReturn(List.of(assignment));
        return batch;
    }

    private Exercise exercise(long id) {
        Exercise exercise = mock(Exercise.class);
        when(exercise.getId()).thenReturn(id);
        when(exercise.getTotalPoints()).thenReturn(new BigDecimal("10"));
        return exercise;
    }

    private ExerciseAttempt attempt(long id, Exercise exercise, int number, String score) {
        ExerciseAttempt attempt = mock(ExerciseAttempt.class);
        when(attempt.getId()).thenReturn(id);
        when(attempt.getStudent()).thenReturn(student);
        when(attempt.getExercise()).thenReturn(exercise);
        when(attempt.getAttemptNumber()).thenReturn(number);
        when(attempt.getTotalScore()).thenReturn(score == null ? null : new BigDecimal(score));
        return attempt;
    }

    private StudentAnswer answer(ExerciseAttempt attempt, Question.QuestionType type, boolean correct) {
        StudentAnswer answer = mock(StudentAnswer.class);
        Question question = mock(Question.class);
        when(question.getQuestionType()).thenReturn(type);
        when(answer.getQuestion()).thenReturn(question);
        when(answer.getExerciseAttempt()).thenReturn(attempt);
        when(answer.isAutoGradable()).thenReturn(true);
        when(answer.getCorrect()).thenReturn(correct);
        return answer;
    }

    @Test
    void describe_UC74_combinesWeakQuestionTypeRetryAndTrendFromBatchQueries() {
        when(student.getId()).thenReturn(1L);
        ClassSession current = session(100L);
        ClassSession s1 = session(99L);
        ClassSession s2 = session(98L);
        ClassSession s3 = session(97L);
        when(classSessionRepository.findSessionsBeforeWithTeacherTypeOrderedDesc(eq(5L), eq(DATE), eq(100L),
                eq(ClassSession.TeacherType.VIETNAMESE), anyList())).thenReturn(List.of(s1, s2, s3));
        Exercise e1 = exercise(11L);
        Exercise e2 = exercise(12L);
        Exercise e3 = exercise(13L);
        List<StudentComment> c1 = List.of(comment(s1, batch(1L, e1)));
        List<StudentComment> c2 = List.of(comment(s2, batch(2L, e2)));
        List<StudentComment> c3 = List.of(comment(s3, batch(3L, e3)));
        when(studentCommentRepository.findByClassSessionIdAndStudentIdIn(100L, List.of(1L))).thenReturn(List.of());
        when(studentCommentRepository.findByClassSessionIdAndStudentIdIn(99L, List.of(1L))).thenReturn(c1);
        when(studentCommentRepository.findByClassSessionIdAndStudentIdIn(98L, List.of(1L))).thenReturn(c2);
        when(studentCommentRepository.findByClassSessionIdAndStudentIdIn(97L, List.of(1L))).thenReturn(c3);
        // Ngữ pháp: 90% ← 70% ← 50% (mới nhất trước) — BTVN buổi trước làm lại: lượt 1 = 6, lượt 2 = 9.
        ExerciseAttempt latest = attempt(1001L, e1, 2, "9");
        List<ExerciseAttempt> attempts = new ArrayList<>(List.of(attempt(1000L, e1, 1, "6"), latest,
                attempt(1002L, e2, 1, "7"), attempt(1003L, e3, 1, "5")));
        when(exerciseAttemptRepository.findByExerciseIdInAndStudentIdIn(any(), eq(List.of(1L)))).thenReturn(attempts);
        // Lượt mới nhất: điền từ đúng 1/3 (yếu) → chỉ nêu điểm yếu này.
        List<StudentAnswer> answers = List.of(
                answer(latest, Question.QuestionType.FILL_IN_BLANK, true),
                answer(latest, Question.QuestionType.FILL_IN_BLANK, false),
                answer(latest, Question.QuestionType.FILL_IN_BLANK, false));
        when(studentAnswerRepository.findWithQuestionByExerciseAttemptIdIn(any())).thenReturn(answers);

        Map<Long, List<String>> result = service.describe(current, List.of(1L), Map.of());

        assertThat(result.get(1L)).containsExactly(
                "điểm cần cải thiện cụ thể: dạng câu điền từ trong bài ngữ pháp",
                "kỹ năng ngữ pháp tiến bộ đều qua các buổi gần đây",
                "chăm làm lại bài để cải thiện điểm");
        verify(studentAnswerGradingRepository, never()).findByStudentAnswerIdInAndLatestIsTrue(any());
    }

    @Test
    void describe_UC74_tableScoreOfLatestRoundOverridesSavedCommentAndNotDoneTwiceIsReminded() {
        when(student.getId()).thenReturn(1L);
        ClassSession current = session(100L);
        ClassSession s1 = session(99L);
        ClassSession s2 = session(98L);
        when(classSessionRepository.findSessionsBeforeWithTeacherTypeOrderedDesc(eq(5L), eq(DATE), eq(100L),
                eq(ClassSession.TeacherType.VIETNAMESE), anyList())).thenReturn(List.of(s1, s2));
        StudentComment s1Comment = comment(s1, null);
        when(s1Comment.getHomeworkPreviousWritingScore()).thenReturn("Chưa làm bài");
        StudentComment s2Comment = comment(s2, null);
        when(studentCommentRepository.findByClassSessionIdAndStudentIdIn(100L, List.of(1L))).thenReturn(List.of());
        when(studentCommentRepository.findByClassSessionIdAndStudentIdIn(99L, List.of(1L))).thenReturn(List.of(s1Comment));
        when(studentCommentRepository.findByClassSessionIdAndStudentIdIn(98L, List.of(1L))).thenReturn(List.of(s2Comment));

        Map<Long, List<String>> result = service.describe(current, List.of(1L),
                Map.of(1L, new HomeworkScoreInput(1L, null, null, null, "chưa làm")));

        assertThat(result.get(1L)).containsExactly("chưa làm BTVN kỹ năng viết hai lần liền (nhắc nhẹ hoàn thành bài)");
        verify(exerciseAttemptRepository, never()).findByExerciseIdInAndStudentIdIn(any(), any());
    }

    @Test
    void describe_UC74_noPreviousSessionReturnsEmpty() {
        ClassSession current = session(100L);
        when(classSessionRepository.findSessionsBeforeWithTeacherTypeOrderedDesc(any(), any(), any(), any(), anyList())).thenReturn(List.of());

        assertThat(service.describe(current, List.of(1L), Map.of())).isEmpty();
        verify(studentCommentRepository, never()).findByClassSessionIdAndStudentIdIn(any(), any());
    }
}
