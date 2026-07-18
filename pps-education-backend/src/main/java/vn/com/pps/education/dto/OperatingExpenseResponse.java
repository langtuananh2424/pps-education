package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OperatingExpenseResponse(
        Long id,
        String expenseNumber,
        String expenseCategoryCode,
        String expenseCategoryName,
        Long siteId,
        LocalDate expenseDate,
        BigDecimal amount,
        String description,
        String paymentMethod,
        String supplierName,
        String receiptNumber,
        String fileUrl,
        String status,
        Long recordedBy,
        Long approvedBy,
        String rejectionReason
) {}
