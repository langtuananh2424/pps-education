package vn.com.pps.education.lms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.math.BigDecimal;

/**
 * Bảng exercise_sections (V226, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-09) — "bài
 * tập con" của 1 Bài "Nghe ép" ({@link Exercise#isForcedListening()}): chấm riêng, mức cần đạt riêng.
 * Xem docs/sdd-groups/09-lms-and-portal.md mục i-bis-2.
 */
@Getter
@Setter
@Entity
@Table(name = "exercise_sections")
public class ExerciseSection extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(nullable = false)
    private String title;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** NULL = dùng {@code exercises.pass_threshold_percent}. */
    @Column(name = "pass_threshold_percent", precision = 5, scale = 2)
    private BigDecimal passThresholdPercent;
}
