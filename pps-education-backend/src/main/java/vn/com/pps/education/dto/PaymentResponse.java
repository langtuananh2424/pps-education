package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentResponse(
        Long id,
        String paymentReference,
        Long invoiceId,
        BigDecimal amount,
        String paymentMethod,
        OffsetDateTime paidAt,
        String bankTransactionId,
        String receiptNumber,
        String status,
        Long confirmedBy,
        OffsetDateTime confirmedAt
) {}
