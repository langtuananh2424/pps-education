package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** UC-30 A2: Kế toán ghi nhận thanh toán thủ công (tiền mặt/chuyển khoản thông thường). */
public record RecordManualPaymentRequest(
        @NotNull BigDecimal amount,
        @NotBlank String paymentMethod,
        OffsetDateTime paidAt,
        String receiptNumber
) {}
