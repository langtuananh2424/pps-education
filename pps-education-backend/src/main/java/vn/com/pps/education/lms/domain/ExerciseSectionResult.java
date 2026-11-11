package vn.com.pps.education.lms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.com.pps.education.common.BaseAuditEntity;
import vn.com.pps.education.student.domain.Student;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bảng exercise_section_results (V226, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-09) —
 * kết quả 1 bài tập con của 1 học sinh trong 1 lần giao. Xem Javadoc ForcedListeningService.
 */
@Getter
@Setter
@Entity
@Table(name = "exercise_section_results")
public class ExerciseSectionResult extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_assignment_id", nullable = false)
    private ExerciseAssignment exerciseAssignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_section_id", nullable = false)
    private ExerciseSection exerciseSection;

    /** Lượt nộp gần nhất mà bài được mở — chỉ để hiển thị. */
    @Column(name = "latest_score_percent", precision = 5, scale = 2)
    private BigDecimal latestScorePercent;

    /** NULL đến khi ĐẠT lần đầu; sau đó = max các lượt có điểm >= mức cần đạt, không bao giờ giảm. */
    @Column(name = "best_score_percent", precision = 5, scale = 2)
    private BigDecimal bestScorePercent;

    @Column(nullable = false)
    private boolean passed = false;

    /** Số lượt nộp bài mở lại, chưa từng đạt, có trả lời >= 1 câu, điểm < mức cần đạt (nộp trắng không tính). */
    @Column(name = "failed_attempts_count", nullable = false)
    private int failedAttemptsCount = 0;

    /** Các câu đã được mở tapescript — giữ vĩnh viễn. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unlocked_question_ids", columnDefinition = "jsonb", nullable = false)
    private List<Long> unlockedQuestionIds = new ArrayList<>();

    @Column(name = "first_passed_at")
    private OffsetDateTime firstPassedAt;
}
