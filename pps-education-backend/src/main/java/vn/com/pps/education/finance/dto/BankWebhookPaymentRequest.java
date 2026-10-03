package vn.com.pps.education.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * UC-30 Main Flow bước 5-6: payload webhook xác nhận thanh toán từ ngân
 * hàng. Không có đặc tả chi tiết payload thật (chưa tích hợp ngân hàng
 * thật, xem InvoiceService) — đối chiếu qua invoiceNumber (mã hóa đơn đã
 * gắn trong qr_code_data khi hiển thị QR cho Phụ huynh).
 */
public record BankWebhookPaymentRequest(
        @NotBlank String invoiceNumber,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String bankTransactionId,
        OffsetDateTime paidAt
) {}
