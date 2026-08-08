package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ListeningPlayProgress;

import java.util.Optional;

public interface ListeningPlayProgressRepository extends JpaRepository<ListeningPlayProgress, Long> {
    Optional<ListeningPlayProgress> findByExerciseAttemptIdAndListeningKey(Long exerciseAttemptId, String listeningKey);
}
