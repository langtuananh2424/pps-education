package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Bảng attempt_integrity_events (bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-07-31 — xem docs/uc/phan-he-07-lms-portal.md, blockquote
 * "Bổ sung" ở UC-24/UC-27/UC-23b) — ghi nhận sự kiện học sinh thoát ra
 * ngoài (đổi tab/thu nhỏ/thoát fullscreen) khi đang làm bài, phục vụ báo
 * phụ huynh/giáo viên khi vượt ngưỡng.
 *
 * attemptId là khóa đa hình (Long thô, không @ManyToOne) — trỏ tới
 * exercise_attempts hoặc review_video_question_submissions tùy attemptType,
 * mirror cách ClassEnrollment.importJobId map thô do trỏ nhiều bảng khác
 * nhau. Log bất biến (không kế thừa BaseAuditEntity, giống
 * ExerciseAttemptHistory) — chỉ thêm dòng mới, không sửa/xóa.
 */
@Getter
@Setter
@Entity
@Table(name = "attempt_integrity_events")
public class AttemptIntegrityEvent {

    public enum AttemptType { EXERCISE, REVIEW_VIDEO_QUESTION }

    public enum EventType { OUT_OF_FOCUS, FULLSCREEN_EXITED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_type", nullable = false, length = 30)
    private AttemptType attemptType;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_class_id")
    private SchoolClass schoolClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at", nullable = false)
    private OffsetDateTime endedAt;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "client_reported_at", nullable = false)
    private OffsetDateTime clientReportedAt;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** Set trên đúng 1 dòng đầu tiên vượt ngưỡng cho (attemptType, attemptId) — chặn báo trùng khi gửi lô sau. */
    @Column(name = "notified_at")
    private OffsetDateTime notifiedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
