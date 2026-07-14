package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UC-45 Main Flow: tự đổi mật khẩu của chính tài khoản đang đăng nhập.
 * currentPassword bỏ trống chỉ hợp lệ khi tài khoản chưa từng có mật khẩu
 * (chỉ đăng nhập Google — UC-45 A3); còn lại bắt buộc và phải khớp.
 */
public record ChangeOwnPasswordRequest(
        String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {}
