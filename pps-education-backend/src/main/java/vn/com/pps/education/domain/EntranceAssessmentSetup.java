package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng entrance_assessment_setups (UC-18c, bổ sung ngoài SDD gốc, đã xác
 * nhận với người dùng 2026-08-28) — bộ đề "Đánh giá đầu vào" (kỳ thi đầu
 * vào làm cơ sở xếp lớp). Cấu trúc giống {@link GradeComponentSetup} (tự
 * setup kỹ năng + điểm) nhưng KHÔNG neo vào lớp/kỳ học vì thí sinh chưa
 * được xếp lớp — giới hạn theo điểm trường ({@link Site}) + năm học
 * ({@link AcademicYear}). Không có quy trình duyệt.
 */
@Getter
@Setter
@Entity
@Table(name = "entrance_assessment_setups")
public class EntranceAssessmentSetup extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(nullable = false, length = 200)
    private String name;

    /** Thang điểm áp dụng cho toàn bộ đầu điểm trong bộ đề — tái dùng enum của sổ điểm. */
    @Enumerated(EnumType.STRING)
    @Column(name = "scale_type", nullable = false, length = 20)
    private GradeComponentSetup.ScaleType scaleType = GradeComponentSetup.ScaleType.POINT_10;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
