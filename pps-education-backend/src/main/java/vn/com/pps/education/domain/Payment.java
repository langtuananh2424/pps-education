package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng payments (SDD > Tài chính & Học phí > Hóa đơn & Thanh toán). UC-30
 * Main Flow bước 4-7: 1 hóa đơn có thể có nhiều payment (thanh toán từng
 * phần → PARTIAL_PAID).
 */
@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment {

    public enum PaymentMethod { QR_BANK, CASH, BANK_TRANSFER, OTHER }

    public enum Status { PENDING, CONFIRMED, REFUNDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(name = "payment_reference", nullable = false, unique = true, length = 100)
    private String paymentReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "paid_at", nullable = false)
    private OffsetDateTime paidAt;

    @Column(name = "bank_transaction_id", length = 200)
    private String bankTransactionId;

    @Column(name = "receipt_number", length = 50)
    private String receiptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.CONFIRMED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;
}
