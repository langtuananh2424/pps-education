package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng grade_evaluation_results (SDD > Học thuật > Sổ điểm & Điểm tổng
 * kết > h, V95 — đổi tên từ grade_period_results, thay grade_period_id
 * bằng academic_term_id + evaluation_type) — Overall/Level đã quy đổi
 * sẵn của 1 học sinh cho 1 (kỳ học, Giữa/Cuối kỳ) cụ thể (UC-53). Hệ
 * thống KHÔNG tự tính lại theo công thức — lưu nguyên giá trị GV đã tính
 * (band IELTS / % / thang riêng). Khác grade_final_summaries (tổng kết
 * toàn học phần, hệ thống tự tính — chưa triển khai).
 *
 * V95 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): thêm
 * {@code comment} ("Nhận xét") + {@code note} ("Ghi chú") — tích hợp
 * nhận xét kỳ trực tiếp vào sổ điểm, hiển thị cho Phụ huynh cùng
 * Overall/Level khi {@code status=OFFICIAL}. Quản lý điểm trường được
 * sửa 2 field này (không sửa điểm) ngay tại bước duyệt
 * ({@link vn.com.pps.education.service.GradeService#publishGrades}).
 *
 * Cùng luồng 4 trạng thái với {@link GradeEntry} — DRAFT → SUBMITTED →
 * OFFICIAL / REJECTED, khoá sửa theo TRẠNG THÁI — xem Javadoc
 * {@link GradeEntry} để biết chi tiết từng trạng thái, áp dụng y hệt ở
 * đây.
 */
@Getter
@Setter
@Entity
@Table(name = "grade_evaluation_results")
public class GradeEvaluationResult {

    public enum ScaleType { NUMERIC, PERCENTAGE, BAND }

    public enum Source { MANUAL, EXCEL_IMPORT }

    public enum Status { DRAFT, SUBMITTED, OFFICIAL, REJECTED }

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
    @JoinColumn(name = "academic_term_id", nullable = false)
    private AcademicTerm academicTerm;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_type", nullable = false, length = 20)
    private GradeComponentSetup.EvaluationType evaluationType;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "scale_type", nullable = false, length = 20)
    private ScaleType scaleType = ScaleType.NUMERIC;

    /** VD "B1", "B2", "Band 6.5" — tự do, GV nhập/import (level tiếng Anh của học sinh, khác "mức độ" chung chung). */
    @Column(length = 100)
    private String level;

    /** "Nhận xét" — nội dung chất lượng học tập, hiển thị PH khi OFFICIAL (V95). */
    @Column(columnDefinition = "TEXT")
    private String comment;

    /** "Ghi chú" — thông tin phụ (VD "vắng buổi kiểm tra"), hiển thị PH khi OFFICIAL (V95). */
    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source = Source.MANUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_job_id")
    private ImportJob importJob;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
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

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;
}
