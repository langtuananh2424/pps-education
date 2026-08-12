package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng review_video_connection_question_slots (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-11) — phân bổ CỐ ĐỊNH 1 câu hỏi CONNECTION vào 1 nhóm (slotIndex, 1..M) cho ĐÚNG 1 học
 * sinh — ngẫu nhiên RIÊNG theo từng học sinh (chống hỏi bài nhau), sinh 1 lần lúc học sinh vào xem
 * lượt đầu tiên (xem {@code ReviewVideoService#startWatchSession}), không đổi lại sau đó dù xem lại
 * bao nhiêu lần. `slotIndex` trên {@link ReviewVideoWatchSession} tham chiếu tới giá trị này.
 */
@Getter
@Setter
@Entity
@Table(name = "review_video_connection_question_slots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"review_video_connection_question_id", "student_id"}))
public class ReviewVideoConnectionQuestionSlot extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_connection_question_id", nullable = false)
    private ReviewVideoConnectionQuestion reviewVideoConnectionQuestion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "slot_index", nullable = false)
    private int slotIndex;
}
