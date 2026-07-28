package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Bảng questions (SDD > LMS & Portal > Ngân hàng câu hỏi & Bài tập > b).
 * Sửa khi đã có student_answers: cấm sửa content/đáp án đúng, phải tạo
 * bản mới + archive bản cũ (SDD) — xem QuestionBankService.updateQuestion().
 */
@Getter
@Setter
@Entity
@Table(name = "questions")
public class Question {

    public enum QuestionType { MULTIPLE_CHOICE, MULTIPLE_ANSWER, TRUE_FALSE, FILL_IN_BLANK, ESSAY, SPEAKING }

    public enum Skill { LISTENING, READING, WRITING, SPEAKING, GRAMMAR, OTHER }

    public enum Difficulty { EASY, MEDIUM, HARD }

    public enum Status { ACTIVE, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_bank_id", nullable = false)
    private QuestionBank questionBank;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 30)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Difficulty difficulty;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "audio_url", length = 1000)
    private String audioUrl;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "reference_passage", columnDefinition = "TEXT")
    private String referencePassage;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    /**
     * Chỉ có ý nghĩa khi questionType=FILL_IN_BLANK — so khớp CHÍNH XÁC
     * (case-insensitive + trim) khi tự chấm, xem
     * ExerciseAttemptService.isAnswerCorrect(). Bổ sung ngoài SDD gốc,
     * đã xác nhận với người dùng — xem V54.
     */
    @Column(name = "correct_answer_text", columnDefinition = "TEXT")
    private String correctAnswerText;

    @Column(name = "default_points", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultPoints = new BigDecimal("1.0");

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
