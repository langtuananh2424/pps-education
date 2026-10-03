package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.PaymentLinkResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.InvoicePaymentLinkService;

import java.util.Map;

/** UC-30 Main Flow bước 3-6: thanh toán QR qua cổng bên thứ ba — xem Javadoc InvoicePaymentLinkService. */
@RestController
public class PaymentLinkController {

    private final InvoicePaymentLinkService invoicePaymentLinkService;

    public PaymentLinkController(InvoicePaymentLinkService invoicePaymentLinkService) {
        this.invoicePaymentLinkService = invoicePaymentLinkService;
    }

    /** Phụ huynh liên kết với học sinh của hóa đơn lấy link/QR thanh toán phần còn nợ. */
    @PostMapping("/api/finance/invoices/{id}/payment-link")
    public ResponseEntity<PaymentLinkResponse> createPaymentLink(@PathVariable Long id,
                                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(invoicePaymentLinkService.createOrReuseLink(id, actor.userId()));
    }

    /**
     * Webhook của cổng thanh toán {@code provider} (VD payos) — permitAll (SecurityConfig), mỗi cổng tự
     * xác thực bằng chữ ký riêng. Định tuyến theo provider để giao dịch của cổng cũ vẫn ghi nhận được sau
     * khi đổi cổng đang dùng.
     */
    @PostMapping("/api/webhooks/payment/{provider}")
    public ResponseEntity<Map<String, Object>> paymentWebhook(@PathVariable String provider, @RequestBody JsonNode body) {
        invoicePaymentLinkService.handleWebhook(provider, body);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
