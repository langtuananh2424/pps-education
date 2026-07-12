package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng invoices (SDD > Tài chính & Học phí > Hóa đơn & Thanh toán). UC-30:
 * Xem hóa đơn & thanh toán học phí (FR-FIN-01, FR-FIN-02). outstanding_amount
 * là cột GENERATED ALWAYS trong DB (V25) — không map ở entity, tính lại ở
 * DTO response (total_amount - paid_amount) để tránh vấn đề Hibernate đọc
 * cột generated ngay sau INSERT trong cùng transaction.
 */
@Getter
@Setter
@Entity
@Table(name = "invoices")
public class Invoice {

    public enum Status { DRAFT, ISSUED, PARTIAL_PAID, PAID, OVERDUE, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_enrollment_id")
    private ClassEnrollment classEnrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_parent_id")
    private Parent payerParent;

    @Column(name = "billing_period_from")
    private LocalDate billingPeriodFrom;

    @Column(name = "billing_period_to")
    private LocalDate billingPeriodTo;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ISSUED;

    @Column(name = "qr_code_data", columnDefinition = "TEXT")
    private String qrCodeData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public BigDecimal getOutstandingAmount() {
        return totalAmount.subtract(paidAmount);
    }
}
