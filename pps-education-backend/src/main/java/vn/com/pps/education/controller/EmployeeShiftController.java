package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AssignShiftRequest;
import vn.com.pps.education.dto.BulkAssignShiftRequest;
import vn.com.pps.education.dto.BulkAssignShiftResponse;
import vn.com.pps.education.dto.EmployeeShiftResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.EmployeeShiftService;

import java.util.List;

/**
 * Bổ sung 2026-08-13 — gán ca cho nhân sự (đơn lẻ/hàng loạt), xem
 * docs/uc/phan-he-04-nhan-su.md (khối bổ sung dưới UC-09/FR-HRM-02).
 */
@RestController
@RequestMapping("/api/employee-shifts")
public class EmployeeShiftController {

    private final EmployeeShiftService employeeShiftService;

    public EmployeeShiftController(EmployeeShiftService employeeShiftService) {
        this.employeeShiftService = employeeShiftService;
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasPermission(null, 'hrm.employee.view')")
    public ResponseEntity<List<EmployeeShiftResponse>> getHistory(@PathVariable Long employeeId) {
        return ResponseEntity.ok(employeeShiftService.getHistory(employeeId));
    }

    @PostMapping("/assign")
    @PreAuthorize("hasPermission(null, 'hrm.employee-shift.assign')")
    public ResponseEntity<EmployeeShiftResponse> assign(@Valid @RequestBody AssignShiftRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(employeeShiftService.assign(request, actor.userId()));
    }

    @PostMapping("/bulk-assign")
    @PreAuthorize("hasPermission(null, 'hrm.employee-shift.assign')")
    public ResponseEntity<BulkAssignShiftResponse> bulkAssign(@Valid @RequestBody BulkAssignShiftRequest request,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(employeeShiftService.bulkAssign(request, actor.userId()));
    }
}
