package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateRoleRequest;
import vn.com.pps.education.dto.RolePermissionMatrixResponse;
import vn.com.pps.education.dto.RoleResponse;
import vn.com.pps.education.dto.UpdateRolePermissionsRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.RoleService;

import java.util.List;

/** UC-03: Cấu hình nhóm quyền mặc định (FR-PER-02). */
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PreAuthorize("hasPermission(null, 'permission.role.view')")
    @GetMapping
    public ResponseEntity<List<RoleResponse>> listRoles() {
        return ResponseEntity.ok(roleService.listRoles());
    }

    @PreAuthorize("hasPermission(null, 'permission.role.create')")
    @PostMapping
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(roleService.createRole(request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'permission.role.delete')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        roleService.deleteRole(id, actor.userId());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasPermission(null, 'permission.role.view')")
    @GetMapping("/{id}/permissions")
    public ResponseEntity<RolePermissionMatrixResponse> getPermissionMatrix(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getPermissionMatrix(id));
    }

    @PreAuthorize("hasPermission(null, 'permission.role.update')")
    @PutMapping("/{id}/permissions")
    public ResponseEntity<Void> updatePermissions(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateRolePermissionsRequest request) {
        roleService.updatePermissions(id, request);
        return ResponseEntity.noContent().build();
    }
}
