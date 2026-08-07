package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Bảng academic_years (V102, bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng 2026-08-07) — danh mục "Năm học" DÙNG CHUNG TOÀN HỆ THỐNG (khác
 * {@link AcademicTerm} — Kỳ học, giới hạn theo điểm trường). Là nguồn FK
 * cho {@code classes}/{@code grade_entries}/{@code student_comments}/
 * {@code class_enrollments}/{@code teaching_plans}.academic_year_id, thay
 * cho chuỗi tự do trước đây.
 */
@Getter
@Setter
@Entity
@Table(name = "academic_years")
public class AcademicYear extends BaseAuditEntity {

    public enum Status { PLANNED, ACTIVE, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PLANNED;

    /** Nullable — dữ liệu backfill từ chuỗi cũ (V102) không có actor thật. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
