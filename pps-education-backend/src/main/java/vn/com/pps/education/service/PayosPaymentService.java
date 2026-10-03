package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Invoice;
import vn.com.pps.education.domain.InvoicePaymentLink;
import vn.com.pps.education.dto.BankWebhookPaymentRequest;
import vn.com.pps.education.dto.PaymentLinkResponse;
import vn.com.pps.education.exception.InvalidWebhookSecretException;
import vn.com.pps.education.exception.PaymentGatewayException;
import vn.com.pps.education.repository.InvoicePaymentLinkRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UC-30 Main Flow bước 3-6 qua payOS (bổ sung ngoài SDD gốc, đã thống nhất với người dùng 2026-10-03):
 * sinh link/QR VietQR động cho PHẦN CÒN NỢ của hóa đơn (bước 3-5) và nhận webhook payOS để gạch nợ
 * (bước 6-7, tái dùng {@link InvoiceService#confirmBankWebhook} nên giữ nguyên idempotency, khoá dòng
 * hóa đơn, lịch sử hệ thống). order_code (V211) là khoá đối chiếu giữa payOS và hóa đơn.
 */
@Service
public class PayosPaymentService {

    private static final Logger log = LoggerFactory.getLogger(PayosPaymentService.class);
    private static final String PROVIDER = "PAYOS";
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter PAYOS_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration LINK_TTL = Duration.ofMinutes(30);
    /** Link sắp hết hạn trong khoảng này thì không dùng lại (tránh Phụ huynh quét đúng lúc link hết hạn). */
    private static final Duration REUSE_MARGIN = Duration.ofMinutes(2);

    private final PayosClient payosClient;
    private final InvoiceService invoiceService;
    private final InvoicePaymentLinkRepository linkRepository;
    private final String returnUrl;
    private final String cancelUrl;

    public PayosPaymentService(PayosClient payosClient,
                               InvoiceService invoiceService,
                               InvoicePaymentLinkRepository linkRepository,
                               @Value("${app.finance.payos.return-url}") String returnUrl,
                               @Value("${app.finance.payos.cancel-url}") String cancelUrl) {
        this.payosClient = payosClient;
        this.invoiceService = invoiceService;
        this.linkRepository = linkRepository;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
    }

    /**
     * Phụ huynh bấm "Thanh toán QR": trả link còn hạn đúng số tiền còn nợ nếu có, ngược lại sinh link mới
     * và hủy các link cũ còn chờ (số nợ đã đổi hoặc sắp hết hạn).
     */
    @Transactional
    public PaymentLinkResponse createOrReuseLink(Long invoiceId, Long actorUserId) {
        if (!payosClient.isConfigured() || returnUrl == null || returnUrl.isBlank() || cancelUrl == null || cancelUrl.isBlank()) {
            throw new PaymentGatewayException("Cổng thanh toán payOS chưa được cấu hình, vui lòng liên hệ trung tâm.");
        }
        Invoice invoice = invoiceService.requirePayableInvoice(invoiceId, actorUserId);
        BigDecimal outstanding = invoice.getOutstandingAmount();
        OffsetDateTime now = OffsetDateTime.now();

        List<InvoicePaymentLink> pending = linkRepository.findByInvoiceIdAndStatus(invoiceId, InvoicePaymentLink.Status.PENDING);
        for (InvoicePaymentLink link : pending) {
            if (link.getAmount().compareTo(outstanding) == 0 && link.getExpiresAt().isAfter(now.plus(REUSE_MARGIN))) {
                return toResponse(link);
            }
        }

        long orderCode = newOrderCode();
        OffsetDateTime expiresAt = now.plus(LINK_TTL);
        String description = "PPS" + String.format("%06d", orderCode % 1_000_000);
        PayosClient.PaymentLink created = payosClient.createPaymentLink(orderCode, outstanding, description,
                returnUrl, cancelUrl, expiresAt.toEpochSecond());

        InvoicePaymentLink link = new InvoicePaymentLink();
        link.setInvoice(invoice);
        link.setProvider(PROVIDER);
        link.setOrderCode(orderCode);
        link.setPaymentLinkId(created.paymentLinkId());
        link.setAmount(outstanding);
        link.setCheckoutUrl(created.checkoutUrl());
        link.setQrCode(created.qrCode());
        link.setBin(created.bin());
        link.setAccountNumber(created.accountNumber());
        link.setAccountName(created.accountName());
        link.setDescription(created.description() != null ? created.description() : description);
        link.setExpiresAt(expiresAt);
        link = linkRepository.save(link);

        for (InvoicePaymentLink stale : pending) {
            stale.setStatus(InvoicePaymentLink.Status.CANCELLED);
            linkRepository.save(stale);
            try {
                payosClient.cancelPaymentLink(stale.getOrderCode(), "Thay bằng link mới");
            } catch (PaymentGatewayException e) {
                // Link cũ tự hết hạn sau tối đa 30 phút; webhook của nó (nếu có) vẫn đối chiếu được qua order_code.
                log.warn("Không hủy được link payOS cũ orderCode={}: {}", stale.getOrderCode(), e.getMessage());
            }
        }
        return toResponse(link);
    }

    /**
     * Webhook payOS (permitAll, SecurityConfig). Chữ ký sai -> 401. Payload hợp lệ nhưng không phải giao
     * dịch thành công, hoặc order_code lạ (kể cả payload thử khi xác nhận webhook trên dashboard payOS) ->
     * bỏ qua và vẫn trả 200 để payOS không gửi lại.
     */
    @Transactional
    public void handleWebhook(JsonNode webhook) {
        if (!payosClient.verifyWebhookSignature(webhook)) {
            throw new InvalidWebhookSecretException("Chữ ký webhook payOS không hợp lệ.");
        }
        JsonNode data = webhook.path("data");
        if (!webhook.path("success").asBoolean(false) || !"00".equals(data.path("code").asText())) {
            return;
        }
        InvoicePaymentLink link = linkRepository.findByProviderAndOrderCode(PROVIDER, data.path("orderCode").asLong())
                .orElse(null);
        if (link == null) {
            log.info("Bỏ qua webhook payOS với orderCode={} không thuộc hóa đơn nào.", data.path("orderCode").asText());
            return;
        }
        if (link.getStatus() == InvoicePaymentLink.Status.PAID) {
            return;
        }

        Invoice invoice = link.getInvoice();
        BigDecimal amount = new BigDecimal(data.path("amount").asText());
        OffsetDateTime paidAt = parsePaidAt(data.path("transactionDateTime").asText(null));
        link.setStatus(InvoicePaymentLink.Status.PAID);
        link.setPaidAt(paidAt);
        linkRepository.save(link);

        if (invoice.getStatus() == Invoice.Status.CANCELLED || invoice.getDeletedAt() != null) {
            // Tiền đã vào tài khoản nhưng hóa đơn đã hủy: không gạch nợ tự động, Kế toán hoàn tiền thủ công.
            log.error("payOS orderCode={} đã thu {} cho hóa đơn {} đã hủy — cần hoàn tiền thủ công.",
                    link.getOrderCode(), amount, invoice.getInvoiceNumber());
            return;
        }
        String reference = data.path("reference").asText("");
        String transactionId = "PAYOS-" + link.getOrderCode() + (reference.isBlank() ? "" : "-" + reference);
        invoiceService.confirmBankWebhook(new BankWebhookPaymentRequest(
                invoice.getInvoiceNumber(), amount, transactionId, paidAt));
    }

    private long newOrderCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            long code = OffsetDateTime.now().toEpochSecond() * 1000 + ThreadLocalRandom.current().nextInt(1000);
            if (linkRepository.findByProviderAndOrderCode(PROVIDER, code).isEmpty()) {
                return code;
            }
        }
        throw new PaymentGatewayException("Không sinh được mã đơn thanh toán, vui lòng thử lại.");
    }

    private OffsetDateTime parsePaidAt(String transactionDateTime) {
        if (transactionDateTime == null || transactionDateTime.isBlank()) {
            return OffsetDateTime.now();
        }
        try {
            return LocalDateTime.parse(transactionDateTime, PAYOS_TIME).atZone(APP_ZONE).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            return OffsetDateTime.now();
        }
    }

    private PaymentLinkResponse toResponse(InvoicePaymentLink l) {
        return new PaymentLinkResponse(l.getInvoice().getId(), l.getOrderCode(), l.getAmount(), l.getStatus().name(),
                l.getCheckoutUrl(), l.getQrCode(), l.getBin(), l.getAccountNumber(), l.getAccountName(),
                l.getDescription(), l.getExpiresAt());
    }
}
