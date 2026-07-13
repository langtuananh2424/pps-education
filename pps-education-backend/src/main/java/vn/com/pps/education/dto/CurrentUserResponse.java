package vn.com.pps.education.dto;

import java.util.List;

/**
 * GET /api/auth/me — hồ sơ tài khoản đang đăng nhập, phục vụ hiển thị
 * sidebar/header phía frontend. Không dùng lại UserResponse (UC-43) vì DTO
 * đó mang field admin-facing (passwordSet, googleLinked) không phù hợp lộ
 * ra ở endpoint tự-phục-vụ này.
 */
public record CurrentUserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phone,
        String departmentName,
        List<String> roleCodes
) {}
