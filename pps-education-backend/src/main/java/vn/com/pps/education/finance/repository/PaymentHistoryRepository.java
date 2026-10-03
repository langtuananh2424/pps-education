package vn.com.pps.education.finance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.finance.domain.PaymentHistory;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {
}
