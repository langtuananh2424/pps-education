package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng grade_period_results (SDD > Học thuật > Sổ điểm & Điểm tổng kết >
 * h, V37 — bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — Overall/
 * Level đã quy đổi sẵn của 1 học sinh cho 1 kỳ đánh giá (UC-53). Hệ
 * thống KHÔNG tự tính lại theo công thức — lưu nguyên giá trị GV đã
 * tính (band IELTS / % / thang riêng). Khác grade_final_summaries (tổng
 * kết toàn học phần, hệ thống tự tính — chưa triển khai).
 *
 * V39: cùng luồng công bố với grade_entries — chỉ còn DRAFT/PUBLISHED,
 * sửa được bất kể status miễn còn trong hạn X ngày hoặc có quyền
 * academic.grade.edit.override.
 */
@Getter
@Setter
@Entity
@Table(name = "grade_period_results")
public class GradePeriodResult {

    public enum ScaleType { NUMERIC, PERCENTAGE, BAND }

    public enum Source { MANUAL, EXCEL_IMPORT }

    public enum Status { DRAFT, PUBLISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_period_id", nullable = false)
    private GradePeriod gradePeriod;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "scale_type", nullable = false, length = 20)
    private ScaleType scaleType = ScaleType.NUMERIC;

    @Column(length = 100)
    private String level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source = Source.MANUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_job_id")
    private ImportJob importJob;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entered_by", nullable = false)
    private User enteredBy;

    @Column(name = "entered_at", nullable = false)
    private OffsetDateTime enteredAt = OffsetDateTime.now();

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private User publishedBy;
}
