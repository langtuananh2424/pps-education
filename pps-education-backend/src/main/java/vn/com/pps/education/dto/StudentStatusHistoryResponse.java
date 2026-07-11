package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record StudentStatusHistoryResponse(
        Long id,
        Long studentId,
        String oldStatus,
        String newStatus,
        String reason,
        LocalDate effectiveDate,
        Long changedBy,
        OffsetDateTime changedAt
) {}
