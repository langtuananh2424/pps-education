package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.ChainFinancialReportResponse;
import vn.com.pps.education.dto.FinancialReportResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.FinanceReportService;

import java.time.LocalDate;
import java.util.List;

/** UC-32: Xem báo cáo tài chính (FR-FIN-04) — xem Javadoc FinanceReportService. */
@RestController
public class FinanceReportController {

    private final FinanceReportService financeReportService;

    public FinanceReportController(FinanceReportService financeReportService) {
        this.financeReportService = financeReportService;
    }

    @GetMapping("/api/finance/reports/my-sites")
    public ResponseEntity<List<FinancialReportResponse>> getMySiteReports(
            @RequestParam LocalDate from, @RequestParam LocalDate to,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(financeReportService.getMySiteReports(from, to, actor.userId()));
    }

    @GetMapping("/api/finance/reports/chain")
    public ResponseEntity<ChainFinancialReportResponse> getChainReport(
            @RequestParam LocalDate from, @RequestParam LocalDate to,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(financeReportService.getChainReport(from, to, actor.userId()));
    }
}
