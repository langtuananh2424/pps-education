package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.InvoiceScholarshipApplication;

import java.util.List;

public interface InvoiceScholarshipApplicationRepository extends JpaRepository<InvoiceScholarshipApplication, Long> {

    List<InvoiceScholarshipApplication> findByInvoiceId(Long invoiceId);
}
