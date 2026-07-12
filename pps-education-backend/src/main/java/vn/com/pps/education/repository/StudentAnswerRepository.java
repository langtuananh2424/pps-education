package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.StudentAnswer;

import java.util.List;
import java.util.Optional;

public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, Long> {
    /** UC-40 SDD: câu hỏi đã có student_answers thì cấm sửa content/đáp án đúng. */
    boolean existsByQuestionId(Long questionId);

    List<StudentAnswer> findByExerciseAttemptId(Long exerciseAttemptId);

    Optional<StudentAnswer> findByExerciseAttemptIdAndQuestionId(Long exerciseAttemptId, Long questionId);
}
