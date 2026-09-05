package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * UC-01 Main Flow bước 4 — đăng nhập nhanh qua Google id_token.
 * screenResolution/browserLanguage/timezone: xem ghi chú ở LoginRequest.
 */
public record GoogleLoginRequest(
        @NotBlank String idToken,
        String screenResolution,
        String browserLanguage,
        String timezone
) {}
