package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Bảng question_choices (SDD > LMS & Portal > Ngân hàng câu hỏi & Bài tập > c) — đáp án trắc nghiệm. */
@Getter
@Setter
@Entity
@Table(name = "question_choices")
public class QuestionChoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /** A/B/C/D hoặc TRUE/FALSE (SDD). */
    @Column(name = "choice_label", nullable = false, length = 10)
    private String choiceLabel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_correct", nullable = false)
    private boolean correct = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
