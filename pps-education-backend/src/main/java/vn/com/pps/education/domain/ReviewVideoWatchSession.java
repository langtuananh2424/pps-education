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

    /** V83 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): NULL = chưa trả lời đủ bộ câu hỏi CONNECTION cho lượt này. */
    @Column(name = "quiz_completed_at")
    private OffsetDateTime quizCompletedAt;

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — CHỈ có ý nghĩa với video
     * CONNECTION: lượt này ứng với nhóm câu hỏi số mấy (1..M, xem {@link ReviewVideoConnectionQuestionSlot}),
     * tính 1 lần lúc tạo session (xem ReviewVideoService#startWatchSession), lặp lại theo chu kỳ
     * modulo M nếu học sinh xem quá M lượt. NULL với video REFLEX (không có khái niệm nhóm câu hỏi).
     */
    @Column(name = "slot_index")
    private Integer slotIndex;

    /**
     * V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — lượt xem này thuộc về ĐÚNG
     * lần giao (bản giao) nào — trước V129, lượt xem chỉ gắn với (video, học sinh), khiến 1 bộ video
     * CONNECTION được giao ĐỘC LẬP từ 2 buổi Nhận xét khác nhau bị TRỘN CHUNG 1 rollup viewCount (xem
     * {@link ReviewVideoProgress}), không tách được điểm/tiến độ theo từng lần giao. NULL cho dữ liệu cũ
     * trước migration (không suy ngược được bản giao gốc, mirror tinh thần source_class_session_id V123).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_video_assignment_id")
    private ReviewVideoAssignment reviewVideoAssignment;
}
