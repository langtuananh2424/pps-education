package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Bảng academic_terms (UC-18, bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng 2026-07-31) — "Giai đoạn/Học kỳ", giới hạn theo điểm trường (site),
 * độc lập với lớp học (1 lớp tồn tại xuyên suốt nhiều kỳ). Hồ sơ lớp/học
 * sinh theo kỳ là dữ liệu TÍNH RA (derived) từ các bảng đã có ngày tháng
 * sẵn (class_enrollments, class_teachers, class_sessions,
 * student_comments...) lọc theo [startDate, endDate] của kỳ.
 */
@Getter
@Setter
@Entity
@Table(name = "academic_terms")
public class AcademicTerm extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    /**
     * Năm học chứa kỳ học này (V157, bổ sung ngoài SDD gốc, đã xác nhận
     * với người dùng 2026-08-28). Nullable ở DB cho dữ liệu kỳ cũ; kỳ tạo
     * mới bắt buộc có (validate ở {@link vn.com.pps.education.dto.CreateAcademicTermRequest}).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYear;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
