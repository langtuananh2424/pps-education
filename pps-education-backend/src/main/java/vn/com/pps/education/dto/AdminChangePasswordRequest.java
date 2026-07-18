package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UC-45 A4: Quản trị viên (quyền user.manage) đổi mật khẩu cho một tài
 * khoản khác — không cần biết mật khẩu hiện tại của tài khoản đó.
 */
public record AdminChangePasswordRequest(
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {}
