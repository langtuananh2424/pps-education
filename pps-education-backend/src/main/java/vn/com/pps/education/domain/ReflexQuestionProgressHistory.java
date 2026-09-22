package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.com.pps.education.common.BaseAuditEntity;
import vn.com.pps.education.common.CriteriaScoreItem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Bảng reflex_question_progress_history (V191, bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-09-21) — UC-23b (Video phản xạ): snapshot CHỈ-THÊM (không sửa đè) mỗi khi AI chấm xong 1 bước
 * (viết hoặc ghi âm) của {@link ReflexQuestionProgress} — bảng đó ghi đè tại chỗ nên không giữ được
 * lịch sử. Phục vụ giáo viên nghe lại audio + xem kết quả AI chấm theo TỪNG lần làm, và xuất toàn bộ dữ
 * liệu audio + kết quả chấm để tiếp tục train AI. Xem {@link vn.com.pps.education.service.ReflexSequentialGradingService}.
 */
@Getter
@Setter
@Entity
@Table(name = "reflex_question_progress_history")
public class ReflexQuestionProgressHistory extends BaseAuditEntity {

    public enum AttemptType { WRITING, SPEAKING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reflex_question_progress_id", nullable = false)
    private ReflexQuestionProgress reflexQuestionProgress;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_question_id", nullable = false)
    private ReviewVideoQuestion reviewVideoQuestion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_assignment_id", nullable = false)
    private ReviewVideoAssignment reviewVideoAssignment;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_type", nullable = false, length = 20)
    private AttemptType attemptType;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @Column(name = "audio_url", length = 1000)
    private String audioUrl;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "max_score", precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "marked_answer", columnDefinition = "TEXT")
    private String markedAnswer;

    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "criteria_scores", columnDefinition = "jsonb")
    private List<CriteriaScoreItem> criteriaScores;

    @Column(name = "graded_at")
    private OffsetDateTime gradedAt;
}
