package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Bảng exercises (SDD > LMS & Portal > Ngân hàng câu hỏi & Bài tập > d) —
 * đề ôn tập/bài tập (UC-40 FR-LMS-10). exercise_type SELF_PRACTICE (UC-27,
 * mở tự do) hoặc ASSIGNED (UC-24, có deadline qua exercise_assignments).
 */
@Getter
@Setter
@Entity
@Table(name = "exercises")
public class Exercise extends BaseAuditEntity {

    public enum ExerciseType { SELF_PRACTICE, ASSIGNED, MOCK_TEST, SKILL_PRACTICE }

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 500)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id")
    private Curriculum curriculum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private CurriculumSubject subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "exercise_type", nullable = false, length = 30)
    private ExerciseType exerciseType;

    @Column(name = "total_points", nullable = false, precision = 6, scale = 2)
    private BigDecimal totalPoints;

    /** NULL = không giới hạn (SDD). */
    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(name = "allow_retake", nullable = false)
    private boolean allowRetake = true;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "show_correct_answers", nullable = false)
    private boolean showCorrectAnswers = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
