package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-01 bước 2: đăng nhập bằng Tài khoản/Mật khẩu. */
public record LoginRequest(
        @NotBlank String usernameOrEmail,
        @NotBlank String password
) {}
