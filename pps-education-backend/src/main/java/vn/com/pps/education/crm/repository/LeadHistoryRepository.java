package vn.com.pps.education.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.crm.domain.LeadHistory;

public interface LeadHistoryRepository extends JpaRepository<LeadHistory, Long> {
}
