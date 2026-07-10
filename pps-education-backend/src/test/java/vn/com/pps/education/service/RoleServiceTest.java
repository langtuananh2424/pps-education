package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Permission;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.RolePermission;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.RolePermissionMatrixResponse;
import vn.com.pps.education.dto.UpdateRolePermissionsRequest;
import vn.com.pps.education.exception.RolePermissionConfirmationRequiredException;
import vn.com.pps.education.repository.PermissionRepository;
import vn.com.pps.education.repository.RolePermissionRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-03: Cấu hình nhóm quyền mặc định — Main Flow, A1 (xóa hết quyền role đang có tài khoản active).
 * Xem docs/uc/phan-he-02-phan-quyen.md.
 */
@Transactional
class RoleServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private Role role;
    private Permission permissionA;
    private Permission permissionB;

    @BeforeEach
    void setUp() {
        role = new Role();
        role.setCode("TEST_ROLE_" + System.nanoTime());
        role.setName("Test role");
        role = roleRepository.save(role);

        permissionA = new Permission();
        permissionA.setCode("test.role_matrix_a_" + System.nanoTime());
        permissionA.setName("A");
        permissionA.setModule("TASK");
        permissionA = permissionRepository.save(permissionA);

        permissionB = new Permission();
        permissionB.setCode("test.role_matrix_b_" + System.nanoTime());
        permissionB.setName("B");
        permissionB.setModule("TASK");
        permissionB = permissionRepository.save(permissionB);

        RolePermission existing = new RolePermission();
        existing.setRole(role);
        existing.setPermission(permissionA);
        rolePermissionRepository.save(existing);
    }

    @Test
    void updatePermissions_UC03_MainFlow_appliesAddAndRemoveDiff() {
        roleService.updatePermissions(role.getId(),
                new UpdateRolePermissionsRequest(Set.of(permissionB.getId()), false));

        RolePermissionMatrixResponse matrix = roleService.getPermissionMatrix(role.getId());
        assertThat(matrix.permissions().stream().filter(p -> p.permissionId().equals(permissionA.getId())).findFirst()
                .orElseThrow().granted()).isFalse();
        assertThat(matrix.permissions().stream().filter(p -> p.permissionId().equals(permissionB.getId())).findFirst()
                .orElseThrow().granted()).isTrue();
    }

    @Test
    void updatePermissions_UC03_A1_requiresConfirmationWhenClearingRoleWithActiveUsers() {
        activateUserForRole(role);

        assertThatThrownBy(() -> roleService.updatePermissions(role.getId(),
                new UpdateRolePermissionsRequest(Set.of(), false)))
                .isInstanceOf(RolePermissionConfirmationRequiredException.class);

        // Chưa bị xóa vì request bị từ chối
        RolePermissionMatrixResponse matrix = roleService.getPermissionMatrix(role.getId());
        assertThat(matrix.permissions().stream().filter(p -> p.permissionId().equals(permissionA.getId())).findFirst()
                .orElseThrow().granted()).isTrue();
    }

    @Test
    void updatePermissions_UC03_A1_proceedsWhenConfirmed() {
        activateUserForRole(role);

        roleService.updatePermissions(role.getId(), new UpdateRolePermissionsRequest(Set.of(), true));

        RolePermissionMatrixResponse matrix = roleService.getPermissionMatrix(role.getId());
        assertThat(matrix.permissions()).allMatch(p -> !p.granted());
    }

    private void activateUserForRole(Role role) {
        User user = new User();
        user.setUsername("role.test.user." + System.nanoTime());
        user.setEmail("role.test.user." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Role Test User");
        user.setStatus(User.Status.ACTIVE);
        user = userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedAt(OffsetDateTime.now());
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }
}
