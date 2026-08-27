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

    /**
     * V140 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — Khối lớp, dùng để AI chấm
     * Speaking/Writing (Mục 1 Video phản xạ + Mục 2 Bài Writing) chọn đúng bảng rubric theo Khối do
     * giáo viên cung cấp riêng cho từng Khối — xem RubricByGradeTrackLoader.
     */
    public enum GradeLevel { GRADE_6, GRADE_7, GRADE_8, GRADE_9 }

    /** V140 — chương trình học (IELTS/CAMBRIDGE), cùng mục đích với {@link GradeLevel}. Khối 6 dùng chung 1 rubric cho cả 2 track (giáo viên xác nhận), track vẫn có thể để trống với Khối 6. */
    public enum Track { IELTS, CAMBRIDGE }

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

    /** V140 — NULL = chưa phân loại (dữ liệu cũ trước V140). */
    @Enumerated(EnumType.STRING)
    @Column(name = "grade_level", length = 10)
    private GradeLevel gradeLevel;

    /** V140 — NULL = chưa phân loại; Khối 6 dùng chung rubric nên track có thể để trống. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Track track;

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
