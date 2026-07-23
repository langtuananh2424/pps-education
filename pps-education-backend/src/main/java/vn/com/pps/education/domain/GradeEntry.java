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
<<<<<<< HEAD
 * UC-20 duyệt điểm). Workflow DRAFT → PENDING (submit, UC-19) →
 * APPROVED/REJECTED (UC-20) — REJECTED quay lại DRAFT khi Giáo viên sửa
 * (enterGrade), khác với curriculums (không có REJECTED riêng).
=======
 * UC-20 công bố điểm dự kiến, UC-62 phúc khảo). V43 (bổ sung ngoài SDD
 * gốc, đã xác nhận với người dùng — sửa đổi lần 2 sau V39): luồng 4
 * trạng thái DRAFT → PROVISIONAL_PUBLISHED → APPEAL → OFFICIAL, khoá sửa
 * hoàn toàn theo TRẠNG THÁI (không còn theo hạn X ngày như V39):
 * <ul>
 *   <li>DRAFT: GV toàn quyền sửa/xoá, không giới hạn thời gian.</li>
 *   <li>PROVISIONAL_PUBLISHED: khoá, GV thường không sửa/xoá được.</li>
 *   <li>APPEAL: chỉ sửa được nếu actor là người đã "tiếp nhận"
 *       ({@code grade_appeal_requests.status=ACCEPTED}) yêu cầu phúc
 *       khảo tương ứng — xem GradeAppealService/GradeService#requireEditableState.
 *       Sửa xong tự động quay lại PROVISIONAL_PUBLISHED (giữ nguyên
 *       publishedAt gốc, hạn Y ngày không bị reset).</li>
 *   <li>OFFICIAL: khoá vĩnh viễn, chỉ hệ thống tự động gán qua
 *       GradeSchedulerService sau khi hết hạn Y ngày
 *       (system_settings.academic.grade_appeal_window_days) kể từ
 *       publishedAt.</li>
 * </ul>
 * Actor có quyền academic.grade.edit.override bỏ qua mọi ràng buộc trên
 * (thêm/sửa/xoá bất kể trạng thái nào).
 *
 * Cột submitted_at/approval_flow_id vẫn còn trong DB (lịch sử luồng
 * duyệt cũ trước V39) nhưng không còn map ở entity.
>>>>>>> develop
 */
@Getter
@Setter
@Entity
@Table(name = "grade_entries")
public class GradeEntry {

<<<<<<< HEAD
    public enum Status { DRAFT, PENDING, APPROVED, REJECTED }
=======
    public enum Status { DRAFT, PROVISIONAL_PUBLISHED, APPEAL, OFFICIAL }
>>>>>>> develop

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
    @Column(nullable = false, length = 30)
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_flow_id")
    private ApprovalFlow approvalFlow;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
<<<<<<< HEAD
    @JoinColumn(name = "approved_by")
    private User approvedBy;
=======
    @JoinColumn(name = "published_by")
    private User publishedBy;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;
>>>>>>> develop
}
