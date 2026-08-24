package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng exams (UC-40, bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-07-30) — "Đề" (VD: IELTS Grade 6), cấp cha của nhiều "Bài"
 * ({@link Exercise}, VD: Unit 1). curriculum CHỈ dùng để lọc/tìm kiếm
 * trong Kho đề — không phải điều kiện hiển thị cho lớp (xem
 * {@link ExamClassAssignment}, điều kiện hiển thị DUY NHẤT).
 */
@Getter
@Setter
@Entity
@Table(name = "exams")
public class Exam extends BaseAuditEntity {

    /** Đề dành cho GV Việt Nam hay GV nước ngoài — dùng để lọc khi giao bài (V74, bổ sung ngoài SDD gốc, 2026-08-04). */
    public enum TeacherType { VIETNAMESE, FOREIGN }

    /** "Loại đề" — độc lập với {@link Exercise#getExerciseType()}, không thay thế (V74, bổ sung ngoài SDD gốc, 2026-08-04). */
    public enum ExamType { REVIEW, HOMEWORK }

    /**
     * V144 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — chuyển hẳn từ
     * {@code Exercise.SkillCategory} (V136/V142) lên cấp Đề: giao bài giờ giao CẢ Đề (mọi Bài trong Đề
     * cùng lúc, xem {@code ExamService#deliverToClass}), nên 1 Đề = 1 nhóm kỹ năng thuần — mọi Bài bên
     * trong cùng nhóm này. READING = 1 bài đọc kèm nhiều câu MULTIPLE_CHOICE; WRITING = câu hỏi tự luận
     * (chủ yếu ESSAY, chấm AI); VOCAB_GRAMMAR = trắc nghiệm câu/điền từ/sắp xếp câu; LISTENING = bài
     * nghe (audioUrl). Bắt buộc chọn khi tạo Đề (CreateExamRequest), sửa được cùng title/teacherType/
     * examType. Dùng để lọc dropdown "chọn Đề Reading/Writing" ở Nhận xét học viên (UC-21).
     */
    public enum SkillCategory { READING, WRITING, VOCAB_GRAMMAR, LISTENING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 500)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    /**
     * V75: Ngân hàng câu hỏi nội bộ 1-1, tạo tự động cùng Đề. Đây là chi
     * tiết lưu trữ — API Giáo viên chỉ làm việc theo examId.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_bank_id", nullable = false, unique = true)
    private QuestionBank questionBank;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /** Bắt buộc chọn 1 trong 2 khi tạo Đề — sửa được cùng title (V74). */
    @Enumerated(EnumType.STRING)
    @Column(name = "teacher_type", nullable = false, length = 20)
    private TeacherType teacherType;

    /** Bắt buộc chọn 1 trong 2 khi tạo Đề — sửa được cùng title (V74). */
    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 20)
    private ExamType examType;

    /** NULL = chưa phân loại (dữ liệu cũ trước V144) — xem Javadoc {@link SkillCategory}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "skill_category", length = 20)
    private SkillCategory skillCategory;

    /**
     * V144 — chuyển từ {@code Exercise.passThresholdPercent} (V89/V100, mặc định 70%): tổng số câu trả
     * lời đúng / tổng số câu hỏi của TẤT CẢ Bài trong Đề đã giao cùng 1 đợt (không phải điểm — xem
     * ExerciseAttemptService#applyPassOutcome) phải đạt ngưỡng này mới tính "đạt", không thì phải làm
     * lại toàn bộ Đề.
     */
    @Column(name = "pass_threshold_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal passThresholdPercent = new BigDecimal("70.00");

    /** V144 — chuyển từ {@code Exercise.allowRetake}: chưa đạt (hoặc muốn thử lại) thì làm lại TOÀN BỘ Bài trong Đề, không riêng từng Bài. */
    @Column(name = "allow_retake", nullable = false)
    private boolean allowRetake = true;

    /** V144 — chuyển từ {@code Exercise.maxAttempts}. NULL = không giới hạn. */
    @Column(name = "max_attempts")
    private Integer maxAttempts;

    /**
     * V87 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — soft-delete "Xóa Đề", cùng
     * pattern PartnerContract/SchoolClass đã dùng (không xóa cứng vì exercises có thể đã tham chiếu).
     */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
