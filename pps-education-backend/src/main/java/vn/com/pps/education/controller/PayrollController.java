package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.PayrollEntryResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.PayrollService;

import java.util.List;

/**
 * UC-12: Xem bảng lương (FR-HRM-04). Tự phục vụ (xem của chính mình) +
 * role-based (Quản lý nhân sự xem toàn hệ thống) — không có permission code
 * riêng trong Precondition, phân biệt hoàn toàn theo role như Main Flow.
 */
@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @GetMapping("/mine")
    public ResponseEntity<PayrollEntryResponse> getMine(@RequestParam(required = false) String periodCode,
                                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(payrollService.getMyPayroll(actor.userId(), periodCode));
    }

    @GetMapping("/entries")
    public ResponseEntity<List<PayrollEntryResponse>> listEntries(@RequestParam Long periodId,
                                                                     @RequestParam(required = false) Long departmentId,
                                                                     @RequestParam(required = false) Long employeeId,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(payrollService.listPayroll(actor.userId(), periodId, departmentId, employeeId));
    }
}
