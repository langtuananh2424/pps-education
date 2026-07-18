package vn.com.pps.education.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.RoleResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.UserRoleAssignmentService;

import java.util.List;

/**
 * UC-46: Gán/Thu hồi vai trò cho tài khoản (FR-PER-05). Tách khỏi
 * UserController (UC-43, /api/users) và UserPermissionOverrideController
 * (UC-04, permission.override.manage) — 3 UC khác lý do thay đổi (SOLID S).
 */
@RestController
@RequestMapping("/api/users/{userId}/roles")
@PreAuthorize("hasPermission(null, 'user.role.manage')")
public class UserRoleController {

    private final UserRoleAssignmentService userRoleAssignmentService;

    public UserRoleController(UserRoleAssignmentService userRoleAssignmentService) {
        this.userRoleAssignmentService = userRoleAssignmentService;
    }

    @GetMapping
    public ResponseEntity<List<RoleResponse>> listAssignedRoles(@PathVariable Long userId) {
        return ResponseEntity.ok(userRoleAssignmentService.listAssignedRoles(userId));
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<Void> assignRole(@PathVariable Long userId,
                                            @PathVariable Long roleId,
                                            @AuthenticationPrincipal AuthenticatedUser actor,
                                            HttpServletRequest httpRequest) {
        userRoleAssignmentService.assignRole(userId, roleId, actor.userId(), httpRequest);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{roleId}")
    public ResponseEntity<Void> revokeRole(@PathVariable Long userId,
                                            @PathVariable Long roleId,
                                            @AuthenticationPrincipal AuthenticatedUser actor,
                                            HttpServletRequest httpRequest) {
        userRoleAssignmentService.revokeRole(userId, roleId, actor.userId(), httpRequest);
        return ResponseEntity.noContent().build();
    }
}
