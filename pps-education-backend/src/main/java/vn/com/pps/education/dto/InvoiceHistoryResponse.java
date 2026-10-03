package vn.com.pps.education.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/** Dòng lịch sử hóa đơn (invoices_history) — changedById/changedByName NULL = hệ thống tự động (cron, webhook). */
public record InvoiceHistoryResponse(
        Long id,
        String action,
        Long changedById,
        String changedByName,
        Map<String, Object> details,
        OffsetDateTime createdAt
) {}
