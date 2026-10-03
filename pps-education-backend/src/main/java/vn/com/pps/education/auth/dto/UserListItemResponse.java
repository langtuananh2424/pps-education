package vn.com.pps.education.auth.dto;

import vn.com.pps.education.permission.dto.RoleResponse;

import java.util.List;

/** UC-44 Main Flow bước 1: 1 dòng trong danh sách tài khoản — không bao giờ chứa password_hash. */
public record UserListItemResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phone,
        Long departmentId,
        String status,
        boolean isManagement,
        List<RoleResponse> roles
) {}
