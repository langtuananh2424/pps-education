package vn.com.pps.education.dto;

import java.time.LocalDate;

public record EmployeeShiftResponse(
        Long id,
        Long employeeId,
        Long shiftId,
        String shiftName,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
