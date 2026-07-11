package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmploymentContractResponse(
        Long id,
        Long employeeId,
        String contractNumber,
        String contractType,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal baseSalary,
        String salaryType,
        String status,
        String fileUrl
) {}
