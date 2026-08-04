package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Bảng review_video_connection_choices (V83) — đáp án trắc nghiệm của 1 câu hỏi CONNECTION, mirror QuestionChoice. */
@Getter
@Setter
@Entity
@Table(name = "review_video_connection_choices")
public class ReviewVideoConnectionChoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_connection_question_id", nullable = false)
    private ReviewVideoConnectionQuestion reviewVideoConnectionQuestion;

    /** A/B/C/D/E — tối đa 5 lựa chọn (2-5 câu theo yêu cầu nghiệp vụ). */
    @Column(name = "choice_label", nullable = false, length = 10)
    private String choiceLabel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_correct", nullable = false)
    private boolean correct = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
