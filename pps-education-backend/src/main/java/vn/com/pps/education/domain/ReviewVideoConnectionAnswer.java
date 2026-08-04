package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.OffsetDateTime;

/**
 * Bảng review_video_connection_answers (V76, bổ sung ngoài SDD gốc, đã xác nhận với người dùng):
 * câu trả lời của học sinh cho 1 câu hỏi CONNECTION, gắn ĐÚNG 1 lượt xem
 * ({@link ReviewVideoWatchSession}) — đây là liên kết khớp cặp 1-1 "xem lượt nào, trả lời lượt
 * đó" quyết định lượt xem đó có tính vào viewCount hay không (xem
 * ReviewVideoService#submitConnectionAnswers/recomputeProgress).
 */
@Getter
@Setter
@Entity
@Table(name = "review_video_connection_answers")
public class ReviewVideoConnectionAnswer extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_connection_question_id", nullable = false)
    private ReviewVideoConnectionQuestion reviewVideoConnectionQuestion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watch_session_id", nullable = false)
    private ReviewVideoWatchSession watchSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selected_choice_id", nullable = false)
    private ReviewVideoConnectionChoice selectedChoice;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "answered_at", nullable = false)
    private OffsetDateTime answeredAt = OffsetDateTime.now();
}
