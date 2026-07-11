package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Bảng leave_request_approvals (SDD > Nhân sự > Đơn từ > b) — từng bước
 * duyệt của 1 đơn từ (UC-11). approver_user_id chỉ điền khi có người thực
 * sự vào duyệt bước đó (SDD) — kể cả bước DEPARTMENT_HEAD đã biết trước
 * người duyệt qua leave_requests.current_approver_id.
 */
@Getter
@Setter
@Entity
@Table(name = "leave_request_approvals")
public class LeaveRequestApproval {

    public enum ApproverRole { DEPARTMENT_HEAD, OPERATIONS_MANAGER, EXECUTIVE }

    public enum Decision { APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_request_id", nullable = false)
    private LeaveRequest leaveRequest;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_role", nullable = false, length = 30)
    private ApproverRole approverRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_user_id")
    private User approverUser;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Decision decision;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;
}
