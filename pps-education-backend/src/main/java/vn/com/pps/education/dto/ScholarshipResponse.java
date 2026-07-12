package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ScholarshipResponse(
        Long id,
        Long studentId,
        String code,
        String name,
        String discountType,
        BigDecimal discountValue,
        String applicableScope,
        LocalDate validFrom,
        LocalDate validTo,
        BigDecimal maxAmount,
        String status,
        Long approvedBy,
        OffsetDateTime approvedAt
) {}
