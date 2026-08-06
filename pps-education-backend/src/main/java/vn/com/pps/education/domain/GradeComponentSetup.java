package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng grade_component_setups (SDD > Học thuật > Sổ điểm & Điểm tổng kết
 * > a, V95 — bổ sung ngoài SDD gốc, đã xác nhận với người dùng, thay thế
 * grade_periods) — cấu hình sổ điểm gắn TRỰC TIẾP (lớp, kỳ học, Giữa/Cuối
 * kỳ) thay vì theo curriculum dùng chung nhiều lớp như trước. Mỗi lớp tự
 * quyết định đánh giá Giữa kỳ/Cuối kỳ/cả hai qua từng setup riêng (VD lớp
 * bổ trợ chỉ tạo 1 setup END_TERM, không tạo setup MID_TERM).
 */
@Getter
@Setter
@Entity
@Table(name = "grade_component_setups")
public class GradeComponentSetup {

    public enum EvaluationType { MID_TERM, END_TERM }

    /** V97: thang điểm áp dụng cho toàn bộ thành phần điểm trong setup — POINT_10 max=10, PERCENT max=100, IELTS max=9 (band, cho phép lẻ). */
    public enum ScaleType { POINT_10, PERCENT, IELTS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_term_id", nullable = false)
    private AcademicTerm academicTerm;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_type", nullable = false, length = 20)
    private EvaluationType evaluationType;

    /** V97 (thay weight_in_final — hệ thống không tính OVERALL cả kỳ, hiển thị riêng Giữa/Cuối kỳ): thang điểm, ràng buộc maxScore của mọi component trong setup này. */
    @Enumerated(EnumType.STRING)
    @Column(name = "scale_type", nullable = false, length = 20)
    private ScaleType scaleType = ScaleType.POINT_10;

    /** Ngày chốt danh sách học sinh — class_enrollments ACTIVE tại đúng ngày này (V95, xác nhận với người dùng). */
    @Column(name = "roster_as_of_date", nullable = false)
    private LocalDate rosterAsOfDate;

    @Column(name = "comment_required", nullable = false)
    private boolean commentRequired = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
