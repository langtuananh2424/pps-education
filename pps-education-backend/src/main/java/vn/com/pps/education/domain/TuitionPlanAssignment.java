package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Bảng tuition_plan_assignments (SDD > Tài chính & Học phí > Định mức học
 * phí) — gán 1 tuition_plan cho 1 lớp cụ thể, cho phép override giá. Mỗi
 * lớp tại 1 thời điểm chỉ có 1 assignment active (effective_to NULL —
 * unique partial index, xem V25).
 */
@Getter
@Setter
@Entity
@Table(name = "tuition_plan_assignments")
public class TuitionPlanAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tuition_plan_id", nullable = false)
    private TuitionPlan tuitionPlan;

    @Column(name = "price_override", precision = 15, scale = 2)
    private BigDecimal priceOverride;

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
