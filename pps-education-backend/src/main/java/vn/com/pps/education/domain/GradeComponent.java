package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Bảng grade_components (SDD > Học thuật > Sổ điểm & Điểm tổng kết > b)
 * — thành phần điểm trong 1 kỳ đánh giá. Code riêng (không dùng lại
 * CurriculumSubject.SubjectCode) vì danh sách khác nhau — grade_components
 * có thêm PROJECT mà curriculum_subjects không có (đúng theo SDD).
 */
@Getter
@Setter
@Entity
@Table(name = "grade_components")
public class GradeComponent {

    public enum ComponentCode { SPEAKING, WRITING, LISTENING, READING, GRAMMAR, PROJECT, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_period_id", nullable = false)
    private GradePeriod gradePeriod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private CurriculumSubject subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ComponentCode code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "weight_in_period", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightInPeriod;

    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore = new BigDecimal("10.00");

    @Column(name = "pass_threshold", precision = 5, scale = 2)
    private BigDecimal passThreshold;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
