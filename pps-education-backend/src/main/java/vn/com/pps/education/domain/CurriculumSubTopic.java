package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bảng sub_topics (V144, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — Sub Topic trong
 * 1 {@link CurriculumUnit} (VD "SUB TOPIC 1: SCHOOL ACTIVITIES AND SUBJECTS"). 1 Lesson ({@link Exam},
 * qua {@code Exam#getSubTopic()}) thuộc đúng 1 Sub Topic.
 */
@Getter
@Setter
@Entity
@Table(name = "sub_topics")
public class CurriculumSubTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private CurriculumUnit unit;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
