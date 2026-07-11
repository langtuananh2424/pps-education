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
 * UC-20 duyệt điểm). Workflow DRAFT → PENDING (submit, UC-19) →
 * APPROVED/REJECTED (UC-20) — REJECTED quay lại DRAFT khi Giáo viên sửa
 * (enterGrade), khác với curriculums (không có REJECTED riêng).
 */
@Getter
@Setter
@Entity
@Table(name = "grade_entries")
public class GradeEntry {

    public enum Status { DRAFT, PENDING, APPROVED, REJECTED }

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
}
