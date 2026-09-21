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
import java.util.Map;

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

    /**
     * V181 (2026-09-16, xác nhận với người dùng) — KHÔNG còn ghi feedback văn xuôi 7 mục dài dòng vào
     * đây nữa — xem {@link #writingMarkedAnswer}.
     *
     * V184 (2026-09-16, phát hiện qua test thật trên staging, xác nhận với người dùng) — tái dùng field
     * này cho 2 trường hợp NGẮN GỌN cần giải thích lý do: (1) AI chấm thất bại (xem
     * ReflexSequentialGradingService#AI_GRADING_FAILED_FEEDBACK), (2) Cổng chặn của rubric kích hoạt (VD
     * "Quá ngắn") — {@code gateNote} từ ReflexWritingGrammarAiGradingService, tối đa 40 từ, GIẢI THÍCH
     * vì sao điểm thấp dù markedAnswer không bôi đỏ lỗi nào (đã xảy ra thật: học sinh không hiểu vì sao
     * bị điểm thấp khi câu đúng ngữ pháp nhưng quá ngắn). NULL khi không rơi vào 2 trường hợp trên.
     */
    @Column(name = "writing_feedback", columnDefinition = "TEXT")
    private String writingFeedback;

    /**
     * V181 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16) — chính câu trả lời viết của
     * học sinh, có đánh dấu lỗi ngữ pháp/từ vựng bằng markup {@code {{err}}...{{/err}}} do AI chèn trực
     * tiếp trong chuỗi (xem ReflexWritingGrammarAiGradingService) — FE tự regex-split để bôi đỏ/gạch
     * chân, đồng nhất cách hiển thị với {@link #speakingTranscript} (V178). Thay cho feedback văn xuôi
     * 7 mục dài dòng trước đây.
     */
    @Column(name = "writing_marked_answer", columnDefinition = "TEXT")
    private String writingMarkedAnswer;

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

    /**
     * V178 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16) — transcript của audio, có
     * đánh dấu lỗi ngữ pháp/từ vựng bằng markup {@code {{err}}...{{/err}}} do AI chèn trực tiếp trong
     * chuỗi (xem ReflexSpeakingContentAiGradingService) — FE tự regex-split để bôi đỏ/gạch chân.
     */
    @Column(name = "speaking_transcript", columnDefinition = "TEXT")
    private String speakingTranscript;

    /**
     * V178 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16) — % từng tiêu chí rubric,
     * tách riêng khỏi {@link #speakingFeedback} (trước đây nhúng thành dòng text trong feedback).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "speaking_criteria_scores", columnDefinition = "jsonb")
    private List<CriteriaScoreItem> speakingCriteriaScores;

    @Column(name = "speaking_graded_at")
    private OffsetDateTime speakingGradedAt;

    @Column(name = "speaking_attempt_count", nullable = false)
    private int speakingAttemptCount;

    /**
     * V165 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07) — true nếu bước viết HOẶC
     * ghi âm của dòng này từng được nộp sau dueAt (chỉ xảy ra khi
     * {@link ReviewVideoAssignment#isLateSubmissionAllowed()}). Không tự reset lại false — dòng này bị
     * sửa đè tại chỗ mỗi lần nộp lại (xem Javadoc lớp) nên chỉ cần 1 cờ duy nhất, không phải lịch sử.
     */
    @Column(name = "is_late_submission", nullable = false)
    private boolean lateSubmission = false;

    /**
     * V185 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21) — 'v2' nếu dòng này chấm bằng
     * bộ tiêu chí Speaking v2 (xem ReflexV2AiGradingService); NULL = luồng cũ. Bước Nói định tuyến theo cột này.
     */
    @Column(name = "rubric_version", length = 10)
    private String rubricVersion;

    /** V185 — điểm Ngữ pháp KHOÁ từ bước viết (luồng v2), mang sang bước nói. */
    @Column(name = "writing_locked_grammar_percent", precision = 5, scale = 2)
    private BigDecimal writingLockedGrammarPercent;

    /** V185 — số lỗi đỏ tô được trong bài viết (luồng v2), đầu vào của trần lỗi đỏ ở bước nói. */
    @Column(name = "writing_red_error_count")
    private Integer writingRedErrorCount;

    /** V185 — bằng chứng chấm bước viết (luồng v2), phục vụ hiệu chuẩn; KHÔNG trả ra FE. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "writing_audit", columnDefinition = "jsonb")
    private Map<String, Object> writingAudit;

    /** V185 — bằng chứng chấm bước nói (luồng v2), phục vụ hiệu chuẩn; KHÔNG trả ra FE. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "speaking_audit", columnDefinition = "jsonb")
    private Map<String, Object> speakingAudit;
}
