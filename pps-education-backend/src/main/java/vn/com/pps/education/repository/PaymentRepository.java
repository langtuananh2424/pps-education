package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Payment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentReference(String paymentReference);

    long countByPaymentReferenceStartingWith(String prefix);

    List<Payment> findByInvoiceId(Long invoiceId);

    @org.springframework.data.jpa.repository.Query("""
            SELECT p FROM Payment p JOIN p.invoice i JOIN i.classEnrollment ce JOIN ce.schoolClass c
            WHERE c.site.id = :siteId AND p.status = :status
            AND p.paidAt BETWEEN :from AND :to
            """)
    List<Payment> findBySiteIdAndStatusAndPaidAtBetween(
            @org.springframework.data.repository.query.Param("siteId") Long siteId,
            @org.springframework.data.repository.query.Param("status") Payment.Status status,
            @org.springframework.data.repository.query.Param("from") OffsetDateTime from,
            @org.springframework.data.repository.query.Param("to") OffsetDateTime to);
}
