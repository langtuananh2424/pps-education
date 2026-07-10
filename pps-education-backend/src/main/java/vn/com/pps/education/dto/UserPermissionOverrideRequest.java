package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;

/** UC-04 bước 3: tùy chỉnh quyền riêng cho 1 tài khoản. */
public record UserPermissionOverrideRequest(
        @NotBlank @Pattern(regexp = "GRANT|REVOKE") String overrideType,
        @NotBlank String reason,
        OffsetDateTime expiresAt
) {}
