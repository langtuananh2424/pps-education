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
import vn.com.pps.education.dto.CreateDepartmentRequest;
import vn.com.pps.education.dto.DepartmentResponse;
import vn.com.pps.education.dto.UpdateDepartmentRequest;
import vn.com.pps.education.service.DepartmentService;

import java.util.List;

/**
 * Bổ sung ngoài UC cụ thể — CRUD danh mục phòng ban (xem Javadoc
 * DepartmentService). GET không gắn thêm quyền ngoài "đã đăng nhập" vì
 * danh sách phòng ban là dữ liệu tra cứu dùng chung nhiều luồng (dropdown
 * tạo nhân sự UC-08, giao việc FR-TSK-01...); tạo/sửa/xóa mới cần quyền
 * hrm.manage vì đây là danh mục thuộc phân hệ Nhân sự.
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ResponseEntity<List<DepartmentResponse>> list() {
        return ResponseEntity.ok(departmentService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'hrm.department.create') or hasPermission(null, 'hrm.manage')")
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.ok(departmentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'hrm.department.update') or hasPermission(null, 'hrm.manage')")
    public ResponseEntity<DepartmentResponse> update(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateDepartmentRequest request) {
        return ResponseEntity.ok(departmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'hrm.department.delete') or hasPermission(null, 'hrm.manage')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
