package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-02 bước 4: sửa quyền — code bất biến khi đã tồn tại (không có trong DTO này). */
public record UpdatePermissionRequest(
        @NotBlank String name,
        String description
) {}
