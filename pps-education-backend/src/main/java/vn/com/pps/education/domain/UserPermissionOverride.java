package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Bảng user_permission_overrides (SDD > Nền tảng > g) — quyền ngoại lệ theo
 * tài khoản, độ ưu tiên cao nhất trong effective_permissions (UC-04).
 *
 * effective_permissions(user) = hợp(role_permissions theo user_roles)
 *                                - REVOKE override còn hiệu lực
 *                                + GRANT override còn hiệu lực
 */
@Getter
@Setter
@Entity
@Table(name = "user_permission_overrides", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "permission_id"}))
public class UserPermissionOverride {

    public enum OverrideType { GRANT, REVOKE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_type", nullable = false, length = 20)
    private OverrideType overrideType;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "granted_by", nullable = false)
    private User grantedBy;

    @Column(name = "granted_at", nullable = false)
    private OffsetDateTime grantedAt = OffsetDateTime.now();

    /** Cho phép ủy quyền có thời hạn, VD "GV được ủy quyền xếp lịch thay TPĐT". */
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
}
