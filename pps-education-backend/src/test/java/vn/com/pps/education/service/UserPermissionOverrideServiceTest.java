package vn.com.pps.education.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Permission;
import vn.com.pps.education.domain.PermissionAuditLog;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserPermissionOverride;
import vn.com.pps.education.dto.EffectivePermissionsResponse;
import vn.com.pps.education.dto.UserPermissionOverrideRequest;
import vn.com.pps.education.repository.PermissionAuditLogRepository;
import vn.com.pps.education.repository.PermissionRepository;
import vn.com.pps.education.repository.UserPermissionOverrideRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-04: Tùy chỉnh quyền riêng cho tài khoản — Main Flow bước 3-6, A2 (gỡ override).
 * Xem docs/uc/phan-he-02-phan-quyen.md.
 */
@Transactional
class UserPermissionOverrideServiceTest extends AbstractIntegrationTest {

    @Autowired
    private UserPermissionOverrideService userPermissionOverrideService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserPermissionOverrideRepository userPermissionOverrideRepository;

    @Autowired
    private PermissionAuditLogRepository permissionAuditLogRepository;

    private User targetUser;
    private User actorUser;
    private Permission permission;

    @BeforeEach
    void setUp() {
        targetUser = new User();
        targetUser.setUsername("override.target." + System.nanoTime());
        targetUser.setEmail("override.target." + System.nanoTime() + "@pps.edu.vn");
        targetUser.setFullName("Override Target");
        targetUser.setStatus(User.Status.ACTIVE);
        targetUser = userRepository.save(targetUser);

        actorUser = new User();
        actorUser.setUsername("override.actor." + System.nanoTime());
        actorUser.setEmail("override.actor." + System.nanoTime() + "@pps.edu.vn");
        actorUser.setFullName("Override Actor (Admin)");
        actorUser.setStatus(User.Status.ACTIVE);
        actorUser = userRepository.save(actorUser);

        permission = new Permission();
        permission.setCode("test.override_" + System.nanoTime());
        permission.setName("Test permission");
        permission.setModule("TASK");
        permission = permissionRepository.save(permission);
    }

    private HttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    @Test
    void upsertOverride_UC04_MainFlow_createsOverrideAndAuditLog() {
        userPermissionOverrideService.upsertOverride(targetUser.getId(), permission.getId(),
                new UserPermissionOverrideRequest("GRANT", "Ủy quyền tạm thời", null),
                actorUser.getId(), request());

        EffectivePermissionsResponse response = userPermissionOverrideService.getEffectivePermissions(targetUser.getId());
        assertThat(response.permissions()).contains(permission.getCode());

        List<PermissionAuditLog> logs = permissionAuditLogRepository.findAll().stream()
                .filter(l -> l.getTargetUser().getId().equals(targetUser.getId()))
                .toList();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(PermissionAuditLog.Action.PERM_OVERRIDE_ADDED);
        assertThat(logs.get(0).getActorUser().getId()).isEqualTo(actorUser.getId());
    }

    @Test
    void upsertOverride_UC04_MainFlow_updatesExistingOverrideInsteadOfDuplicating() {
        userPermissionOverrideService.upsertOverride(targetUser.getId(), permission.getId(),
                new UserPermissionOverrideRequest("GRANT", "Ly do 1", null), actorUser.getId(), request());
        userPermissionOverrideService.upsertOverride(targetUser.getId(), permission.getId(),
                new UserPermissionOverrideRequest("REVOKE", "Ly do 2", null), actorUser.getId(), request());

        List<UserPermissionOverride> overrides = userPermissionOverrideRepository.findByUserId(targetUser.getId());
        assertThat(overrides).hasSize(1);
        assertThat(overrides.get(0).getOverrideType()).isEqualTo(UserPermissionOverride.OverrideType.REVOKE);
        assertThat(overrides.get(0).getReason()).isEqualTo("Ly do 2");
    }

    @Test
    void removeOverride_UC04_A2_softExpiresAndWritesAuditLog() {
        userPermissionOverrideService.upsertOverride(targetUser.getId(), permission.getId(),
                new UserPermissionOverrideRequest("GRANT", "Ly do", null), actorUser.getId(), request());

        userPermissionOverrideService.removeOverride(targetUser.getId(), permission.getId(), actorUser.getId(), request());

        UserPermissionOverride override = userPermissionOverrideRepository
                .findByUserIdAndPermissionId(targetUser.getId(), permission.getId()).orElseThrow();
        assertThat(override.getExpiresAt()).isNotNull();
        assertThat(override.getExpiresAt()).isBeforeOrEqualTo(OffsetDateTime.now());

        List<PermissionAuditLog> logs = permissionAuditLogRepository.findAll().stream()
                .filter(l -> l.getTargetUser().getId().equals(targetUser.getId())
                        && l.getAction() == PermissionAuditLog.Action.PERM_OVERRIDE_REMOVED)
                .toList();
        assertThat(logs).hasSize(1);
    }
}
