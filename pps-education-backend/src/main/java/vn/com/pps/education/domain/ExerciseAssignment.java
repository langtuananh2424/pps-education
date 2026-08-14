package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bảng exercise_assignments (SDD > LMS & Portal > Ngân hàng câu hỏi & Bài
 * tập > f) — giao đề ASSIGNED cho 1 lớp kèm deadline (UC-40 Main Flow
 * bước 3).
 */
@Getter
@Setter
@Entity
@Table(name = "exercise_assignments")
public class ExerciseAssignment {

    public enum Status { ACTIVE, CANCELLED, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    @Column(name = "available_from", nullable = false)
    private OffsetDateTime availableFrom = OffsetDateTime.now();

    /** NULL = không deadline — bài tự luyện (SDD). */
    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "late_submission_allowed", nullable = false)
    private boolean lateSubmissionAllowed = false;

    @Column(name = "late_penalty_percent", precision = 5, scale = 2)
    private BigDecimal latePenaltyPercent;

    /** NULL = cả lớp; có giá trị = cá nhân hóa (SDD). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_student_ids", columnDefinition = "jsonb")
    private List<Long> targetStudentIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    /** V82 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): NULL = chưa báo GV % hoàn thành khi hết hạn. */
    @Column(name = "teacher_notified_at")
    private OffsetDateTime teacherNotifiedAt;

    /** V92 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06): NULL = chưa nhắc Phụ huynh trước hạn nộp. */
    @Column(name = "parent_reminder_sent_at")
    private OffsetDateTime parentReminderSentAt;

    /**
     * V123 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14) — buổi học (UC-21) mà Giáo
     * viên đang viết Nhận xét lúc chọn Bài này làm "BTVN buổi sau", để Portal học sinh hiển thị được
     * "giao trong buổi nào". NULL với bản giao tạo TRƯỚC V123 (không backfill được).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_class_session_id")
    private ClassSession sourceClassSession;
}
