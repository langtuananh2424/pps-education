package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Bảng entrance_assessment_scores (UC-18c, bổ sung ngoài SDD gốc, đã xác
 * nhận với người dùng 2026-08-28) — điểm của 1 thí sinh cho 1 đầu điểm
 * trong bộ đề đánh giá đầu vào.
 */
@Getter
@Setter
@Entity
@Table(name = "entrance_assessment_scores")
public class EntranceAssessmentScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false)
    private EntranceAssessmentResult result;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_id", nullable = false)
    private EntranceAssessmentComponent component;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "absence_flag", nullable = false)
    private boolean absenceFlag = false;
}
