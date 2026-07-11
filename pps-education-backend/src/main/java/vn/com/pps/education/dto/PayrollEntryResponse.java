package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayrollEntryResponse(
        Long id,
        String periodCode,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        Long employeeId,
        String employeeCode,
        String employeeFullName,
        BigDecimal baseSalary,
        BigDecimal teachingHours,
        BigDecimal hourlyRate,
        BigDecimal workDays,
        BigDecimal bonuses,
        BigDecimal penalties,
        BigDecimal tax,
        BigDecimal socialInsurance,
        BigDecimal healthInsurance,
        BigDecimal unemploymentInsurance,
        BigDecimal grossSalary,
        BigDecimal totalDeductions,
        BigDecimal netSalary,
        String status,
        /** A1 — true nếu kỳ được yêu cầu chưa có dữ liệu, đây là kỳ gần nhất đã có. */
        boolean fallbackToLatestAvailable
) {}
