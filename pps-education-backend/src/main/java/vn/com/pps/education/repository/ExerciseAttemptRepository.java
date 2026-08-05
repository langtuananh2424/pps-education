package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ExerciseAttempt;

import java.util.List;

public interface ExerciseAttemptRepository extends JpaRepository<ExerciseAttempt, Long> {
    long countByExerciseIdAndStudentId(Long exerciseId, Long studentId);

    List<ExerciseAttempt> findByExerciseIdAndStudentIdOrderByAttemptNumberDesc(Long exerciseId, Long studentId);

    List<ExerciseAttempt> findByExerciseAssignmentIdOrderByAttemptNumberDesc(Long exerciseAssignmentId);
}
