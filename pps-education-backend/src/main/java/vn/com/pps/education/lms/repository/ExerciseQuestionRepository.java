package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.lms.domain.ExerciseQuestion;

import java.util.List;

public interface ExerciseQuestionRepository extends JpaRepository<ExerciseQuestion, Long> {
    List<ExerciseQuestion> findByExerciseIdOrderByDisplayOrder(Long exerciseId);
    boolean existsByExerciseIdAndQuestionId(Long exerciseId, Long questionId);
    long countByExerciseId(Long exerciseId);
}
