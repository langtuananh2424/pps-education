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
 * Bảng tasks (SDD > Task Management & Thông báo > Task Management > a) —
 * công việc được giao (UC-06 FR-TSK-01). status tự động COMPLETED khi
 * toàn bộ {@link TaskAssignment} = COMPLETED; OVERDUE do cron job nightly
 * đặt khi quá hạn (SDD) — xem TaskSchedulerService.
 */
@Getter
@Setter
@Entity
@Table(name = "tasks")
public class Task {

    public enum TaskType { GENERAL, URGENT, RECURRING, PROJECT }

    public enum Priority { LOW, NORMAL, HIGH, URGENT }

    public enum Status { OPEN, IN_PROGRESS, COMPLETED, CANCELLED, OVERDUE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(name = "task_code", nullable = false, unique = true, length = 50)
    private String taskCode;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /** Phòng ban liên quan — mặc định lấy theo phòng ban người giao việc (UC-06). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 30)
    private TaskType taskType = TaskType.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /** Cho phép chia task lớn thành sub-tasks (SDD) — chưa có UC nào dùng, giữ FK sẵn theo schema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private Task parentTask;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;
}
