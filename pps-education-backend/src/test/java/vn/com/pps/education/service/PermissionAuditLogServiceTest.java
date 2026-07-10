package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Permission;
import vn.com.pps.education.domain.PermissionAuditLog;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.PermissionAuditLogResponse;
import vn.com.pps.education.dto.PermissionAuditLogSearchRequest;
import vn.com.pps.education.repository.PermissionAuditLogRepository;
import vn.com.pps.education.repository.PermissionRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-05: Xem nhật ký thay đổi quyền — Main Flow (filter), A1 (không có kết quả).
 * Xem docs/uc/phan-he-02-phan-quyen.md.
 */
@Transactional
class PermissionAuditLogServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PermissionAuditLogService permissionAuditLogService;

    @Autowired
    private PermissionAuditLogRepository permissionAuditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    private User actorUser;
    private User targetUser;
    private Permission permission;

    @BeforeEach
    void setUp() {
        actorUser = createUser("audit.actor");
        targetUser = createUser("audit.target");

        permission = new Permission();
        permission.setCode("test.audit_" + System.nanoTime());
        permission.setName("Test permission");
        permission.setModule("TASK");
        permission = permissionRepository.save(permission);

        PermissionAuditLog log = new PermissionAuditLog();
        log.setActorUser(actorUser);
        log.setTargetUser(targetUser);
        log.setAction(PermissionAuditLog.Action.PERM_OVERRIDE_ADDED);
        log.setTargetPermission(permission);
        permissionAuditLogRepository.save(log);
    }

    private User createUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName(prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }

    @Test
    void search_UC05_MainFlow_filtersByActorTargetActionAndDateRange() {
        var filter = new PermissionAuditLogSearchRequest(
                actorUser.getId(), targetUser.getId(), "PERM_OVERRIDE_ADDED",
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1));

        Page<PermissionAuditLogResponse> page = permissionAuditLogService.search(filter, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).actorUserId()).isEqualTo(actorUser.getId());
        assertThat(page.getContent().get(0).targetUserId()).isEqualTo(targetUser.getId());
        assertThat(page.getContent().get(0).action()).isEqualTo("PERM_OVERRIDE_ADDED");
    }

    @Test
    void search_UC05_A1_returnsEmptyListWhenNoMatch() {
        var filter = new PermissionAuditLogSearchRequest(actorUser.getId(), null, "PERM_OVERRIDE_REMOVED", null, null);

        Page<PermissionAuditLogResponse> page = permissionAuditLogService.search(filter, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
    }
}
