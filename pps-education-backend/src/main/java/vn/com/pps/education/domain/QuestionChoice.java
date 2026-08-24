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

    /**
     * V143 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — ảnh riêng cho lựa chọn này,
     * dùng cho câu hỏi Listening dạng "nghe rồi chọn đáp án bằng hình" (mỗi đáp án là 1 tấm ảnh thay vì
     * chữ). NULL với mọi dạng câu hỏi khác (đáp án vẫn là chữ như trước).
     */
    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "is_correct", nullable = false)
    private boolean correct = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
