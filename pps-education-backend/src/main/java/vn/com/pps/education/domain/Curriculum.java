package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng curriculums (SDD > Học thuật > Khung chương trình & Lớp học > a).
 * UC-16 (khung chuẩn, site=NULL) + UC-16b (bản tùy biến theo điểm trường,
 * site NOT NULL — CHƯA code Service cho nhánh này trong phạm vi hiện tại).
 */
@Getter
@Setter
@Entity
@Table(name = "curriculums")
public class Curriculum extends BaseAuditEntity {

    public enum ClassCategory { MAIN, SUPPLEMENTARY, EXAM_PREP, OTHER }

    public enum Status { DRAFT, PENDING_APPROVAL, ACTIVE, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 300)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_curriculum_id")
    private Curriculum parentCurriculum;

    @Enumerated(EnumType.STRING)
    @Column(name = "class_category", nullable = false, length = 30)
    private ClassCategory classCategory;

    @Column(length = 50)
    private String level;

    @Column(name = "total_periods")
    private Integer totalPeriods;

    @Column(name = "default_grade_pass_threshold", precision = 4, scale = 2)
    private BigDecimal defaultGradePassThreshold = new BigDecimal("5.0");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
