package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreatePermissionRequest;
import vn.com.pps.education.dto.PermissionResponse;
import vn.com.pps.education.dto.UpdatePermissionRequest;
import vn.com.pps.education.service.PermissionService;

import java.util.List;

/** UC-02: Quản lý danh mục quyền (FR-PER-01). */
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @PreAuthorize("hasPermission(null, 'permission.catalog.manage')")
    @GetMapping
    public ResponseEntity<List<PermissionResponse>> listAll() {
        return ResponseEntity.ok(permissionService.listAll());
    }

    @PreAuthorize("hasPermission(null, 'permission.catalog.create') or hasPermission(null, 'permission.catalog.manage')")
    @PostMapping
    public ResponseEntity<PermissionResponse> create(@Valid @RequestBody CreatePermissionRequest request) {
        return ResponseEntity.ok(permissionService.create(request));
    }

    @PreAuthorize("hasPermission(null, 'permission.catalog.update') or hasPermission(null, 'permission.catalog.manage')")
    @PutMapping("/{id}")
    public ResponseEntity<PermissionResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody UpdatePermissionRequest request) {
        return ResponseEntity.ok(permissionService.update(id, request));
    }

    @PreAuthorize("hasPermission(null, 'permission.catalog.delete') or hasPermission(null, 'permission.catalog.manage')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
