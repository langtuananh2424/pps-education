package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Bảng notifications (SDD > Task Management & Thông báo > Notifications >
 * a) — nội dung thông báo, tách khỏi kênh gửi (notification_deliveries).
 * Không có history — bản chất là log.
 */
@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification {

    public enum NotificationType {
        ATTENDANCE_ABSENT, TASK_ASSIGNED, TASK_COMMENT, INVOICE_DUE, GRADE_PUBLISHED,
        COMMENT_APPROVED, PARTNER_FEEDBACK, LEAVE_REQUEST_STATUS, SYSTEM_ANNOUNCEMENT,
        GRADE_APPEAL_REQUESTED, EXAM_INTEGRITY_VIOLATION, HOMEWORK_DEADLINE_SUMMARY, OTHER
    }

    public enum Priority { LOW, NORMAL, HIGH, URGENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /** Loại object liên quan để click chuyển trang. */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.NORMAL;

    /** NULL = system. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by")
    private User triggeredBy;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "dismissed_at")
    private OffsetDateTime dismissedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
