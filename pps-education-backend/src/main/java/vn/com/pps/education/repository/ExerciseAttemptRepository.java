package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ExerciseAttempt;

public interface ExerciseAttemptRepository extends JpaRepository<ExerciseAttempt, Long> {
}
