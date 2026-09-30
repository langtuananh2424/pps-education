package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.com.pps.education.domain.Role;

/**
 * UC-03 bổ sung — tạo 1 vai trò (nhóm quyền) tùy chỉnh. Luôn is_system=false (role hệ thống chỉ seed qua migration).
 *
 * dataScope, copyFromRoleId (V202, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-30): phạm vi
 * dữ liệu của vai trò và vai trò mẫu để sao chép sẵn toàn bộ quyền ("Tạo từ mẫu"); copyFromRoleId=null
 * thì vai trò bắt đầu không có quyền nào.
 */
public record CreateRoleRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @NotNull Role.DataScope dataScope,
        Long copyFromRoleId
) {}
