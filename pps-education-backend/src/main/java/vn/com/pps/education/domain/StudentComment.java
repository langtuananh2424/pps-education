package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Bảng student_comments (SDD > Học thuật > Nhận xét định kỳ > a) — nhận
 * xét học sinh hàng ngày theo buổi học (UC-21 Viết nhận xét, UC-22 Duyệt
 * nhận xét). Chốt lại 2026-08-12 (đã xác nhận với người dùng) — bỏ hẳn 2
 * biểu mẫu MID_TERM/END_TERM (nhận xét theo kỳ đánh giá), nay chỉ còn 1
 * biểu mẫu DAILY; nhận xét theo kỳ đánh giá dùng field {@code comment}
 * trong {@link GradeEvaluationResult} (sổ điểm, UC-19/53) thay thế.
 * Workflow DRAFT→PENDING(submit)→APPROVED/REJECTED, dùng chung
 * ApprovalFlow (entity_type=STUDENT_COMMENT) — xem Javadoc StudentCommentService.
 */
@Getter
@Setter
@Entity
@Table(name = "student_comments")
public class StudentComment {

    /** Chốt lại 2026-08-12 (đã xác nhận với người dùng) — bỏ hẳn MID_TERM/END_TERM (nhận xét theo kỳ đánh giá), thay bằng field {@code comment} trong {@link GradeEvaluationResult} (sổ điểm, UC-19/53). Chỉ còn DAILY. */
    public enum CommentType { DAILY }

    public enum Severity { POSITIVE, NORMAL, CONCERN, WARNING }

    public enum Status { DRAFT, PENDING, APPROVED, REJECTED }

    /**
     * Thái độ học tập buổi đó (chỉ dùng khi commentType=DAILY, bổ sung
     * ngoài SDD gốc). Chốt lại 2026-08-12 (thay cho thang 6 mức
     * POOR/WEAK/AVERAGE/ABOVE_AVERAGE/FAIR/GOOD ngày 2026-07-27, đã xác
     * nhận với người dùng — bỏ Kém/Trung bình khá, thêm Xuất sắc) — mỗi
     * mức quy đổi 1 tỷ lệ % cố định, dùng để tính "Thái độ học tập"
     * trung bình (Portal): WEAK=Yếu 20%, AVERAGE=Trung bình 50%,
     * FAIR=Khá 70%, GOOD=Tốt 90%, EXCELLENT=Xuất sắc 100%. Cột DB chỉ là
     * VARCHAR(20) thường (không CHECK constraint) — dữ liệu cũ có
     * ABOVE_AVERAGE đã được backfill sang FAIR bởi V116 (xem migration).
     */
    public enum Attitude { WEAK, AVERAGE, FAIR, GOOD, EXCELLENT }

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_session_id", nullable = false)
    private ClassSession classSession;

    /** Copy từ schoolClass.academicYear tại thời điểm tạo (V102/V103, bổ sung ngoài SDD gốc, đã xác nhận với người dùng). */
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

    /**
     * "BTVN buổi trước — Offline — Reading" (V135, bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-08-21) — điểm % giáo viên tự chấm tay bài Reading giao offline buổi trước. CHỈ áp dụng khi
     * {@code classSession.teacherType=VIETNAMESE}; buổi FOREIGN tiếp tục dùng {@link #homeworkPreviousScore}
     * như trước (cột "Offline" gộp, không tách Reading/Writing) — xem Javadoc StudentCommentService.
     */
    @Column(name = "homework_previous_reading_score", length = 30)
    private String homeworkPreviousReadingScore;

    /** Mirror {@link #homeworkPreviousReadingScore} cho kỹ năng Writing (V135). */
    @Column(name = "homework_previous_writing_score", length = 30)
    private String homeworkPreviousWritingScore;

    /**
     * VD "Unit 4 Trang 18" — BTVN ngữ pháp OFFLINE giao cho buổi SAU, hạn
     * nộp ngầm hiểu là ngày buổi học kế tiếp (V55).
     *
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-18 — GIAO
     * ĐỒNG THỜI với homeworkNextExerciseAssignment (kênh ONLINE): trước
     * đây 2 field này loại trừ lẫn nhau (chỉ 1 khác NULL), nay độc lập
     * hoàn toàn — 1 buổi có thể vừa giao BTVN offline (chữ tự do) vừa
     * giao 1 Exercise online cho cùng kênh Ngữ pháp.
     */
    @Column(name = "homework_next", columnDefinition = "TEXT")
    private String homeworkNext;

