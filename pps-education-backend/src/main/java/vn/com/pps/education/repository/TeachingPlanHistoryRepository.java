package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.TeachingPlanHistory;

public interface TeachingPlanHistoryRepository extends JpaRepository<TeachingPlanHistory, Long> {
}
