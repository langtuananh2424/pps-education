package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** UC-31 Main Flow bước 2-3. siteId để trống = chi dùng chung nhiều điểm trường (A1). */
public record CreateOperatingExpenseRequest(
        @NotBlank String expenseCategoryCode,
        Long siteId,
        @NotNull LocalDate expenseDate,
        @NotNull BigDecimal amount,
        @NotBlank String description,
        @NotBlank String paymentMethod,
        String supplierName,
        String receiptNumber,
        String fileUrl
) {}
