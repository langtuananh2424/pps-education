package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bảng student_answers (SDD > LMS & Portal > Ngân hàng câu hỏi & Bài tập
 * > h) — câu trả lời từng câu trong 1 lượt làm bài. Entity tạo trước
 * cùng đợt UC-40 — xem Javadoc ExerciseAttempt.
 */
@Getter
@Setter
@Entity
@Table(name = "student_answers")
public class StudentAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_attempt_id", nullable = false)
    private ExerciseAttempt exerciseAttempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /** FILL_IN_BLANK, ESSAY (SDD). */
    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    /** MULTIPLE_CHOICE/MULTIPLE_ANSWER (SDD). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_choice_ids", columnDefinition = "jsonb")
    private List<Long> selectedChoiceIds;

    /** SPEAKING — audio HS upload (SDD). */
    @Column(name = "audio_answer_url", length = 1000)
    private String audioAnswerUrl;

    /**
     * V78 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — WORD_BANK/SENTENCE_BUILDING.
     * WORD_BANK: đáp án HS chọn theo đúng thứ tự chỗ trống. SENTENCE_BUILDING: thứ tự khối từ/cụm HS
     * đã chọn. So khớp elementwise (case-insensitive + trim) với Question.structuredContent khi chấm
     * — xem ExerciseAttemptService.isAnswerCorrect().
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_answer", columnDefinition = "jsonb")
    private List<String> structuredAnswer;

    @Column(name = "is_auto_gradable", nullable = false)
    private boolean autoGradable;

    @Column(name = "auto_score", precision = 5, scale = 2)
    private BigDecimal autoScore;

    @Column(name = "is_correct")
    private Boolean correct;
}
