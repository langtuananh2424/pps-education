package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        Long studentId,
        String studentFullName,
        String studentCode,
        Long classEnrollmentId,
        Long payerParentId,
        LocalDate billingPeriodFrom,
        LocalDate billingPeriodTo,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        String status,
        String qrCodeData,
        List<InvoiceItemResponse> items
) {}
