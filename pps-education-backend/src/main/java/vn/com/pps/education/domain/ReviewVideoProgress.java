package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng review_video_progress (SDD > LMS & Portal > Kho Video Ôn tập > d) —
 * MỚI HOÀN TOÀN (UC-23a, 2026-07-27, bổ sung ngoài SDD gốc đã xác nhận
 * với người dùng): theo dõi tiến độ xem của từng học sinh cho từng video.
 * `watchedSeconds` là mốc giây CAO NHẤT từng đạt (không giảm khi tua
 * tới); `isCompleted` tính lại mỗi lần cập nhật =
 * watchedSeconds >= duration * 0.8, lưu dư thừa để giáo viên xem thống kê
 * nhanh (an toàn vì duration_seconds luôn có sẵn từ lúc tạo video).
 */
@Getter
@Setter
@Entity
@Table(name = "review_video_progress")
public class ReviewVideoProgress extends BaseAuditEntity {

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

    /** V59: đổi ý nghĩa — tính lại ở Service = viewCount >= reviewVideo.requiredViewCount (không còn chỉ dựa vào watchedSeconds đơn thuần). */
    @Column(name = "is_completed", nullable = false)
    private boolean completed = false;

    /** V59: số lượt xem (ReviewVideoWatchSession) đã đạt completionThresholdPercent — rollup, tính lại mỗi lần có session mới. */
    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    /**
     * V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — rollup viewCount/completed
     * này thuộc về ĐÚNG lần giao nào — mirror {@link ReviewVideoWatchSession#getReviewVideoAssignment()},
     * xem Javadoc field đó để hiểu lý do. NULL cho dữ liệu cũ trước migration.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_video_assignment_id")
    private ReviewVideoAssignment reviewVideoAssignment;
}
