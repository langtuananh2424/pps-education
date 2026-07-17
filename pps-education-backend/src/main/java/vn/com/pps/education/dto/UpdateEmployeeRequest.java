package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/**
 * UC-08 Main Flow bước 2: cập nhật thông tin cá nhân của hồ sơ đã có.
 * employeeCode/userId bất biến. positionId null = bỏ trống chức vụ — vai
 * trò do hệ thống từng tự gán theo chức vụ cũ (nếu có) sẽ bị thu hồi
 * (UC-08 A5, FR-HRM-06/UC-52).
 */
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
        Long positionId,
        Long departmentId,
        @NotNull Boolean isManagement,
        Boolean isDefaultShiftRequired,
        @NotBlank String status,
        LocalDate terminationDate
) {}
