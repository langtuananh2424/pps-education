package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng leave_requests (SDD > Nhân sự > Đơn từ > a) — đơn từ của 1 nhân sự
 * (UC-10 nộp, UC-11 duyệt theo workflow nhiều bước).
 */
@Getter
@Setter
@Entity
@Table(name = "leave_requests")
public class LeaveRequest {

    public enum LeaveType { ANNUAL, SICK, UNPAID, LATE, EARLY_LEAVE, PERSONAL }

    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Áp dụng LATE/EARLY_LEAVE. */
    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "total_days", nullable = false, precision = 4, scale = 2)
    private BigDecimal totalDays;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "current_step", nullable = false)
    private int currentStep = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_approver_id")
    private User currentApprover;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;
}
