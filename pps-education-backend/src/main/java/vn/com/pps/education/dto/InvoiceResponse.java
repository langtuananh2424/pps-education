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
        List<InvoiceItemResponse> items,
        // Bổ sung 2026-10-03 cho màn Thu phí & hóa đơn phía Kế toán — NULL nếu hóa đơn không gắn ghi danh lớp.
        Long classId,
        String className,
        Long siteId,
        String siteName
) {}
