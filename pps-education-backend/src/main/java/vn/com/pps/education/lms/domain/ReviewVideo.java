package vn.com.pps.education.lms.domain;

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

    /**
     * Ý nghĩa khác nhau theo videoType (V59, mặc định 80 — V168 đổi mặc định REFLEX thành 70 lúc tạo
     * mới, xem ReviewVideoService#addVideo): với CONNECTION là % pass điểm trắc nghiệm gộp mọi lượt
     * (dùng cho báo cáo giáo viên UC-66, xem ReviewVideoReportService); với REFLEX là ngưỡng % đạt
     * (viết VÀ nói) mỗi câu hỏi (V168, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-08 —
     * trước đó hardcode 70 cố định ở ReflexSequentialGradingService, xem migration V168).
     */
    @Column(name = "completion_threshold_percent", nullable = false)
    private int completionThresholdPercent = 80;

    /** Chỉ có ý nghĩa với videoType=CONNECTION — số lượt xem đạt ngưỡng % tối thiểu để video được tính "đạt" (V59, mặc định 1). */
    @Column(name = "required_view_count", nullable = false)
    private int requiredViewCount = 1;

    /**
     * Chỉ có ý nghĩa với videoType=CONNECTION — % (số lượt đạt / tổng lượt yêu
     * cầu) để phía học viên hiện popup nhắc "đã đạt tiêu chí, muốn dừng hay làm
     * tiếp" (V167, mặc định 100 từ V218 — trước đó 70). Độc lập với
     * completionThresholdPercent (ngưỡng % pass điểm trắc nghiệm) và
     * requiredViewCount (số lượt tối thiểu hoàn thành). V218 (bổ sung ngoài SDD
     * gốc, đã xác nhận với người dùng 2026-10-21) — PM chốt CONNECTION phải xem
     * HẾT đủ requiredViewCount lượt mới tính hoàn thành; 100% tắt hẳn nhánh dừng
     * sớm theo mặc định (xem ReviewVideoService#createVideo).
     */
    @Column(name = "session_pass_ratio_threshold_percent", nullable = false)
    private int sessionPassRatioThresholdPercent = 100;

    /**
     * Chỉ có ý nghĩa với videoType=REFLEX (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-10-15, xem migration V213) — ngưỡng % SỐ CÂU đạt (số câu questionPassed=true /
     * tổng số câu, xem ReviewVideoReportService#isReflexQuestionPassed) để tính Đạt/Chưa đạt
     * TỔNG THỂ của 1 học sinh. KHÁC với completionThresholdPercent (ngưỡng điểm Viết/Nói của
     * TỪNG câu riêng lẻ). Mặc định 70, mirror cơ chế isConnectionVideoPassed đã có của CONNECTION.
     */
    @Column(name = "pass_ratio_threshold_percent", nullable = false)
    private int passRatioThresholdPercent = 70;
}
