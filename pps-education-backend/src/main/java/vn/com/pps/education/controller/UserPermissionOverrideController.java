package vn.com.pps.education.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.EffectivePermissionsResponse;
import vn.com.pps.education.dto.UserPermissionOverrideRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.UserPermissionOverrideService;

/** UC-04: Tùy chỉnh quyền riêng cho tài khoản (FR-PER-03). */
@RestController
@RequestMapping("/api/users/{userId}")
@PreAuthorize("hasPermission(null, 'permission.override.manage')")
public class UserPermissionOverrideController {

    private final UserPermissionOverrideService userPermissionOverrideService;

    public UserPermissionOverrideController(UserPermissionOverrideService userPermissionOverrideService) {
        this.userPermissionOverrideService = userPermissionOverrideService;
    }

    @GetMapping("/effective-permissions")
    public ResponseEntity<EffectivePermissionsResponse> getEffectivePermissions(@PathVariable Long userId) {
        return ResponseEntity.ok(userPermissionOverrideService.getEffectivePermissions(userId));
    }

    @PutMapping("/permission-overrides/{permissionId}")
    public ResponseEntity<Void> upsertOverride(@PathVariable Long userId,
                                                @PathVariable Long permissionId,
                                                @Valid @RequestBody UserPermissionOverrideRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser actor,
                                                HttpServletRequest httpRequest) {
        userPermissionOverrideService.upsertOverride(userId, permissionId, request, actor.userId(), httpRequest);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/permission-overrides/{permissionId}")
    public ResponseEntity<Void> removeOverride(@PathVariable Long userId,
                                                @PathVariable Long permissionId,
                                                @AuthenticationPrincipal AuthenticatedUser actor,
                                                HttpServletRequest httpRequest) {
        userPermissionOverrideService.removeOverride(userId, permissionId, actor.userId(), httpRequest);
        return ResponseEntity.noContent().build();
    }
}
