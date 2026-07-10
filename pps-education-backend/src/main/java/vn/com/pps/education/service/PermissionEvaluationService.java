package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.UserPermissionOverride;
import vn.com.pps.education.repository.RolePermissionRepository;
import vn.com.pps.education.repository.UserPermissionOverrideRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-04: tính effective_permissions (dùng cho hiển thị UC-04 bước 2 và cho
 * PpsPermissionEvaluator ở tầng authorization). Công thức (docs/sdd-groups/
 * 02-nen-tang.md): hợp(role_permissions theo user_roles) - REVOKE override
 * còn hiệu lực + GRANT override còn hiệu lực. Tính lại mỗi lần gọi (không
 * cache) -- áp dụng ngay lập tức mà không cần cơ chế invalidate.
 */
@Service
public class PermissionEvaluationService {

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;

    public PermissionEvaluationService(UserRoleRepository userRoleRepository,
                                        RolePermissionRepository rolePermissionRepository,
                                        UserPermissionOverrideRepository userPermissionOverrideRepository) {
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userPermissionOverrideRepository = userPermissionOverrideRepository;
    }

    @Transactional(readOnly = true)
    public Set<String> getEffectivePermissions(Long userId) {
        Set<String> fromRoles = userRoleRepository.findByUserId(userId).stream()
                .flatMap(ur -> rolePermissionRepository.findByRoleId(ur.getRole().getId()).stream())
                .map(rp -> rp.getPermission().getCode())
                .collect(Collectors.toSet());

        List<UserPermissionOverride> activeOverrides = userPermissionOverrideRepository.findByUserId(userId).stream()
                .filter(this::isActive)
                .toList();

        Set<String> revoked = activeOverrides.stream()
                .filter(o -> o.getOverrideType() == UserPermissionOverride.OverrideType.REVOKE)
                .map(o -> o.getPermission().getCode())
                .collect(Collectors.toSet());
        Set<String> granted = activeOverrides.stream()
                .filter(o -> o.getOverrideType() == UserPermissionOverride.OverrideType.GRANT)
                .map(o -> o.getPermission().getCode())
                .collect(Collectors.toSet());

        Set<String> effective = new HashSet<>(fromRoles);
        effective.removeAll(revoked);
        effective.addAll(granted);
        return effective;
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(Long userId, String permissionCode) {
        return getEffectivePermissions(userId).contains(permissionCode);
    }

    /** UC-04 A1 -- override hết hạn tự động bị loại khỏi effective_permissions, không cần cron. */
    private boolean isActive(UserPermissionOverride override) {
        return override.getExpiresAt() == null || override.getExpiresAt().isAfter(OffsetDateTime.now());
    }
}
