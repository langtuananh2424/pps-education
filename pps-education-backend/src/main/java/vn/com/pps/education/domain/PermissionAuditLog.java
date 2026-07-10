package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Bảng permission_audit_log (SDD > Nền tảng > h) — nhật ký thay đổi phân
 * quyền (UC-05). UC-04 ghi PERM_OVERRIDE_ADDED/PERM_OVERRIDE_REMOVED;
 * ROLE_GRANTED/ROLE_REVOKED chưa có UC nào yêu cầu ghi (dành cho 1 UC gán
 * role cho tài khoản chưa được đặc tả) — enum định nghĩa đủ 4 giá trị để
 * UC-05 filter chấp nhận cả 4, nhưng chỉ 2 giá trị PERM_OVERRIDE_* được
 * ghi ở giai đoạn hiện tại.
 */
@Getter
@Setter
@Entity
@Table(name = "permission_audit_log")
public class PermissionAuditLog {

    public enum Action { ROLE_GRANTED, ROLE_REVOKED, PERM_OVERRIDE_ADDED, PERM_OVERRIDE_REMOVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actorUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Action action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_role_id")
    private Role targetRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_permission_id")
    private Permission targetPermission;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "ip_address")
    @JdbcTypeCode(SqlTypes.INET)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
