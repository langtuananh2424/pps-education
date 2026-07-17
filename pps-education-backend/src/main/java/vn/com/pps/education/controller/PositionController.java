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
import vn.com.pps.education.dto.CreatePositionRequest;
import vn.com.pps.education.dto.PositionDefaultRolesResponse;
import vn.com.pps.education.dto.PositionResponse;
import vn.com.pps.education.dto.UpdatePositionDefaultRolesRequest;
import vn.com.pps.education.dto.UpdatePositionRequest;
import vn.com.pps.education.service.PositionService;

import java.util.List;

/**
 * FR-HRM-06/UC-52 — CRUD danh mục chức vụ + cấu hình role mặc định (xem
 * Javadoc PositionService). GET không gắn thêm quyền ngoài "đã đăng nhập"
 * vì danh sách chức vụ là dữ liệu tra cứu dùng chung nhiều luồng (dropdown
 * tạo nhân sự UC-08); tạo/sửa/xóa/cấu hình role mặc định cần quyền
 * hrm.manage vì đây là danh mục thuộc phân hệ Nhân sự.
 */
@RestController
@RequestMapping("/api/positions")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping
    public ResponseEntity<List<PositionResponse>> list() {
        return ResponseEntity.ok(positionService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PositionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(positionService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'hrm.manage')")
    public ResponseEntity<PositionResponse> create(@Valid @RequestBody CreatePositionRequest request) {
        return ResponseEntity.ok(positionService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'hrm.manage')")
    public ResponseEntity<PositionResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody UpdatePositionRequest request) {
        return ResponseEntity.ok(positionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'hrm.manage')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        positionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/default-roles")
    @PreAuthorize("hasPermission(null, 'hrm.manage')")
    public ResponseEntity<PositionDefaultRolesResponse> getDefaultRoles(@PathVariable Long id) {
        return ResponseEntity.ok(positionService.getDefaultRoles(id));
    }

    @PutMapping("/{id}/default-roles")
    @PreAuthorize("hasPermission(null, 'hrm.manage')")
    public ResponseEntity<Void> updateDefaultRoles(@PathVariable Long id,
                                                     @RequestBody UpdatePositionDefaultRolesRequest request) {
        positionService.updateDefaultRoles(id, request);
        return ResponseEntity.noContent().build();
    }
}
