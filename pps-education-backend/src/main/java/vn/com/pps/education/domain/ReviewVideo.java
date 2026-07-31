package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng review_videos (SDD > LMS & Portal > Kho Video Ôn tập > b) — từng
 * video/audio trong 1 bộ, đã upload lên CDN (URL, không lưu file trực
 * tiếp — NFR-TECH-07) hoặc dán link YouTube. Không có history, sửa tạo
 * bản ghi mới (SDD). Tái cấu trúc 2026-07-27 từ "lesson_materials" — bỏ
 * material_type/is_downloadable, thay bằng source_type; duration_seconds
 * bắt buộc cho cả 3 nguồn (dùng để tính % xem, UC-23a).
 */
@Getter
@Setter
@Entity
@Table(name = "review_videos")
public class ReviewVideo extends BaseAuditEntity {

    public enum SourceType { YOUTUBE_URL, R2_VIDEO, R2_AUDIO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_set_id", nullable = false)
    private ReviewVideoSet reviewVideoSet;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /** Chỉ có ý nghĩa với videoType=CONNECTION — % ngưỡng để 1 lượt xem được tính "đạt" (V59, mặc định 80). */
    @Column(name = "completion_threshold_percent", nullable = false)
    private int completionThresholdPercent = 80;

    /** Chỉ có ý nghĩa với videoType=CONNECTION — số lượt xem đạt ngưỡng % tối thiểu để video được tính "đạt" (V59, mặc định 1). */
    @Column(name = "required_view_count", nullable = false)
    private int requiredViewCount = 1;
}
