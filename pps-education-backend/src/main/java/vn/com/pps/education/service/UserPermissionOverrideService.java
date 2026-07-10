package vn.com.pps.education.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Permission;
import vn.com.pps.education.domain.PermissionAuditLog;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserPermissionOverride;
import vn.com.pps.education.dto.EffectivePermissionsResponse;
import vn.com.pps.education.dto.UserPermissionOverrideRequest;
import vn.com.pps.education.exception.AccountInactiveException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.PermissionAuditLogRepository;
import vn.com.pps.education.repository.PermissionRepository;
import vn.com.pps.education.repository.UserPermissionOverrideRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;

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

    public UserPermissionOverrideService(UserRepository userRepository,
                                          PermissionRepository permissionRepository,
                                          UserPermissionOverrideRepository userPermissionOverrideRepository,
                                          PermissionAuditLogRepository permissionAuditLogRepository,
                                          PermissionEvaluationService permissionEvaluationService) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.userPermissionOverrideRepository = userPermissionOverrideRepository;
        this.permissionAuditLogRepository = permissionAuditLogRepository;
        this.permissionEvaluationService = permissionEvaluationService;
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quyền id=" + permissionId));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản thực hiện id=" + actorUserId));

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

        writeAuditLog(actor, targetUser, PermissionAuditLog.Action.PERM_OVERRIDE_ADDED, permission, httpRequest);
    }

    /** A2 -- gỡ override: tự động hết hiệu lực (giữ row cho audit) thay vì xóa cứng. */
    @Transactional
    public void removeOverride(Long targetUserId, Long permissionId, Long actorUserId, HttpServletRequest httpRequest) {
        UserPermissionOverride override = userPermissionOverrideRepository
                .findByUserIdAndPermissionId(targetUserId, permissionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy override cho user=%d, permission=%d".formatted(targetUserId, permissionId)));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản thực hiện id=" + actorUserId));

        override.setExpiresAt(OffsetDateTime.now());
        userPermissionOverrideRepository.save(override);

        writeAuditLog(actor, override.getUser(), PermissionAuditLog.Action.PERM_OVERRIDE_REMOVED,
                override.getPermission(), httpRequest);
    }

    private void writeAuditLog(User actor, User target, PermissionAuditLog.Action action, Permission permission,
                                HttpServletRequest httpRequest) {
        PermissionAuditLog log = new PermissionAuditLog();
        log.setActorUser(actor);
        log.setTargetUser(target);
        log.setAction(action);
        log.setTargetPermission(permission);
        log.setIpAddress(httpRequest.getRemoteAddr());
        permissionAuditLogRepository.save(log);
    }

    private User getActiveUserOrThrow(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + userId));
        if (user.getStatus() != User.Status.ACTIVE) {
            throw new AccountInactiveException("Tài khoản không hoạt động.");
        }
        return user;
    }
}
