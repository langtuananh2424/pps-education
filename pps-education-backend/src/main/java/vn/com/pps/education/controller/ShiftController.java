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
import vn.com.pps.education.dto.CreateShiftRequest;
import vn.com.pps.education.dto.ShiftResponse;
import vn.com.pps.education.dto.UpdateShiftRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ShiftService;

import java.util.List;

/**
 * Bổ sung 2026-08-13 — quản lý danh mục ca làm việc, xem
 * docs/uc/phan-he-04-nhan-su.md (khối bổ sung dưới UC-09/FR-HRM-02). GET
 * không gate thêm quyền ngoài "đã đăng nhập" vì là dữ liệu tra cứu dùng
 * chung (đối chiếu chấm công UC-09, dropdown gán ca), cùng cách
 * PositionController xử lý danh mục chức vụ.
 */
@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @GetMapping
    public ResponseEntity<List<ShiftResponse>> list() {
        return ResponseEntity.ok(shiftService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShiftResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(shiftService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'hrm.shift.create')")
    public ResponseEntity<ShiftResponse> create(@Valid @RequestBody CreateShiftRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(shiftService.create(request, actor.userId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'hrm.shift.update')")
    public ResponseEntity<ShiftResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody UpdateShiftRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(shiftService.update(id, request, actor.userId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'hrm.shift.update')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        shiftService.deactivate(id, actor.userId());
        return ResponseEntity.noContent().build();
    }
}
