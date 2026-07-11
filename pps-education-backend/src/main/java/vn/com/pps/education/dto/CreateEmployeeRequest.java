package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/** UC-08 Main Flow bước 1-2: khởi tạo hồ sơ nhân sự mới cho 1 user đã có sẵn. */
public record CreateEmployeeRequest(
        @NotNull Long userId,
        @NotBlank String employeeCode,
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
        Boolean isDefaultShiftRequired,
        @NotNull LocalDate hireDate
) {}
