package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Bảng reflex_question_progress (V139, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22)
 * — UC-23b (Video phản xạ) V2: tiến trình tuần tự cho 1 câu hỏi của 1 học sinh trong 1 lần giao —
 * viết trước (AI chấm ngữ pháp) → đạt → mở khoá ghi âm → AI chấm nội dung → đạt → mở câu tiếp theo.
 * Xem {@link ReflexSequentialGradingService}.
 *
 * KHÔNG dùng lại {@link ReviewVideoQuestionSubmission} (audioUrl NOT NULL ở đó — không hợp với việc
 * "đã viết nhưng chưa ghi âm"; bảng cũ giữ nguyên cho lịch sử/luồng chấm tay cũ, xem
 * ReviewVideoGradingPanel.tsx). 1 dòng/(câu hỏi, học sinh, lần giao) — SỬA ĐÈ tại chỗ mỗi lần thử lại
 * (không giữ lịch sử từng lần, "không giới hạn số lần thử lại, chỉ cần lưu tiến trình dở" đã xác nhận
 * với người dùng) — *AttemptCount chỉ để hiển thị thống kê, KHÔNG phải rào chặn.
 */
@Getter
@Setter
@Entity
@Table(name = "reflex_question_progress")
public class ReflexQuestionProgress extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_question_id", nullable = false)
    private ReviewVideoQuestion reviewVideoQuestion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_assignment_id", nullable = false)
    private ReviewVideoAssignment reviewVideoAssignment;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @Column(name = "writing_score", precision = 5, scale = 2)
    private BigDecimal writingScore;

    @Column(name = "writing_max_score", precision = 5, scale = 2)
    private BigDecimal writingMaxScore;

    @Column(name = "writing_feedback", columnDefinition = "TEXT")
    private String writingFeedback;

    @Column(name = "writing_graded_at")
    private OffsetDateTime writingGradedAt;

    @Column(name = "writing_attempt_count", nullable = false)
    private int writingAttemptCount;

    /**
     * V141 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — gợi ý câu trả lời đã sửa
     * lỗi ngữ pháp, CHỈ sửa trong chính câu học sinh viết (giữ nguyên cấu trúc/ý), KHÔNG phải câu mẫu
     * tự bịa — FE chỉ hiện ra khi writingAttemptCount >= 3 VÀ vẫn chưa đạt (xem ReflexVideoTaskPage.tsx).
     */
    @Column(name = "writing_corrected_answer", columnDefinition = "TEXT")
    private String writingCorrectedAnswer;

    @Column(name = "audio_url", length = 1000)
    private String audioUrl;

    @Column(name = "speaking_score", precision = 5, scale = 2)
    private BigDecimal speakingScore;

    @Column(name = "speaking_max_score", precision = 5, scale = 2)
    private BigDecimal speakingMaxScore;

    @Column(name = "speaking_feedback", columnDefinition = "TEXT")
    private String speakingFeedback;

    @Column(name = "speaking_graded_at")
    private OffsetDateTime speakingGradedAt;

    @Column(name = "speaking_attempt_count", nullable = false)
    private int speakingAttemptCount;
}
