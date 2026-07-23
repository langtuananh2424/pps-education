package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Bảng listening_practice_gradings (UC-26 — bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng). GV chấm thủ công 1
 * {@link ListeningPracticeAttempt} chế độ SPEAKING — tách hoàn toàn
 * khỏi student_answer_grading (UC-41), 1 attempt tối đa 1 lần chấm
 * (UNIQUE practice_attempt_id) — sửa thì update tại chỗ, không cần
 * versioning như student_answer_grading vì không có nhiều câu hỏi con.
 */
@Getter
@Setter
@Entity
@Table(name = "listening_practice_gradings")
public class ListeningPracticeGrading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practice_attempt_id", nullable = false, unique = true)
    private ListeningPracticeAttempt practiceAttempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grader_user_id", nullable = false)
    private User grader;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "graded_at", nullable = false)
    private OffsetDateTime gradedAt = OffsetDateTime.now();
}
