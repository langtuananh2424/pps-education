package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.InvoiceHistory;

import java.util.List;

public interface InvoiceHistoryRepository extends JpaRepository<InvoiceHistory, Long> {

    List<InvoiceHistory> findByInvoiceIdOrderByCreatedAtAsc(Long invoiceId);
}
