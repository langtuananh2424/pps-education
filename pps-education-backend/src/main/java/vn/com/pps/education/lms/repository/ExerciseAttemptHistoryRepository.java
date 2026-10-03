package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.lms.domain.ExerciseAttemptHistory;

public interface ExerciseAttemptHistoryRepository extends JpaRepository<ExerciseAttemptHistory, Long> {
}
