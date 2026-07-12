package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Bảng task_assignments (SDD > Task Management & Thông báo > Task
 * Management > b) — gán 1 task cho 1 người thực hiện, mỗi người theo dõi
 * tiến độ độc lập (UC-06 A2). assignmentStatus PENDING_REVIEW bổ sung
 * ngoài SDD gốc — xem Javadoc migration V23 / Task.
 */
@Getter
@Setter
@Entity
@Table(name = "task_assignments")
public class TaskAssignment {

    public enum Status { PENDING, ACCEPTED, IN_PROGRESS, PENDING_REVIEW, COMPLETED, DECLINED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignee_user_id", nullable = false)
    private User assignee;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent = BigDecimal.ZERO;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "decline_reason", columnDefinition = "TEXT")
    private String declineReason;
}
