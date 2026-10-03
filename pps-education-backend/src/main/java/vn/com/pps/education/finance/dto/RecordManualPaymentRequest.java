package vn.com.pps.education.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** UC-30 A2: Kế toán ghi nhận thanh toán thủ công (tiền mặt/chuyển khoản thông thường). */
public record RecordManualPaymentRequest(
        @NotNull @Positive BigDecimal amount,
        @NotBlank String paymentMethod,
        OffsetDateTime paidAt,
        String receiptNumber
) {}
