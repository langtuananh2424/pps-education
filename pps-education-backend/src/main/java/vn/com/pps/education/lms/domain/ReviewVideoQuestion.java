package vn.com.pps.education.lms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;
import vn.com.pps.education.common.ReflexQuestionFormat;

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

    /**
     * V200 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — dạng đề giáo viên chọn. NULL = câu hỏi
     * cũ, hệ thống suy dạng đề từ khối/tuyến + thời gian ghi âm như trước (xem ReflexV2Task#forGradeTrack).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "question_format", length = 20)
    private ReflexQuestionFormat questionFormat;

    /** V200 — ảnh tranh (dạng PICTURE) để giáo viên đối chiếu khi soạn mô tả; không gửi vào AI chấm. */
    @Column(name = "picture_image_url", length = 1000)
    private String pictureImageUrl;

    /**
     * V200 — mô tả tranh bằng chữ (dạng PICTURE) giáo viên đã duyệt: chỉ gửi vào lượt chấm viết/nói để xét lạc
     * đề; KHÔNG gửi vào lượt phiên âm, KHÔNG trả cho học sinh.
     */
    @Column(name = "picture_brief", columnDefinition = "TEXT")
    private String pictureBrief;
}
