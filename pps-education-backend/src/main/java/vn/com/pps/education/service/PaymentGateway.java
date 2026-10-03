package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Cổng thanh toán QR của bên thứ ba cho UC-30 (hiện có {@link PayosGateway}). Thêm nhà cung cấp mới =
 * thêm 1 class {@code @Component} triển khai interface này; chọn nhà cung cấp đang dùng để SINH link mới
 * bằng {@code app.finance.payment-provider}. Webhook định tuyến theo nhà cung cấp trên đường dẫn
 * ({@code /api/webhooks/payment/{provider}}) nên giao dịch đang bay của nhà cung cấp cũ vẫn được ghi nhận
 * sau khi đổi. Phần còn lại (bảng invoice_payment_links, gạch nợ, Portal) không biết nhà cung cấp cụ thể.
 */
public interface PaymentGateway {

    /** Yêu cầu tạo link/QR cho 1 khoản thanh toán. {@code orderCode} duy nhất theo nhà cung cấp. */
    record LinkRequest(long orderCode, BigDecimal amount, String description, String returnUrl,
                       String cancelUrl, long expiredAtEpochSeconds) {}

    /** Link/QR đã tạo. {@code qrCode} là chuỗi VietQR (EMV) để Portal tự vẽ ảnh. */
    record Link(String paymentLinkId, String checkoutUrl, String qrCode, String bin,
                String accountNumber, String accountName, String description) {}

    /** Giao dịch thành công do webhook báo về. {@code reference} là mã giao dịch ngân hàng (có thể rỗng). */
    record PaidEvent(long orderCode, BigDecimal amount, String reference, OffsetDateTime paidAt) {}

    /** Mã lưu ở cột invoice_payment_links.provider và dùng trong đường dẫn webhook (không phân biệt hoa thường). */
    String providerCode();

    /** Đã đủ cấu hình (khoá API...) để tạo link chưa. */
    boolean isConfigured();

    Link createPaymentLink(LinkRequest request);

    void cancelPaymentLink(long orderCode, String reason);

    /**
     * Xác thực và đọc webhook. Sai chữ ký -> ném {@link vn.com.pps.education.exception.InvalidWebhookSecretException}.
     * Trả {@link Optional#empty()} khi payload hợp lệ nhưng không phải giao dịch thành công cần xử lý
     * (giao dịch thất bại, payload thử khi khai báo webhook...).
     */
    Optional<PaidEvent> parseWebhook(JsonNode body);
}
