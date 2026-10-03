package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.lms.domain.ListeningPracticeGrading;

import java.util.Optional;

public interface ListeningPracticeGradingRepository extends JpaRepository<ListeningPracticeGrading, Long> {

    Optional<ListeningPracticeGrading> findByPracticeAttemptId(Long practiceAttemptId);
}
