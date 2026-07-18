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

    /** V37 — chỉ phục vụ hiển thị đúng định dạng ở FE; max_score vẫn là cận trên validate. */
    public enum ScaleType { NUMERIC, PERCENTAGE, BAND }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_period_id", nullable = false)
    private GradePeriod gradePeriod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private CurriculumSubject subject;

    /** V37 — tham chiếu danh mục kỹ năng (UC-54), dùng khi code=OTHER cần kỹ năng ngoài enum gốc. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id")
    private Skill skill;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "scale_type", nullable = false, length = 20)
    private ScaleType scaleType = ScaleType.NUMERIC;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
