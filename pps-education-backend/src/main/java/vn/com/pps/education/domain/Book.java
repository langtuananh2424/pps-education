package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bảng books (V148, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — Sách giáo trình
 * (VD "Cambridge English for Schools 6"), thuộc 1 {@link Curriculum} (chương trình+khối, VD "IELTS
 * Grade 6" — {@code track}/{@code gradeLevel} đã có sẵn từ V140, không lặp lại ở đây). Cấp cha của
 * {@link CurriculumUnit} — mục lục sách đầy đủ: Curriculum -&gt; Sách -&gt; Unit -&gt; Sub Topic.
 */
@Getter
@Setter
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
