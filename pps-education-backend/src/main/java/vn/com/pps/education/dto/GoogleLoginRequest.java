package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-01 Main Flow bước 4 — đăng nhập nhanh qua Google id_token. */
public record GoogleLoginRequest(
        @NotBlank String idToken
) {}
