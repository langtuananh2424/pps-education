package vn.com.pps.education.dto;

import java.math.BigDecimal;

public record InvoiceItemResponse(
        Long id,
        String itemType,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount
) {}
