package vn.com.pps.education.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.PermissionAuditLog;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.RoleResponse;
import vn.com.pps.education.exception.AccountInactiveException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.PermissionAuditLogRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-46: Gán/Thu hồi vai trò cho tài khoản — Main Flow, A1 (thu hồi), A2
 * (thu hồi role chưa từng gán). Xem docs/uc/phan-he-02-phan-quyen.md.
 */
@Transactional
class UserRoleAssignmentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private UserRoleAssignmentService userRoleAssignmentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PermissionAuditLogRepository permissionAuditLogRepository;

    private User targetUser;
    private User actorUser;
    private Role hrManagerRole;

    @BeforeEach
    void setUp() {
        targetUser = newUser("role.target");
        actorUser = newUser("role.actor");
        hrManagerRole = roleRepository.findByCode("HR_MANAGER").orElseThrow();
    }

    private HttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    @Test
    void assignRole_UC46_MainFlow_createsUserRoleAndAuditLog() {
        userRoleAssignmentService.assignRole(targetUser.getId(), hrManagerRole.getId(), actorUser.getId(), request());

        List<RoleResponse> roles = userRoleAssignmentService.listAssignedRoles(targetUser.getId());
        assertThat(roles).extracting(RoleResponse::code).containsExactly("HR_MANAGER");

        List<PermissionAuditLog> logs = permissionAuditLogRepository.findAll().stream()
                .filter(l -> l.getTargetUser().getId().equals(targetUser.getId()))
                .toList();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(PermissionAuditLog.Action.ROLE_GRANTED);
        assertThat(logs.get(0).getTargetRole().getId()).isEqualTo(hrManagerRole.getId());
        assertThat(logs.get(0).getActorUser().getId()).isEqualTo(actorUser.getId());
    }

    @Test
    void assignRole_UC46_MainFlow_idempotentWhenAlreadyAssigned() {
        userRoleAssignmentService.assignRole(targetUser.getId(), hrManagerRole.getId(), actorUser.getId(), request());
        userRoleAssignmentService.assignRole(targetUser.getId(), hrManagerRole.getId(), actorUser.getId(), request());

        assertThat(userRoleRepository.findByUserId(targetUser.getId())).hasSize(1);
        long grantLogs = permissionAuditLogRepository.findAll().stream()
                .filter(l -> l.getTargetUser().getId().equals(targetUser.getId())
                        && l.getAction() == PermissionAuditLog.Action.ROLE_GRANTED)
                .count();
        assertThat(grantLogs).isEqualTo(1);
    }

    @Test
    void assignRole_rejectsWhenTargetAccountInactive() {
        targetUser.setStatus(User.Status.INACTIVE);
        userRepository.save(targetUser);

        assertThatThrownBy(() -> userRoleAssignmentService.assignRole(
                targetUser.getId(), hrManagerRole.getId(), actorUser.getId(), request()))
                .isInstanceOf(AccountInactiveException.class);
    }

    @Test
    void assignRole_rejectsWhenRoleNotFound() {
        assertThatThrownBy(() -> userRoleAssignmentService.assignRole(
                targetUser.getId(), -1L, actorUser.getId(), request()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void revokeRole_UC46_A1_deletesUserRoleAndWritesAuditLog() {
        userRoleAssignmentService.assignRole(targetUser.getId(), hrManagerRole.getId(), actorUser.getId(), request());

        userRoleAssignmentService.revokeRole(targetUser.getId(), hrManagerRole.getId(), actorUser.getId(), request());

        assertThat(userRoleRepository.findByUserIdAndRoleId(targetUser.getId(), hrManagerRole.getId())).isEmpty();
        List<PermissionAuditLog> logs = permissionAuditLogRepository.findAll().stream()
                .filter(l -> l.getTargetUser().getId().equals(targetUser.getId())
                        && l.getAction() == PermissionAuditLog.Action.ROLE_REVOKED)
                .toList();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getTargetRole().getId()).isEqualTo(hrManagerRole.getId());
    }

    @Test
    void revokeRole_UC46_A1_allowsRevokingFromInactiveAccount() {
        userRoleAssignmentService.assignRole(targetUser.getId(), hrManagerRole.getId(), actorUser.getId(), request());
        targetUser.setStatus(User.Status.INACTIVE);
        userRepository.save(targetUser);

        userRoleAssignmentService.revokeRole(targetUser.getId(), hrManagerRole.getId(), actorUser.getId(), request());

        assertThat(userRoleRepository.findByUserIdAndRoleId(targetUser.getId(), hrManagerRole.getId())).isEmpty();
    }

    @Test
    void revokeRole_UC46_A2_rejectsWhenNotAssigned() {
        assertThatThrownBy(() -> userRoleAssignmentService.revokeRole(
                targetUser.getId(), hrManagerRole.getId(), actorUser.getId(), request()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listAssignedRoles_UC46_MainFlow_returnsCurrentRolesSortedByCode() {
        Role teacherRole = roleRepository.findByCode("TEACHER").orElseThrow();
        userRoleAssignmentService.assignRole(targetUser.getId(), hrManagerRole.getId(), actorUser.getId(), request());
        userRoleAssignmentService.assignRole(targetUser.getId(), teacherRole.getId(), actorUser.getId(), request());

        List<RoleResponse> roles = userRoleAssignmentService.listAssignedRoles(targetUser.getId());

        assertThat(roles).extracting(RoleResponse::code).containsExactly("HR_MANAGER", "TEACHER");
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
