package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Permission;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.RoleHistory;
import vn.com.pps.education.domain.RolePermission;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.CreateRoleRequest;
import vn.com.pps.education.dto.PermissionMatrixItem;
import vn.com.pps.education.dto.RolePermissionMatrixResponse;
import vn.com.pps.education.dto.RoleResponse;
import vn.com.pps.education.dto.UpdateRolePermissionsRequest;
import vn.com.pps.education.exception.DuplicateRoleCodeException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RoleNotDeletableException;
import vn.com.pps.education.exception.RolePermissionConfirmationRequiredException;
import vn.com.pps.education.repository.PermissionAuditLogRepository;
import vn.com.pps.education.repository.PermissionRepository;
import vn.com.pps.education.repository.RoleHistoryRepository;
import vn.com.pps.education.repository.RolePermissionRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-03: Cấu hình nhóm quyền mặc định (FR-PER-02).
 * Xem docs/uc/phan-he-02-phan-quyen.md — Main Flow, A1 (xóa hết quyền của
 * role đang có tài khoản active cần xác nhận lại).
 *
 * Bổ sung: createRole/deleteRole — SDD ghi is_system=TRUE "không cho xóa
 * qua UI" (ngụ ý is_system=FALSE thì xóa được) nhưng chưa từng có
 * endpoint tạo/xóa. deleteRole chặn nếu role đã từng xuất hiện trong
 * permission_audit_log (UC-46 ROLE_GRANTED/ROLE_REVOKED) vì cột đó NOT
 * NULL REFERENCES — không thể xóa role mà không phá vỡ chứng cứ tra
 * soát của FR-PER-04, nên chỉ xóa được role tạo nhầm, chưa từng gán cho ai.
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleHistoryRepository roleHistoryRepository;
    private final PermissionAuditLogRepository permissionAuditLogRepository;
    private final UserRepository userRepository;

    public RoleService(RoleRepository roleRepository,
                        PermissionRepository permissionRepository,
                        RolePermissionRepository rolePermissionRepository,
                        UserRoleRepository userRoleRepository,
                        RoleHistoryRepository roleHistoryRepository,
                        PermissionAuditLogRepository permissionAuditLogRepository,
                        UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleHistoryRepository = roleHistoryRepository;
        this.permissionAuditLogRepository = permissionAuditLogRepository;
        this.userRepository = userRepository;
    }

    /** UC-03 bổ sung — tạo 1 vai trò tùy chỉnh (luôn is_system=false). */
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request, Long actorUserId) {
        if (roleRepository.findByCode(request.code()).isPresent()) {
            throw new DuplicateRoleCodeException("Mã vai trò đã tồn tại: " + request.code());
        }
        User actor = getUserOrThrow(actorUserId);

        Role role = new Role();
        role.setCode(request.code());
        role.setName(request.name());
        role.setDescription(request.description());
        role.setSystem(false);
        role = roleRepository.save(role);

        writeHistory(role, actor, RoleHistory.Action.CREATED);
        return toResponse(role);
    }

    /**
     * UC-03 bổ sung — xóa 1 vai trò tùy chỉnh (hard-delete). Chặn nếu:
     * (1) là 1 trong 11 role hệ thống; (2) đang gán cho tài khoản nào đó
     * (user_roles); (3) đã từng xuất hiện trong permission_audit_log
     * (UC-46) — cột target_role_id ở đó NOT NULL REFERENCES, xóa sẽ vi
     * phạm toàn vẹn dữ liệu và phá chứng cứ tra soát FR-PER-04.
     */
    @Transactional
    public void deleteRole(Long roleId, Long actorUserId) {
        Role role = getRoleOrThrow(roleId);
        if (role.isSystem()) {
            throw new RoleNotDeletableException("Không thể xóa vai trò hệ thống '" + role.getCode() + "'.");
        }
        if (!userRoleRepository.findByRoleId(roleId).isEmpty()) {
            throw new RoleNotDeletableException(
                    "Vai trò '" + role.getCode() + "' đang được gán cho tài khoản — thu hồi hết (UC-46) trước khi xóa.");
        }
        if (permissionAuditLogRepository.existsByTargetRoleId(roleId)) {
            throw new RoleNotDeletableException(
                    "Vai trò '" + role.getCode() + "' đã từng được gán/thu hồi cho tài khoản — không thể xóa để giữ đúng chứng cứ tra soát.");
        }
        User actor = getUserOrThrow(actorUserId);

        rolePermissionRepository.deleteAll(rolePermissionRepository.findByRoleId(roleId));
        writeHistory(role, actor, RoleHistory.Action.DELETED);
        roleRepository.delete(role);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getCode))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RolePermissionMatrixResponse getPermissionMatrix(Long roleId) {
        Role role = getRoleOrThrow(roleId);
        Set<Long> grantedIds = rolePermissionRepository.findByRoleId(roleId).stream()
                .map(rp -> rp.getPermission().getId())
                .collect(Collectors.toSet());

        List<PermissionMatrixItem> items = permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(Permission::getModule).thenComparing(Permission::getCode))
                .map(p -> new PermissionMatrixItem(p.getId(), p.getCode(), p.getName(), p.getModule(),
                        grantedIds.contains(p.getId())))
                .toList();

        return new RolePermissionMatrixResponse(role.getId(), role.getCode(), items);
    }

    @Transactional
    public void updatePermissions(Long roleId, UpdateRolePermissionsRequest request) {
        Role role = getRoleOrThrow(roleId);
        List<RolePermission> current = rolePermissionRepository.findByRoleId(roleId);
        Set<Long> currentIds = current.stream().map(rp -> rp.getPermission().getId()).collect(Collectors.toSet());
        Set<Long> requestedIds = new HashSet<>(request.permissionIds());

        // A1 -- bỏ hết quyền của role đang có tài khoản active, cần xác nhận lại
        boolean clearingAll = requestedIds.isEmpty() && !currentIds.isEmpty();
        if (clearingAll && !request.confirm() && hasActiveAccounts(roleId)) {
            throw new RolePermissionConfirmationRequiredException(
                    "Role '%s' đang có tài khoản hoạt động. Xác nhận lại (confirm=true) để xóa hết quyền."
                            .formatted(role.getCode()));
        }

        Set<Long> toAdd = new HashSet<>(requestedIds);
        toAdd.removeAll(currentIds);
        List<RolePermission> toRemove = current.stream()
                .filter(rp -> !requestedIds.contains(rp.getPermission().getId()))
                .toList();

        rolePermissionRepository.deleteAll(toRemove);

        List<RolePermission> newRows = toAdd.stream().map(permissionId -> {
            Permission permission = permissionRepository.findById(permissionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quyền id=" + permissionId));
            RolePermission rp = new RolePermission();
            rp.setRole(role);
            rp.setPermission(permission);
            return rp;
        }).toList();
        rolePermissionRepository.saveAll(newRows);
    }

    private boolean hasActiveAccounts(Long roleId) {
        return userRoleRepository.findByRoleId(roleId).stream()
                .map(UserRole::getUser)
                .anyMatch(u -> u.getStatus() == User.Status.ACTIVE);
    }

    private Role getRoleOrThrow(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy role id=" + roleId));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + userId));
    }

    private void writeHistory(Role role, User actor, RoleHistory.Action action) {
        RoleHistory history = new RoleHistory();
        history.setRole(role);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> details = new HashMap<>();
        details.put("code", role.getCode());
        details.put("name", role.getName());
        history.setDetails(details);
        roleHistoryRepository.save(history);
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isSystem());
    }
}
