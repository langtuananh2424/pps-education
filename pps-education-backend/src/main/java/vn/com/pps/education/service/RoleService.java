package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Permission;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.RolePermission;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.PermissionMatrixItem;
import vn.com.pps.education.dto.RolePermissionMatrixResponse;
import vn.com.pps.education.dto.RoleResponse;
import vn.com.pps.education.dto.UpdateRolePermissionsRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RolePermissionConfirmationRequiredException;
import vn.com.pps.education.repository.PermissionRepository;
import vn.com.pps.education.repository.RolePermissionRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-03: Cấu hình nhóm quyền mặc định (FR-PER-02).
 * Xem docs/uc/phan-he-02-phan-quyen.md — Main Flow, A1 (xóa hết quyền của
 * role đang có tài khoản active cần xác nhận lại).
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;

    public RoleService(RoleRepository roleRepository,
                        PermissionRepository permissionRepository,
                        RolePermissionRepository rolePermissionRepository,
                        UserRoleRepository userRoleRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRepository = userRoleRepository;
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

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isSystem());
    }
}
