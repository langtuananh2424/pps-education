package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradeComponentHistory;

public interface GradeComponentHistoryRepository extends JpaRepository<GradeComponentHistory, Long> {
}
