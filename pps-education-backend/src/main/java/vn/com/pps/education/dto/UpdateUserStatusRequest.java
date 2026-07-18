package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** UC-47 Main Flow bước 2 / A1: chuyển trạng thái tài khoản. */
public record UpdateUserStatusRequest(
        @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE|SUSPENDED") String status
) {}
