package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.InvoiceHistory;

public interface InvoiceHistoryRepository extends JpaRepository<InvoiceHistory, Long> {
}
