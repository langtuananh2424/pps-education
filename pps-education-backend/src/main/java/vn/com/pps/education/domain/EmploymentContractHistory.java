package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Bảng employment_contracts_history (SDD > Nhân sự > Hồ sơ nhân sự — "Có
 * employment_contracts_history", không định nghĩa cột) — lịch sử thay đổi
 * hợp đồng lao động (UC-08 Main Flow bước 5). Cùng pattern JSONB diff-log
 * với employees_history/permission_audit_log.
 */
@Getter
@Setter
@Entity
@Table(name = "employment_contracts_history")
public class EmploymentContractHistory {

    public enum Action { CREATED, UPDATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private EmploymentContract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Action action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
