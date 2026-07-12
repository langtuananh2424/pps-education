package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ExerciseAttemptHistory;

public interface ExerciseAttemptHistoryRepository extends JpaRepository<ExerciseAttemptHistory, Long> {
}
