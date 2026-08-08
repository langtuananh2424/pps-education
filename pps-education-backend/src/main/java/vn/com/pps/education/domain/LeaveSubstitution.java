package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng leave_substitutions (SDD > Nhân sự > Đơn từ > c) — giáo viên dạy
 * thay theo đơn nghỉ (UC-10 bước 5: gán ngay khi nộp đơn; UC-11 A2: thu
 * hồi ngay khi đơn bị Từ chối; UC-11 Mở rộng: scheduled job tự thu hồi
 * sau end_date + 2 ngày nếu đơn Đã duyệt).
 */
@Getter
@Setter
@Entity
@Table(name = "leave_substitutions")
public class LeaveSubstitution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_request_id", nullable = false)
    private LeaveRequest leaveRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_session_id", nullable = false)
    private ClassSession classSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_teacher_id", nullable = false)
    private ClassTeacher classTeacher;

    /** Giáo viên chính của buổi học tại thời điểm gán, để trả lại khi thu hồi. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_teacher_id", nullable = false)
    private User originalTeacher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "substitute_teacher_id", nullable = false)
    private User substituteTeacher;

    /** NULL = đang dạy thay; có giá trị = đã thu hồi (chính là log thu hồi). */
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
