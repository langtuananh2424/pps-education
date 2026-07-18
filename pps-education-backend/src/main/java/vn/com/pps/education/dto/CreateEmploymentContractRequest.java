package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** UC-08 Main Flow bước 3: thêm 1 hợp đồng lao động mới cho nhân sự. */
public record CreateEmploymentContractRequest(
        @NotBlank String contractNumber,
        @NotBlank String contractType,
        @NotNull LocalDate startDate,
        LocalDate endDate, // NULL nếu INDEFINITE
        @NotNull @Positive BigDecimal baseSalary,
        @NotBlank String salaryType,
        @NotBlank String status,
        String fileUrl
) {}
