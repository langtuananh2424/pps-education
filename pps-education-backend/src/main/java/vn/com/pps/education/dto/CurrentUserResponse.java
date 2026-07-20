package vn.com.pps.education.dto;

import java.util.List;
import java.util.Set;

/**
 * GET /api/auth/me — hồ sơ tài khoản đang đăng nhập, phục vụ hiển thị
 * sidebar/header phía frontend. Không dùng lại UserResponse (UC-43) vì DTO
 * đó mang field admin-facing (passwordSet, googleLinked) không phù hợp lộ
 * ra ở endpoint tự-phục-vụ này.
 *
 * studentId: null trừ khi tài khoản có hồ sơ Student liên kết (students.user_id) —
 * cho phép Học sinh tự đăng nhập tra ra studentId của chính mình để gọi tiếp các
 * API Portal (UC-42 chọn lớp đang xem, UC-23a/24/26/27 xem bài giảng/điểm/chuyên
 * cần...), tương tự cách Phụ huynh tra qua GET /api/portal/parent/children.
 *
 * permissions: effective permissions (hợp nhất role_permissions +
 * user_permission_overrides — bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng) của chính tài khoản đang gọi, tái dùng đúng
 * PermissionEvaluationService.getEffectivePermissions đã dùng để enforce
 * @PreAuthorize("hasPermission(...)") ở PpsPermissionEvaluator — cho phép
 * Admin FE ẩn/hiện menu/nút hành động theo đúng quyền thật trong DB thay vì
 * bảng hardcode tĩnh phía client.
 */
public record CurrentUserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phone,
        String departmentName,
        List<String> roleCodes,
        Long studentId,
        Set<String> permissions
) {}
