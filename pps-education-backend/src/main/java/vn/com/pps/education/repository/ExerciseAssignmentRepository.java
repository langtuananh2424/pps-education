package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ExerciseAssignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExerciseAssignmentRepository extends JpaRepository<ExerciseAssignment, Long> {
    List<ExerciseAssignment> findBySchoolClassIdAndStatus(Long classId, ExerciseAssignment.Status status);

    List<ExerciseAssignment> findByExerciseIdAndSchoolClassIdAndStatus(
            Long exerciseId, Long classId, ExerciseAssignment.Status status);

    /** UC-21 mở rộng (BTVN online — dán uuid làm phương án thay dropdown, V55). */
    Optional<ExerciseAssignment> findByUuid(UUID uuid);
}
