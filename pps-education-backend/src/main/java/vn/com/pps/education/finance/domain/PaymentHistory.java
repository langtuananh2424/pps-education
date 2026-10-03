package vn.com.pps.education.finance.domain;

import vn.com.pps.education.auth.domain.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Bảng payments_history (SDD "Có payments_history", không định nghĩa cột)
 * — pattern JSONB diff-log giống {@link InvoiceHistory}.
 */
@Getter
@Setter
@Entity
@Table(name = "payments_history")
public class PaymentHistory {

    public enum Action { CREATED, UPDATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    /** NULL = thay đổi do hệ thống tự động (cron, webhook ngân hàng) — V210. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Action action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
