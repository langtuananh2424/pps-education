package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/** UC-08 Main Flow bước 2: cập nhật thông tin cá nhân của hồ sơ đã có. employeeCode/userId bất biến. */
public record UpdateEmployeeRequest(
        @NotNull @Past LocalDate dateOfBirth,
        String idCardNumber,
        LocalDate idCardIssuedDate,
        String idCardIssuedPlace,
        String permanentAddress,
        String currentAddress,
        String bankAccountNumber,
        String bankName,
        String taxCode,
        String socialInsuranceNumber,
        @NotBlank String employeeType,
        String positionTitle,
        Long departmentId,
        @NotNull Boolean isManagement,
        Boolean isDefaultShiftRequired,
        @NotBlank String status,
        LocalDate terminationDate
) {}
