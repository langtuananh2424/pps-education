package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Bảng review_video_submissions (SDD > LMS & Portal > Kho Video Ôn tập >
 * d) — MỚI HOÀN TOÀN (UC-23b, 2026-07-27, bổ sung ngoài SDD gốc đã xác
 * nhận với người dùng): Học sinh nộp audio trả lời cho video loại REFLEX
 * (kiểm tra videoType ở Service, không đặt CHECK trên bảng này), Giáo
 * viên chấm điểm (score/maxScore) + nhận xét (feedback). Học sinh nộp
 * lại (resubmit) GHI ĐÈ audioUrl (UNIQUE review_video_id+student_id,
 * giống ReviewVideoProgress) và xoá sạch điểm/nhận xét cũ (Service tự
 * xoá) vì điểm cũ chấm cho nội dung audio đã không còn tồn tại — không
 * giữ lịch sử các lần nộp trước.
 */
@Getter
@Setter
@Entity
@Table(name = "review_video_submissions")
public class ReviewVideoSubmission extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_id", nullable = false)
    private ReviewVideo reviewVideo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "audio_url", nullable = false, length = 1000)
    private String audioUrl;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    /** NULL = chưa chấm. */
    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "max_score", precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy;

    @Column(name = "graded_at")
    private OffsetDateTime gradedAt;
}
