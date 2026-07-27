package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateOperatingExpenseRequest;
import vn.com.pps.education.dto.DecideOperatingExpenseRequest;
import vn.com.pps.education.dto.OperatingExpenseResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.OperatingExpenseService;

import java.time.LocalDate;
import java.util.List;

/** UC-31: Ghi nhận chi vận hành (FR-FIN-03) — xem Javadoc OperatingExpenseService. */
@RestController
public class OperatingExpenseController {

    private final OperatingExpenseService operatingExpenseService;

    public OperatingExpenseController(OperatingExpenseService operatingExpenseService) {
        this.operatingExpenseService = operatingExpenseService;
    }

    @PostMapping("/api/finance/operating-expenses")
    @PreAuthorize("hasPermission(null, 'finance.expense.create')")
    public ResponseEntity<OperatingExpenseResponse> create(@Valid @RequestBody CreateOperatingExpenseRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(operatingExpenseService.create(request, actor.userId()));
    }

    @GetMapping("/api/finance/operating-expenses")
    @PreAuthorize("hasPermission(null, 'finance.expense.create') or hasPermission(null, 'finance.expense.approve')")
    public ResponseEntity<List<OperatingExpenseResponse>> list(
            @RequestParam(required = false) Long siteId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok(operatingExpenseService.listBySiteAndPeriod(siteId, from, to));
    }

    @PostMapping("/api/finance/operating-expenses/{id}/decision")
    @PreAuthorize("hasPermission(null, 'finance.expense.approve')")
    public ResponseEntity<OperatingExpenseResponse> decide(@PathVariable Long id,
                                                              @Valid @RequestBody DecideOperatingExpenseRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(operatingExpenseService.decide(id, request, actor.userId()));
    }
}