    /**
     * "BTVN — Offline — Reading" (V135, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-21) —
     * mô tả (bài + trang) BTVN Reading giao offline cho buổi sau. CHỈ áp dụng khi
     * {@code classSession.teacherType=VIETNAMESE}; buổi FOREIGN tiếp tục dùng {@link #homeworkNext} như
     * trước (cột "Offline" gộp, không tách Reading/Writing).
     */
    @Column(name = "homework_next_reading", columnDefinition = "TEXT")
    private String homeworkNextReading;

    /** Mirror {@link #homeworkNextReading} cho kỹ năng Writing (V135). */
    @Column(name = "homework_next_writing", columnDefinition = "TEXT")
    private String homeworkNextWriting;

    /**
     * BTVN ngữ pháp ONLINE giao cho buổi sau — NULL = không giao gì qua
     * kênh này. Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — xem
     * V55. Từ 2026-08-18 (đã xác nhận với người dùng), field này KHÔNG
     * còn loại trừ với homeworkNext (offline) — cả 2 giao được đồng thời,
     * xem Javadoc homeworkNext.
     *
     * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * chọn 1 Exercise khiến {@code StudentCommentService} tự tạo/tái dùng
     * 1 {@link ExerciseAssignment} giao cho TOÀN BỘ học sinh ACTIVE của
     * lớp (không chỉ học sinh đang được nhận xét — đảo ngược ý "theo từng
     * học sinh" của V55), hạn nộp = buổi học kế tiếp. Xem
     * StudentCommentService#resolveExerciseHomework.
     *
     * V127 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19)
     * — field này KHÔNG còn là điểm phát sinh giao bài: chỉ có giá trị
     * SAU KHI Giáo viên bấm "Gửi nhận xét" (submitComments() thật sự gọi
     * resolveExerciseHomework). Lựa chọn CHƯA giao (còn DRAFT/REJECTED)
     * nằm ở {@link #pendingHomeworkNextExerciseId} — xem Javadoc field đó.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homework_next_exercise_assignment_id")
    private ExerciseAssignment homeworkNextExerciseAssignment;

    /**
     * V144 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — "giao cả Đề" (chọn 1
     * {@link Exam}, tự động giao TẤT CẢ Bài Published trong Đề, xem {@code ExamService#deliverToClass})
     * thay cho chọn giao riêng 1 Bài ({@link #homeworkNextExerciseAssignment}, VẪN GIỮ song song cho
     * đường "giao lẻ" nâng cao khi cần). CHỈ 1 trong 2 field có giá trị tại 1 thời điểm cho kênh Ngữ
     * pháp — mirror vòng đời V65/V127: chỉ có giá trị SAU submit, lựa chọn CHƯA giao ở
     * {@link #pendingHomeworkNextExamId}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homework_next_exam_id")
    private Exam homeworkNextExam;

    /**
     * BTVN Video Ôn tập giao cho buổi sau — luôn ONLINE, NULL = không
     * giao video. V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-07-30): đổi từ trỏ thẳng {@code ReviewVideoSet} (V55) sang trỏ
     * {@link ReviewVideoAssignment} — cùng cơ chế giao cả lớp + hạn nộp =
     * buổi kế tiếp như kênh Ngữ pháp ở trên, xem
     * StudentCommentService#resolveVideoHomework.
     *
     * V127 — mirror {@link #homeworkNextExerciseAssignment}: chỉ có giá
     * trị SAU submit, lựa chọn CHƯA giao ở {@link #pendingHomeworkNextReviewVideoSetId}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homework_next_review_video_assignment_id")
    private ReviewVideoAssignment homeworkNextReviewVideoAssignment;

    /**
     * "BTVN — Online — Reading" (V137, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-21) —
     * mirror {@link #homeworkNextExerciseAssignment} (kênh Ngữ pháp/TV+NP) nhưng cho kênh Reading, trỏ
     * 1 {@link ExerciseAssignment} của Exercise thuộc Đề có {@code skillCategory=READING} (xem Exam.SkillCategory).
     * CHỈ áp dụng khi {@code classSession.teacherType=VIETNAMESE}. Cùng vòng đời V65/V127 với kênh Ngữ
     * pháp — chỉ có giá trị SAU submit, lựa chọn CHƯA giao nằm ở {@link #pendingHomeworkNextReadingExerciseId}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homework_next_reading_exercise_assignment_id")
    private ExerciseAssignment homeworkNextReadingExerciseAssignment;

    /** Mirror {@link #homeworkNextReadingExerciseAssignment} cho kỹ năng Writing (V137). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homework_next_writing_exercise_assignment_id")
    private ExerciseAssignment homeworkNextWritingExerciseAssignment;

    /** Mirror {@link #homeworkNextExam} cho kỹ năng Reading (V144) — Đề phải có skillCategory=READING. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homework_next_reading_exam_id")
    private Exam homeworkNextReadingExam;

    /** Mirror {@link #homeworkNextExam} cho kỹ năng Writing (V144) — Đề phải có skillCategory=WRITING. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homework_next_writing_exam_id")
    private Exam homeworkNextWritingExam;

    @Column(columnDefinition = "TEXT")
    private String note;

    // ===== BTVN buổi sau CHƯA giao (còn DRAFT/REJECTED) — V127 =====
    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19 — trước V127,
    // homeworkNextExerciseAssignment/homeworkNextReviewVideoAssignment ở trên vừa lưu
    // LỰA CHỌN vừa là ĐIỂM GIAO BÀI (chọn xong là giao ngay, kể cả lúc Lưu nháp — trước cả khi
    // Gửi nhận xét/được duyệt). Giờ 3 field dưới đây lưu TẠM lựa chọn thô (id nguồn, không phải
    // id bản giao) trong lúc còn DRAFT/REJECTED; StudentCommentService#submitComments() mới
    // thật sự materialize sang 2 field FK ở trên (gọi lại đúng resolveExerciseHomework/
    // resolveVideoHomework, không đổi logic) — xem Javadoc submitComments.

    /** Id Exercise (nguồn, không phải id bản giao) Giáo viên vừa chọn nhưng CHƯA Gửi nhận xét. */
    @Column(name = "pending_homework_next_exercise_id")
    private Long pendingHomeworkNextExerciseId;

