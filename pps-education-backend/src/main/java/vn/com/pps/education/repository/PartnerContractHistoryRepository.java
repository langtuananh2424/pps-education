package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.PartnerContractHistory;

public interface PartnerContractHistoryRepository extends JpaRepository<PartnerContractHistory, Long> {
}
