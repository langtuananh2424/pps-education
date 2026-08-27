package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ExerciseQuestion;

import java.util.List;

public interface ExerciseQuestionRepository extends JpaRepository<ExerciseQuestion, Long> {
    List<ExerciseQuestion> findByExerciseIdOrderByDisplayOrder(Long exerciseId);
    boolean existsByExerciseIdAndQuestionId(Long exerciseId, Long questionId);
    long countByExerciseId(Long exerciseId);
}
