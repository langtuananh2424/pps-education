package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.LeadHistory;

public interface LeadHistoryRepository extends JpaRepository<LeadHistory, Long> {
}
