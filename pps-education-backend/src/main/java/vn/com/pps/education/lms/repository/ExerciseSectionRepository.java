package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.lms.domain.ExerciseSection;

import java.util.List;

public interface ExerciseSectionRepository extends JpaRepository<ExerciseSection, Long> {
    List<ExerciseSection> findByExerciseIdOrderByDisplayOrder(Long exerciseId);
    long countByExerciseId(Long exerciseId);
}
