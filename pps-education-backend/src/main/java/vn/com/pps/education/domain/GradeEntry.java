package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng grade_entries (SDD > Học thuật > Sổ điểm & Điểm tổng kết > c) —
 * điểm cụ thể của 1 học sinh cho 1 thành phần điểm (UC-19 nhập điểm,
 * UC-20 duyệt/từ chối điểm). V95 (bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng): {@code gradeComponent} trỏ tới {@link GradeEvaluationComponent}
 * (thay {@code GradeComponent} cũ, gắn theo {@link GradeComponentSetup}
 * — lớp + kỳ học + Giữa/Cuối kỳ — thay vì theo curriculum dùng chung
 * nhiều lớp). {@code academicTerm}/{@code evaluationType} denormalize từ
 * setup để báo cáo/thống kê truy vấn nhanh theo kỳ, không cần join.
 * V44 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — thay hẳn luồng
 * "công bố dự kiến + phúc khảo" UC-62 cũ):
 * luồng 4 trạng thái DRAFT → SUBMITTED → OFFICIAL / REJECTED:
 * <ul>
 *   <li>DRAFT: GV toàn quyền sửa/xoá, không giới hạn thời gian.</li>
 *   <li>SUBMITTED: chờ Quản lý điểm trường duyệt, không sửa được.</li>
 *   <li>OFFICIAL: duyệt xong, hiển thị cho phụ huynh ngay, khoá vĩnh viễn
 *       (chỉ override bỏ qua).</li>
 *   <li>REJECTED: bị từ chối, GV hoặc Quản lý có thể sửa lại → gửi duyệt
 *       lại (SUBMITTED) hoặc Quản lý duyệt trực tiếp (OFFICIAL).</li>
 * </ul>
 * Actor có quyền academic.grade.edit.override bỏ qua mọi ràng buộc sửa/xoá
 * (thêm/sửa/xoá bất kể trạng thái nào).
 */
@Getter
@Setter
@Entity
@Table(name = "grade_entries")
public class GradeEntry {

    public enum Status { DRAFT, SUBMITTED, OFFICIAL, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_evaluation_component_id", nullable = false)
    private GradeEvaluationComponent gradeComponent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_term_id", nullable = false)
    private AcademicTerm academicTerm;

    /** Copy từ schoolClass.academicYear tại thời điểm tạo (V101/V102, bổ sung ngoài SDD gốc, đã xác nhận với người dùng). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_type", nullable = false, length = 20)
    private GradeComponentSetup.EvaluationType evaluationType;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "absence_flag", nullable = false)
    private boolean absenceFlag = false;

    @Column(name = "teacher_note", columnDefinition = "TEXT")
    private String teacherNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entered_by", nullable = false)
    private User enteredBy;

    @Column(name = "entered_at", nullable = false)
    private OffsetDateTime enteredAt = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.DRAFT;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private User publishedBy;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;
}
