package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

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

    /**
     * V87 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — soft-delete "Xóa Đề", cùng
     * pattern PartnerContract/SchoolClass đã dùng (không xóa cứng vì exercises có thể đã tham chiếu).
     */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
