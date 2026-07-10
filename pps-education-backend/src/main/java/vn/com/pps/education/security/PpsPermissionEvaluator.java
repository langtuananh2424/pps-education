package vn.com.pps.education.security;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import vn.com.pps.education.service.PermissionEvaluationService;

import java.io.Serializable;

/**
 * UC-02..UC-05 (Hybrid PBAC) — backing cho @PreAuthorize("hasPermission(...)").
 * targetDomainObject luôn null ở dự án này (check theo permission code, chưa
 * cần per-object ACL) — overload (targetId, targetType) chỉ là delegate mỏng.
 */
@Component
public class PpsPermissionEvaluator implements PermissionEvaluator {

    private final PermissionEvaluationService permissionEvaluationService;

    public PpsPermissionEvaluator(PermissionEvaluationService permissionEvaluationService) {
        this.permissionEvaluationService = permissionEvaluationService;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        return checkPermission(authentication, permission);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        return checkPermission(authentication, permission);
    }

    private boolean checkPermission(Authentication authentication, Object permission) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return false;
        }
        return permissionEvaluationService.hasPermission(user.userId(), String.valueOf(permission));
    }
}
