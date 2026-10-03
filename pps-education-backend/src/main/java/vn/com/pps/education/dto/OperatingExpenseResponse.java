package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

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
        String rejectionReason,
        // Bổ sung 2026-10-03 cho màn Chi phí vận hành — siteName NULL = chi dùng chung (UC-31 A1).
        String siteName,
        String recordedByName,
        String approvedByName,
        OffsetDateTime createdAt
) {}
