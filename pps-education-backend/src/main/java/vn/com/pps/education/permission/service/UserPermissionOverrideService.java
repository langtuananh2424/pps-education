package vn.com.pps.education.permission.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.permission.domain.Permission;
import vn.com.pps.education.permission.domain.PermissionAuditLog;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.permission.domain.UserPermissionOverride;
import vn.com.pps.education.permission.dto.EffectivePermissionsResponse;
import vn.com.pps.education.permission.dto.UserPermissionOverrideRequest;
import vn.com.pps.education.exception.AccountInactiveException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.permission.repository.PermissionAuditLogRepository;
import vn.com.pps.education.permission.repository.PermissionRepository;
import vn.com.pps.education.permission.repository.UserPermissionOverrideRepository;
import vn.com.pps.education.auth.repository.UserRepository;
import vn.com.pps.education.security.ClientIpResolver;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * UC-04: Tùy chỉnh quyền riêng cho tài khoản (FR-PER-03).
 * Xem docs/uc/phan-he-02-phan-quyen.md — Main Flow bước 3-6, A2 (gỡ override).
 * Tách khỏi PermissionEvaluationService (SRP — "tính toán" vs "sửa + ghi audit").
 */
@Service
public class UserPermissionOverrideService {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;
    private final PermissionAuditLogRepository permissionAuditLogRepository;
    private final PermissionEvaluationService permissionEvaluationService;
    private final ClientIpResolver clientIpResolver;

    public UserPermissionOverrideService(UserRepository userRepository,
                                          PermissionRepository permissionRepository,
                                          UserPermissionOverrideRepository userPermissionOverrideRepository,
                                          PermissionAuditLogRepository permissionAuditLogRepository,
                                          PermissionEvaluationService permissionEvaluationService,
                                          ClientIpResolver clientIpResolver) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.userPermissionOverrideRepository = userPermissionOverrideRepository;
        this.permissionAuditLogRepository = permissionAuditLogRepository;
        this.permissionEvaluationService = permissionEvaluationService;
        this.clientIpResolver = clientIpResolver;
    }

    @Transactional(readOnly = true)
    public EffectivePermissionsResponse getEffectivePermissions(Long targetUserId) {
        getActiveUserOrThrow(targetUserId);
        return new EffectivePermissionsResponse(targetUserId, permissionEvaluationService.getEffectivePermissions(targetUserId));
    }

    @Transactional
    public void upsertOverride(Long targetUserId, Long permissionId, UserPermissionOverrideRequest request,
                                Long actorUserId, HttpServletRequest httpRequest) {
        User targetUser = getActiveUserOrThrow(targetUserId);
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("error.userPermissionOverride.permissionNotFound",
                        new Object[]{permissionId}, "Không tìm thấy quyền id=" + permissionId));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.userPermissionOverride.actorNotFound",
                        new Object[]{actorUserId}, "Không tìm thấy tài khoản thực hiện id=" + actorUserId));

        // UNIQUE(user_id, permission_id) -- đã tồn tại thì cập nhật thay vì insert trùng
        UserPermissionOverride override = userPermissionOverrideRepository
                .findByUserIdAndPermissionId(targetUserId, permissionId)
                .orElseGet(() -> {
                    UserPermissionOverride created = new UserPermissionOverride();
                    created.setUser(targetUser);
                    created.setPermission(permission);
                    return created;
                });
        override.setOverrideType(UserPermissionOverride.OverrideType.valueOf(request.overrideType()));
        override.setReason(request.reason());
        override.setExpiresAt(request.expiresAt());
        override.setGrantedBy(actor);
        override.setGrantedAt(OffsetDateTime.now());
        userPermissionOverrideRepository.save(override);

        Map<String, Object> details = new HashMap<>();
        details.put("overrideType", override.getOverrideType().name());
        details.put("reason", override.getReason());
        details.put("expiresAt", override.getExpiresAt() != null ? override.getExpiresAt().toString() : null);
        writeAuditLog(actor, targetUser, PermissionAuditLog.Action.PERM_OVERRIDE_ADDED, permission, details, httpRequest);
    }

    /** A2 -- gỡ override: tự động hết hiệu lực (giữ row cho audit) thay vì xóa cứng. */
    @Transactional
    public void removeOverride(Long targetUserId, Long permissionId, Long actorUserId, HttpServletRequest httpRequest) {
        UserPermissionOverride override = userPermissionOverrideRepository
                .findByUserIdAndPermissionId(targetUserId, permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("error.userPermissionOverride.overrideNotFound",
                        new Object[]{targetUserId, permissionId},
                        "Không tìm thấy override cho user=%d, permission=%d".formatted(targetUserId, permissionId)));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.userPermissionOverride.actorNotFound",
                        new Object[]{actorUserId}, "Không tìm thấy tài khoản thực hiện id=" + actorUserId));

        Map<String, Object> details = new HashMap<>();
        details.put("overrideType", override.getOverrideType().name());
        details.put("reason", override.getReason());

        override.setExpiresAt(OffsetDateTime.now());
        userPermissionOverrideRepository.save(override);

        writeAuditLog(actor, override.getUser(), PermissionAuditLog.Action.PERM_OVERRIDE_REMOVED,
                override.getPermission(), details, httpRequest);
    }

    private void writeAuditLog(User actor, User target, PermissionAuditLog.Action action, Permission permission,
                                Map<String, Object> details, HttpServletRequest httpRequest) {
        PermissionAuditLog log = new PermissionAuditLog();
        log.setActorUser(actor);
        log.setTargetUser(target);
        log.setAction(action);
        log.setTargetPermission(permission);
        log.setDetails(details);
        log.setIpAddress(clientIpResolver.resolve(httpRequest));
        permissionAuditLogRepository.save(log);
    }

    private User getActiveUserOrThrow(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.userPermissionOverride.accountNotFound",
                        new Object[]{userId}, "Không tìm thấy tài khoản id=" + userId));
        if (user.getStatus() != User.Status.ACTIVE) {
            throw new AccountInactiveException("error.accountInactive.short", new Object[]{},
                    "Tài khoản không hoạt động.");
        }
        return user;
    }
}
