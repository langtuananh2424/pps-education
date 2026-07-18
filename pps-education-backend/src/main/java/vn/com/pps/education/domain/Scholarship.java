package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng scholarships (SDD > Tài chính & Học phí > Học bổng/Miễn giảm),
 * dùng ở UC-30 Main Flow bước 1 (A3 — áp dụng nếu học sinh có scholarship
 * active). Không có UC riêng mô tả luồng cấp/duyệt — xem
 * {@link vn.com.pps.education.service.ScholarshipService}. Không history —
 * thay đổi thì REVOKE record cũ, tạo record mới (SDD).
 */
@Getter
@Setter
@Entity
@Table(name = "scholarships")
public class Scholarship {

    public enum DiscountType { PERCENTAGE, FIXED_AMOUNT }

    public enum ApplicableScope { PER_INVOICE, ONE_TIME }

    public enum Status { ACTIVE, EXPIRED, REVOKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 300)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_scope", nullable = false, length = 20)
    private ApplicableScope applicableScope = ApplicableScope.PER_INVOICE;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approved_by", nullable = false)
    private User approvedBy;

    @Column(name = "approved_at", nullable = false)
    private OffsetDateTime approvedAt = OffsetDateTime.now();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
