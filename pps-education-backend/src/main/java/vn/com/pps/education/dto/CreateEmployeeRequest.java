package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/**
 * UC-08 Main Flow bước 1-2: khởi tạo hồ sơ nhân sự mới. Cung cấp ĐÚNG 1
 * trong 2 (kiểm tra ở EmployeeService):
 * - userId: gắn hồ sơ vào tài khoản đã có sẵn.
 * - newAccount: nhân sự chưa có tài khoản — tạo tài khoản kèm hồ sơ trong
 *   cùng 1 transaction (cơ chế UC-43/FR-USR-01, dưới thẩm quyền hrm.manage
 *   của luồng này — xem ghi chú UC-08).
 */
public record CreateEmployeeRequest(
        Long userId,
        @Valid CreateUserRequest newAccount,
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
