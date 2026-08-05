package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Bảng exercises (SDD > LMS & Portal > Ngân hàng câu hỏi & Bài tập > d) —
 * "Bài" trong 1 "Đề" ({@link Exam}, UC-40, bổ sung ngoài SDD gốc, đã xác
 * nhận với người dùng 2026-07-30 — tái cấu trúc Kho đề 2 cấp). exercise_type
 * SELF_PRACTICE/ASSIGNED/MOCK_TEST/SKILL_PRACTICE không còn quyết định
 * việc học sinh có xem/làm được hay không — MỌI loại đều cần Đề của Bài
 * đó đã gán cho lớp (xem {@link ExamClassAssignment}) VÀ được giao qua
 * Nhận xét học viên (UC-21, xem ExerciseService#deliverToClass).
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

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

    /** V89 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05): BTVN dưới ngưỡng này phải làm lại — xem ExerciseAttemptService#applyPassOutcome. */
    @Column(name = "pass_threshold_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal passThresholdPercent = new BigDecimal("80.00");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
