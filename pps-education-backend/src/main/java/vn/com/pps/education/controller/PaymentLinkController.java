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
import vn.com.pps.education.service.PayosPaymentService;

import java.util.Map;

/** UC-30 Main Flow bước 3-6: thanh toán QR qua payOS — xem Javadoc PayosPaymentService. */
@RestController
public class PaymentLinkController {

    private final PayosPaymentService payosPaymentService;

    public PaymentLinkController(PayosPaymentService payosPaymentService) {
        this.payosPaymentService = payosPaymentService;
    }

    /** Phụ huynh liên kết với học sinh của hóa đơn lấy link/QR thanh toán phần còn nợ. */
    @PostMapping("/api/finance/invoices/{id}/payment-link")
    public ResponseEntity<PaymentLinkResponse> createPaymentLink(@PathVariable Long id,
                                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(payosPaymentService.createOrReuseLink(id, actor.userId()));
    }

    /** Webhook payOS (permitAll, SecurityConfig) — tự xác thực bằng chữ ký HMAC với checksum key. */
    @PostMapping("/api/webhooks/payos")
    public ResponseEntity<Map<String, Object>> payosWebhook(@RequestBody JsonNode body) {
        payosPaymentService.handleWebhook(body);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
