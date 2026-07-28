package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng review_video_questions (SDD > LMS & Portal > Kho Video Ôn tập)
 * — MỚI HOÀN TOÀN (V57, 2026-07-28, bổ sung ngoài SDD gốc đã xác nhận
 * với người dùng): câu hỏi gắn 1 mốc thời gian (timestampSeconds) trong
 * 1 video REFLEX — chỉ có ý nghĩa khi reviewVideo.reviewVideoSet.videoType
 * = REFLEX (kiểm tra ở Service, không CHECK trên bảng này, giống cách
 * ReviewVideoSubmission cũ kiểm tra videoType). maxRecordingSeconds/
 * maxAttempts đặt riêng theo TỪNG câu hỏi (đã xác nhận với người dùng —
 * không dùng chung 1 giá trị cho cả video).
 */
@Getter
@Setter
@Entity
@Table(name = "review_video_questions")
public class ReviewVideoQuestion extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_id", nullable = false)
    private ReviewVideo reviewVideo;

    @Column(name = "timestamp_seconds", nullable = false)
    private int timestampSeconds;

    @Column(length = 500)
    private String prompt;

    @Column(name = "max_recording_seconds", nullable = false)
    private int maxRecordingSeconds;

    /** NULL = không giới hạn số lần nộp lại. */
    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
