package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CommendationResponse(
        Long id,
        Long employeeId,
        String recordType,
        LocalDate recordDate,
        String title,
        BigDecimal amount,
        Long decidedByUserId
) {}
