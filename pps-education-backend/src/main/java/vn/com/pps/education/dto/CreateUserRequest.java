package vn.com.pps.education.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UC-43 Main Flow bước 1-2: khởi tạo tài khoản người dùng (FR-USR-01).
 * password tùy chọn — bỏ trống (null) để tạo tài khoản chỉ đăng nhập Google
 * (password_hash = NULL, khớp theo email — UC-01 A4); nếu nhập thì tối
 * thiểu 8 ký tự (UC-43 A2). KHÔNG có trường role — gán role sau qua
 * UC-03/UC-04, không thuộc bước khởi tạo.
 */
public record CreateUserRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 20) String phone,
        @Size(min = 8, max = 72) String password,
        Long departmentId,
        Boolean isManagement
) {}
