package vn.com.pps.education.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.academic.domain.TeachingPlanHistory;

public interface TeachingPlanHistoryRepository extends JpaRepository<TeachingPlanHistory, Long> {
}
