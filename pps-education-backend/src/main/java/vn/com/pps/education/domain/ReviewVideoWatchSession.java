package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.OffsetDateTime;

/**
 * Bảng review_video_watch_sessions (SDD > LMS & Portal > Kho Video Ôn
 * tập) — MỚI HOÀN TOÀN (V59, 2026-07-28, bổ sung ngoài SDD gốc đã xác
 * nhận với người dùng): 1 dòng = 1 LƯỢT xem của học sinh cho 1 video
 * CONNECTION (khác {@link ReviewVideoProgress}, vốn là watermark suốt
 * đời không phân biệt được "lần" nào với "lần" nào).
 * {@code watchedSeconds} là mốc cao nhất trong CHÍNH lượt này;
 * {@code qualified} = đã đạt completionThresholdPercent trong lượt này
 * — chỉ lượt qualified mới tính vào viewCount của
 * {@link ReviewVideoProgress}.
 */
@Getter
@Setter
@Entity
@Table(name = "review_video_watch_sessions")
public class ReviewVideoWatchSession extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_id", nullable = false)
    private ReviewVideo reviewVideo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "watched_seconds", nullable = false)
    private int watchedSeconds = 0;

    @Column(name = "is_qualified", nullable = false)
    private boolean qualified = false;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt = OffsetDateTime.now();
}
