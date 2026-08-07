package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Bảng student_comments (SDD > Học thuật > Nhận xét định kỳ > a) — nhận
 * xét học sinh theo 3 biểu mẫu (UC-21 Viết nhận xét, UC-22 Duyệt nhận
 * xét). Workflow DRAFT→PENDING(submit)→APPROVED/REJECTED, dùng chung
 * ApprovalFlow (entity_type=STUDENT_COMMENT) — xem Javadoc StudentCommentService.
 */
@Getter
@Setter
@Entity
@Table(name = "student_comments")
public class StudentComment {

    public enum CommentType { DAILY, MID_TERM, END_TERM }

    public enum Severity { POSITIVE, NORMAL, CONCERN, WARNING }

    public enum Status { DRAFT, PENDING, APPROVED, REJECTED }

    /**
     * Thái độ học tập buổi đó (chỉ dùng khi commentType=DAILY, bổ sung
     * ngoài SDD gốc, đã xác nhận với người dùng 2026-07-24, mở rộng từ 3
     * lên 6 mức 2026-07-27) — Kém/Yếu/Trung bình/Trung bình khá/Khá/Tốt.
     * Giữ nguyên tên hằng số POOR/AVERAGE/GOOD của 3 mức cũ (dữ liệu đã
     * lưu qua EnumType.STRING vẫn đọc được, không cần migration backfill).
     */
    public enum Attitude { POOR, WEAK, AVERAGE, ABOVE_AVERAGE, FAIR, GOOD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_user_id", nullable = false)
    private User teacher;

    @Enumerated(EnumType.STRING)
    @Column(name = "comment_type", nullable = false, length = 20)
    private CommentType commentType;

    /** Chỉ set khi commentType=DAILY (SDD). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_session_id")
    private ClassSession classSession;

    /** Chỉ set khi commentType=MID_TERM/END_TERM. V95 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): đổi từ GradePeriod (theo curriculum) sang AcademicTerm (theo kỳ học). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_term_id")
    private AcademicTerm academicTerm;

    /** Copy từ schoolClass.academicYear tại thời điểm tạo, áp dụng cho cả DAILY lẫn MID_TERM/END_TERM (V102/V103, bổ sung ngoài SDD gốc, đã xác nhận với người dùng). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYear;

    @Column(name = "comment_date", nullable = false)
    private LocalDate commentDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_content", columnDefinition = "jsonb")
    private Map<String, Object> structuredContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity = Severity.NORMAL;

    /** Cờ "PH cần chú ý ngay" — độc lập với severity (SDD). */
    @Column(name = "is_warning", nullable = false)
    private boolean warning = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_flow_id")
    private ApprovalFlow approvalFlow;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "visible_to_parent_at")
    private OffsetDateTime visibleToParentAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // ===== Nhận xét Hàng ngày kiểu mới (chỉ dùng khi commentType=DAILY) =====
    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-24.

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Attitude attitude;

    /** VD "80%", hoặc nhãn tự động "Chưa làm bài"/"Đang chờ chấm" khi chưa nhập tay (V68, xem StudentCommentService.resolvedGrammarPrevious). */
    @Column(name = "homework_previous_score", length = 30)
    private String homeworkPreviousScore;

    /** VD "80%" — chấm BTVN Nghe-nói (Video Ôn tập) buổi TRƯỚC buổi này, nhập tay độc lập với homeworkPreviousScore (Ngữ pháp). Bổ sung V56, nới độ dài V68. */
    @Column(name = "homework_previous_speaking_score", length = 30)
    private String homeworkPreviousSpeakingScore;

    /** VD "Unit 4 Trang 18" — BTVN giao cho buổi SAU, hạn nộp ngầm hiểu là ngày buổi học kế tiếp. Chỉ còn dùng cho kênh ngữ pháp OFFLINE (V55) — kênh ONLINE dùng homeworkNextExerciseAssignment. */
    @Column(name = "homework_next", columnDefinition = "TEXT")
    private String homeworkNext;

    /**
     * BTVN ngữ pháp ONLINE giao cho buổi sau — NULL = kênh này đang
     * OFFLINE (dùng homeworkNext) hoặc không giao gì. Bổ sung ngoài SDD
     * gốc, đã xác nhận với người dùng — xem V55.
     *
     * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * việc set field này giờ là ĐIỂM PHÁT SINH giao bài — chọn 1 Exercise
     * ở đây khiến {@code StudentCommentService} tự tạo/tái dùng 1
     * {@link ExerciseAssignment} giao cho TOÀN BỘ học sinh ACTIVE của
     * lớp (không chỉ học sinh đang được nhận xét — đảo ngược ý "theo từng
     * học sinh" của V55), hạn nộp = buổi học kế tiếp. Xem
     * StudentCommentService#resolveExerciseHomework.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homework_next_exercise_assignment_id")
    private ExerciseAssignment homeworkNextExerciseAssignment;

    /**
     * BTVN Video Ôn tập giao cho buổi sau — luôn ONLINE, NULL = không
     * giao video. V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-07-30): đổi từ trỏ thẳng {@code ReviewVideoSet} (V55) sang trỏ
     * {@link ReviewVideoAssignment} — cùng cơ chế giao cả lớp + hạn nộp =
     * buổi kế tiếp như kênh Ngữ pháp ở trên, xem
     * StudentCommentService#resolveVideoHomework.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homework_next_review_video_assignment_id")
    private ReviewVideoAssignment homeworkNextReviewVideoAssignment;

    @Column(columnDefinition = "TEXT")
    private String note;
}
