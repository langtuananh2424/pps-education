package vn.com.pps.education.dto;

import java.time.LocalDate;

public record StudentTransferHistoryResponse(
        Long id,
        Long studentId,
        String transferType,
        Long fromClassId,
        Long toClassId,
        Long fromSiteId,
        Long toSiteId,
        LocalDate effectiveDate,
        String reason,
        Long approvedBy
) {}
