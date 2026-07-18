package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** UC-08 Main Flow bước 3: chỉnh sửa hợp đồng đã có. contractNumber bất biến. */
public record UpdateEmploymentContractRequest(
        @NotBlank String contractType,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull @Positive BigDecimal baseSalary,
        @NotBlank String salaryType,
        @NotBlank String status,
        String fileUrl
) {}
