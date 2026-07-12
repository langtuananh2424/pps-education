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
}
