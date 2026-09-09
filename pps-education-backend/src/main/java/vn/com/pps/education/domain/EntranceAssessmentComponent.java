package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Bảng entrance_assessment_components (UC-18c, bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng 2026-08-28) — 1 "đầu điểm" / kỹ năng trong bộ đề
 * đánh giá đầu vào (mirror {@link GradeEvaluationComponent}).
 */
@Getter
@Setter
@Entity
@Table(name = "entrance_assessment_components")
public class EntranceAssessmentComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "setup_id", nullable = false)
    private EntranceAssessmentSetup setup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore = new BigDecimal("10.00");

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
