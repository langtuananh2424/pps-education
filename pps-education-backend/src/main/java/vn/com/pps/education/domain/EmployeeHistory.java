package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Bảng employees_history (SDD > Nhân sự > Hồ sơ nhân sự — "Có
 * employees_history", không định nghĩa cột) — lịch sử phiên bản hồ sơ nhân
 * sự (UC-08 Main Flow bước 5). Áp dụng lại pattern JSONB diff-log của
 * permission_audit_log (đã xác nhận với PM thay vì tự sáng tác schema mới).
 */
@Getter
@Setter
@Entity
@Table(name = "employees_history")
public class EmployeeHistory {

    public enum Action { CREATED, UPDATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

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
