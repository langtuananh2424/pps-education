package vn.com.pps.education.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.PermissionAuditLog;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.RoleResponse;
import vn.com.pps.education.exception.AccountInactiveException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.PermissionAuditLogRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * UC-46: Gán/Thu hồi vai trò cho tài khoản (FR-PER-05).
 * Xem docs/uc/phan-he-02-phan-quyen.md — Main Flow, A1 (thu hồi), A2 (thu
 * hồi role chưa từng gán). Tách khỏi RoleService (UC-03 — cấu hình quyền
 * MẶC ĐỊNH của 1 role) và UserPermissionOverrideService (UC-04 — override
 * 1 permission lẻ cho 1 tài khoản) — 3 lý do thay đổi khác nhau (SOLID S).
 */
@Service
public class UserRoleAssignmentService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionAuditLogRepository permissionAuditLogRepository;

    public UserRoleAssignmentService(UserRepository userRepository,
                                      RoleRepository roleRepository,
                                      UserRoleRepository userRoleRepository,
                                      PermissionAuditLogRepository permissionAuditLogRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.permissionAuditLogRepository = permissionAuditLogRepository;
    }

    /** Main Flow bước 1: danh sách role hiện tại của 1 tài khoản. */
    @Transactional(readOnly = true)
    public List<RoleResponse> listAssignedRoles(Long userId) {
        getUserOrThrow(userId);
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRole)
                .sorted(Comparator.comparing(Role::getCode))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Main Flow bước 2-4: gán 1 role cho tài khoản — idempotent (đã gán
     * rồi thì không tạo trùng, không ghi thêm log). Yêu cầu tài khoản
     * đích đang ACTIVE (giống Precondition UC-04).
     */
    @Transactional
    public void assignRole(Long userId, Long roleId, Long actorUserId, HttpServletRequest httpRequest) {
        User target = getActiveUserOrThrow(userId);
        Role role = getRoleOrThrow(roleId);

        if (userRoleRepository.findByUserIdAndRoleId(userId, roleId).isPresent()) {
            return; // đã gán rồi -- idempotent
        }
        User actor = getUserOrThrow(actorUserId);

        UserRole userRole = new UserRole();
        userRole.setUser(target);
        userRole.setRole(role);
        userRole.setAssignedBy(actor);
        userRole.setAssignedAt(OffsetDateTime.now());
        userRoleRepository.save(userRole);

        writeAuditLog(actor, target, PermissionAuditLog.Action.ROLE_GRANTED, role, httpRequest);
    }

    /**
     * A1: thu hồi 1 role đã gán. Không yêu cầu tài khoản đang ACTIVE (giống
     * UC-04 A2 — vẫn thu hồi được role của tài khoản đã bị vô hiệu hóa).
     * A2: role chưa từng gán -- ResourceNotFoundException.
     */
    @Transactional
    public void revokeRole(Long userId, Long roleId, Long actorUserId, HttpServletRequest httpRequest) {
        UserRole userRole = userRoleRepository.findByUserIdAndRoleId(userId, roleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tài khoản id=%d chưa được gán role id=%d".formatted(userId, roleId)));
        User actor = getUserOrThrow(actorUserId);

        userRoleRepository.delete(userRole);

        writeAuditLog(actor, userRole.getUser(), PermissionAuditLog.Action.ROLE_REVOKED, userRole.getRole(),
                httpRequest);
    }

    private void writeAuditLog(User actor, User target, PermissionAuditLog.Action action, Role role,
                                HttpServletRequest httpRequest) {
        PermissionAuditLog log = new PermissionAuditLog();
        log.setActorUser(actor);
        log.setTargetUser(target);
        log.setAction(action);
        log.setTargetRole(role);
        log.setIpAddress(httpRequest.getRemoteAddr());
        permissionAuditLogRepository.save(log);
    }

    private User getActiveUserOrThrow(Long userId) {
        User user = getUserOrThrow(userId);
        if (user.getStatus() != User.Status.ACTIVE) {
            throw new AccountInactiveException("Tài khoản không hoạt động.");
        }
        return user;
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + userId));
    }

    private Role getRoleOrThrow(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy role id=" + roleId));
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isSystem());
    }
}
