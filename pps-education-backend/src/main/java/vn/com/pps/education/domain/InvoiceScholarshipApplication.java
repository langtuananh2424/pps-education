package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Bảng invoice_scholarship_applications — áp học bổng vào hóa đơn (UC-30 Main Flow bước 1, A3). */
@Getter
@Setter
@Entity
@Table(name = "invoice_scholarship_applications")
public class InvoiceScholarshipApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scholarship_id", nullable = false)
    private Scholarship scholarship;

    /** Đã snapshot tại thời điểm áp dụng, không tính lại (SDD). */
    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applied_by", nullable = false)
    private User appliedBy;
}
