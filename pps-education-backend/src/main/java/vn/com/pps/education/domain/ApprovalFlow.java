package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng approval_flows (SDD > Nền tảng, migration V1) — luồng duyệt dùng
 * chung cho nhiều nghiệp vụ (entity_type). Lần đầu dùng ở UC-17 (phê
 * duyệt khung chương trình tùy biến, entity_type=CURRICULUM). Thiết kế
 * để tái sử dụng cho UC-19/20 (entity_type=GRADE_ENTRY) và UC-21/22
 * (entity_type=STUDENT_COMMENT) khi triển khai — xem
 * .claude/rules/solid.md (ví dụ ApprovalFlowRepository dùng chung).
 */
@Getter
@Setter
@Entity
@Table(name = "approval_flows")
public class ApprovalFlow extends BaseAuditEntity {

    public enum EntityType { CURRICULUM, STUDENT_COMMENT, GRADE_ENTRY, TEACHING_PLAN }

    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED }

    public enum Decision { APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private EntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Decision decision;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "batch_id")
    private UUID batchId;
}
