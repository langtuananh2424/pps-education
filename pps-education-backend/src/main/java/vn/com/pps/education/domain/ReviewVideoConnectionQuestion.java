package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng review_video_connection_questions (V76, bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng): câu hỏi trắc nghiệm tự chấm cho video CONNECTION — chỉ có ý nghĩa khi
 * reviewVideo.reviewVideoSet.videoType = CONNECTION (kiểm tra ở Service, không CHECK trên bảng
 * này, giống cách ReviewVideoQuestion REFLEX kiểm tra videoType). Khác ReviewVideoQuestion
 * (REFLEX) ở chỗ không gắn timestamp/audio — hiện SAU KHI xem xong 1 lượt, không phải tại 1 mốc
 * giữa video.
 */
@Getter
@Setter
@Entity
@Table(name = "review_video_connection_questions")
public class ReviewVideoConnectionQuestion extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_id", nullable = false)
    private ReviewVideo reviewVideo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