    /** Id ReviewVideoSet (nguồn) Giáo viên vừa chọn nhưng CHƯA Gửi nhận xét — mirror {@link #pendingHomeworkNextExerciseId}. */
    @Column(name = "pending_homework_next_review_video_set_id")
    private Long pendingHomeworkNextReviewVideoSetId;

    /** Id Exercise (nguồn, skillCategory=READING) Giáo viên vừa chọn nhưng CHƯA Gửi nhận xét (V137) — mirror {@link #pendingHomeworkNextExerciseId}. */
    @Column(name = "pending_homework_next_reading_exercise_id")
    private Long pendingHomeworkNextReadingExerciseId;

    /** Mirror {@link #pendingHomeworkNextReadingExerciseId} cho kỹ năng Writing, skillCategory=WRITING (V137). */
    @Column(name = "pending_homework_next_writing_exercise_id")
    private Long pendingHomeworkNextWritingExerciseId;

    /** Id Exam (nguồn, không phải id bản giao) Giáo viên vừa chọn "giao cả Đề" nhưng CHƯA Gửi nhận xét (V144) — mirror {@link #pendingHomeworkNextExerciseId}. */
    @Column(name = "pending_homework_next_exam_id")
    private Long pendingHomeworkNextExamId;

    /** Mirror {@link #pendingHomeworkNextExamId} cho kỹ năng Reading (V144). */
    @Column(name = "pending_homework_next_reading_exam_id")
    private Long pendingHomeworkNextReadingExamId;

    /** Mirror {@link #pendingHomeworkNextExamId} cho kỹ năng Writing (V144). */
    @Column(name = "pending_homework_next_writing_exam_id")
    private Long pendingHomeworkNextWritingExamId;

    /** Hạn nộp tự chọn (giờ tường thuật thô, chưa quy đổi múi giờ) đi kèm lựa chọn CHƯA giao ở trên — quy đổi thật ở resolveDueAt() lúc Gửi. */
    @Column(name = "pending_homework_next_due_date")
    private LocalDateTime pendingHomeworkNextDueDate;
}
