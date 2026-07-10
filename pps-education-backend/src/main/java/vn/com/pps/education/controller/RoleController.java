package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.RolePermissionMatrixResponse;
import vn.com.pps.education.dto.RoleResponse;
import vn.com.pps.education.dto.UpdateRolePermissionsRequest;
import vn.com.pps.education.service.RoleService;

import java.util.List;

/** UC-03: Cấu hình nhóm quyền mặc định (FR-PER-02). */
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasPermission(null, 'permission.role.manage')")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public ResponseEntity<List<RoleResponse>> listRoles() {
        return ResponseEntity.ok(roleService.listRoles());
    }

    @GetMapping("/{id}/permissions")
    public ResponseEntity<RolePermissionMatrixResponse> getPermissionMatrix(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getPermissionMatrix(id));
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<Void> updatePermissions(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateRolePermissionsRequest request) {
        roleService.updatePermissions(id, request);
        return ResponseEntity.noContent().build();
    }
}
