package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Bảng exercise_questions (SDD > LMS & Portal > Ngân hàng câu hỏi & Bài tập > e) — câu hỏi thuộc đề. */
@Getter
@Setter
@Entity
@Table(name = "exercise_questions")
public class ExerciseQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal points;
}
