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

    /** Chỉ set khi commentType=MID_TERM/END_TERM (SDD). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_period_id")
    private GradePeriod gradePeriod;

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
}
