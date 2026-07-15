package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UC-49 Main Flow bước 2: sửa hồ sơ tài khoản (FR-USR-05). KHÔNG có
 * username/email/password/status — 3 trường đó thuộc UC-43/UC-45/UC-47,
 * không thuộc phạm vi UC-49. departmentId = null nghĩa là bỏ trống phòng
 * ban (A1).
 */
public record UpdateUserRequest(
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 20) String phone,
        Long departmentId,
        @NotNull Boolean isManagement
) {}
