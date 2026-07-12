package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Bảng attendance_sessions (SDD > Học thuật > Lịch dạy & Điểm danh > c) —
 * header cho việc điểm danh 1 buổi cụ thể (UC-15). Không có history
 * riêng — "chi tiết thay đổi đã có ở attendance_marks_history" (SDD).
 */
@Getter
@Setter
@Entity
@Table(name = "attendance_sessions")
public class AttendanceSession {

    public enum Mode { SESSION_LEVEL, PERIOD_LEVEL }

    public enum Status { DRAFT, SUBMITTED, LOCKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_session_id", nullable = false, unique = true)
    private ClassSession classSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Mode mode = Mode.SESSION_LEVEL;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marked_by", nullable = false)
    private User markedBy;

    @Column(name = "marked_at", nullable = false)
    private OffsetDateTime markedAt = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;
}
