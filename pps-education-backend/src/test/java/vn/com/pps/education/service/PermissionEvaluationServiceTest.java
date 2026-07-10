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
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.repository.PermissionRepository;
import vn.com.pps.education.repository.RolePermissionRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserPermissionOverrideRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-04: effective_permissions = hợp(role_permissions theo user_roles)
 * - REVOKE override còn hiệu lực + GRANT override còn hiệu lực (A1: override hết hạn bị loại).
 * Xem docs/sdd-groups/02-nen-tang.md.
 */
@Transactional
class PermissionEvaluationServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PermissionEvaluationService permissionEvaluationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserPermissionOverrideRepository userPermissionOverrideRepository;

    private User user;
    private Permission permA;
    private Permission permB;
    private Permission permC;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("effperm.test.user");
        user.setEmail("effperm.test.user@pps.edu.vn");
        user.setFullName("Effective Permission Test User");
        user.setStatus(User.Status.ACTIVE);
        user = userRepository.save(user);

        Role role = new Role();
        role.setCode("TEST_EFFPERM_ROLE_" + System.nanoTime());
        role.setName("Test role");
        role = roleRepository.save(role);

        permA = createPermission("test.effperm_a");
        permB = createPermission("test.effperm_b");
        permC = createPermission("test.effperm_c");

        linkRolePermission(role, permA);
        linkRolePermission(role, permB);

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedAt(OffsetDateTime.now());
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private Permission createPermission(String code) {
        Permission permission = new Permission();
        permission.setCode(code + "_" + System.nanoTime());
        permission.setName(code);
        permission.setModule("TASK");
        return permissionRepository.save(permission);
    }

    private void linkRolePermission(Role role, Permission permission) {
        RolePermission rp = new RolePermission();
        rp.setRole(role);
        rp.setPermission(permission);
        rolePermissionRepository.save(rp);
    }

    private UserPermissionOverride createOverride(Permission permission, UserPermissionOverride.OverrideType type,
                                                    OffsetDateTime expiresAt) {
        UserPermissionOverride override = new UserPermissionOverride();
        override.setUser(user);
        override.setPermission(permission);
        override.setOverrideType(type);
        override.setReason("test");
        override.setGrantedBy(user);
        override.setGrantedAt(OffsetDateTime.now());
        override.setExpiresAt(expiresAt);
        return userPermissionOverrideRepository.save(override);
    }

    @Test
    void getEffectivePermissions_UC04_MainFlow_unionsRolePermissions() {
        Set<String> effective = permissionEvaluationService.getEffectivePermissions(user.getId());

        assertThat(effective).contains(permA.getCode(), permB.getCode());
        assertThat(effective).doesNotContain(permC.getCode());
    }

    @Test
    void getEffectivePermissions_appliesActiveRevokeOverride() {
        createOverride(permA, UserPermissionOverride.OverrideType.REVOKE, null);

        Set<String> effective = permissionEvaluationService.getEffectivePermissions(user.getId());

        assertThat(effective).doesNotContain(permA.getCode());
        assertThat(effective).contains(permB.getCode());
    }

    @Test
    void getEffectivePermissions_appliesActiveGrantOverride() {
        createOverride(permC, UserPermissionOverride.OverrideType.GRANT, null);

        Set<String> effective = permissionEvaluationService.getEffectivePermissions(user.getId());

        assertThat(effective).contains(permC.getCode());
    }

    @Test
    void getEffectivePermissions_UC04_A1_ignoresExpiredRevokeOverride() {
        createOverride(permB, UserPermissionOverride.OverrideType.REVOKE, OffsetDateTime.now().minusDays(1));

        Set<String> effective = permissionEvaluationService.getEffectivePermissions(user.getId());

        // Override REVOKE đã hết hạn -- permB vẫn còn hiệu lực qua role
        assertThat(effective).contains(permB.getCode());
    }
}
