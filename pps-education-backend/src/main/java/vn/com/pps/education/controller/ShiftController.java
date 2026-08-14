package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AssignEmployeeShiftRequest;
import vn.com.pps.education.dto.CreateShiftRequest;
import vn.com.pps.education.dto.EmployeeShiftResponse;
import vn.com.pps.education.dto.ShiftResponse;
import vn.com.pps.education.dto.UpdateShiftRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.EmployeeShiftService;
import vn.com.pps.education.service.ShiftService;

import java.util.List;

/**
 * UC-70: Quản lý Ca làm việc + Gán ca cho nhân sự — xem Javadoc ShiftService.
 */
@RestController
public class ShiftController {

    private final ShiftService shiftService;
    private final EmployeeShiftService employeeShiftService;

    public ShiftController(ShiftService shiftService, EmployeeShiftService employeeShiftService) {
        this.shiftService = shiftService;
        this.employeeShiftService = employeeShiftService;
    }

    @GetMapping("/api/shifts")
    public ResponseEntity<List<ShiftResponse>> listShifts() {
        return ResponseEntity.ok(shiftService.listShifts());
    }

    @PostMapping("/api/shifts")
    @PreAuthorize("hasPermission(null, 'hrm.shift.create')")
    public ResponseEntity<ShiftResponse> createShift(@Valid @RequestBody CreateShiftRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(shiftService.createShift(request, actor.userId()));
    }

    @PutMapping("/api/shifts/{id}")
    @PreAuthorize("hasPermission(null, 'hrm.shift.update')")
    public ResponseEntity<ShiftResponse> updateShift(@PathVariable Long id, @Valid @RequestBody UpdateShiftRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(shiftService.updateShift(id, request, actor.userId()));
    }

    @PutMapping("/api/shifts/{id}/deactivate")
    @PreAuthorize("hasPermission(null, 'hrm.shift.update')")
    public ResponseEntity<ShiftResponse> deactivateShift(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(shiftService.deactivateShift(id, actor.userId()));
    }

    @PostMapping("/api/employee-shifts")
    @PreAuthorize("hasPermission(null, 'hrm.employee-shift.assign')")
    public ResponseEntity<EmployeeShiftResponse> assignEmployeeShift(@Valid @RequestBody AssignEmployeeShiftRequest request) {
        return ResponseEntity.ok(employeeShiftService.assignShift(request));
    }

    @GetMapping("/api/employees/{id}/shifts")
    public ResponseEntity<List<EmployeeShiftResponse>> listEmployeeShifts(@PathVariable Long id) {
        return ResponseEntity.ok(employeeShiftService.listByEmployee(id));
    }
}
