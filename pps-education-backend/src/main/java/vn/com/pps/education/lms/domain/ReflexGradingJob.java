package vn.com.pps.education.lms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.OffsetDateTime;

/**
 * Bảng reflex_grading_job (V222, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-22) — 1 lượt chấm AI
 * đang chờ/đang chạy/đã đóng của 1 bước (làn) trên {@link ReflexQuestionProgress}. Xem
 * {@link vn.com.pps.education.lms.service.ReflexGradingQueueService}.
 */
@Getter
@Setter
@Entity
@Table(name = "reflex_grading_job")
public class ReflexGradingJob extends BaseAuditEntity {

    public enum Lane { WRITING, SPEAKING }

    public enum Status { QUEUED, RUNNING, DONE, REJECTED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reflex_question_progress_id", nullable = false)
    private Long progressId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Lane lane;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "recording_filter")
    private Boolean recordingFilter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.QUEUED;

    @Column(name = "try_count", nullable = false)
    private int tryCount;

    @Column(name = "next_run_at", nullable = false)
    private OffsetDateTime nextRunAt = OffsetDateTime.now();

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
}
