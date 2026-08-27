package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng exercise_attempts (SDD > LMS & Portal > Ngân hàng câu hỏi & Bài
 * tập > g) — lượt học sinh làm bài (UC-24/UC-26/UC-27). Entity tạo trước
 * cùng đợt UC-40 để questions_history có đủ điều kiện kiểm tra "đã có
 * student_answers" — Service/Controller xử lý làm bài sẽ thêm ở batch
 * UC-24/26/27.
 */
@Getter
@Setter
@Entity
@Table(name = "exercise_attempts")
public class ExerciseAttempt {

    public enum Status { IN_PROGRESS, SUBMITTED, AUTO_GRADED, FULLY_GRADED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    /** NULL cho SELF_PRACTICE (SDD). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_assignment_id")
    private ExerciseAssignment exerciseAssignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber = 1;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt = OffsetDateTime.now();

    /** NULL = đang làm dở (SDD). */
    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "auto_grade_score", precision = 6, scale = 2)
    private BigDecimal autoGradeScore;

    @Column(name = "manual_grade_score", precision = 6, scale = 2)
    private BigDecimal manualGradeScore;

    @Column(name = "total_score", precision = 6, scale = 2)
    private BigDecimal totalScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.IN_PROGRESS;

    @Column(name = "is_late_submission", nullable = false)
    private boolean lateSubmission = false;

    /** V89: NULL = chưa chấm xong; xem ExerciseAttemptService#applyPassOutcome (UC-27, BTVN <ngưỡng phải làm lại). */
    @Column(name = "passed")
    private Boolean passed;

    /** V92 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06): lượt bị hệ thống dừng ép do vi phạm giám sát vượt ngưỡng — xem AttemptIntegrityService/ExerciseAttemptService#forceStopByIntegrityViolation. */
    @Column(name = "stopped_by_integrity_violation", nullable = false)
    private boolean stoppedByIntegrityViolation = false;

    /** V93 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06): Giáo viên đánh dấu lượt này là kết quả CHÍNH THỨC khi học sinh có nhiều lượt làm — xem ExerciseAttemptService#selectForGrading. Tối đa 1 lượt được chọn/(exercise, student). */
    @Column(name = "selected_for_grading", nullable = false)
    private boolean selectedForGrading = false;

    /** V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25): học sinh ĐÃ ĐẠT nhưng còn lượt làm lại tự nguyện dừng lại để xem đáp án sớm — xem ExerciseAttemptService#revealAnswersAndClose. */
    @Column(name = "answers_revealed_early", nullable = false)
    private boolean answersRevealedEarly = false;
}
