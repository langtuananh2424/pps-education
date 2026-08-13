package vn.com.pps.education.dto;

import java.time.LocalDate;

/** Bổ sung 2026-08-13 — xem docs/uc/phan-he-04-nhan-su.md (khối bổ sung dưới UC-09). */
public record EmployeeShiftResponse(
        Long id,
        Long employeeId,
        String employeeFullName,
        Long shiftId,
        String shiftCode,
        String shiftName,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
