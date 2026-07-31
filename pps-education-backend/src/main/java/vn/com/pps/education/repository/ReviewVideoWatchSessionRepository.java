package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoWatchSession;

public interface ReviewVideoWatchSessionRepository extends JpaRepository<ReviewVideoWatchSession, Long> {

    int countByReviewVideoIdAndStudentIdAndQualifiedTrue(Long reviewVideoId, Long studentId);
}
