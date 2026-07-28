package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng grade_appeal_requests (V43, bổ sung ngoài SDD gốc, đã xác nhận
 * với người dùng — UC-62 Phúc khảo điểm). Học sinh/Phụ huynh gửi yêu cầu
 * phúc khảo trên 1 bản ghi điểm đang PROVISIONAL_PUBLISHED (GradeEntry
 * hoặc GradePeriodResult — polymorphic qua entityType/entityId, giống
 * pattern Notification.entityType/entityId, không FK cứng tới 2 bảng
 * khác nhau). Giáo viên phụ trách lớp phải "tiếp nhận" (status=ACCEPTED)
 * mới được sửa điểm của đúng học sinh này (xem GradeService#requireEditableState).
 * Sửa điểm xong tự động chuyển status=RESOLVED (xem GradeAppealService).
 */
@Getter
@Setter
@Entity
@Table(name = "grade_appeal_requests")
public class GradeAppealRequest {

    public enum EntityType { GRADE_ENTRY, GRADE_PERIOD_RESULT }

    public enum Status { PENDING, ACCEPTED, RESOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private EntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedBy;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by_user_id")
    private User acceptedBy;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
