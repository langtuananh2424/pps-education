package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Bảng invoice_payment_links (V211): link/QR thanh toán payOS cho phần còn nợ của 1 hóa đơn (UC-30
 * Main Flow bước 3-6). order_code là khoá đối chiếu khi payOS gọi webhook.
 */
@Getter
@Setter
@Entity
@Table(name = "invoice_payment_links")
public class InvoicePaymentLink extends BaseAuditEntity {

    public enum Status { PENDING, PAID, CANCELLED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false, length = 20)
    private String provider = "PAYOS";

    @Column(name = "order_code", nullable = false)
    private Long orderCode;

    @Column(name = "payment_link_id", length = 100)
    private String paymentLinkId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(length = 20)
    private String bin;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "account_name", length = 200)
    private String accountName;

    @Column(length = 50)
    private String description;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;
}
