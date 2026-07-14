package vn.com.pps.education.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Permission;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.RolePermission;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.CreateRoleRequest;
import vn.com.pps.education.dto.RolePermissionMatrixResponse;
import vn.com.pps.education.dto.RoleResponse;
import vn.com.pps.education.dto.UpdateRolePermissionsRequest;
import vn.com.pps.education.exception.DuplicateRoleCodeException;
import vn.com.pps.education.exception.RoleNotDeletableException;
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

    @Autowired
    private UserRoleAssignmentService userRoleAssignmentService;

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

    @Test
    void createRole_boSung_MainFlow_savesCustomRoleWithSystemFalse() {
        RoleResponse created = roleService.createRole(
                new CreateRoleRequest("CUSTOM_ROLE_" + System.nanoTime(), "Vai trò tùy chỉnh", "Mô tả test"),
                newUser().getId());

        assertThat(created.isSystem()).isFalse();
        assertThat(created.name()).isEqualTo("Vai trò tùy chỉnh");
    }

    @Test
    void createRole_boSung_rejectsDuplicateCode() {
        Long actorId = newUser().getId();
        String code = "CUSTOM_ROLE_" + System.nanoTime();
        roleService.createRole(new CreateRoleRequest(code, "Lần 1", null), actorId);

        assertThatThrownBy(() -> roleService.createRole(new CreateRoleRequest(code, "Lần 2", null), actorId))
                .isInstanceOf(DuplicateRoleCodeException.class);
    }

    @Test
    void deleteRole_boSung_MainFlow_removesRoleNeverAssigned() {
        Long actorId = newUser().getId();
        RoleResponse created = roleService.createRole(
                new CreateRoleRequest("CUSTOM_ROLE_" + System.nanoTime(), "Xóa được", null), actorId);

        roleService.deleteRole(created.id(), actorId);

        assertThat(roleRepository.findById(created.id())).isEmpty();
    }

    @Test
    void deleteRole_boSung_rejectsSystemRole() {
        Role systemRole = roleRepository.findByCode("TEACHER").orElseThrow();

        assertThatThrownBy(() -> roleService.deleteRole(systemRole.getId(), newUser().getId()))
                .isInstanceOf(RoleNotDeletableException.class);
    }

    @Test
    void deleteRole_boSung_rejectsWhenCurrentlyAssigned() {
        activateUserForRole(role);

        assertThatThrownBy(() -> roleService.deleteRole(role.getId(), newUser().getId()))
                .isInstanceOf(RoleNotDeletableException.class);
    }

    @Test
    void deleteRole_boSung_rejectsWhenHasAuditHistoryEvenAfterRevoke() {
        User actor = newUser();
        User target = newUser();
        userRoleAssignmentService.assignRole(target.getId(), role.getId(), actor.getId(), mockRequest());
        userRoleAssignmentService.revokeRole(target.getId(), role.getId(), actor.getId(), mockRequest());

        assertThatThrownBy(() -> roleService.deleteRole(role.getId(), actor.getId()))
                .isInstanceOf(RoleNotDeletableException.class);
    }

    private HttpServletRequest mockRequest() {
        return new MockHttpServletRequest();
    }

    private User newUser() {
        User user = new User();
        user.setUsername("role.crud.user." + System.nanoTime());
        user.setEmail("role.crud.user." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Role CRUD Test User");
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
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
