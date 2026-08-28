package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Bảng entrance_assessment_results (UC-18c, bổ sung ngoài SDD gốc, đã xác
 * nhận với người dùng 2026-08-28) — 1 dòng = kết quả đánh giá đầu vào của
 * 1 thí sinh trong 1 bộ đề. Đối tượng chấm là {@link Lead} HOẶC
 * {@link Student} (dùng đúng 1 trong 2 — CHECK ở DB). Lưu thêm trình độ
 * đề xuất + lớp đề xuất; {@code placedFlag} đánh dấu đã chuyển sang xếp
 * lớp (UC-18). Không có quy trình duyệt — nhập trực tiếp.
 */
@Getter
@Setter
@Entity
@Table(name = "entrance_assessment_results")
public class EntranceAssessmentResult extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "setup_id", nullable = false)
    private EntranceAssessmentSetup setup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(name = "candidate_name", nullable = false, length = 200)
    private String candidateName;

    @Column(name = "assessed_date", nullable = false)
    private LocalDate assessedDate;

    /** Nhập tay — hệ thống KHÔNG tự tính từ điểm đầu điểm (mirror sổ điểm). */
    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "recommended_level", length = 100)
    private String recommendedLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommended_class_id")
    private SchoolClass recommendedClass;

    @Column(name = "placed_flag", nullable = false)
    private boolean placedFlag = false;

    @Column(columnDefinition = "TEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entered_by", nullable = false)
    private User enteredBy;
}
