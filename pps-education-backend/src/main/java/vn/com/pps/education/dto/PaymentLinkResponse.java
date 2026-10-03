package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Link/QR thanh toán payOS cho phần còn nợ của hóa đơn. {@code qrCode} là chuỗi VietQR (EMV) để frontend
 * tự vẽ ảnh QR; {@code checkoutUrl} là trang thanh toán của payOS (dự phòng khi không quét được QR).
 */
public record PaymentLinkResponse(
        Long invoiceId,
        Long orderCode,
        BigDecimal amount,
        String status,
        String checkoutUrl,
        String qrCode,
        String bin,
        String accountNumber,
        String accountName,
        String description,
        OffsetDateTime expiresAt
) {}
