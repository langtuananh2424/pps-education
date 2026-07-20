package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng listening_practice_attempts (UC-26 — bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng). 1 lượt học sinh luyện 1
 * {@link ListeningPracticeItem} — tự luyện, không giới hạn lượt (khác
 * UC-24 exercise_attempts).
 */
@Getter
@Setter
@Entity
@Table(name = "listening_practice_attempts")
public class ListeningPracticeAttempt extends BaseAuditEntity {

    public enum Status { IN_PROGRESS, SUBMITTED, GRADED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practice_item_id", nullable = false)
    private ListeningPracticeItem practiceItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber = 1;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    /** A1 — tạm dừng giữa chừng, lưu vị trí để tiếp tục sau. */
    @Column(name = "paused_position_seconds")
    private Integer pausedPositionSeconds;

    /** Chế độ DICTATION. */
    @Column(name = "dictation_answer_text", columnDefinition = "TEXT")
    private String dictationAnswerText;

    @Column(name = "dictation_score", precision = 5, scale = 2)
    private BigDecimal dictationScore;

    /** Chế độ SPEAKING. */
    @Column(name = "audio_answer_url", length = 1000)
    private String audioAnswerUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.IN_PROGRESS;
}
