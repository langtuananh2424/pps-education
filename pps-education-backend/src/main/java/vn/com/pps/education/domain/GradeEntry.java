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
 * UC-20 công bố điểm). V39 (bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng): bỏ luồng duyệt/từ chối — chỉ còn DRAFT (chưa công bố) → PUBLISHED
 * (công bố). Sửa điểm KHÔNG còn khoá theo status — actor còn trong hạn X
 * ngày (grade_period_edit_windows) hoặc có quyền
 * academic.grade.edit.override thì sửa được kể cả khi đã PUBLISHED (giá
 * trị mới hiển thị ngay cho Phụ huynh, không cần công bố lại).
 *
 * Cột submitted_at/approval_flow_id vẫn còn trong DB (lịch sử luồng
 * duyệt cũ) nhưng không còn map ở entity — không còn submit/ApprovalFlow
 * cho điểm nữa.
 */
@Getter
@Setter
@Entity
@Table(name = "grade_entries")
public class GradeEntry {

    public enum Status { DRAFT, PUBLISHED }

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
    @JoinColumn(name = "grade_component_id", nullable = false)
    private GradeComponent gradeComponent;

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
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private User publishedBy;
}
