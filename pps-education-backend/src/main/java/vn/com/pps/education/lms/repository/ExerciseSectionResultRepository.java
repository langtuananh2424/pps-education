package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.lms.domain.ExerciseSectionResult;

import java.util.List;
import java.util.Optional;

public interface ExerciseSectionResultRepository extends JpaRepository<ExerciseSectionResult, Long> {
    List<ExerciseSectionResult> findByExerciseAssignmentIdAndStudentId(Long exerciseAssignmentId, Long studentId);
    Optional<ExerciseSectionResult> findByExerciseAssignmentIdAndStudentIdAndExerciseSectionId(
            Long exerciseAssignmentId, Long studentId, Long exerciseSectionId);
}
