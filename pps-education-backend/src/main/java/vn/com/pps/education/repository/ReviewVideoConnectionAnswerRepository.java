package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoConnectionAnswer;

import java.util.List;

public interface ReviewVideoConnectionAnswerRepository extends JpaRepository<ReviewVideoConnectionAnswer, Long> {

    List<ReviewVideoConnectionAnswer> findByWatchSessionId(Long watchSessionId);
}
