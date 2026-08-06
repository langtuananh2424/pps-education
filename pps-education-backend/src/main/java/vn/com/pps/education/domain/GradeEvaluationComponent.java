package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Bảng grade_evaluation_components (SDD > Học thuật > Sổ điểm & Điểm tổng
 * kết > b, V95 — thay thế grade_components) — thành phần điểm trong 1
 * {@link GradeComponentSetup} (lớp + kỳ học + Giữa/Cuối kỳ). Code riêng
 * (không dùng lại CurriculumSubject.SubjectCode) vì danh sách khác nhau —
 * có thêm PROJECT mà curriculum_subjects không có.
 */
@Getter
@Setter
@Entity
@Table(name = "grade_evaluation_components")
public class GradeEvaluationComponent {

    public enum ComponentCode { SPEAKING, WRITING, LISTENING, READING, GRAMMAR, PROJECT, OTHER }

    /** Chỉ phục vụ hiển thị đúng định dạng ở FE; max_score vẫn là cận trên validate. */
    public enum ScaleType { NUMERIC, PERCENTAGE, BAND }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_component_setup_id", nullable = false)
    private GradeComponentSetup gradeComponentSetup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private CurriculumSubject subject;

    /** Tham chiếu danh mục kỹ năng (UC-54), dùng khi code=OTHER cần kỹ năng ngoài enum gốc. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ComponentCode code;

    @Column(nullable = false, length = 200)
    private String name;

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
