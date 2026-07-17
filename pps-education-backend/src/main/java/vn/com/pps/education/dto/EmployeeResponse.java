package vn.com.pps.education.dto;

import java.time.LocalDate;

public record EmployeeResponse(
        Long id,
        Long userId,
        String fullName,
        String employeeCode,
        LocalDate dateOfBirth,
        String idCardNumber,
        LocalDate idCardIssuedDate,
        String idCardIssuedPlace,
        String permanentAddress,
        String currentAddress,
        String bankAccountNumber,
        String bankName,
        String taxCode,
        String socialInsuranceNumber,
        String employeeType,
        Long positionId,
        String positionName,
        Long departmentId,
        boolean isManagement,
        boolean isDefaultShiftRequired,
        LocalDate hireDate,
        LocalDate terminationDate,
        String status
) {}
