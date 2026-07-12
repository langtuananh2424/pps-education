package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.OperatingExpenseHistory;

public interface OperatingExpenseHistoryRepository extends JpaRepository<OperatingExpenseHistory, Long> {
}
