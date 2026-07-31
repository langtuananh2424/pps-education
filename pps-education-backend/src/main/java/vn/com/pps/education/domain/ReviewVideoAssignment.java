package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bảng review_video_assignments (V65, bổ sung ngoài SDD gốc, đã xác nhận
 * với người dùng — giao bộ Video Ôn tập cho 1 lớp kèm hạn nộp, phát sinh
 * từ Nhận xét học viên UC-21 khi Giáo viên chọn 1 bộ làm "BTVN buổi sau").
 * Mirror {@link ExerciseAssignment} — không có lateSubmissionAllowed/
 * latePenaltyPercent vì không áp dụng cho video (không "nộp bài" theo
 * nghĩa chấm điểm trễ hạn).
 */
@Getter
@Setter
@Entity
@Table(name = "review_video_assignments")
public class ReviewVideoAssignment {

    public enum Status { ACTIVE, CANCELLED, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_set_id", nullable = false)
    private ReviewVideoSet reviewVideoSet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    @Column(name = "available_from", nullable = false)
    private OffsetDateTime availableFrom = OffsetDateTime.now();

    /** NULL = không hạn nộp. */
    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    /** NULL = cả lớp (mặc định sau V65 — luôn giao cả lớp qua Nhận xét). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_student_ids", columnDefinition = "jsonb")
    private List<Long> targetStudentIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;
}
