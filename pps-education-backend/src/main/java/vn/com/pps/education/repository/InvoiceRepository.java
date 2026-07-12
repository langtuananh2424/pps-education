package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Invoice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByIdAndDeletedAtIsNull(Long id);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    long countByInvoiceNumberStartingWith(String prefix);

    List<Invoice> findByStudentIdAndDeletedAtIsNullOrderByIssueDateDesc(Long studentId);

    List<Invoice> findByStatusInAndDueDateBeforeAndDeletedAtIsNull(List<Invoice.Status> statuses, LocalDate before);

    boolean existsByClassEnrollmentIdAndBillingPeriodFromAndDeletedAtIsNull(
            Long classEnrollmentId, LocalDate billingPeriodFrom);

    @org.springframework.data.jpa.repository.Query("""
            SELECT i FROM Invoice i JOIN i.classEnrollment ce JOIN ce.schoolClass c
            WHERE c.site.id = :siteId AND i.deletedAt IS NULL
            AND i.issueDate BETWEEN :from AND :to
            """)
    List<Invoice> findBySiteIdAndIssueDateBetween(@org.springframework.data.repository.query.Param("siteId") Long siteId,
                                                    @org.springframework.data.repository.query.Param("from") LocalDate from,
                                                    @org.springframework.data.repository.query.Param("to") LocalDate to);
}
