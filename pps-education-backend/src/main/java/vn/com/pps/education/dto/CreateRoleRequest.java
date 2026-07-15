package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-03 bổ sung — tạo 1 vai trò (nhóm quyền) tùy chỉnh. Luôn is_system=false (11 role hệ thống chỉ seed qua V4). */
public record CreateRoleRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description
) {}
