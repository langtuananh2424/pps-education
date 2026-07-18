package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** UC-08 Main Flow bước 4: ghi nhận 1 sự kiện khen thưởng/kỷ luật. */
public record CreateCommendationRequest(
        @NotBlank String recordType,
        @NotNull LocalDate recordDate,
        @NotBlank String title,
        BigDecimal amount
) {}
