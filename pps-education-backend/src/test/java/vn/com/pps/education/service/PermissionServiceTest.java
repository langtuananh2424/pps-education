package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Permission;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.RolePermission;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserPermissionOverride;
import vn.com.pps.education.dto.CreatePermissionRequest;
import vn.com.pps.education.dto.PermissionResponse;
import vn.com.pps.education.dto.UpdatePermissionRequest;
import vn.com.pps.education.exception.DuplicatePermissionCodeException;
import vn.com.pps.education.exception.PermissionInUseException;
import vn.com.pps.education.repository.PermissionRepository;
import vn.com.pps.education.repository.RolePermissionRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserPermissionOverrideRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-02: Quản lý danh mục quyền — Main Flow, A1 (trùng code), A2 (xóa quyền đang dùng).
 * Xem docs/uc/phan-he-02-phan-quyen.md.
 */
@Transactional
class PermissionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPermissionOverrideRepository userPermissionOverrideRepository;

    private User adminUser;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("permission.test.admin");
        user.setEmail("permission.test.admin@pps.edu.vn");
        user.setFullName("Permission Test Admin");
        user.setStatus(User.Status.ACTIVE);
        adminUser = userRepository.save(user);
    }

    @Test
    void create_UC02_MainFlow_createsPermission() {
        PermissionResponse response = permissionService.create(
                new CreatePermissionRequest("test.create_permission", "Test permission", "TASK", "Mô tả"));

        assertThat(response.id()).isNotNull();
        assertThat(response.code()).isEqualTo("test.create_permission");
        assertThat(permissionRepository.findByCode("test.create_permission")).isPresent();
    }

    @Test
    void update_UC02_MainFlow_updatesNameAndDescriptionOnly() {
        PermissionResponse created = permissionService.create(
                new CreatePermissionRequest("test.update_permission", "Ten cu", "TASK", "Mo ta cu"));

        PermissionResponse updated = permissionService.update(created.id(),
                new UpdatePermissionRequest("Ten moi", "Mo ta moi"));

        assertThat(updated.name()).isEqualTo("Ten moi");
        assertThat(updated.description()).isEqualTo("Mo ta moi");
        assertThat(updated.code()).isEqualTo("test.update_permission");
    }

    @Test
    void delete_UC02_MainFlow_deletesUnreferencedPermission() {
        PermissionResponse created = permissionService.create(
                new CreatePermissionRequest("test.delete_permission", "Test", "TASK", null));

        permissionService.delete(created.id());

        assertThat(permissionRepository.findById(created.id())).isEmpty();
    }

    @Test
    void create_UC02_A1_rejectsDuplicateCode() {
        permissionService.create(new CreatePermissionRequest("test.duplicate", "Test", "TASK", null));

        assertThatThrownBy(() -> permissionService.create(
                new CreatePermissionRequest("test.duplicate", "Test 2", "TASK", null)))
                .isInstanceOf(DuplicatePermissionCodeException.class);
    }

    @Test
    void delete_UC02_A2_rejectsWhenReferencedByRolePermissions() {
        PermissionResponse created = permissionService.create(
                new CreatePermissionRequest("test.role_referenced", "Test", "TASK", null));

        Role role = new Role();
        role.setCode("TEST_ROLE_" + System.nanoTime());
        role.setName("Test role");
        roleRepository.save(role);

        Permission permission = permissionRepository.findById(created.id()).orElseThrow();
        RolePermission rolePermission = new RolePermission();
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        rolePermissionRepository.save(rolePermission);

        assertThatThrownBy(() -> permissionService.delete(created.id()))
                .isInstanceOf(PermissionInUseException.class);
    }

    @Test
    void delete_UC02_A2_rejectsWhenReferencedByUserOverride() {
        PermissionResponse created = permissionService.create(
                new CreatePermissionRequest("test.override_referenced", "Test", "TASK", null));
        Permission permission = permissionRepository.findById(created.id()).orElseThrow();

        UserPermissionOverride override = new UserPermissionOverride();
        override.setUser(adminUser);
        override.setPermission(permission);
        override.setOverrideType(UserPermissionOverride.OverrideType.GRANT);
        override.setReason("test");
        override.setGrantedBy(adminUser);
        override.setGrantedAt(OffsetDateTime.now());
        userPermissionOverrideRepository.save(override);

        assertThatThrownBy(() -> permissionService.delete(created.id()))
                .isInstanceOf(PermissionInUseException.class);
    }
}
