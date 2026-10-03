package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Invoice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByIdAndDeletedAtIsNull(Long id);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * UC-30 webhook ngân hàng — khoá dòng hóa đơn (SELECT ... FOR UPDATE) trước khi cộng paid_amount,
     * tránh 2 webhook đồng thời cùng đọc paid_amount cũ rồi ghi đè lẫn nhau (lost update).
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT i FROM Invoice i WHERE i.invoiceNumber = :invoiceNumber")
    Optional<Invoice> findForUpdateByInvoiceNumber(
            @org.springframework.data.repository.query.Param("invoiceNumber") String invoiceNumber);

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

    /**
     * Màn Thu phí & hóa đơn phía Kế toán (bổ sung 2026-10-03): lọc theo kỳ phát hành (bắt buộc, giới hạn
     * số dòng), trạng thái/điểm trường/lớp tuỳ chọn. Hóa đơn không gắn ghi danh lớp (classEnrollment NULL)
     * chỉ hiện khi không lọc theo điểm trường/lớp. Tìm theo từ khoá làm ở Service (tránh lỗi Postgres không
     * suy được kiểu tham số NULL trong LOWER/CONCAT).
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT i FROM Invoice i LEFT JOIN i.classEnrollment ce LEFT JOIN ce.schoolClass c
            WHERE i.deletedAt IS NULL
            AND i.issueDate BETWEEN :from AND :to
            AND (:status IS NULL OR i.status = :status)
            AND (:siteId IS NULL OR c.site.id = :siteId)
            AND (:classId IS NULL OR c.id = :classId)
            ORDER BY i.issueDate DESC, i.id DESC
            """)
    List<Invoice> searchForStaff(@org.springframework.data.repository.query.Param("from") LocalDate from,
                                  @org.springframework.data.repository.query.Param("to") LocalDate to,
                                  @org.springframework.data.repository.query.Param("status") Invoice.Status status,
                                  @org.springframework.data.repository.query.Param("siteId") Long siteId,
                                  @org.springframework.data.repository.query.Param("classId") Long classId);
}
