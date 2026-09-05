package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * UC-01 bước 2: đăng nhập bằng Tài khoản/Mật khẩu.
 * screenResolution/browserLanguage/timezone: bổ sung ngoài SDD gốc (đã xác
 * nhận với người dùng 2026-09-05) — metadata thiết bị cho lịch sử đăng nhập
 * ở Quản lý người dùng, optional vì client cũ không gửi vẫn đăng nhập được.
 */
public record LoginRequest(
        @NotBlank String usernameOrEmail,
        @NotBlank String password,
        String screenResolution,
        String browserLanguage,
        String timezone
) {}
