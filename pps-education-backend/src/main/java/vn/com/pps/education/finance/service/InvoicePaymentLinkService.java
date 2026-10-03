package vn.com.pps.education.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.finance.domain.Invoice;
import vn.com.pps.education.finance.domain.InvoicePaymentLink;
import vn.com.pps.education.finance.dto.BankWebhookPaymentRequest;
import vn.com.pps.education.finance.dto.PaymentLinkResponse;
import vn.com.pps.education.exception.PaymentGatewayException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.finance.repository.InvoicePaymentLinkRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UC-30 Main Flow bước 3-6 qua cổng thanh toán QR bên thứ ba (bổ sung ngoài SDD gốc, đã thống nhất với
 * người dùng 2026-10-03): sinh link/QR VietQR động cho PHẦN CÒN NỢ của hóa đơn (bước 3-5) và nhận webhook
 * để gạch nợ (bước 6-7, tái dùng {@link InvoiceService#confirmBankWebhook} nên giữ nguyên idempotency,
 * khoá dòng hóa đơn, lịch sử hệ thống). Không biết nhà cung cấp cụ thể: mọi thứ riêng của từng nhà cung
 * cấp nằm sau {@link PaymentGateway}. order_code (V211) là khoá đối chiếu giữa nhà cung cấp và hóa đơn.
 */
@Service
public class InvoicePaymentLinkService {

    private static final Logger log = LoggerFactory.getLogger(InvoicePaymentLinkService.class);
    private static final Duration LINK_TTL = Duration.ofMinutes(30);
    /** Link sắp hết hạn trong khoảng này thì không dùng lại (tránh Phụ huynh quét đúng lúc link hết hạn). */
    private static final Duration REUSE_MARGIN = Duration.ofMinutes(2);

    private final List<PaymentGateway> gateways;
    private final PaymentGateway activeGateway;
    private final InvoiceService invoiceService;
    private final InvoicePaymentLinkRepository linkRepository;
    private final String returnUrl;
    private final String cancelUrl;

    public InvoicePaymentLinkService(List<PaymentGateway> gateways,
                                     InvoiceService invoiceService,
                                     InvoicePaymentLinkRepository linkRepository,
                                     @Value("${app.finance.payment-provider:PAYOS}") String activeProvider,
                                     @Value("${app.finance.payment-return-url:}") String returnUrl,
                                     @Value("${app.finance.payment-cancel-url:}") String cancelUrl) {
        this.gateways = gateways;
        this.activeGateway = gateways.stream()
                .filter(g -> g.providerCode().equalsIgnoreCase(activeProvider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Không có cổng thanh toán nào khớp app.finance.payment-provider="
                        + activeProvider + " (đang có: " + gateways.stream().map(PaymentGateway::providerCode).toList() + ")."));
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
        if (!activeGateway.isConfigured() || isBlank(returnUrl) || isBlank(cancelUrl)) {
            throw new PaymentGatewayException("Cổng thanh toán chưa được cấu hình, vui lòng liên hệ trung tâm.");
        }
        Invoice invoice = invoiceService.requirePayableInvoice(invoiceId, actorUserId);
        BigDecimal outstanding = invoice.getOutstandingAmount();
        OffsetDateTime now = OffsetDateTime.now();
        String provider = activeGateway.providerCode();

        List<InvoicePaymentLink> pending = linkRepository.findByInvoiceIdAndStatus(invoiceId, InvoicePaymentLink.Status.PENDING);
        for (InvoicePaymentLink link : pending) {
            if (link.getProvider().equals(provider) && link.getAmount().compareTo(outstanding) == 0
                    && link.getExpiresAt().isAfter(now.plus(REUSE_MARGIN))) {
                return toResponse(link);
            }
        }

        long orderCode = newOrderCode(provider);
        OffsetDateTime expiresAt = now.plus(LINK_TTL);
        String description = "PPS" + String.format("%06d", orderCode % 1_000_000);
        PaymentGateway.Link created = activeGateway.createPaymentLink(new PaymentGateway.LinkRequest(
                orderCode, outstanding, description, returnUrl, cancelUrl, expiresAt.toEpochSecond()));

        InvoicePaymentLink link = new InvoicePaymentLink();
        link.setInvoice(invoice);
        link.setProvider(provider);
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
                gatewayFor(stale.getProvider()).cancelPaymentLink(stale.getOrderCode(), "Thay bằng link mới");
            } catch (PaymentGatewayException | ResourceNotFoundException e) {
                // Link cũ tự hết hạn sau tối đa 30 phút; webhook của nó (nếu có) vẫn đối chiếu được qua order_code.
                log.warn("Không hủy được link cũ provider={} orderCode={}: {}", stale.getProvider(), stale.getOrderCode(), e.getMessage());
            }
        }
        return toResponse(link);
    }

    /**
     * Webhook của nhà cung cấp {@code provider} (permitAll, SecurityConfig). Chữ ký sai -> 401 (do gateway).
     * Payload hợp lệ nhưng không phải giao dịch thành công, hoặc order_code lạ (kể cả payload thử khi khai
     * báo webhook) -> bỏ qua và vẫn trả 200 để nhà cung cấp không gửi lại.
     */
    @Transactional
    public void handleWebhook(String provider, JsonNode webhook) {
        PaymentGateway gateway = gatewayFor(provider);
        Optional<PaymentGateway.PaidEvent> event = gateway.parseWebhook(webhook);
        if (event.isEmpty()) {
            return;
        }
        PaymentGateway.PaidEvent paid = event.get();
        InvoicePaymentLink link = linkRepository.findByProviderAndOrderCode(gateway.providerCode(), paid.orderCode()).orElse(null);
        if (link == null) {
            log.info("Bỏ qua webhook {} với orderCode={} không thuộc hóa đơn nào.", gateway.providerCode(), paid.orderCode());
            return;
        }
        if (link.getStatus() == InvoicePaymentLink.Status.PAID) {
            return;
        }

        Invoice invoice = link.getInvoice();
        link.setStatus(InvoicePaymentLink.Status.PAID);
        link.setPaidAt(paid.paidAt());
        linkRepository.save(link);

        if (invoice.getStatus() == Invoice.Status.CANCELLED || invoice.getDeletedAt() != null) {
            // Tiền đã vào tài khoản nhưng hóa đơn đã hủy: không gạch nợ tự động, Kế toán hoàn tiền thủ công.
            log.error("{} orderCode={} đã thu {} cho hóa đơn {} đã hủy — cần hoàn tiền thủ công.",
                    gateway.providerCode(), link.getOrderCode(), paid.amount(), invoice.getInvoiceNumber());
            return;
        }
        String reference = paid.reference() == null ? "" : paid.reference();
        String transactionId = gateway.providerCode() + "-" + link.getOrderCode() + (reference.isBlank() ? "" : "-" + reference);
        invoiceService.confirmBankWebhook(new BankWebhookPaymentRequest(
                invoice.getInvoiceNumber(), paid.amount(), transactionId, paid.paidAt()));
    }

    private PaymentGateway gatewayFor(String provider) {
        return gateways.stream()
                .filter(g -> g.providerCode().equalsIgnoreCase(provider))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("error.paymentProvider.notFound", new Object[]{provider},
                        "Không có cổng thanh toán " + provider + "."));
    }

    private long newOrderCode(String provider) {
        for (int attempt = 0; attempt < 5; attempt++) {
            long code = OffsetDateTime.now().toEpochSecond() * 1000 + ThreadLocalRandom.current().nextInt(1000);
            if (linkRepository.findByProviderAndOrderCode(provider, code).isEmpty()) {
                return code;
            }
        }
        throw new PaymentGatewayException("Không sinh được mã đơn thanh toán, vui lòng thử lại.");
    }

    private PaymentLinkResponse toResponse(InvoicePaymentLink l) {
        return new PaymentLinkResponse(l.getInvoice().getId(), l.getOrderCode(), l.getAmount(), l.getStatus().name(),
                l.getCheckoutUrl(), l.getQrCode(), l.getBin(), l.getAccountNumber(), l.getAccountName(),
                l.getDescription(), l.getExpiresAt());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
