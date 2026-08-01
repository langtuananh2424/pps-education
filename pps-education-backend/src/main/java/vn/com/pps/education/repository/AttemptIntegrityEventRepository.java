package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.AttemptIntegrityEvent;

import java.util.List;

public interface AttemptIntegrityEventRepository extends JpaRepository<AttemptIntegrityEvent, Long> {
    List<AttemptIntegrityEvent> findByAttemptTypeAndAttemptId(AttemptIntegrityEvent.AttemptType attemptType, Long attemptId);

    boolean existsByAttemptTypeAndAttemptIdAndNotifiedAtIsNotNull(AttemptIntegrityEvent.AttemptType attemptType, Long attemptId);
}
