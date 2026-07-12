package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.BankWebhookPaymentRequest;
import vn.com.pps.education.dto.GenerateInvoicesRequest;
import vn.com.pps.education.dto.InvoiceResponse;
import vn.com.pps.education.dto.PaymentResponse;
import vn.com.pps.education.dto.RecordManualPaymentRequest;
import vn.com.pps.education.exception.InvalidWebhookSecretException;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.InvoiceService;

import java.util.List;

/** UC-30: Xem hóa đơn & thanh toán học phí (FR-FIN-01, FR-FIN-02) — xem Javadoc InvoiceService. */
@RestController
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final String bankWebhookSecret;

    public InvoiceController(InvoiceService invoiceService,
                              @Value("${app.finance.bank-webhook-secret}") String bankWebhookSecret) {
        this.invoiceService = invoiceService;
        this.bankWebhookSecret = bankWebhookSecret;
    }

    /** Main Flow bước 1 — endpoint thủ công cho Kế toán/STAFF, bổ sung cho cron tự động (FinanceSchedulerService). */
    @PostMapping("/api/finance/invoices/generate")
    @PreAuthorize("hasPermission(null, 'finance.manage')")
    public ResponseEntity<List<InvoiceResponse>> generateInvoices(@Valid @RequestBody GenerateInvoicesRequest request,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(invoiceService.generateInvoices(request, actor.userId()));
    }

    @GetMapping("/api/finance/invoices/my")
    public ResponseEntity<List<InvoiceResponse>> listMyInvoices(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(invoiceService.listMyInvoices(actor.userId()));
    }

    @GetMapping("/api/finance/invoices/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(invoiceService.getInvoice(id, actor.userId()));
    }

    /** A2: Kế toán ghi nhận thanh toán thủ công (tiền mặt/chuyển khoản thông thường). */
    @PostMapping("/api/finance/invoices/{id}/payments")
    @PreAuthorize("hasPermission(null, 'finance.manage')")
    public ResponseEntity<PaymentResponse> recordManualPayment(@PathVariable Long id,
                                                                  @Valid @RequestBody RecordManualPaymentRequest request,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(invoiceService.recordManualPayment(id, request, actor.userId()));
    }

    /**
     * Main Flow bước 5-6: webhook ngân hàng (permitAll, SecurityConfig) —
     * không có JWT vì gọi từ hệ thống ngoài, tự xác thực qua shared secret
     * X-Webhook-Secret (app.finance.bank-webhook-secret).
     */
    @PostMapping("/api/webhooks/bank-payment")
    public ResponseEntity<PaymentResponse> confirmBankWebhook(@RequestHeader("X-Webhook-Secret") String secret,
                                                                 @Valid @RequestBody BankWebhookPaymentRequest request) {
        if (!bankWebhookSecret.equals(secret)) {
            throw new InvalidWebhookSecretException("Webhook secret không hợp lệ.");
        }
        return ResponseEntity.ok(invoiceService.confirmBankWebhook(request));
    }
}
