package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng tuition_plans (SDD > Tài chính & Học phí > Định mức học phí).
 * Không có UC nào mô tả riêng luồng tạo/sửa định mức — là hạ tầng bắt
 * buộc để UC-30 (sinh hóa đơn) hoạt động được, xem
 * {@link vn.com.pps.education.service.TuitionPlanService}.
 */
@Getter
@Setter
@Entity
@Table(name = "tuition_plans")
public class TuitionPlan {

    public enum PricingModel { COURSE, PER_SESSION, MONTHLY }

    public enum ClassTypeFilter { LINKED, OPEN }

    public enum Status { ACTIVE, INACTIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 300)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_model", nullable = false, length = 20)
    private PricingModel pricingModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "class_type_filter", length = 20)
    private ClassTypeFilter classTypeFilter;

    @Column(name = "base_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "price_per_unit", precision = 15, scale = 2)
    private BigDecimal pricePerUnit;

    @Column(name = "unit_count")
    private Integer unitCount;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
