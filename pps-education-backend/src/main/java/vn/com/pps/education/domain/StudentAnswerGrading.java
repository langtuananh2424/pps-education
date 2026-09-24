package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.common.KeyGrammarOutcome;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Bảng student_answer_grading (SDD > LMS & Portal > Ngân hàng câu hỏi &
 * Bài tập > i) — GV chấm tự luận/Nói (UC-41). Không có history — sửa
 * điểm tạo bản ghi mới (latest=true, cột DB is_final), bản cũ chuyển
 * latest=false thay vì UPDATE trực tiếp (SDD).
 */
@Getter
@Setter
@Entity
@Table(name = "student_answer_grading")
public class StudentAnswerGrading {

    /**
     * V138, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — HUMAN (GV chấm tay, UC-41)
     * hay AI (chấm tự động ESSAY thuộc Bài Exercise.skillCategory=WRITING, xem WritingAiGradingService/
     * ExerciseAttemptService#gradeAndFinalize). grader vẫn LUÔN là 1 User thật (dùng
     * exercise.createdBy khi gradingSource=AI, không thêm user hệ thống ảo) — field này chỉ để phân
     * biệt rõ trong dữ liệu/lịch sử, tránh hiểu nhầm giáo viên đã tự tay chấm.
     */
    public enum GradingSource { HUMAN, AI }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_answer_id", nullable = false)
    private StudentAnswer studentAnswer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grader_user_id", nullable = false)
    private User grader;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    /**
     * V182 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16, PILOT Khối 7 IELTS) — CHÍNH
     * bài viết của học sinh, đánh dấu lỗi bằng markup {@code {{mã|đoạn văn bản}}} (5 loại lỗi × 2 mức độ,
     * do AI chèn trực tiếp) — CHỈ có giá trị khi được chấm bằng rubric "v3", xem
     * {@link vn.com.pps.education.service.WritingAiGradingService}. NULL khi HUMAN chấm hoặc AI chấm
     * bằng rubric cũ (chưa lên v3) — khi đó vẫn dùng {@link #feedback} như trước.
     */
    @Column(name = "marked_answer", columnDefinition = "TEXT")
    private String markedAnswer;

    /** V182 — % từng tiêu chí rubric v3 (TR/TA, CC, LR, GRA...), tách riêng khỏi {@link #feedback}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "criteria_scores", columnDefinition = "jsonb")
    private List<CriteriaScoreItem> criteriaScores;

    /**
     * V196 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — Key Grammar (filter 2, gói
     * {@code key-grammar}) — {@code null} khi Bài không gắn Key Grammar hoặc chưa lên rubric "v3". FE dùng
     * {@code redoRequired} để hiện dải cảnh báo "cần viết lại bài" (UC-40/41 mở rộng).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_grammar", columnDefinition = "jsonb")
    private KeyGrammarOutcome keyGrammar;

    @Column(name = "graded_at", nullable = false)
    private OffsetDateTime gradedAt = OffsetDateTime.now();

    @Column(name = "is_final", nullable = false)
    private boolean latest = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_source", nullable = false, length = 10)
    private GradingSource gradingSource = GradingSource.HUMAN;
}
