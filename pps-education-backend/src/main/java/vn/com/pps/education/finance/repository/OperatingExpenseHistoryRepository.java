package vn.com.pps.education.finance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.finance.domain.OperatingExpenseHistory;

public interface OperatingExpenseHistoryRepository extends JpaRepository<OperatingExpenseHistory, Long> {
}
